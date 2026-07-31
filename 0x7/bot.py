import os
import asyncio
import logging

import discord
from discord.ext import commands, tasks
from dotenv import load_dotenv

from utils.vibe import random_presence
from utils.mc_api import start_api_server

load_dotenv()

TOKEN = os.getenv("DISCORD_TOKEN")

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("bot")

intents = discord.Intents.default()
intents.members = True
intents.message_content = True
intents.voice_states = True
intents.guilds = True
intents.moderation = True  # audit-log ban/unban events for antinuke


class ModBot(commands.Bot):
    def __init__(self):
        super().__init__(command_prefix="!", intents=intents, help_command=None)

    async def setup_hook(self):
        for ext in (
            "cogs.admin_config",
            "cogs.panel",
            "cogs.autorole",
            "cogs.automod",
            "cogs.antiraid",
            "cogs.levels",
            "cogs.afk_vc",
            "cogs.utility",
            "cogs.tickets",
            "cogs.giveaway",
            "cogs.verification",
            "cogs.welcome",
            "cogs.keys",
        ):
            await self.load_extension(ext)
            log.info("Loaded %s", ext)

        self.mc_api_runner = await start_api_server(self)

        synced = await self.tree.sync()
        log.info("Synced %d slash commands", len(synced))

    async def on_ready(self):
        log.info("Logged in as %s (ID: %s)", self.user, self.user.id)
        if not self.presence_rotator.is_running():
            self.presence_rotator.start()

    @tasks.loop(minutes=2)
    async def presence_rotator(self):
        activity_type, text = random_presence()
        await self.change_presence(activity=discord.Activity(type=activity_type, name=text))

    async def close(self):
        runner = getattr(self, "mc_api_runner", None)
        if runner:
            await runner.cleanup()
        await super().close()


async def main():
    if not TOKEN:
        raise RuntimeError(
            "DISCORD_TOKEN is not set. Copy .env.example to .env and put your bot token in it."
        )
    bot = ModBot()
    async with bot:
        await bot.start(TOKEN)


if __name__ == "__main__":
    asyncio.run(main())
