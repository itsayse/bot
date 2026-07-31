import io
import html
import discord
from discord import app_commands
from discord.ext import commands

from utils.storage import get_config, save_config
from utils.checks import is_bot_admin, is_mod_member


TRANSCRIPT_CSS = """
body { background:#313338; color:#dbdee1; font-family:'gg sans',Helvetica,Arial,sans-serif; margin:0; padding:24px; }
.header { border-bottom:1px solid #3f4147; padding-bottom:16px; margin-bottom:16px; }
.header h1 { margin:0 0 4px; font-size:22px; color:#fff; }
.header p { margin:2px 0; color:#949ba4; font-size:13px; }
.msg { display:flex; gap:16px; padding:8px 0; }
.msg:hover { background:#2e3035; }
.avatar { width:40px; height:40px; border-radius:50%; flex-shrink:0; }
.body { min-width:0; }
.meta { display:flex; align-items:baseline; gap:8px; }
.author { font-weight:600; color:#f2f3f5; }
.time { font-size:12px; color:#949ba4; }
.content { white-space:pre-wrap; word-wrap:break-word; line-height:1.4; }
.attachment { margin-top:6px; }
.attachment img { max-width:400px; max-height:300px; border-radius:8px; display:block; }
.attachment a { color:#00a8fc; text-decoration:none; }
"""


def _fmt_content(content: str) -> str:
    return html.escape(content) if content else "<i>[no text content]</i>"


async def generate_transcript_html(channel: discord.TextChannel, closed_by: str) -> discord.File:
    messages = [m async for m in channel.history(limit=2000, oldest_first=True)]

    rows = []
    for m in messages:
        avatar = m.author.display_avatar.url if m.author.display_avatar else ""
        ts = m.created_at.strftime("%Y-%m-%d %H:%M UTC")
        attach_html = ""
        for a in m.attachments:
            if a.content_type and a.content_type.startswith("image/"):
                attach_html += f'<div class="attachment"><img src="{html.escape(a.url)}"></div>'
            else:
                attach_html += f'<div class="attachment"><a href="{html.escape(a.url)}">{html.escape(a.filename)}</a></div>'
        rows.append(f"""
        <div class="msg">
            <img class="avatar" src="{html.escape(str(avatar))}">
            <div class="body">
                <div class="meta"><span class="author">{html.escape(str(m.author))}</span><span class="time">{ts}</span></div>
                <div class="content">{_fmt_content(m.content)}</div>
                {attach_html}
            </div>
        </div>""")

    page = f"""<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Transcript — {html.escape(channel.name)}</title>
<style>{TRANSCRIPT_CSS}</style></head>
<body>
<div class="header">
    <h1>#{html.escape(channel.name)}</h1>
    <p>{len(messages)} messages • closed by {html.escape(closed_by)}</p>
</div>
{''.join(rows)}
</body></html>"""

    buf = io.BytesIO(page.encode("utf-8"))
    return discord.File(buf, filename=f"transcript-{channel.name}.html")


class TicketPanelView(discord.ui.View):
    """Persistent view — registered once in setup() so the button keeps working after restarts."""

    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="Open a ticket", style=discord.ButtonStyle.blurple, custom_id="tickets:open")
    async def open_ticket(self, interaction: discord.Interaction, button: discord.ui.Button):
        cog: "Tickets" = interaction.client.get_cog("Tickets")
        await cog.create_ticket(interaction)


class TicketCloseView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="Close ticket", style=discord.ButtonStyle.red, custom_id="tickets:close")
    async def close_ticket(self, interaction: discord.Interaction, button: discord.ui.Button):
        cog: "Tickets" = interaction.client.get_cog("Tickets")
        await cog.close_ticket(interaction)


