import discord
from discord import app_commands
from discord.ext import commands

from utils.storage import get_config, save_config
from utils.checks import is_bot_admin, is_bot_admin_member


class AdminConfig(commands.Cog):
    """Root /config command group. Only the server owner, Administrators, or holders of a
    configured bot-admin role can use these. The owner controls who else can configure the bot
    via /config botadmin add-role."""

    def __init__(self, bot: commands.Bot):
        self.bot = bot

    config = app_commands.Group(name="config", description="Configure the bot for this server")

    # ---------- error handler for the whole group ----------
    async def cog_app_command_error(self, interaction: discord.Interaction, error: app_commands.AppCommandError):
        if isinstance(error, app_commands.CheckFailure):
            await interaction.response.send_message(f"⛔ {error}", ephemeral=True)
        else:
            await interaction.response.send_message(f"⚠️ Something went wrong: {error}", ephemeral=True)

    # =========================================================
    # BOT ADMIN ROLES (server owner only, this is the "who can configure the bot" list)
    # =========================================================
    botadmin = app_commands.Group(name="botadmin", parent=config, description="Manage who can configure this bot")

    @botadmin.command(name="add-role", description="Allow a role to configure the bot (owner/admin only)")
    @app_commands.describe(role="Role that should be able to configure the bot")
    async def botadmin_add(self, interaction: discord.Interaction, role: discord.Role):
        if not (interaction.user.id == interaction.guild.owner_id or interaction.user.guild_permissions.administrator):
            return await interaction.response.send_message(
                "⛔ Only the server owner or an Administrator can grant bot-admin roles.", ephemeral=True
            )
        cfg = get_config(interaction.guild.id)
        if role.id not in cfg["admin_roles"]:
            cfg["admin_roles"].append(role.id)
            save_config(interaction.guild.id, cfg)
        await interaction.response.send_message(f"✅ {role.mention} can now configure the bot.", ephemeral=True)

    @botadmin.command(name="remove-role", description="Revoke a role's ability to configure the bot")
    async def botadmin_remove(self, interaction: discord.Interaction, role: discord.Role):
        if not (interaction.user.id == interaction.guild.owner_id or interaction.user.guild_permissions.administrator):
            return await interaction.response.send_message(
                "⛔ Only the server owner or an Administrator can revoke bot-admin roles.", ephemeral=True
            )
        cfg = get_config(interaction.guild.id)
        if role.id in cfg["admin_roles"]:
            cfg["admin_roles"].remove(role.id)
            save_config(interaction.guild.id, cfg)
        await interaction.response.send_message(f"✅ {role.mention} can no longer configure the bot.", ephemeral=True)

    @botadmin.command(name="list", description="List roles allowed to configure the bot")
    async def botadmin_list(self, interaction: discord.Interaction):
        cfg = get_config(interaction.guild.id)
        if not cfg["admin_roles"]:
            return await interaction.response.send_message("No extra bot-admin roles set (only owner/Administrators).", ephemeral=True)
        mentions = [f"<@&{r}>" for r in cfg["admin_roles"]]
        await interaction.response.send_message("Bot-admin roles: " + ", ".join(mentions), ephemeral=True)

    # =========================================================
    # MOD ROLES
    # =========================================================
    modrole = app_commands.Group(name="modrole", parent=config, description="Manage moderator roles")

    @modrole.command(name="add", description="Add a role that can use moderation commands")
    @is_bot_admin()
    async def modrole_add(self, interaction: discord.Interaction, role: discord.Role):
        cfg = get_config(interaction.guild.id)
        if role.id not in cfg["mod_roles"]:
            cfg["mod_roles"].append(role.id)
            save_config(interaction.guild.id, cfg)
        await interaction.response.send_message(f"✅ {role.mention} is now a moderator role.", ephemeral=True)

    @modrole.command(name="remove", description="Remove a moderator role")
    @is_bot_admin()
    async def modrole_remove(self, interaction: discord.Interaction, role: discord.Role):
        cfg = get_config(interaction.guild.id)
        if role.id in cfg["mod_roles"]:
            cfg["mod_roles"].remove(role.id)
            save_config(interaction.guild.id, cfg)
        await interaction.response.send_message(f"✅ {role.mention} is no longer a moderator role.", ephemeral=True)

    @modrole.command(name="list", description="List moderator roles")
    @is_bot_admin()
    async def modrole_list(self, interaction: discord.Interaction):
        cfg = get_config(interaction.guild.id)
        if not cfg["mod_roles"]:
            return await interaction.response.send_message("No moderator roles configured.", ephemeral=True)
        mentions = [f"<@&{r}>" for r in cfg["mod_roles"]]
        await interaction.response.send_message("Moderator roles: " + ", ".join(mentions), ephemeral=True)

    # =========================================================
    # GENERAL
    # =========================================================
    @config.command(name="logchannel", description="Set the general mod-log channel")
    @is_bot_admin()
    async def logchannel(self, interaction: discord.Interaction, channel: discord.TextChannel):
        cfg = get_config(interaction.guild.id)
        cfg["log_channel"] = channel.id
        save_config(interaction.guild.id, cfg)
        await interaction.response.send_message(f"✅ Log channel set to {channel.mention}.", ephemeral=True)

    @config.command(name="view", description="View the full current configuration")
    @is_bot_admin()
    async def view(self, interaction: discord.Interaction):
        cfg = get_config(interaction.guild.id)
        embed = discord.Embed(title="⚙️ Bot Configuration", color=discord.Color.blurple())

        embed.add_field(
            name="Roles",
            value=(
                f"Bot-admin roles: {', '.join(f'<@&{r}>' for r in cfg['admin_roles']) or 'none (owner/admin only)'}\n"
                f"Mod roles: {', '.join(f'<@&{r}>' for r in cfg['mod_roles']) or 'none'}"
            ),
            inline=False,
        )
        ar = cfg["autorole"]
        embed.add_field(
            name="Auto-role",
            value=f"Enabled: {ar['enabled']}\nRoles: {', '.join(f'<@&{r}>' for r in ar['role_ids']) or 'none'}",
            inline=False,
        )
        am = cfg["automod"]
        embed.add_field(
            name="Automod (bad words)",
            value=f"Enabled: {am['enabled']}\nPunishment: {am['punishment']}\nMute: {am['mute_minutes']}m\nWarn limit: {am['warn_limit']} → {am['escalate_punishment']}",
            inline=False,
        )
        ar2 = cfg["antiraid"]
        embed.add_field(
            name="Anti-raid / Anti-nuke",
            value=(
                f"Raid: enabled={ar2['enabled']}, threshold={ar2['join_threshold']}/{ar2['join_interval']}s, action={ar2['action']}\n"
                f"Nuke guard: enabled={ar2['antinuke_enabled']}, limit={ar2['antinuke_action_limit']}/{ar2['antinuke_interval']}s, action={ar2['antinuke_punishment']}"
            ),
            inline=False,
        )
        tk = cfg["tickets"]
        embed.add_field(
            name="Tickets",
            value=f"Enabled: {tk['enabled']}\nCategory: {('<#%s>' % tk['category_id']) if tk['category_id'] else 'not set'}\nSupport roles: {', '.join(f'<@&{r}>' for r in tk['support_roles']) or 'none'}",
            inline=False,
        )
        lv = cfg["levels"]
        embed.add_field(
            name="Levels / Points",
            value=f"Enabled: {lv['enabled']}\nPoints per message: {lv['min_points']}-{lv['max_points']}\nCooldown: {lv['cooldown_seconds']}s",
            inline=False,
        )
        afk = cfg["afk_vc"]
        embed.add_field(
            name="AFK voice move",
            value=f"Enabled: {afk['enabled']}\nAFK channel: {('<#%s>' % afk['afk_channel_id']) if afk['afk_channel_id'] else 'not set'}\nTimeout: {afk['mute_timeout_minutes']}m",
            inline=False,
        )
        await interaction.response.send_message(embed=embed, ephemeral=True)


async def setup(bot: commands.Bot):
    await bot.add_cog(AdminConfig(bot))
