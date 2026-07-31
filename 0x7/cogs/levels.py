import time
import random
import discord
from discord import app_commands
from discord.ext import commands, tasks

from utils.storage import get_config, save_config, get_levels, save_levels

DROP_PROMPTS = [
    "💬 Say something! First **3** replies to this message score points.",
    "⚡ Points drop incoming! Hit **reply** on this message — fastest **3** win.",
    "🎁 Free points! Reply to this exact message to grab some — only the first **3**.",
    "🏃 Race you! Reply here and be one of the first **3** to cash in.",
    "🔔 Ding ding! Reply to this message for a shot at 10-60 points. Only **3** spots.",
]

MEDALS = {1: "🥇", 2: "🥈", 3: "🥉"}


def xp_for_level(level: int, base: int, growth: int) -> int:
    return base + level * growth


def level_color(level: int) -> discord.Color:
    """A little gradient so higher levels feel shinier."""
    if level >= 50:
        return discord.Color.from_rgb(255, 215, 0)   # gold
    if level >= 25:
        return discord.Color.from_rgb(168, 85, 247)  # purple
    if level >= 10:
        return discord.Color.from_rgb(59, 130, 246)  # blue
    return discord.Color.from_rgb(34, 197, 94)        # green


def make_bar(current: int, needed: int, length: int = 14) -> str:
    filled = 0 if needed <= 0 else min(length, round(length * current / needed))
    return "█" * filled + "░" * (length - filled)


