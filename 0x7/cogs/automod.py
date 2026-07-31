import os
import re
import time
import discord
from collections import defaultdict, deque
from discord.ext import commands, tasks
from datetime import timedelta, datetime, timezone

from utils.storage import get_config, save_config, BASE_DIR
from utils import vibe

BAD_WORDS_PATH = os.path.join(BASE_DIR, "bad.txt")

INVITE_PATTERN = re.compile(
    r"(?:discord\.gg/|discord(?:app)?\.com/invite/)([a-zA-Z0-9-]+)",
    re.IGNORECASE,
)


class AutoMod(commands.Cog):
    """Bad-word filter + spam control. Configure via /panel -> Automod."""

    def __init__(self, bot: commands.Bot):
        self.bot = bot
        self.bad_words = set()
        self._pattern = None
        self._mtime = None
        self.load_bad_words()
        self.reload_watcher.start()

        # per-member message history for spam detection: (guild_id, user_id) -> deque[(timestamp, content)]
        self._history = defaultdict(lambda: deque(maxlen=20))

    def cog_unload(self):
        self.reload_watcher.cancel()

    # ---------- bad word list handling ----------
    def load_bad_words(self):
        if not os.path.exists(BAD_WORDS_PATH):
            self.bad_words = set()
            self._pattern = None
            return
        mtime = os.path.getmtime(BAD_WORDS_PATH)
        if mtime == self._mtime:
            return
        self._mtime = mtime
        with open(BAD_WORDS_PATH, "r", encoding="utf-8") as f:
            words = {line.strip().lower() for line in f if line.strip() and not line.strip().startswith("#")}
        self.bad_words = words
        if words:
            escaped = [re.escape(w) for w in words]
            self._pattern = re.compile(r"\b(" + "|".join(escaped) + r")\b", re.IGNORECASE)
        else:
            self._pattern = None

    @tasks.loop(seconds=30)
    async def reload_watcher(self):
        self.load_bad_words()

    @reload_watcher.before_loop
    async def before_watcher(self):
        await self.bot.wait_until_ready()

    def contains_bad_word(self, content: str):
        if not self._pattern:
            return None
        match = self._pattern.search(content)
        return match.group(0) if match else None

    def contains_invite(self, content: str):
        match = INVITE_PATTERN.search(content)
        return match.group(1) if match else None

    async def is_own_server_invite(self, invite_code: str, guild: discord.Guild) -> bool:
        try:
            invite = await self.bot.fetch_invite(invite_code)
            return invite.guild is not None and invite.guild.id == guild.id
        except discord.HTTPException:
            # invalid/expired invite -> treat as not-our-server so it still gets flagged
            return False

    # ---------- spam detection ----------
    def check_spam(self, guild_id, user_id, content, spam_cfg):
        """Returns a reason string ('rate' or 'duplicate') if this message trips spam limits, else None."""
        now = time.time()
        key = (guild_id, user_id)
        hist = self._history[key]
        hist.append((now, content))

        interval = spam_cfg["interval_seconds"]
        msg_limit = spam_cfg["message_limit"]
        recent = [t for t, _ in hist if now - t <= interval]
        if len(recent) >= msg_limit:
            return "rate"

        dup_limit = spam_cfg["duplicate_limit"]
        if dup_limit and len(hist) >= dup_limit:
            last_n = list(hist)[-dup_limit:]
            if all(c == content for _, c in last_n) and content.strip():
                return "duplicate"

        return None

    # ---------- the actual filter ----------
    @commands.Cog.listener()
    async def on_message(self, message: discord.Message):
        if message.author.bot or not message.guild:
            return
        cfg = get_config(message.guild.id)
        am = cfg["automod"]
        if not am["enabled"]:
            return
        member = message.author
        if isinstance(member, discord.Member):
            member_role_ids = {r.id for r in member.roles}
            if any(rid in member_role_ids for rid in am["bypass_roles"]):
                return
            if member.guild_permissions.administrator:
                return

        # ---- bad word check ----
        hit = self.contains_bad_word(message.content)
        if hit:
            await self._handle_violation(message, cfg, am, kind="badword", detail=hit)
            return

        # ---- invite link check ----
        invite_cfg = am.get("invite_filter", {})
        if invite_cfg.get("enabled"):
            code = self.contains_invite(message.content)
            if code:
                flag = True
                if invite_cfg.get("allow_own_server", True):
                    flag = not await self.is_own_server_invite(code, message.guild)
                if flag:
                    await self._handle_violation(message, cfg, am, kind="invite", detail=code, invite_cfg=invite_cfg)
                    return

        # ---- spam check ----
        spam_cfg = am.get("spam", {})
        if spam_cfg.get("enabled"):
            reason = self.check_spam(message.guild.id, member.id, message.content, spam_cfg)
            if reason:
                await self._handle_violation(message, cfg, am, kind="spam", detail=reason, spam_cfg=spam_cfg)
                return

    async def _handle_violation(self, message, cfg, am, kind, detail, spam_cfg=None, invite_cfg=None):
        member = message.author
        guild = message.guild

        try:
            await message.delete()
        except discord.HTTPException:
            pass

        if kind == "badword":
            await self._log(guild, am, f"🚫 Deleted a message from {member.mention} in {message.channel.mention} (matched `{detail}`)")
            punishment = am["punishment"]
        elif kind == "invite":
            await self._log(guild, am, f"🔗 Deleted an invite link from {member.mention} in {message.channel.mention} (`discord.gg/{detail}`)")
            punishment = (invite_cfg or {}).get("punishment", "warn")
        else:
            await self._log(guild, am, f"🌊 Spam ({detail}) from {member.mention} in {message.channel.mention} — message deleted.")
            punishment = (spam_cfg or {}).get("punishment", "warn")

        # spam and invite-link triggers also wipe that member's recent messages in this channel
        if kind in ("spam", "invite"):
            await self._purge_recent(message.channel, member, am)

        if punishment == "delete":
            if am.get("dm_on_warn", True) and kind in ("badword", "invite"):
                text = vibe.badword_deleted_dm(guild.name) if kind == "badword" else vibe.invite_deleted_dm(guild.name)
                await vibe.try_dm(member, text)
            return

        if punishment == "warn":
            warns = am["warns"]
            key = str(member.id)
            warns[key] = warns.get(key, 0) + 1
            count = warns[key]
            save_config(guild.id, cfg)
            try:
                if kind == "badword":
                    await message.channel.send(f"⚠️ {member.mention}, watch your language. Warning {count}/{am['warn_limit']}.", delete_after=8)
                elif kind == "invite":
                    await message.channel.send(f"⚠️ {member.mention}, no posting invite links here. Warning {count}/{am['warn_limit']}.", delete_after=8)
                else:
                    await message.channel.send(f"⚠️ {member.mention}, slow down. Warning {count}/{am['warn_limit']}.", delete_after=8)
            except discord.HTTPException:
                pass

            if am.get("dm_on_warn", True):
                if kind == "badword":
                    text = vibe.badword_dm(guild.name, count, am["warn_limit"])
                elif kind == "invite":
                    text = vibe.invite_dm(guild.name, count, am["warn_limit"])
                else:
                    text = vibe.spam_dm(guild.name, count, am["warn_limit"])
                await vibe.try_dm(member, text)

            if count >= am["warn_limit"]:
                warns[key] = 0
                save_config(guild.id, cfg)
                escalate_to = am["escalate_punishment"]
                await self._apply_punishment(guild, member, escalate_to, am, "reached the warn limit")
                if am.get("dm_on_warn", True):
                    await vibe.try_dm(member, vibe.escalation_dm(guild.name, escalate_to))
            return

        # non-warn punishment applied directly
        if kind == "badword":
            reason = f"used a banned word (`{detail}`)"
        elif kind == "invite":
            reason = f"posted an invite link (`discord.gg/{detail}`)"
        else:
            reason = f"spam ({detail})"
        await self._apply_punishment(guild, member, punishment, am, reason)
        if am.get("dm_on_warn", True):
            await vibe.try_dm(member, vibe.escalation_dm(guild.name, punishment))

    async def _purge_recent(self, channel, member, am):
        minutes = am.get("raid_purge_minutes", 0)
        if not minutes:
            return
        cutoff = datetime.now(timezone.utc) - timedelta(minutes=minutes)
        try:
            deleted = await channel.purge(
                limit=500,
                after=cutoff,
                check=lambda m: m.author.id == member.id,
                bulk=True,
            )
            if deleted:
                await self._log(
                    channel.guild, am,
                    f"🧹 Purged {len(deleted)} messages from {member.mention} in {channel.mention} (last {minutes}m)."
                )
        except discord.HTTPException:
            pass

    async def _apply_punishment(self, guild, member, punishment, am, reason):
        try:
            if punishment == "mute":
                await member.timeout(timedelta(minutes=am["mute_minutes"]), reason=f"Automod: {reason}")
                await self._log(guild, am, f"🔇 Muted {member.mention} for {am['mute_minutes']}m — {reason}")
            elif punishment == "kick":
                await member.kick(reason=f"Automod: {reason}")
                await self._log(guild, am, f"👢 Kicked {member.mention} — {reason}")
            elif punishment == "ban":
                await member.ban(reason=f"Automod: {reason}", delete_message_seconds=0)
                await self._log(guild, am, f"🔨 Banned {member.mention} — {reason}")
        except discord.Forbidden:
            await self._log(guild, am, f"⚠️ Couldn't apply `{punishment}` to {member.mention} — missing permissions.")

    async def _log(self, guild, am, text):
        channel_id = am.get("log_channel")
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
    await bot.add_cog(AutoMod(bot))
