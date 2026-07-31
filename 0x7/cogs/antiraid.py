import time
import discord
from discord.ext import commands
from collections import defaultdict, deque
from datetime import datetime, timezone

from utils.storage import get_config


class AntiRaid(commands.Cog):
    """Join-raid and anti-nuke protection. Configure via /panel -> Anti-Raid."""

    def __init__(self, bot: commands.Bot):
        self.bot = bot
        self.join_times = defaultdict(deque)            # guild_id -> deque[timestamps]
        self.destructive_actions = defaultdict(deque)   # (guild_id, actor_id) -> deque[timestamps]

    # -------------------- raid detection --------------------
    @commands.Cog.listener()
    async def on_member_join(self, member: discord.Member):
        cfg = get_config(member.guild.id)
        ar = cfg["antiraid"]
        if not ar["enabled"]:
            return

        now = time.time()
        dq = self.join_times[member.guild.id]
        dq.append(now)
        while dq and now - dq[0] > ar["join_interval"]:
            dq.popleft()

        raid_triggered = len(dq) >= ar["join_threshold"]

        account_age = datetime.now(timezone.utc) - member.created_at
        too_new = account_age.total_seconds() < ar["min_account_age_hours"] * 3600

        if raid_triggered or too_new:
            await self._log(member.guild, ar, f"🚨 Raid protection triggered for {member.mention} (raid burst={raid_triggered}, new account={too_new}).")
            action = ar["action"]
            try:
                if action == "ban" or (raid_triggered and action == "lockdown"):
                    if action == "lockdown":
                        await member.guild.edit(verification_level=discord.VerificationLevel.high)
                        await self._log(member.guild, ar, "🔒 Server verification level raised due to raid burst.")
                    if action == "ban":
                        await member.ban(reason="Anti-raid protection", delete_message_seconds=0)
                elif action == "kick" or too_new:
                    await member.kick(reason="Anti-raid protection (account too new / raid burst)")
                elif action == "lockdown":
                    await member.guild.edit(verification_level=discord.VerificationLevel.high)
                    await self._log(member.guild, ar, "🔒 Server verification level raised due to raid burst.")
            except discord.Forbidden:
                await self._log(member.guild, ar, "⚠️ Missing permissions to act on raid protection.")

    # -------------------- anti-nuke detection --------------------
    async def _track_destructive(self, guild: discord.Guild, actor: discord.Member, what: str):
        cfg = get_config(guild.id)
        ar = cfg["antiraid"]
        if not ar["antinuke_enabled"] or actor is None or actor.bot:
            return
        if actor.id == guild.owner_id:
            return
        now = time.time()
        key = (guild.id, actor.id)
        dq = self.destructive_actions[key]
        dq.append(now)
        while dq and now - dq[0] > ar["antinuke_interval"]:
            dq.popleft()

        if len(dq) >= ar["antinuke_action_limit"]:
            dq.clear()
            await self._log(guild, ar, f"🚨 Anti-nuke triggered: {actor.mention} performed rapid destructive actions ({what}).")
            try:
                if ar["antinuke_punishment"] == "ban":
                    await actor.ban(reason="Anti-nuke protection: rapid destructive actions", delete_message_seconds=0)
                else:
                    roles_to_remove = [r for r in actor.roles if r.name != "@everyone" and r < guild.me.top_role]
                    if roles_to_remove:
                        await actor.remove_roles(*roles_to_remove, reason="Anti-nuke protection: rapid destructive actions")
            except discord.Forbidden:
                await self._log(guild, ar, "⚠️ Missing permissions to punish the offending account.")

    async def _find_actor(self, guild: discord.Guild, action: discord.AuditLogAction, target_id: int = None):
        try:
            async for entry in guild.audit_logs(limit=5, action=action):
                if target_id is None or getattr(entry.target, "id", None) == target_id:
                    return entry.user
        except discord.Forbidden:
            return None
        return None

    @commands.Cog.listener()
    async def on_guild_channel_delete(self, channel: discord.abc.GuildChannel):
        actor = await self._find_actor(channel.guild, discord.AuditLogAction.channel_delete, channel.id)
        if isinstance(actor, discord.Member):
            await self._track_destructive(channel.guild, actor, "channel delete")

    @commands.Cog.listener()
    async def on_guild_role_delete(self, role: discord.Role):
        actor = await self._find_actor(role.guild, discord.AuditLogAction.role_delete, role.id)
        if isinstance(actor, discord.Member):
            await self._track_destructive(role.guild, actor, "role delete")

    @commands.Cog.listener()
    async def on_member_ban(self, guild: discord.Guild, user: discord.User):
        actor = await self._find_actor(guild, discord.AuditLogAction.ban, user.id)
        if isinstance(actor, discord.Member):
            await self._track_destructive(guild, actor, "member ban")

    async def _log(self, guild, ar, text):
        channel_id = ar.get("log_channel")
        if not channel_id:
            cfg = get_config(guild.id)
            channel_id = cfg.get("log_channel")
        if not channel_id:
            return
        channel = guild.get_channel(channel_id)
        if channel:
            try:
                await channel.send(text)
            except discord.HTTPException:
                pass


async def setup(bot: commands.Bot):
    await bot.add_cog(AntiRaid(bot))