class Levels(commands.Cog):
    """Points/leveling system. Points are earned via 'drops' the bot posts in a configured
    channel — the first 3 members to *reply* to that message win a random 10-60 points.
    Configure all of this via /panel -> Points & Levels."""

    def __init__(self, bot: commands.Bot):
        self.bot = bot
        # guild_id -> {"message_id": int, "channel_id": int, "repliers": [user_id, ...]}
        self.active_drops = {}
        self.last_drop_time = {}
        self.drop_scheduler.start()

    def cog_unload(self):
        self.drop_scheduler.cancel()

    # -------------------- user-facing --------------------
    @app_commands.command(name="rank", description="Check your (or someone else's) level and points")
    async def rank(self, interaction: discord.Interaction, member: discord.Member = None):
        member = member or interaction.user
        data = get_levels(interaction.guild.id)
        entry = data.get(str(member.id), {"xp": 0, "level": 0})
        cfg = get_config(interaction.guild.id)
        lv = cfg["levels"]
        needed = xp_for_level(entry["level"], lv["xp_per_level_base"], lv["xp_per_level_growth"])

        sorted_users = sorted(data.items(), key=lambda kv: (kv[1]["level"], kv[1]["xp"]), reverse=True)
        rank_pos = next((i + 1 for i, (uid, _) in enumerate(sorted_users) if uid == str(member.id)), None)

        embed = discord.Embed(title=f"📊 {member.display_name}'s Rank", color=level_color(entry["level"]))
        embed.add_field(name="Level", value=f"**{entry['level']}**", inline=True)
        if rank_pos:
            embed.add_field(name="Server Rank", value=f"**#{rank_pos}**", inline=True)
        embed.add_field(
            name="XP",
            value=f"`{make_bar(entry['xp'], needed)}`\n{entry['xp']}/{needed} XP",
            inline=False,
        )
        embed.set_thumbnail(url=member.display_avatar.url)
        await interaction.response.send_message(embed=embed)

    @app_commands.command(name="leaderboard", description="Show the server's top members by level/XP")
    async def leaderboard(self, interaction: discord.Interaction):
        data = get_levels(interaction.guild.id)
        if not data:
            return await interaction.response.send_message("No one has earned points yet.", ephemeral=True)
        sorted_users = sorted(data.items(), key=lambda kv: (kv[1]["level"], kv[1]["xp"]), reverse=True)[:10]
        medals = ["🥇", "🥈", "🥉"]
        lines = []
        for i, (uid, entry) in enumerate(sorted_users):
            member = interaction.guild.get_member(int(uid))
            name = member.display_name if member else f"User {uid}"
            prefix = medals[i] if i < 3 else f"**{i + 1}.**"
            lines.append(f"{prefix} {name} — Level {entry['level']} ({entry['xp']} XP)")
        embed = discord.Embed(title="🏆 Leaderboard", description="\n".join(lines), color=discord.Color.gold())
        await interaction.response.send_message(embed=embed)

    # -------------------- points drop --------------------
    async def send_drop(self, guild: discord.Guild, channel_id: int = None):
        """Posts a new points-drop message. Returns the message, or None if it couldn't be sent."""
        cfg = get_config(guild.id)
        lv = cfg["levels"]
        channel_id = channel_id or lv["drop_channel"]
        if not channel_id:
            return None
        channel = guild.get_channel(channel_id)
        if not isinstance(channel, (discord.TextChannel, discord.Thread)):
            return None

        embed = discord.Embed(
            title="💰 Points Drop!",
            description=f"{random.choice(DROP_PROMPTS)}\n\n**Range:** {lv['min_points']}-{lv['max_points']} points each",
            color=discord.Color.gold(),
        )
        embed.set_footer(text="⚡ First come, first served — reply to THIS message")
        try:
            msg = await channel.send(embed=embed)
        except discord.HTTPException:
            return None

        self.active_drops[guild.id] = {"message_id": msg.id, "channel_id": channel.id, "repliers": []}
        self.last_drop_time[guild.id] = time.time()
        return msg

    @tasks.loop(minutes=1)
    async def drop_scheduler(self):
        for guild in self.bot.guilds:
            cfg = get_config(guild.id)
            lv = cfg["levels"]
            interval = lv.get("drop_interval_minutes", 0)
            if not lv["enabled"] or not lv["drop_channel"] or not interval:
                continue
            if guild.id in self.active_drops:
                continue  # previous drop hasn't been claimed out yet
            last = self.last_drop_time.get(guild.id, 0)
            if time.time() - last >= interval * 60:
                await self.send_drop(guild)

    @drop_scheduler.before_loop
    async def before_drop_scheduler(self):
        await self.bot.wait_until_ready()

    async def award_points(self, guild: discord.Guild, member: discord.Member, points: int):
        data = get_levels(guild.id)
        entry = data.setdefault(str(member.id), {"xp": 0, "level": 0})
        entry["xp"] += points

        cfg = get_config(guild.id)
        lv = cfg["levels"]
        needed = xp_for_level(entry["level"], lv["xp_per_level_base"], lv["xp_per_level_growth"])
        leveled_up = False
        while entry["xp"] >= needed:
            entry["xp"] -= needed
            entry["level"] += 1
            leveled_up = True
            needed = xp_for_level(entry["level"], lv["xp_per_level_base"], lv["xp_per_level_growth"])
        save_levels(guild.id, data)

        if leveled_up:
            await self._announce_levelup(guild, member, entry["level"])
            reward_role_id = lv["role_rewards"].get(str(entry["level"]))
            if reward_role_id:
                role = guild.get_role(reward_role_id)
                if role:
                    try:
                        await member.add_roles(role, reason=f"Reached level {entry['level']}")
                    except discord.Forbidden:
                        pass
        return entry["level"], leveled_up

    async def _announce_levelup(self, guild: discord.Guild, member: discord.Member, new_level: int):
        cfg = get_config(guild.id)
        channel_id = cfg["levels"]["level_up_channel"]
        if not channel_id:
            return
        channel = guild.get_channel(channel_id)
        if not channel:
            return

        embed = discord.Embed(
            title="🎉 Level Up!",
            description=f"{member.mention} just leveled up to **Level {new_level}**! 🚀",
            color=level_color(new_level),
        )
        embed.set_author(name=member.display_name, icon_url=member.display_avatar.url)
        embed.set_thumbnail(url=member.display_avatar.url)
        embed.add_field(name="✨ New Level", value=str(new_level))
        embed.timestamp = discord.utils.utcnow()
        try:
            await channel.send(embed=embed)
        except discord.HTTPException:
            pass

    # -------------------- reply-to-claim listener --------------------
    @commands.Cog.listener()
    async def on_message(self, message: discord.Message):
        if message.author.bot or not message.guild:
            return
        cfg = get_config(message.guild.id)
        lv = cfg["levels"]
        if not lv["enabled"]:
            return

        drop = self.active_drops.get(message.guild.id)
        if not drop:
            return
        if not message.reference or message.reference.message_id != drop["message_id"]:
            return
        if message.channel.id != drop["channel_id"]:
            return
        if message.author.id in drop["repliers"]:
            return

        points = random.randint(lv["min_points"], lv["max_points"])
        drop["repliers"].append(message.author.id)
        place = len(drop["repliers"])

        await self.award_points(message.guild, message.author, points)

        try:
            await message.reply(
                f"{MEDALS.get(place, '🎉')} **+{points} points!** ({place}/3 claimed)",
                mention_author=False,
            )
        except discord.HTTPException:
            pass

        if place >= 3:
            del self.active_drops[message.guild.id]
            channel = message.guild.get_channel(drop["channel_id"])
            if channel:
                try:
                    orig = await channel.fetch_message(drop["message_id"])
                    closed_embed = discord.Embed(
                        title="✅ Points Drop — Claimed!",
                        description="All 3 spots were claimed. Watch this channel for the next one!",
                        color=discord.Color.dark_grey(),
                    )
                    await orig.edit(embed=closed_embed)
                except discord.HTTPException:
                    pass


async def setup(bot: commands.Bot):
    await bot.add_cog(Levels(bot))
