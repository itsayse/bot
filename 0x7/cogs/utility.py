import discord
from discord import app_commands
from discord.ext import commands
from datetime import timedelta

from utils.checks import is_mod
from utils.storage import get_config, save_config
from utils import vibe


class Utility(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot

    async def cog_app_command_error(self, interaction: discord.Interaction, error: app_commands.AppCommandError):
        if isinstance(error, app_commands.CheckFailure):
            await interaction.response.send_message(f"⛔ {error}", ephemeral=True)
        else:
            await interaction.response.send_message(f"⚠️ {error}", ephemeral=True)

    def _above_me(self, guild: discord.Guild, member: discord.Member) -> bool:
        return member.top_role >= guild.me.top_role

    # -------------------- pfp lookup --------------------
    @app_commands.command(name="pfp", description="Look up a member's profile picture")
    @app_commands.describe(member="Whose avatar to look up (defaults to you)", server_avatar="Show their server-specific avatar if they have one")
    async def pfp(self, interaction: discord.Interaction, member: discord.Member = None, server_avatar: bool = False):
        member = member or interaction.user
        avatar = member.guild_avatar if (server_avatar and member.guild_avatar) else member.avatar or member.default_avatar

        embed = discord.Embed(title=f"{member.display_name}'s avatar", color=member.color if member.color.value else discord.Color.blurple())
        embed.set_image(url=avatar.url)
        formats = ["png", "jpg", "webp"] + (["gif"] if avatar.is_animated() else [])
        links = " • ".join(f"[{fmt}]({avatar.with_format(fmt).url})" for fmt in formats)
        embed.add_field(name="Links", value=links, inline=False)
        await interaction.response.send_message(embed=embed)

    # -------------------- help --------------------
    @app_commands.command(name="help", description="Show what this bot can do")
    async def help_cmd(self, interaction: discord.Interaction):
        embed = discord.Embed(
            title="🤖 Bot Commands",
            description="Almost everything is configured through one place: **`/panel`** ✨\n"
                        "It opens an interactive menu — no need to memorize slash commands.",
            color=discord.Color.blurple(),
        )
        embed.add_field(
            name="⚙️ Configuration",
            value="`/panel` — open the live config panel (owner/admin/bot-admin roles only)\n"
                  "Covers: General & Roles, Automod, Auto-Role, Anti-Raid/Nuke, Points & Levels, AFK Voice, Tickets, Verification, Welcome.",
            inline=False,
        )
        embed.add_field(name="📈 Points & Levels", value="`/rank` `/leaderboard`\nPoints come from **drops** the bot posts — reply first to win!", inline=False)
        embed.add_field(name="🖼️ Profile", value="`/pfp`", inline=False)
        embed.add_field(
            name="🎫 Tickets & 🔒 Verification",
            value="`/ticket setup` `/ticket supportrole` `/ticket logchannel` `/ticket add` `/ticket close`\n`/verify setup`",
            inline=False,
        )
        embed.add_field(
            name="🔨 Moderation",
            value=(
                "**Members:** `/kick` `/ban` `/softban` `/unban` `/banlist`\n"
                "**Timeouts:** `/timeout` `/untimeout`\n"
                "**Warnings:** `/warn` `/unwarn` `/warnings` `/clearwarnings`\n"
                "**Channel:** `/purge` `/purgeuser` `/lock` `/unlock` `/slowmode`\n"
                "**Profile:** `/nick` `/addrole` `/removerole`\n"
                "**Voice:** `/vckick` `/vcmove`\n"
                "**Messaging:** `/say` `/announce` `/dm`"
            ),
            inline=False,
        )
        await interaction.response.send_message(embed=embed, ephemeral=True)

    # ==================== MEMBERS ====================
    @app_commands.command(name="kick", description="Kick a member")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def kick(self, interaction: discord.Interaction, member: discord.Member, reason: str = "No reason provided"):
        if self._above_me(interaction.guild, member):
            return await interaction.response.send_message("⚠️ I can't kick someone with an equal/higher role than me.", ephemeral=True)
        await member.kick(reason=f"{interaction.user}: {reason}")
        await interaction.response.send_message(f"👢 Kicked {member.mention} — {reason}")

    @app_commands.command(name="ban", description="Ban a member")
    @app_commands.describe(delete_days="How many days of their recent messages to delete (0-7)")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def ban(self, interaction: discord.Interaction, member: discord.Member, reason: str = "No reason provided", delete_days: app_commands.Range[int, 0, 7] = 0):
        if self._above_me(interaction.guild, member):
            return await interaction.response.send_message("⚠️ I can't ban someone with an equal/higher role than me.", ephemeral=True)
        await member.ban(reason=f"{interaction.user}: {reason}", delete_message_seconds=delete_days * 86400)
        await interaction.response.send_message(f"🔨 Banned {member.mention} — {reason}")

    @app_commands.command(name="softban", description="Ban then immediately unban a member (wipes their recent messages, doesn't block rejoining)")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def softban(self, interaction: discord.Interaction, member: discord.Member, reason: str = "No reason provided"):
        if self._above_me(interaction.guild, member):
            return await interaction.response.send_message("⚠️ I can't softban someone with an equal/higher role than me.", ephemeral=True)
        await member.ban(reason=f"Softban by {interaction.user}: {reason}", delete_message_seconds=86400)
        await interaction.guild.unban(member, reason="Softban — auto unban")
        await interaction.response.send_message(f"🧹 Softbanned {member.mention} — {reason} (messages wiped, free to rejoin)")

    @app_commands.command(name="unban", description="Unban a user by ID")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def unban(self, interaction: discord.Interaction, user_id: str, reason: str = "No reason provided"):
        try:
            user = discord.Object(id=int(user_id))
            await interaction.guild.unban(user, reason=f"{interaction.user}: {reason}")
            await interaction.response.send_message(f"✅ Unbanned user `{user_id}`.")
        except (ValueError, discord.NotFound):
            await interaction.response.send_message("⚠️ That user isn't banned or the ID is invalid.", ephemeral=True)

    @app_commands.command(name="banlist", description="List banned users in this server")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def banlist(self, interaction: discord.Interaction):
        await interaction.response.defer(ephemeral=True)
        bans = [entry async for entry in interaction.guild.bans(limit=25)]
        if not bans:
            return await interaction.followup.send("✅ No banned users.", ephemeral=True)
        lines = [f"`{b.user.id}` — {b.user} — {b.reason or 'no reason given'}" for b in bans]
        embed = discord.Embed(title="🔨 Banned users (up to 25 shown)", description="\n".join(lines), color=discord.Color.red())
        await interaction.followup.send(embed=embed, ephemeral=True)

    # ==================== TIMEOUTS ====================
    @app_commands.command(name="timeout", description="Timeout (mute) a member")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def timeout(self, interaction: discord.Interaction, member: discord.Member, minutes: app_commands.Range[int, 1, 40320], reason: str = "No reason provided"):
        if self._above_me(interaction.guild, member):
            return await interaction.response.send_message("⚠️ I can't time out someone with an equal/higher role than me.", ephemeral=True)
        await member.timeout(timedelta(minutes=minutes), reason=f"{interaction.user}: {reason}")
        await interaction.response.send_message(f"🔇 Timed out {member.mention} for {minutes}m — {reason}")

    @app_commands.command(name="untimeout", description="Remove a member's timeout")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def untimeout(self, interaction: discord.Interaction, member: discord.Member):
        if member.timed_out_until is None:
            return await interaction.response.send_message(f"ℹ️ {member.mention} isn't timed out.", ephemeral=True)
        await member.timeout(None, reason=f"Timeout removed by {interaction.user}")
        await interaction.response.send_message(f"✅ Removed timeout for {member.mention}.")

    # ==================== WARNINGS ====================
    # Shares the same `automod.warns` counter the bad-word/spam filter uses,
    # so manual warns and automod warns count toward the same escalation limit.
    @app_commands.command(name="warn", description="Manually warn a member (DMs them, counts toward automod's warn limit)")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def warn(self, interaction: discord.Interaction, member: discord.Member, reason: str):
        cfg = get_config(interaction.guild.id)
        am = cfg["automod"]
        key = str(member.id)
        am["warns"][key] = am["warns"].get(key, 0) + 1
        count = am["warns"][key]
        save_config(interaction.guild.id, cfg)

        try:
            await member.send(vibe.manual_warn_dm(interaction.guild.name, reason, count, am["warn_limit"]))
        except discord.Forbidden:
            pass

        await interaction.response.send_message(f"⚠️ Warned {member.mention} — {reason} ({count}/{am['warn_limit']})")

        if count >= am["warn_limit"]:
            am["warns"][key] = 0
            save_config(interaction.guild.id, cfg)
            await interaction.followup.send(f"⬆️ {member.mention} hit the warn limit. Escalation punishment (`{am['escalate_punishment']}`) is handled automatically the next time automod catches them, or apply it manually now.")

    @app_commands.command(name="unwarn", description="Remove one warning from a member")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def unwarn(self, interaction: discord.Interaction, member: discord.Member):
        cfg = get_config(interaction.guild.id)
        am = cfg["automod"]
        key = str(member.id)
        current = am["warns"].get(key, 0)
        if current <= 0:
            return await interaction.response.send_message(f"ℹ️ {member.mention} has no warnings to remove.", ephemeral=True)
        am["warns"][key] = current - 1
        save_config(interaction.guild.id, cfg)
        await interaction.response.send_message(f"✅ Removed one warning from {member.mention} — now at {am['warns'][key]}/{am['warn_limit']}.")

    @app_commands.command(name="warnings", description="Check a member's current warning count")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def warnings(self, interaction: discord.Interaction, member: discord.Member):
        cfg = get_config(interaction.guild.id)
        am = cfg["automod"]
        count = am["warns"].get(str(member.id), 0)
        await interaction.response.send_message(f"📋 {member.mention} has **{count}/{am['warn_limit']}** warnings.", ephemeral=True)

    @app_commands.command(name="clearwarnings", description="Reset a member's warnings to zero")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def clearwarnings(self, interaction: discord.Interaction, member: discord.Member):
        cfg = get_config(interaction.guild.id)
        cfg["automod"]["warns"].pop(str(member.id), None)
        save_config(interaction.guild.id, cfg)
        await interaction.response.send_message(f"✅ Cleared all warnings for {member.mention}.")

    # ==================== CHANNEL ====================
    @app_commands.command(name="purge", description="Bulk delete messages in this channel")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def purge(self, interaction: discord.Interaction, amount: app_commands.Range[int, 1, 100]):
        await interaction.response.defer(ephemeral=True)
        deleted = await interaction.channel.purge(limit=amount)
        await interaction.followup.send(f"🧹 Deleted {len(deleted)} messages.", ephemeral=True)

    @app_commands.command(name="purgeuser", description="Bulk delete a specific member's recent messages in this channel")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def purgeuser(self, interaction: discord.Interaction, member: discord.Member, amount: app_commands.Range[int, 1, 100] = 50):
        await interaction.response.defer(ephemeral=True)
        deleted = await interaction.channel.purge(limit=200, check=lambda m: m.author.id == member.id, before=interaction.created_at)
        deleted = deleted[:amount]
        await interaction.followup.send(f"🧹 Deleted {len(deleted)} messages from {member.mention}.", ephemeral=True)

    @app_commands.command(name="lock", description="Lock this channel (stop @everyone from sending messages)")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def lock(self, interaction: discord.Interaction, reason: str = "No reason provided"):
        overwrite = interaction.channel.overwrites_for(interaction.guild.default_role)
        overwrite.send_messages = False
        await interaction.channel.set_permissions(interaction.guild.default_role, overwrite=overwrite, reason=f"{interaction.user}: {reason}")
        await interaction.response.send_message(f"🔒 Channel locked — {reason}")

    @app_commands.command(name="unlock", description="Unlock this channel (restore @everyone's send permission)")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def unlock(self, interaction: discord.Interaction):
        overwrite = interaction.channel.overwrites_for(interaction.guild.default_role)
        overwrite.send_messages = None
        await interaction.channel.set_permissions(interaction.guild.default_role, overwrite=overwrite, reason=f"Unlocked by {interaction.user}")
        await interaction.response.send_message("🔓 Channel unlocked.")

    @app_commands.command(name="slowmode", description="Set this channel's slowmode delay (0 to disable)")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def slowmode(self, interaction: discord.Interaction, seconds: app_commands.Range[int, 0, 21600]):
        await interaction.channel.edit(slowmode_delay=seconds)
        if seconds == 0:
            await interaction.response.send_message("🐇 Slowmode disabled.")
        else:
            await interaction.response.send_message(f"🐌 Slowmode set to {seconds}s.")

    # ==================== PROFILE / ROLES ====================
    @app_commands.command(name="nick", description="Change a member's nickname")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def nick(self, interaction: discord.Interaction, member: discord.Member, nickname: str = None):
        if self._above_me(interaction.guild, member):
            return await interaction.response.send_message("⚠️ I can't change the nickname of someone with an equal/higher role than me.", ephemeral=True)
        await member.edit(nick=nickname, reason=f"{interaction.user}")
        if nickname:
            await interaction.response.send_message(f"✅ Set {member.mention}'s nickname to `{nickname}`.")
        else:
            await interaction.response.send_message(f"✅ Reset {member.mention}'s nickname.")

    @app_commands.command(name="addrole", description="Add a role to a member")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def addrole(self, interaction: discord.Interaction, member: discord.Member, role: discord.Role):
        if role >= interaction.guild.me.top_role:
            return await interaction.response.send_message("⚠️ I can't assign a role equal to or higher than my own.", ephemeral=True)
        await member.add_roles(role, reason=f"{interaction.user}")
        await interaction.response.send_message(f"✅ Added {role.mention} to {member.mention}.")

    @app_commands.command(name="removerole", description="Remove a role from a member")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def removerole(self, interaction: discord.Interaction, member: discord.Member, role: discord.Role):
        if role >= interaction.guild.me.top_role:
            return await interaction.response.send_message("⚠️ I can't remove a role equal to or higher than my own.", ephemeral=True)
        await member.remove_roles(role, reason=f"{interaction.user}")
        await interaction.response.send_message(f"✅ Removed {role.mention} from {member.mention}.")

    # ==================== VOICE ====================
    @app_commands.command(name="vckick", description="Disconnect a member from voice chat")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def vckick(self, interaction: discord.Interaction, member: discord.Member):
        if not member.voice:
            return await interaction.response.send_message(f"ℹ️ {member.mention} isn't in a voice channel.", ephemeral=True)
        await member.move_to(None, reason=f"{interaction.user}")
        await interaction.response.send_message(f"🔌 Disconnected {member.mention} from voice.")

    @app_commands.command(name="vcmove", description="Move a member to another voice channel")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def vcmove(self, interaction: discord.Interaction, member: discord.Member, channel: discord.VoiceChannel):
        if not member.voice:
            return await interaction.response.send_message(f"ℹ️ {member.mention} isn't in a voice channel.", ephemeral=True)
        await member.move_to(channel, reason=f"{interaction.user}")
        await interaction.response.send_message(f"➡️ Moved {member.mention} to {channel.mention}.")

    # ==================== MESSAGING ====================
    @app_commands.command(name="say", description="Send a plain message to a channel as the bot")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def say(self, interaction: discord.Interaction, channel: discord.TextChannel, message: str):
        try:
            await channel.send(message)
        except discord.Forbidden:
            return await interaction.response.send_message(f"⚠️ I can't send messages in {channel.mention}.", ephemeral=True)
        await interaction.response.send_message(f"✅ Sent to {channel.mention}.", ephemeral=True)

    @app_commands.command(name="announce", description="Post a rich embed announcement to a channel")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def announce(self, interaction: discord.Interaction, channel: discord.TextChannel, ping_everyone: bool = False):
        await interaction.response.send_modal(AnnounceModal(channel, ping_everyone))

    @app_commands.command(name="dm", description="DM a member as the bot")
    @app_commands.default_permissions(moderate_members=True)
    @is_mod()
    async def dm(self, interaction: discord.Interaction, member: discord.Member, message: str):
        try:
            await member.send(message)
        except discord.Forbidden:
            return await interaction.response.send_message(f"⚠️ Couldn't DM {member.mention} — their DMs are closed.", ephemeral=True)
        await interaction.response.send_message(f"✅ DM sent to {member.mention}.", ephemeral=True)


class AnnounceModal(discord.ui.Modal, title="Post an announcement"):
    announce_title = discord.ui.TextInput(label="Title", max_length=256)
    announce_body = discord.ui.TextInput(label="Message", style=discord.TextStyle.paragraph, max_length=4000)

    def __init__(self, channel: discord.TextChannel, ping_everyone: bool):
        super().__init__()
        self.channel = channel
        self.ping_everyone = ping_everyone

    async def on_submit(self, interaction: discord.Interaction):
        embed = discord.Embed(title=self.announce_title.value, description=self.announce_body.value, color=discord.Color.blurple())
        embed.set_footer(text=f"Announced by {interaction.user.display_name}")
        try:
            content = "@everyone" if self.ping_everyone else None
            await self.channel.send(content=content, embed=embed, allowed_mentions=discord.AllowedMentions(everyone=self.ping_everyone))
        except discord.Forbidden:
            return await interaction.response.send_message(f"⚠️ I can't send messages in {self.channel.mention}.", ephemeral=True)
        await interaction.response.send_message(f"✅ Announcement posted in {self.channel.mention}.", ephemeral=True)


async def setup(bot: commands.Bot):
    await bot.add_cog(Utility(bot))
