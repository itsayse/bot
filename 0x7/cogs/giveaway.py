import time
import random
import re
import discord
from discord import app_commands
from discord.ext import commands, tasks

from utils.storage import get_config, save_config
from utils.checks import is_mod


DURATION_RE = re.compile(r"(\d+)\s*(s|m|h|d|w)", re.IGNORECASE)
UNIT_SECONDS = {"s": 1, "m": 60, "h": 3600, "d": 86400, "w": 604800}


def parse_duration(text: str) -> int:
    total = 0
    for amount, unit in DURATION_RE.findall(text):
        total += int(amount) * UNIT_SECONDS[unit.lower()]
    return total


class GiveawayView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="🎉 Enter", style=discord.ButtonStyle.green, custom_id="giveaway:enter")
    async def enter(self, interaction: discord.Interaction, button: discord.ui.Button):
        cog: "Giveaway" = interaction.client.get_cog("Giveaway")
        await cog.enter_giveaway(interaction)


class Giveaway(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot
        bot.add_view(GiveawayView())
        self.checker.start()

    def cog_unload(self):
        self.checker.cancel()

    giveaway = app_commands.Group(name="giveaway", description="Run giveaways")

    async def cog_app_command_error(self, interaction: discord.Interaction, error: app_commands.AppCommandError):
        if isinstance(error, app_commands.CheckFailure):
            await interaction.response.send_message(f"⛔ {error}", ephemeral=True)
        else:
            await interaction.response.send_message(f"⚠️ {error}", ephemeral=True)

    @giveaway.command(name="start", description="Start a giveaway (duration examples: 30m, 2h, 1d)")
    @app_commands.describe(duration="e.g. 10m, 2h, 1d", winners="How many winners", prize="What are you giving away")
    @is_mod()
    async def start(self, interaction: discord.Interaction, duration: str, winners: int, prize: str, channel: discord.TextChannel = None):
        seconds = parse_duration(duration)
        if seconds <= 0:
            return await interaction.response.send_message("⚠️ Invalid duration. Try something like `30m`, `2h`, `1d`.", ephemeral=True)

        target_channel = channel or interaction.channel
        ends_at = int(time.time()) + seconds

        embed = discord.Embed(
            title="🎉 GIVEAWAY 🎉",
            description=f"**Prize:** {prize}\n**Winners:** {winners}\n**Ends:** <t:{ends_at}:R> (<t:{ends_at}:f>)\n\nClick 🎉 Enter below to join!",
            color=discord.Color.gold(),
        )
        embed.set_footer(text=f"Hosted by {interaction.user}")
        msg = await target_channel.send(embed=embed, view=GiveawayView())

        cfg = get_config(interaction.guild.id)
        cfg["giveaways"][str(msg.id)] = {
            "channel_id": target_channel.id,
            "prize": prize,
            "winners": winners,
            "ends_at": ends_at,
            "host_id": interaction.user.id,
            "entrants": [],
            "ended": False,
        }
        save_config(interaction.guild.id, cfg)
        await interaction.response.send_message(f"✅ Giveaway started in {target_channel.mention}!", ephemeral=True)

    @giveaway.command(name="end", description="End a giveaway early")
    @is_mod()
    async def end(self, interaction: discord.Interaction, message_id: str):
        cfg = get_config(interaction.guild.id)
        gw = cfg["giveaways"].get(message_id)
        if not gw or gw["ended"]:
            return await interaction.response.send_message("Giveaway not found or already ended.", ephemeral=True)
        await self._finish_giveaway(interaction.guild, message_id, gw)
        await interaction.response.send_message("✅ Giveaway ended.", ephemeral=True)

    @giveaway.command(name="reroll", description="Reroll winners for a finished giveaway")
    @is_mod()
    async def reroll(self, interaction: discord.Interaction, message_id: str):
        cfg = get_config(interaction.guild.id)
        gw = cfg["giveaways"].get(message_id)
        if not gw:
            return await interaction.response.send_message("Giveaway not found.", ephemeral=True)
        channel = interaction.guild.get_channel(gw["channel_id"])
        winners = self._pick_winners(gw)
        if not winners:
            return await interaction.response.send_message("No valid entrants to reroll from.", ephemeral=True)
        mentions = ", ".join(f"<@{w}>" for w in winners)
        await channel.send(f"🔁 New winner(s) for **{gw['prize']}**: {mentions}!")
        await interaction.response.send_message("✅ Rerolled.", ephemeral=True)

    async def enter_giveaway(self, interaction: discord.Interaction):
        cfg = get_config(interaction.guild.id)
        gw = cfg["giveaways"].get(str(interaction.message.id))
        if not gw or gw["ended"]:
            return await interaction.response.send_message("This giveaway has ended.", ephemeral=True)
        if interaction.user.id in gw["entrants"]:
            gw["entrants"].remove(interaction.user.id)
            save_config(interaction.guild.id, cfg)
            return await interaction.response.send_message("❌ You left the giveaway.", ephemeral=True)
        gw["entrants"].append(interaction.user.id)
        save_config(interaction.guild.id, cfg)
        await interaction.response.send_message("✅ You're entered! Click again to leave.", ephemeral=True)

    def _pick_winners(self, gw):
        pool = list(gw["entrants"])
        if not pool:
            return []
        k = min(gw["winners"], len(pool))
        return random.sample(pool, k)

    async def _finish_giveaway(self, guild, message_id, gw):
        gw["ended"] = True
        channel = guild.get_channel(gw["channel_id"])
        winners = self._pick_winners(gw)

        cfg = get_config(guild.id)
        cfg["giveaways"][message_id] = gw
        save_config(guild.id, cfg)

        if not channel:
            return
        try:
            msg = await channel.fetch_message(int(message_id))
            embed = msg.embeds[0]
            embed.description = f"**Prize:** {gw['prize']}\n**Winners:** {gw['winners']}\n🎉 Giveaway ended 🎉"
            embed.color = discord.Color.dark_grey()
            await msg.edit(embed=embed, view=None)
        except discord.NotFound:
            pass

        if winners:
            mentions = ", ".join(f"<@{w}>" for w in winners)
            await channel.send(f"🎉 Congratulations {mentions}! You won **{gw['prize']}**!")
        else:
            await channel.send(f"No valid entries — nobody won **{gw['prize']}**.")

    @tasks.loop(seconds=20)
    async def checker(self):
        for guild in self.bot.guilds:
            cfg = get_config(guild.id)
            changed = False
            for message_id, gw in list(cfg["giveaways"].items()):
                if gw["ended"]:
                    continue
                if time.time() >= gw["ends_at"]:
                    await self._finish_giveaway(guild, message_id, gw)
                    changed = True
            if changed:
                pass  # _finish_giveaway already saves

    @checker.before_loop
    async def before_checker(self):
        await self.bot.wait_until_ready()


async def setup(bot: commands.Bot):
    await bot.add_cog(Giveaway(bot))
