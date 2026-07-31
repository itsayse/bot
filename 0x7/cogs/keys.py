import discord
from discord import app_commands
from discord.ext import commands, tasks

from utils import keystore, role_sync
from utils.checks import is_bot_admin
from utils.storage import get_config


def _fmt_dt(iso_str):
    from datetime import datetime
    return f"<t:{int(datetime.fromisoformat(iso_str).timestamp())}:R>"


def _key_dm_embed(rec, title="🔑 Your Minecraft Server Key", note=None):
    embed = discord.Embed(title=title, color=discord.Color.green())
    lines = [
        f"**Key:** `{rec['key']}`",
        "",
        "**How to use it:**",
        "1. Join the server.",
        "2. Run `/key <code> <your_discord_user_id>` in-game (both in one command).",
        "3. Set a password with `/register <password> <confirm>`.",
        "",
        "Need your Discord ID? Turn on Developer Mode in Discord "
        "(Settings → Advanced), then right-click your own profile → **Copy User ID**.",
    ]
    if note:
        lines.insert(0, note)
        lines.insert(1, "")
    embed.description = "\n".join(lines)
    embed.add_field(name="Expires", value=_fmt_dt(rec["expires_at"]))
    embed.set_footer(text="Keep this key private — whoever redeems it first gets bound to it.")
    return embed


def _gift_embed(rec, gifter_name):
    embed = discord.Embed(title="🎁 You've received a gift!", color=discord.Color.gold())
    embed.description = (
        f"**{gifter_name}** sent you a Minecraft server access key!\n\n"
        f"||`{rec['key']}`||\n"
        "*(spoiler-tapped for privacy — tap to reveal)*\n\n"
        "**Redeem it:**\n"
        "1. Join the server.\n"
        "2. Run `/key <code> <your_discord_user_id>` in-game.\n"
        "3. Set a password with `/register <password> <confirm>`.\n\n"
        f"Valid for **30 days** — expires {_fmt_dt(rec['expires_at'])}."
    )
    embed.set_footer(text="Enjoy! 🎉")
    return embed


