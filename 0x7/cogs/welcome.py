import os
import discord
from discord.ext import commands

from utils.storage import get_config, BASE_DIR

WELCOME_IMAGE_PATH = os.path.join(BASE_DIR, "assets", "welcome.png")


def _format(template: str, member: discord.Member) -> str:
    return (
        template
        .replace("{member}", member.mention)
        .replace("{member_name}", member.display_name)
        .replace("{guild}", member.guild.name)
        .replace("{membercount}", str(member.guild.member_count))
    )


class Welcome(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot

    @commands.Cog.listener()
    async def on_member_join(self, member: discord.Member):
        cfg = get_config(member.guild.id)
        w = cfg["welcome"]
        if not w["enabled"]:
            return

        if w["channel_id"]:
            channel = member.guild.get_channel(w["channel_id"])
            if channel:
                embed = discord.Embed(
                    description=_format(w["message"], member),
                    color=discord.Color.blurple(),
                )
                embed.set_thumbnail(url=member.display_avatar.url)

                file = None
                if w.get("use_image") and os.path.exists(WELCOME_IMAGE_PATH):
                    file = discord.File(WELCOME_IMAGE_PATH, filename="welcome.png")
                    embed.set_image(url="attachment://welcome.png")

                try:
                    if file:
                        await channel.send(embed=embed, file=file)
                    else:
                        await channel.send(embed=embed)
                except discord.HTTPException:
                    pass

        if w.get("dm_enabled"):
            try:
                await member.send(_format(w["dm_message"], member))
            except discord.Forbidden:
                pass


async def setup(bot: commands.Bot):
    await bot.add_cog(Welcome(bot))
