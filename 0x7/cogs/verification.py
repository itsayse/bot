import io
import random
import string
import time

import discord
from discord import app_commands
from discord.ext import commands
from PIL import Image, ImageDraw, ImageFont

from utils.storage import get_config, save_config
from utils.checks import is_bot_admin

# in-memory captcha sessions: (guild_id, user_id) -> {"code": str, "expires": float}
_SESSIONS = {}
SESSION_TTL = 300  # seconds


def _load_font(size):
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/msttcorefonts/Arial_Bold.ttf",
        "C:\\Windows\\Fonts\\arialbd.ttf",
        "/Library/Fonts/Arial Bold.ttf",
    ):
        try:
            return ImageFont.truetype(path, size)
        except (OSError, IOError):
            continue
    return ImageFont.load_default()


def generate_captcha(length=6):
    """Returns (code:str, discord.File) — a distorted-text image challenge."""
    code = "".join(random.choices(string.ascii_uppercase + string.digits, k=length))

    W, H = 260, 100
    img = Image.new("RGB", (W, H), (30, 32, 38))
    draw = ImageDraw.Draw(img)

    # noise lines
    for _ in range(6):
        x1, y1 = random.randint(0, W), random.randint(0, H)
        x2, y2 = random.randint(0, W), random.randint(0, H)
        draw.line((x1, y1, x2, y2), fill=(random.randint(60, 100),) * 3, width=2)

    # noise dots
    for _ in range(120):
        x, y = random.randint(0, W), random.randint(0, H)
        draw.point((x, y), fill=(random.randint(70, 110),) * 3)

    font = _load_font(46)
    x = 20
    for ch in code:
        y = random.randint(10, 25)
        color = (random.randint(180, 255), random.randint(180, 255), random.randint(180, 255))
        char_img = Image.new("RGBA", (50, 60), (0, 0, 0, 0))
        char_draw = ImageDraw.Draw(char_img)
        char_draw.text((5, 0), ch, font=font, fill=color)
        angle = random.randint(-25, 25)
        char_img = char_img.rotate(angle, expand=True)
        img.paste(char_img, (x, y), char_img)
        x += 38

    buf = io.BytesIO()
    img.save(buf, format="PNG")
    buf.seek(0)
    return code, discord.File(buf, filename="captcha.png")


class CaptchaModal(discord.ui.Modal, title="Enter the code"):
    answer = discord.ui.TextInput(label="Code from the image", max_length=10, placeholder="e.g. 4F9X2A")

    def __init__(self, cog: "Verification"):
        super().__init__()
        self.cog = cog

    async def on_submit(self, interaction: discord.Interaction):
        await self.cog.check_answer(interaction, self.answer.value)


class VerifyPanelView(discord.ui.View):
    """Persistent view — the single 'Verify' button posted in the verify channel."""

    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="✅ Verify", style=discord.ButtonStyle.success, custom_id="verify:start")
    async def start(self, interaction: discord.Interaction, button: discord.ui.Button):
        cog: "Verification" = interaction.client.get_cog("Verification")
        await cog.start_verification(interaction)


class CaptchaRetryView(discord.ui.View):
    def __init__(self, cog: "Verification"):
        super().__init__(timeout=SESSION_TTL)
        self.cog = cog

    @discord.ui.button(label="Enter Code", style=discord.ButtonStyle.blurple, custom_id="verify:enter")
    async def enter(self, interaction: discord.Interaction, button: discord.ui.Button):
        await interaction.response.send_modal(CaptchaModal(self.cog))

    @discord.ui.button(label="🔄 New Image", style=discord.ButtonStyle.secondary, custom_id="verify:newimg")
    async def new_image(self, interaction: discord.Interaction, button: discord.ui.Button):
        await self.cog.start_verification(interaction, edit=True)