class Keys(commands.Cog):
    """Minecraft server-key system: generate, gift, extend, shorten, remove — one key per player,
    linked to a Discord account so a role can be auto-granted/revoked while the key is valid."""

    def __init__(self, bot: commands.Bot):
        self.bot = bot
        self._expiry_sweep.start()

    def cog_unload(self):
        self._expiry_sweep.cancel()

    key = app_commands.Group(name="key", description="Manage Minecraft server access keys")

    async def cog_app_command_error(self, interaction: discord.Interaction, error: app_commands.AppCommandError):
        if isinstance(error, app_commands.CheckFailure):
            await interaction.response.send_message(f"⛔ {error}", ephemeral=True)
        else:
            await interaction.response.send_message(f"⚠️ {error}", ephemeral=True)

    # ---------------- background: strip roles once a key goes invalid ----------------
    @tasks.loop(minutes=3)
    async def _expiry_sweep(self):
        for rec in keystore.list_keys():
            if rec.get("role_granted") and not keystore.is_valid(rec):
                ok, _ = await role_sync.revoke_key_role(rec.get("guild_id"), rec.get("discord_id"))
                if ok:
                    keystore.mark_role_granted(rec["key"], False)

    @_expiry_sweep.before_loop
    async def _before_sweep(self):
        await self.bot.wait_until_ready()

    # ---------------- /key generate ----------------
    @key.command(name="generate", description="Generate a new 32-char server key")
    @app_commands.describe(
        days="How many days until it expires (default 30)",
        send_to="Optionally DM the key straight to this user, with instructions",
    )
    @app_commands.default_permissions(administrator=True)
    @is_bot_admin()
    async def generate(
        self,
        interaction: discord.Interaction,
        days: app_commands.Range[int, 1, 365] = 30,
        send_to: discord.Member = None,
    ):
        rec = keystore.generate_key(days=days, created_by=str(interaction.user.id), guild_id=interaction.guild.id)
        embed = discord.Embed(title="🔑 New server key generated", color=discord.Color.green())
        embed.add_field(name="Key", value=f"`{rec['key']}`", inline=False)
        embed.add_field(name="Expires", value=_fmt_dt(rec["expires_at"]), inline=False)
        embed.set_footer(text="This key is single-use — the first Minecraft account to redeem it is bound to it.")

        note = None
        if send_to is not None:
            try:
                await send_to.send(embed=_key_dm_embed(
                    rec, note=f"You've been given access to **{interaction.guild.name}**'s Minecraft server!"
                ))
                note = f"📨 Sent to {send_to.mention} via DM."
            except discord.Forbidden:
                note = f"⚠️ Couldn't DM {send_to.mention} (their DMs are closed) — send the key manually."

        await interaction.response.send_message(embed=embed, content=note, ephemeral=True)

    # ---------------- /giftkey ----------------
    @app_commands.command(name="giftkey", description="Generate a key and send it to someone as a surprise gift DM")
    @app_commands.describe(user="Who to gift the key to", days="How many days it's valid for (default 30)")
    @app_commands.default_permissions(administrator=True)
    @is_bot_admin()
    async def giftkey(
        self,
        interaction: discord.Interaction,
        user: discord.Member,
        days: app_commands.Range[int, 1, 365] = 30,
    ):
        rec = keystore.generate_key(days=days, created_by=str(interaction.user.id), guild_id=interaction.guild.id)
        embed = _gift_embed(rec, interaction.user.display_name)
        try:
            await user.send(embed=embed)
        except discord.Forbidden:
            return await interaction.response.send_message(
                f"⚠️ Generated the key but couldn't DM {user.mention} (their DMs are closed).\n"
                f"Key: `{rec['key']}` — you'll need to send it manually.",
                ephemeral=True,
            )
        announce = discord.Embed(
            description=f"🎁 {interaction.user.mention} sent a gift to {user.mention}! Check your DMs~",
            color=discord.Color.gold(),
        )
        await interaction.response.send_message(embed=announce)

    # ---------------- /key extend ----------------
    @key.command(name="extend", description="Extend a key's expiry (also un-revokes it)")
    @app_commands.describe(key="The 32-char key", days="Days to extend by (default 30)")
    @app_commands.default_permissions(administrator=True)
    @is_bot_admin()
    async def extend(self, interaction: discord.Interaction, key: str, days: app_commands.Range[int, 1, 365] = 30):
        rec = keystore.extend_key(key, days=days)
        if not rec:
            return await interaction.response.send_message("⚠️ No key found with that code.", ephemeral=True)
        role_note = ""
        if keystore.is_valid(rec) and rec.get("discord_id") and not rec.get("role_granted"):
            granted, _ = await role_sync.grant_key_role(rec.get("guild_id"), rec.get("discord_id"))
            keystore.mark_role_granted(rec["key"], granted)
            if granted:
                role_note = " Their role was restored."
        await interaction.response.send_message(
            f"✅ Extended `{rec['key']}` — now expires {_fmt_dt(rec['expires_at'])}.{role_note}", ephemeral=True
        )

    # ---------------- /key shorten ----------------
    @key.command(name="shorten", description="Shorten a key's expiry (floors at 'expires now')")
    @app_commands.describe(key="The 32-char key", days="Days to cut off the expiry (default 7)")
    @app_commands.default_permissions(administrator=True)
    @is_bot_admin()
    async def shorten(self, interaction: discord.Interaction, key: str, days: app_commands.Range[int, 1, 365] = 7):
        rec = keystore.shorten_key(key, days=days)
        if not rec:
            return await interaction.response.send_message("⚠️ No key found with that code.", ephemeral=True)
        role_note = ""
        if not keystore.is_valid(rec) and rec.get("role_granted"):
            ok, _ = await role_sync.revoke_key_role(rec.get("guild_id"), rec.get("discord_id"))
            if ok:
                keystore.mark_role_granted(rec["key"], False)
                role_note = " Their role was removed since it's now expired."
        await interaction.response.send_message(
            f"✂️ Shortened `{rec['key']}` — now expires {_fmt_dt(rec['expires_at'])}.{role_note}", ephemeral=True
        )

    # ---------------- /key remove (revoke + immediate role strip) ----------------
    @key.command(name="remove", description="Remove a key's access immediately — player can't play until given a new key")
    @app_commands.describe(key="The 32-char key")
    @app_commands.default_permissions(administrator=True)
    @is_bot_admin()
    async def remove(self, interaction: discord.Interaction, key: str):
        rec = keystore.revoke_key(key)
        if not rec:
            return await interaction.response.send_message("⚠️ No key found with that code.", ephemeral=True)
        role_note = ""
        if rec.get("role_granted"):
            ok, _ = await role_sync.revoke_key_role(rec.get("guild_id"), rec.get("discord_id"))
            if ok:
                keystore.mark_role_granted(rec["key"], False)
                role_note = " Their role was removed."
        await interaction.response.send_message(f"🚫 Removed access for `{rec['key']}`.{role_note}", ephemeral=True)

    # ---------------- /key delete (permanent) ----------------
    @key.command(name="delete", description="Permanently delete a key record")
    @app_commands.describe(key="The 32-char key")
    @app_commands.default_permissions(administrator=True)
    @is_bot_admin()
    async def delete(self, interaction: discord.Interaction, key: str):
        rec = keystore.delete_key(key)
        if not rec:
            return await interaction.response.send_message("⚠️ No key found with that code.", ephemeral=True)
        if rec.get("role_granted"):
            await role_sync.revoke_key_role(rec.get("guild_id"), rec.get("discord_id"))
        await interaction.response.send_message(f"🗑️ Deleted `{rec['key']}`.", ephemeral=True)

    # ---------------- /key info ----------------
    @key.command(name="info", description="Look up a key's status")
    @app_commands.describe(key="The 32-char key")
    @app_commands.default_permissions(administrator=True)
    @is_bot_admin()
    async def info(self, interaction: discord.Interaction, key: str):
        rec = keystore.get_key(key)
        if not rec:
            return await interaction.response.send_message("⚠️ No key found with that code.", ephemeral=True)
        embed = discord.Embed(title=f"🔑 `{rec['key']}`", color=discord.Color.blurple())
        embed.add_field(name="Status", value=keystore.status_label(rec), inline=True)
        embed.add_field(name="Expires", value=_fmt_dt(rec["expires_at"]), inline=True)
        embed.add_field(name="Claimed by", value=rec["redeemed_name"] or "*unclaimed*", inline=True)
        embed.add_field(
            name="Linked Discord",
            value=f"<@{rec['discord_id']}>" if rec.get("discord_id") else "*not linked*",
            inline=True,
        )
        embed.add_field(name="Role granted", value="🟢 Yes" if rec.get("role_granted") else "🔴 No", inline=True)
        await interaction.response.send_message(embed=embed, ephemeral=True)

    # ---------------- /key list ----------------
    @key.command(name="list", description="List recent keys and their status")
    @app_commands.default_permissions(administrator=True)
    @is_bot_admin()
    async def list_keys(self, interaction: discord.Interaction):
        keys = sorted(keystore.list_keys(), key=lambda r: r["created_at"], reverse=True)[:25]
        if not keys:
            return await interaction.response.send_message("No keys generated yet.", ephemeral=True)
        lines = [
            f"`{r['key'][:8]}...` — {keystore.status_label(r)} — "
            f"{r['redeemed_name'] or 'unclaimed'} — "
            f"{'🔗 linked' if r.get('discord_id') else 'unlinked'} — "
            f"expires {_fmt_dt(r['expires_at'])}"
            for r in keys
        ]
        embed = discord.Embed(title="🔑 Server Keys (most recent 25)", description="\n".join(lines), color=discord.Color.blurple())
        await interaction.response.send_message(embed=embed, ephemeral=True)


async def setup(bot: commands.Bot):
    await bot.add_cog(Keys(bot))