class Tickets(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot
        bot.add_view(TicketPanelView())
        bot.add_view(TicketCloseView())

    ticket = app_commands.Group(name="ticket", description="Configure and manage the ticket system")

    async def cog_app_command_error(self, interaction: discord.Interaction, error: app_commands.AppCommandError):
        if isinstance(error, app_commands.CheckFailure):
            await interaction.response.send_message(f"⛔ {error}", ephemeral=True)
        else:
            await interaction.response.send_message(f"⚠️ {error}", ephemeral=True)

    # -------------------- config --------------------
    @ticket.command(name="setup", description="Post the ticket panel in a channel")
    @app_commands.default_permissions(administrator=True)
    @is_bot_admin()
    async def setup_panel(self, interaction: discord.Interaction, channel: discord.TextChannel, category: discord.CategoryChannel):
        cfg = get_config(interaction.guild.id)
        cfg["tickets"]["panel_channel"] = channel.id
        cfg["tickets"]["category_id"] = category.id
        cfg["tickets"]["enabled"] = True

        embed = discord.Embed(
            title="🎫 Support Tickets",
            description="Need help? Click the button below to open a private ticket with the support team.",
            color=discord.Color.blurple(),
        )
        msg = await channel.send(embed=embed, view=TicketPanelView())
        cfg["tickets"]["panel_message"] = msg.id
        save_config(interaction.guild.id, cfg)
        await interaction.response.send_message(f"✅ Ticket panel posted in {channel.mention}.", ephemeral=True)

    @ticket.command(name="supportrole", description="Add/remove a role that can see and manage tickets")
    @app_commands.default_permissions(administrator=True)
    @app_commands.choices(mode=[app_commands.Choice(name="add", value="add"), app_commands.Choice(name="remove", value="remove")])
    @is_bot_admin()
    async def supportrole(self, interaction: discord.Interaction, mode: app_commands.Choice[str], role: discord.Role):
        cfg = get_config(interaction.guild.id)
        roles = cfg["tickets"]["support_roles"]
        if mode.value == "add" and role.id not in roles:
            roles.append(role.id)
        elif mode.value == "remove" and role.id in roles:
            roles.remove(role.id)
        save_config(interaction.guild.id, cfg)
        await interaction.response.send_message("✅ Updated support roles.", ephemeral=True)

    @ticket.command(name="logchannel", description="Set the channel ticket transcripts/logs go to")
    @app_commands.default_permissions(administrator=True)
    @is_bot_admin()
    async def logchannel(self, interaction: discord.Interaction, channel: discord.TextChannel):
        cfg = get_config(interaction.guild.id)
        cfg["tickets"]["log_channel"] = channel.id
        save_config(interaction.guild.id, cfg)
        await interaction.response.send_message(f"✅ Ticket log channel set to {channel.mention}.", ephemeral=True)

    @ticket.command(name="add", description="Add a member to the current ticket")
    async def add_member(self, interaction: discord.Interaction, member: discord.Member):
        cfg = get_config(interaction.guild.id)
        tickets = cfg["tickets"]["open_tickets"]
        if str(interaction.channel.id) not in tickets:
            return await interaction.response.send_message("This isn't a ticket channel.", ephemeral=True)
        await interaction.channel.set_permissions(member, view_channel=True, send_messages=True, read_message_history=True)
        await interaction.response.send_message(f"✅ Added {member.mention} to the ticket.")

    @ticket.command(name="close", description="Close the current ticket")
    async def close_cmd(self, interaction: discord.Interaction):
        await self.close_ticket(interaction)

    # -------------------- logic --------------------
    @commands.Cog.listener()
    async def on_guild_channel_delete(self, channel: discord.abc.GuildChannel):
        cfg = get_config(channel.guild.id)
        tk = cfg["tickets"]
        key = str(channel.id)
        if key in tk["open_tickets"]:
            tk["open_tickets"].pop(key)
            save_config(channel.guild.id, cfg)

    async def create_ticket(self, interaction: discord.Interaction):
        cfg = get_config(interaction.guild.id)
        tk = cfg["tickets"]
        if not tk["enabled"] or not tk["category_id"]:
            return await interaction.response.send_message("Tickets aren't set up yet.", ephemeral=True)

        category = interaction.guild.get_channel(tk["category_id"])
        if category is None:
            return await interaction.response.send_message("Ticket category is missing — ask an admin to run /ticket setup again.", ephemeral=True)

        # avoid duplicate open tickets for the same user
        stale = []
        blocked = False
        for chan_id, data in tk["open_tickets"].items():
            existing = interaction.guild.get_channel(int(chan_id))
            if existing is None:
                stale.append(chan_id)  # channel deleted outside /ticket close — clean it up
                continue
            if data["owner"] == interaction.user.id:
                blocked = True
                blocked_channel = existing

        for chan_id in stale:
            tk["open_tickets"].pop(chan_id, None)
        if stale:
            save_config(interaction.guild.id, cfg)

        if blocked:
            return await interaction.response.send_message(f"You already have an open ticket: {blocked_channel.mention}", ephemeral=True)

        tk["counter"] += 1
        number = tk["counter"]

        overwrites = {
            interaction.guild.default_role: discord.PermissionOverwrite(view_channel=False),
            interaction.user: discord.PermissionOverwrite(view_channel=True, send_messages=True, read_message_history=True),
            interaction.guild.me: discord.PermissionOverwrite(view_channel=True, send_messages=True, manage_channels=True),
        }
        for role_id in tk["support_roles"]:
            role = interaction.guild.get_role(role_id)
            if role:
                overwrites[role] = discord.PermissionOverwrite(view_channel=True, send_messages=True, read_message_history=True)

        channel = await interaction.guild.create_text_channel(
            name=f"ticket-{number:04d}",
            category=category,
            overwrites=overwrites,
            reason=f"Ticket opened by {interaction.user}",
        )
        tk["open_tickets"][str(channel.id)] = {"owner": interaction.user.id, "number": number}
        save_config(interaction.guild.id, cfg)

        embed = discord.Embed(
            title=f"Ticket #{number:04d}",
            description=tk.get("welcome_message", "Hey {member}! Support will be with you shortly.").replace("{member}", interaction.user.mention),
            color=discord.Color.green(),
        )
        await channel.send(embed=embed, view=TicketCloseView())
        await interaction.response.send_message(f"✅ Ticket created: {channel.mention}", ephemeral=True)

    async def close_ticket(self, interaction: discord.Interaction):
        cfg = get_config(interaction.guild.id)
        tk = cfg["tickets"]
        key = str(interaction.channel.id)
        if key not in tk["open_tickets"]:
            return await interaction.response.send_message("This isn't a ticket channel.", ephemeral=True)

        data = tk["open_tickets"].pop(key)
        save_config(interaction.guild.id, cfg)

        # acknowledge immediately — transcript generation can take longer than
        # Discord's 3s interaction window, so respond before doing slow work
        await interaction.response.send_message("🔒 Closing this ticket and generating a transcript...")

        log_channel = interaction.guild.get_channel(tk["log_channel"]) if tk["log_channel"] else None
        if log_channel:
            owner = interaction.guild.get_member(data["owner"])
            owner_text = owner.mention if owner else data["owner"]

            if tk.get("transcripts_enabled", True):
                try:
                    transcript = await generate_transcript_html(interaction.channel, str(interaction.user))
                    await log_channel.send(
                        f"🔒 Ticket #{data['number']:04d} (owner: {owner_text}) closed by {interaction.user.mention}.",
                        file=transcript,
                    )
                except discord.HTTPException:
                    await log_channel.send(
                        f"🔒 Ticket #{data['number']:04d} (owner: {owner_text}) closed by {interaction.user.mention}. "
                        f"(⚠️ couldn't generate transcript)"
                    )
            else:
                await log_channel.send(
                    f"🔒 Ticket #{data['number']:04d} (owner: {owner_text}) closed by {interaction.user.mention}."
                )

        await interaction.channel.delete(reason=f"Ticket closed by {interaction.user}")


async def setup(bot: commands.Bot):
    await bot.add_cog(Tickets(bot))