class Verification(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot
        bot.add_view(VerifyPanelView())

    verify = app_commands.Group(name="verify", description="Configure the join-verification system")

    async def cog_app_command_error(self, interaction: discord.Interaction, error: app_commands.AppCommandError):
        if isinstance(error, app_commands.CheckFailure):
            await interaction.response.send_message(f"⛔ {error}", ephemeral=True)
        else:
            await interaction.response.send_message(f"⚠️ {error}", ephemeral=True)

    @verify.command(name="setup", description="Post the verification panel in a channel")
    @is_bot_admin()
    async def setup_panel(self, interaction: discord.Interaction, channel: discord.TextChannel):
        cfg = get_config(interaction.guild.id)
        v = cfg["verification"]
        if not v["unverified_role"]:
            return await interaction.response.send_message(
                "⚠️ Set an unverified role first in `/panel → Verification` (that role should have channels hidden via server permissions).",
                ephemeral=True,
            )
        embed = discord.Embed(
            title="🔒 Verify to unlock the server",
            description="Click **Verify** below, solve the quick image code, and you're in.",
            color=discord.Color.blurple(),
        )
        msg = await channel.send(embed=embed, view=VerifyPanelView())
        v["verify_channel"] = channel.id
        v["panel_message"] = msg.id
        v["enabled"] = True
        save_config(interaction.guild.id, cfg)
        await interaction.response.send_message(f"✅ Verification panel posted in {channel.mention}.", ephemeral=True)

    # -------------------- join handling --------------------
    @commands.Cog.listener()
    async def on_member_join(self, member: discord.Member):
        cfg = get_config(member.guild.id)
        v = cfg["verification"]
        if not v["enabled"] or not v["unverified_role"]:
            return
        role = member.guild.get_role(v["unverified_role"])
        if role:
            try:
                await member.add_roles(role, reason="Pending verification")
            except discord.Forbidden:
                pass

    # -------------------- captcha flow --------------------
    async def start_verification(self, interaction: discord.Interaction, edit: bool = False):
        cfg = get_config(interaction.guild.id)
        v = cfg["verification"]
        code, file = generate_captcha(v.get("captcha_length", 6))
        _SESSIONS[(interaction.guild.id, interaction.user.id)] = {"code": code, "expires": time.time() + SESSION_TTL}

        embed = discord.Embed(
            title="Type the code shown in the image",
            description="Case-insensitive. Expires in 5 minutes.",
            color=discord.Color.blurple(),
        )
        embed.set_image(url="attachment://captcha.png")
        view = CaptchaRetryView(self)

        if edit:
            await interaction.response.edit_message(attachments=[file], embed=embed, view=view)
        else:
            await interaction.response.send_message(embed=embed, file=file, view=view, ephemeral=True)

    async def check_answer(self, interaction: discord.Interaction, submitted: str):
        session = _SESSIONS.get((interaction.guild.id, interaction.user.id))
        if not session or session["expires"] < time.time():
            return await interaction.response.send_message("⌛ That code expired — click Verify again.", ephemeral=True)

        if submitted.strip().upper() != session["code"]:
            return await interaction.response.send_message("❌ Incorrect. Click **🔄 New Image** to try again.", ephemeral=True)

        _SESSIONS.pop((interaction.guild.id, interaction.user.id), None)
        cfg = get_config(interaction.guild.id)
        v = cfg["verification"]
        member = interaction.user

        try:
            if v["unverified_role"]:
                role = interaction.guild.get_role(v["unverified_role"])
                if role:
                    await member.remove_roles(role, reason="Verified")
            if v["verified_role"]:
                role = interaction.guild.get_role(v["verified_role"])
                if role:
                    await member.add_roles(role, reason="Verified")
        except discord.Forbidden:
            return await interaction.response.send_message(
                "✅ Code correct, but I don't have permission to update your roles — ping a mod.", ephemeral=True
            )

        await interaction.response.send_message("✅ Verified! Welcome in.", ephemeral=True)

        if v["log_channel"]:
            log_channel = interaction.guild.get_channel(v["log_channel"])
            if log_channel:
                await log_channel.send(f"✅ {member.mention} passed verification.")


async def setup(bot: commands.Bot):
    await bot.add_cog(Verification(bot))
