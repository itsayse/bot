import time
import discord
from discord.ext import commands, tasks

from utils.storage import get_config


class AfkVC(commands.Cog):
    """Moves long-muted/deafened voice members to an AFK channel. Configure via /panel -> AFK Voice."""

    def __init__(self, bot: commands.Bot):
        self.bot = bot
        # (guild_id, user_id) -> timestamp since they became muted/deafened
        self.muted_since = {}
        self.checker.start()

    def cog_unload(self):
        self.checker.cancel()

    @commands.Cog.listener()
    async def on_voice_state_update(self, member: discord.Member, before: discord.VoiceState, after: discord.VoiceState):
        key = (member.guild.id, member.id)

        cfg = get_config(member.guild.id)
        afk_channel_id = cfg["afk_vc"]["afk_channel_id"]
        if after.channel is None or after.channel.id == afk_channel_id:
            self.muted_since.pop(key, None)
            return

        is_muted = bool(after.self_mute or after.mute or after.self_deaf or after.deaf)
        if is_muted:
            self.muted_since.setdefault(key, time.time())
        else:
            self.muted_since.pop(key, None)

    @tasks.loop(seconds=30)
    async def checker(self):
        now = time.time()
        for guild in self.bot.guilds:
            cfg = get_config(guild.id)
            afk_cfg = cfg["afk_vc"]
            if not afk_cfg["enabled"] or not afk_cfg["afk_channel_id"]:
                continue
            afk_channel = guild.get_channel(afk_cfg["afk_channel_id"])
            if not isinstance(afk_channel, discord.VoiceChannel):
                continue
            timeout = afk_cfg["mute_timeout_minutes"] * 60
            ignore_roles = set(afk_cfg["ignore_roles"])

            for key, started in list(self.muted_since.items()):
                gid, uid = key
                if gid != guild.id:
                    continue
                if now - started < timeout:
                    continue
                member = guild.get_member(uid)
                if not member or not member.voice or not member.voice.channel:
                    self.muted_since.pop(key, None)
                    continue
                if member.voice.channel.id == afk_channel.id:
                    self.muted_since.pop(key, None)
                    continue
                if any(r.id in ignore_roles for r in member.roles):
                    continue
                try:
                    await member.move_to(afk_channel, reason="Muted in voice too long")
                except discord.Forbidden:
                    pass
                finally:
                    self.muted_since.pop(key, None)

    @checker.before_loop
    async def before_checker(self):
        await self.bot.wait_until_ready()


async def setup(bot: commands.Bot):
    await bot.add_cog(AfkVC(bot))
