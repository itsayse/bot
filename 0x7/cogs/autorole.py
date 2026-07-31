import discord
from discord.ext import commands

from utils.storage import get_config


class AutoRole(commands.Cog):
    """Gives configured roles to new members on join. Configure via /panel -> Auto-Role."""

    def __init__(self, bot: commands.Bot):
        self.bot = bot

    @commands.Cog.listener()
    async def on_member_join(self, member: discord.Member):
        cfg = get_config(member.guild.id)
        ar = cfg["autorole"]
        if not ar["enabled"] or not ar["role_ids"]:
            return
        roles = [member.guild.get_role(rid) for rid in ar["role_ids"]]
        roles = [r for r in roles if r is not None]
        if roles:
            try:
                await member.add_roles(*roles, reason="Auto-role on join")
            except discord.Forbidden:
                pass


async def setup(bot: commands.Bot):
    await bot.add_cog(AutoRole(bot))
