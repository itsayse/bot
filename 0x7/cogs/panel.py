import discord
from discord import app_commands
from discord.ext import commands

from utils.storage import get_config, save_config, get_levels, save_levels
from utils.checks import is_bot_admin_member
from utils import keystore, role_sync

PUNISHMENTS = ["delete", "warn", "mute", "kick", "ban"]
ESCALATIONS = ["mute", "kick", "ban"]
RAID_ACTIONS = ["kick", "ban", "lockdown"]
NUKE_PUNISHMENTS = ["strip_roles", "ban"]

CATEGORIES = [
    ("overview", "🏠 Overview (everything at a glance)"),
    ("general", "⚙️ General & Roles"),
    ("automod", "🛡️ Automod"),
    ("autorole", "🎭 Auto-Role"),
    ("antiraid", "🚨 Anti-Raid / Anti-Nuke"),
    ("levels", "📈 Points & Levels"),
    ("afkvc", "🔇 AFK Voice"),
    ("tickets", "🎫 Tickets"),
    ("verification", "🔒 Verification"),
    ("welcome", "🎉 Welcome"),
    ("keys", "🔑 Server Keys"),
]


def _mention_roles(ids):
    return ", ".join(f"<@&{r}>" for r in ids) if ids else "*none*"


def _mention_channel(cid):
    return f"<#{cid}>" if cid else "*not set*"


def _role_defaults(ids):
    return [discord.Object(id=r) for r in ids]


def _channel_defaults(cid):
    return [discord.Object(id=cid)] if cid else []


def build_embed(guild: discord.Guild, category: str, page: int = 0) -> discord.Embed:
    cfg = get_config(guild.id)
    embed = discord.Embed(color=discord.Color.blurple())
    embed.set_author(
        name=f"{guild.name} — Configuration Panel",
        icon_url=guild.icon.url if guild.icon else None,
    )

    if category == "overview":
        embed.title = "🏠 Overview — everything at a glance"
        embed.description = "Pick a section below (dropdown, or the quick buttons) to change anything."
        embed.add_field(
            name="⚙️ General",
            value=f"Log: {_mention_channel(cfg['log_channel'])} • Bot-admins: {_mention_roles(cfg['admin_roles'])} • Mods: {_mention_roles(cfg['mod_roles'])}",
            inline=False,
        )
        am = cfg["automod"]
        embed.add_field(
            name="🛡️ Automod",
            value=f"{'🟢' if am['enabled'] else '🔴'} • punishment `{am['punishment']}` • {am['warn_limit']} warns → `{am['escalate_punishment']}` • mute {am['mute_minutes']}m",
            inline=False,
        )
        ar = cfg["autorole"]
        embed.add_field(
            name="🎭 Auto-Role",
            value=f"{'🟢' if ar['enabled'] else '🔴'} • {_mention_roles(ar['role_ids'])}",
            inline=False,
        )
        ar2 = cfg["antiraid"]
        embed.add_field(
            name="🚨 Anti-Raid / Anti-Nuke",
            value=(
                f"Raid {'🟢' if ar2['enabled'] else '🔴'} ({ar2['join_threshold']}/{ar2['join_interval']}s → `{ar2['action']}`) • "
                f"Nuke {'🟢' if ar2['antinuke_enabled'] else '🔴'} ({ar2['antinuke_action_limit']}/{ar2['antinuke_interval']}s → `{ar2['antinuke_punishment']}`)"
            ),
            inline=False,
        )
        lv = cfg["levels"]
        embed.add_field(
            name="📈 Points & Levels",
            value=(
                f"{'🟢' if lv['enabled'] else '🔴'} • {lv['min_points']}-{lv['max_points']} pts • "
                f"drop: {_mention_channel(lv['drop_channel'])} ({'every ' + str(lv['drop_interval_minutes']) + 'm' if lv['drop_interval_minutes'] else 'manual'}) • "
                f"level-up: {_mention_channel(lv['level_up_channel'])} • {len(lv['role_rewards'])} role reward(s)"
            ),
            inline=False,
        )
        afk = cfg["afk_vc"]
        embed.add_field(
            name="🔇 AFK Voice",
            value=f"{'🟢' if afk['enabled'] else '🔴'} • {_mention_channel(afk['afk_channel_id'])} • {afk['mute_timeout_minutes']}m timeout",
            inline=False,
        )
        tk = cfg["tickets"]
        embed.add_field(
            name="🎫 Tickets",
            value=f"{'🟢' if tk['enabled'] else '🔴'} • {len(tk['open_tickets'])} open • transcripts {'🟢' if tk.get('transcripts_enabled', True) else '🔴'}",
            inline=False,
        )
        v = cfg["verification"]
        embed.add_field(
            name="🔒 Verification",
            value=f"{'🟢' if v['enabled'] else '🔴'} • {_mention_channel(v['verify_channel'])}",
            inline=False,
        )
        w = cfg["welcome"]
        embed.add_field(
            name="🎉 Welcome",
            value=f"{'🟢' if w['enabled'] else '🔴'} • {_mention_channel(w['channel_id'])}",
            inline=False,
        )

    elif category == "general":
        embed.title = "⚙️ General & Roles"
        embed.add_field(name="📋 Mod-log channel", value=_mention_channel(cfg["log_channel"]), inline=False)
        embed.add_field(
            name="🔑 Bot-admin roles",
            value=_mention_roles(cfg["admin_roles"]) + "\n*(server owner & Administrators can always use this panel)*",
            inline=False,
        )
        embed.add_field(name="🛡️ Moderator roles", value=_mention_roles(cfg["mod_roles"]), inline=False)

    elif category == "automod":
        am = cfg["automod"]
        embed.title = f"🛡️ Automod (page {page + 1}/4)"
        if page == 0:
            embed.add_field(name="Status", value="🟢 Enabled" if am["enabled"] else "🔴 Disabled", inline=True)
            embed.add_field(name="Punishment", value=f"`{am['punishment']}`", inline=True)
            embed.add_field(name="Log channel", value=_mention_channel(am["log_channel"]), inline=True)
            embed.add_field(name="Escalation", value=f"After **{am['warn_limit']}** warns → `{am['escalate_punishment']}`", inline=True)
            embed.add_field(name="Mute duration", value=f"{am['mute_minutes']} min", inline=True)
        elif page == 1:
            embed.add_field(name="🚫 Bypass roles", value=_mention_roles(am["bypass_roles"]), inline=False)
            embed.add_field(name="📖 Bad-word list", value=f"`bad.txt` — auto-reloads every 30s", inline=False)
            embed.add_field(name="📩 DM on warn", value="🟢 On" if am.get("dm_on_warn", True) else "🔴 Off", inline=False)
        elif page == 2:
            sp = am.get("spam", {})
            embed.add_field(name="Spam control", value="🟢 Enabled" if sp.get("enabled") else "🔴 Disabled", inline=True)
            embed.add_field(name="Flood trigger", value=f"{sp.get('message_limit')} msgs / {sp.get('interval_seconds')}s", inline=True)
            embed.add_field(name="Duplicate trigger", value=f"{sp.get('duplicate_limit')} identical in a row", inline=True)
            embed.add_field(name="Spam punishment", value=f"`{sp.get('punishment')}`", inline=True)
            embed.add_field(name="Spam mute duration", value=f"{sp.get('mute_minutes')} min", inline=True)
        else:
            inv = am.get("invite_filter", {})
            embed.add_field(name="Invite-link filter", value="🟢 Enabled" if inv.get("enabled") else "🔴 Disabled", inline=True)
            embed.add_field(name="Allow own server's invite", value="✅ Yes" if inv.get("allow_own_server") else "❌ No", inline=True)
            embed.add_field(name="Invite punishment", value=f"`{inv.get('punishment')}`", inline=True)
            embed.add_field(
                name="🧹 Raid purge",
                value=f"On spam/invite triggers, also wipe that member's messages from the **last {am.get('raid_purge_minutes', 30)} min** in that channel (0 = off)",
                inline=False,
            )

    elif category == "autorole":
        ar = cfg["autorole"]
        embed.title = "🎭 Auto-Role"
        embed.add_field(name="Status", value="🟢 Enabled" if ar["enabled"] else "🔴 Disabled", inline=True)
        embed.add_field(name="Roles given on join", value=_mention_roles(ar["role_ids"]), inline=False)

    elif category == "antiraid":
        ar2 = cfg["antiraid"]
        embed.title = f"🚨 Anti-Raid / Anti-Nuke (page {page + 1}/2)"
        if page == 0:
            embed.add_field(name="Raid protection", value="🟢 Enabled" if ar2["enabled"] else "🔴 Disabled", inline=True)
            embed.add_field(name="Trigger", value=f"{ar2['join_threshold']} joins / {ar2['join_interval']}s", inline=True)
            embed.add_field(name="Min account age", value=f"{ar2['min_account_age_hours']}h", inline=True)
            embed.add_field(name="Action", value=f"`{ar2['action']}`", inline=True)
            embed.add_field(name="Log channel", value=_mention_channel(ar2["log_channel"]), inline=True)
        else:
            embed.add_field(name="Anti-nuke", value="🟢 Enabled" if ar2["antinuke_enabled"] else "🔴 Disabled", inline=True)
            embed.add_field(name="Trigger", value=f"{ar2['antinuke_action_limit']} destructive actions / {ar2['antinuke_interval']}s", inline=True)
            embed.add_field(name="Punishment", value=f"`{ar2['antinuke_punishment']}`", inline=True)
            embed.add_field(name="Manual lockdown", value="Use the button below to raise/lower verification level instantly.", inline=False)

    elif category == "levels":
        lv = cfg["levels"]
        embed.title = f"📈 Points & Levels (page {page + 1}/2)"
        if page == 0:
            embed.add_field(name="Status", value="🟢 Enabled" if lv["enabled"] else "🔴 Disabled", inline=True)
            embed.add_field(name="Points per drop", value=f"{lv['min_points']}-{lv['max_points']}", inline=True)
            embed.add_field(name="Drop channel", value=_mention_channel(lv["drop_channel"]), inline=True)
            embed.add_field(
                name="Auto-drop interval",
                value=(f"every {lv['drop_interval_minutes']} min" if lv["drop_interval_minutes"] else "manual only"),
                inline=True,
            )
            embed.add_field(name="Level-up channel", value=_mention_channel(lv["level_up_channel"]), inline=True)
            embed.add_field(
                name="How points work",
                value="The bot posts a **drop** message in the drop channel. The first **3** members to *reply* to it each win a random amount of points.",
                inline=False,
            )
        else:
            rewards = lv["role_rewards"]
            if rewards:
                lines = "\n".join(
                    f"Level **{lvl}** → <@&{rid}>" for lvl, rid in sorted(rewards.items(), key=lambda kv: int(kv[0]))
                )
            else:
                lines = "*none set*"
            embed.add_field(name="🎁 Role rewards", value=lines, inline=False)

    elif category == "afkvc":
        afk = cfg["afk_vc"]
        embed.title = "🔇 AFK Voice"
        embed.add_field(name="Status", value="🟢 Enabled" if afk["enabled"] else "🔴 Disabled", inline=True)
        embed.add_field(name="AFK channel", value=_mention_channel(afk["afk_channel_id"]), inline=True)
        embed.add_field(name="Mute timeout", value=f"{afk['mute_timeout_minutes']} min", inline=True)
        embed.add_field(name="Ignored roles", value=_mention_roles(afk["ignore_roles"]), inline=False)

    elif category == "tickets":
        tk = cfg["tickets"]
        embed.title = "🎫 Tickets"
        embed.add_field(name="Status", value="🟢 Enabled" if tk["enabled"] else "🔴 Disabled", inline=True)
        embed.add_field(name="Panel channel", value=_mention_channel(tk["panel_channel"]), inline=True)
        embed.add_field(name="Category", value=_mention_channel(tk["category_id"]), inline=True)
        embed.add_field(name="Log channel", value=_mention_channel(tk["log_channel"]), inline=True)
        embed.add_field(name="Web transcripts", value="🟢 On" if tk.get("transcripts_enabled", True) else "🔴 Off", inline=True)
        embed.add_field(name="Open tickets", value=str(len(tk["open_tickets"])), inline=True)
        embed.add_field(name="Support roles", value=_mention_roles(tk["support_roles"]), inline=False)
        embed.add_field(
            name="ℹ️ How to post the panel",
            value="Use `/ticket setup #channel #category` — the panel message itself is posted by that command.",
            inline=False,
        )

    elif category == "verification":
        v = cfg["verification"]
        embed.title = "🔒 Verification"
        embed.add_field(name="Status", value="🟢 Enabled" if v["enabled"] else "🔴 Disabled", inline=True)
        embed.add_field(name="Verify channel", value=_mention_channel(v["verify_channel"]), inline=True)
        embed.add_field(name="Captcha length", value=str(v.get("captcha_length", 6)), inline=True)
        embed.add_field(name="Unverified role", value=_mention_roles([v["unverified_role"]]) if v["unverified_role"] else "*not set*", inline=True)
        embed.add_field(name="Verified role", value=_mention_roles([v["verified_role"]]) if v["verified_role"] else "*none — just removes unverified*", inline=True)
        embed.add_field(
            name="ℹ️ Setup checklist",
            value=(
                "1. Create an **Unverified** role and hide channels from it in Server Settings.\n"
                "2. Set that role below.\n"
                "3. Run `/verify setup #channel` to post the Verify button."
            ),
            inline=False,
        )

    elif category == "welcome":
        w = cfg["welcome"]
        embed.title = "🎉 Welcome Messages"
        embed.add_field(name="Status", value="🟢 Enabled" if w["enabled"] else "🔴 Disabled", inline=True)
        embed.add_field(name="Channel", value=_mention_channel(w["channel_id"]), inline=True)
        embed.add_field(name="Use welcome.png", value="🟢 On" if w.get("use_image") else "🔴 Off", inline=True)
        embed.add_field(name="Also DM new members", value="🟢 On" if w.get("dm_enabled") else "🔴 Off", inline=True)
        embed.add_field(name="Message template", value=f"```{w['message']}```", inline=False)
        if w.get("dm_enabled"):
            embed.add_field(name="DM message template", value=f"```{w['dm_message']}```", inline=False)
        embed.add_field(name="Placeholders", value="`{member}` `{member_name}` `{guild}` `{membercount}`", inline=False)

    elif category == "keys":
        k = cfg["keys"]
        embed.title = "🔑 Minecraft Server Keys"
        embed.add_field(
            name="Key role",
            value=(f"<@&{k['key_role']}>" if k.get("key_role") else "*not set — no role will be granted*"),
            inline=True,
        )
        embed.add_field(name="Default validity", value=f"{k.get('default_days', 30)} days", inline=True)
        embed.add_field(
            name="ℹ️ How it works",
            value=(
                "Generate a key with `/key generate` (or gift one with `/giftkey @user`).\n"
                "A player redeems it in-game with `/key <code> <their_discord_id>`, which links their "
                "Discord account and grants the key role above.\n"
                "The role is auto-removed the moment a key expires or is removed."
            ),
            inline=False,
        )
        recent = sorted(keystore.list_keys(), key=lambda r: r["created_at"], reverse=True)[:8]
        if recent:
            lines = [
                f"`{r['key'][:8]}...` — {keystore.status_label(r)} — "
                f"{r['redeemed_name'] or 'unclaimed'} — "
                f"{'🔗' if r.get('discord_id') else '—'}"
                for r in recent
            ]
            embed.add_field(name="Recent keys (8 of most recent)", value="\n".join(lines), inline=False)
        else:
            embed.add_field(name="Recent keys", value="*none generated yet*", inline=False)

    embed.set_footer(text="Use the dropdown to switch sections • Only bot-admins can use this panel")
    return embed


# ============================================================
#  MODALS
# ============================================================
class _BaseModal(discord.ui.Modal):
    def __init__(self, panel_view: "PanelView", title: str):
        super().__init__(title=title)
        self.panel_view = panel_view

    async def _finish(self, interaction: discord.Interaction, confirm: str):
        await interaction.response.send_message(confirm, ephemeral=True)
        await self.panel_view.push_update(interaction.guild)


class AutomodNumbersModal(_BaseModal):
    warn_limit = discord.ui.TextInput(label="Warn limit (before escalation)", placeholder="3", max_length=3)
    mute_minutes = discord.ui.TextInput(label="Mute duration (minutes)", placeholder="10", max_length=5)

    def __init__(self, panel_view, am):
        super().__init__(panel_view, "Configure Automod Numbers")
        self.warn_limit.default = str(am["warn_limit"])
        self.mute_minutes.default = str(am["mute_minutes"])

    async def on_submit(self, interaction: discord.Interaction):
        try:
            wl = max(1, int(self.warn_limit.value))
            mm = max(1, int(self.mute_minutes.value))
        except ValueError:
            return await interaction.response.send_message("⚠️ Please enter whole numbers.", ephemeral=True)
        cfg = get_config(interaction.guild.id)
        cfg["automod"]["warn_limit"] = wl
        cfg["automod"]["mute_minutes"] = mm
        save_config(interaction.guild.id, cfg)
        await self._finish(interaction, f"✅ Warn limit set to {wl}, mute duration set to {mm}m.")


class SpamNumbersModal(_BaseModal):
    message_limit = discord.ui.TextInput(label="Message limit (flood trigger)", placeholder="5", max_length=3)
    interval_seconds = discord.ui.TextInput(label="...within how many seconds", placeholder="5", max_length=4)
    duplicate_limit = discord.ui.TextInput(label="Identical messages in a row", placeholder="3", max_length=3)
    mute_minutes = discord.ui.TextInput(label="Mute duration (minutes, if punishment=mute)", placeholder="10", max_length=5)

    def __init__(self, panel_view, spam_cfg):
        super().__init__(panel_view, "Configure Spam Control")
        self.message_limit.default = str(spam_cfg["message_limit"])
        self.interval_seconds.default = str(spam_cfg["interval_seconds"])
        self.duplicate_limit.default = str(spam_cfg["duplicate_limit"])
        self.mute_minutes.default = str(spam_cfg["mute_minutes"])

    async def on_submit(self, interaction: discord.Interaction):
        try:
            ml = max(2, int(self.message_limit.value))
            iv = max(1, int(self.interval_seconds.value))
            dl = max(0, int(self.duplicate_limit.value))
            mm = max(1, int(self.mute_minutes.value))
        except ValueError:
            return await interaction.response.send_message("⚠️ Please enter whole numbers.", ephemeral=True)
        cfg = get_config(interaction.guild.id)
        cfg["automod"]["spam"]["message_limit"] = ml
        cfg["automod"]["spam"]["interval_seconds"] = iv
        cfg["automod"]["spam"]["duplicate_limit"] = dl
        cfg["automod"]["spam"]["mute_minutes"] = mm
        save_config(interaction.guild.id, cfg)
        await self._finish(interaction, f"✅ Spam control updated: {ml} msgs/{iv}s, {dl} duplicates, mute {mm}m.")


class RaidPurgeModal(_BaseModal):
    minutes = discord.ui.TextInput(label="Purge window in minutes (0 = off)", placeholder="30", max_length=4)

    def __init__(self, panel_view, am):
        super().__init__(panel_view, "Configure Raid Purge")
        self.minutes.default = str(am.get("raid_purge_minutes", 30))

    async def on_submit(self, interaction: discord.Interaction):
        try:
            m = max(0, int(self.minutes.value))
        except ValueError:
            return await interaction.response.send_message("⚠️ Please enter a whole number.", ephemeral=True)
        cfg = get_config(interaction.guild.id)
        cfg["automod"]["raid_purge_minutes"] = m
        save_config(interaction.guild.id, cfg)
        if m:
            await self._finish(interaction, f"✅ On spam/invite triggers, the bot will now purge that member's last {m} minutes of messages in the channel.")
        else:
            await self._finish(interaction, "✅ Raid purge disabled — only the triggering message will be deleted.")


class TicketWelcomeModal(_BaseModal):
    text = discord.ui.TextInput(label="Ticket welcome message ({member} works)", style=discord.TextStyle.paragraph, max_length=1000)

    def __init__(self, panel_view, tk):
        super().__init__(panel_view, "Edit Ticket Welcome Message")
        self.text.default = tk["welcome_message"]

    async def on_submit(self, interaction: discord.Interaction):
        cfg = get_config(interaction.guild.id)
        cfg["tickets"]["welcome_message"] = self.text.value
        save_config(interaction.guild.id, cfg)
        await self._finish(interaction, "✅ Ticket welcome message updated.")


class CaptchaLengthModal(_BaseModal):
    length = discord.ui.TextInput(label="Captcha code length (4-10)", placeholder="6", max_length=2)

    def __init__(self, panel_view, v):
        super().__init__(panel_view, "Configure Captcha")
        self.length.default = str(v.get("captcha_length", 6))

    async def on_submit(self, interaction: discord.Interaction):
        try:
            n = max(4, min(10, int(self.length.value)))
        except ValueError:
            return await interaction.response.send_message("⚠️ Please enter a whole number.", ephemeral=True)
        cfg = get_config(interaction.guild.id)
        cfg["verification"]["captcha_length"] = n
        save_config(interaction.guild.id, cfg)
        await self._finish(interaction, f"✅ Captcha length set to {n} characters.")


class WelcomeMessagesModal(_BaseModal):
    message = discord.ui.TextInput(label="Channel message", style=discord.TextStyle.paragraph, max_length=1000)
    dm_message = discord.ui.TextInput(label="DM message", style=discord.TextStyle.paragraph, max_length=1000, required=False)

    def __init__(self, panel_view, w):
        super().__init__(panel_view, "Edit Welcome Messages")
        self.message.default = w["message"]
        self.dm_message.default = w["dm_message"]

    async def on_submit(self, interaction: discord.Interaction):
        cfg = get_config(interaction.guild.id)
        cfg["welcome"]["message"] = self.message.value
        cfg["welcome"]["dm_message"] = self.dm_message.value or cfg["welcome"]["dm_message"]
        save_config(interaction.guild.id, cfg)
        await self._finish(interaction, "✅ Welcome messages updated.")


class AntiraidThresholdModal(_BaseModal):
    joins = discord.ui.TextInput(label="Join threshold (# of joins)", placeholder="6", max_length=3)
    seconds = discord.ui.TextInput(label="...within how many seconds", placeholder="15", max_length=4)
    min_age = discord.ui.TextInput(label="Min account age (hours)", placeholder="24", max_length=5)

    def __init__(self, panel_view, ar):
        super().__init__(panel_view, "Configure Raid Thresholds")
        self.joins.default = str(ar["join_threshold"])
        self.seconds.default = str(ar["join_interval"])
        self.min_age.default = str(ar["min_account_age_hours"])

    async def on_submit(self, interaction: discord.Interaction):
        try:
            j = max(2, int(self.joins.value))
            s = max(3, int(self.seconds.value))
            a = max(0, int(self.min_age.value))
        except ValueError:
            return await interaction.response.send_message("⚠️ Please enter whole numbers.", ephemeral=True)
        cfg = get_config(interaction.guild.id)
        cfg["antiraid"]["join_threshold"] = j
        cfg["antiraid"]["join_interval"] = s
        cfg["antiraid"]["min_account_age_hours"] = a
        save_config(interaction.guild.id, cfg)
        await self._finish(interaction, f"✅ Raid trigger: {j} joins/{s}s, min account age {a}h.")


class AntinukeModal(_BaseModal):
    action_limit = discord.ui.TextInput(label="Destructive-action limit", placeholder="4", max_length=3)
    interval_seconds = discord.ui.TextInput(label="...within how many seconds", placeholder="10", max_length=4)

    def __init__(self, panel_view, ar):
        super().__init__(panel_view, "Configure Anti-Nuke")
        self.action_limit.default = str(ar["antinuke_action_limit"])
        self.interval_seconds.default = str(ar["antinuke_interval"])

    async def on_submit(self, interaction: discord.Interaction):
        try:
            al = max(2, int(self.action_limit.value))
            iv = max(3, int(self.interval_seconds.value))
        except ValueError:
            return await interaction.response.send_message("⚠️ Please enter whole numbers.", ephemeral=True)
        cfg = get_config(interaction.guild.id)
        cfg["antiraid"]["antinuke_action_limit"] = al
        cfg["antiraid"]["antinuke_interval"] = iv
        save_config(interaction.guild.id, cfg)
        await self._finish(interaction, f"✅ Anti-nuke trigger: {al} actions/{iv}s.")


class PointRangeModal(_BaseModal):
    min_points = discord.ui.TextInput(label="Minimum points per drop win", placeholder="10", max_length=4)
    max_points = discord.ui.TextInput(label="Maximum points per drop win", placeholder="60", max_length=4)

    def __init__(self, panel_view, lv):
        super().__init__(panel_view, "Configure Point Range")
        self.min_points.default = str(lv["min_points"])
        self.max_points.default = str(lv["max_points"])

    async def on_submit(self, interaction: discord.Interaction):
        try:
            mn = max(1, int(self.min_points.value))
            mx = int(self.max_points.value)
        except ValueError:
            return await interaction.response.send_message("⚠️ Please enter whole numbers.", ephemeral=True)
        if mx < mn:
            return await interaction.response.send_message("⚠️ Max must be ≥ min.", ephemeral=True)
        cfg = get_config(interaction.guild.id)
        cfg["levels"]["min_points"] = mn
        cfg["levels"]["max_points"] = mx
        save_config(interaction.guild.id, cfg)
        await self._finish(interaction, f"✅ Points per drop win set to {mn}-{mx}.")


class DropIntervalModal(_BaseModal):
    minutes = discord.ui.TextInput(
        label="Auto-drop every N minutes (0 = manual only)", placeholder="0", max_length=5
    )

    def __init__(self, panel_view, lv):
        super().__init__(panel_view, "Configure Auto-Drop Interval")
        self.minutes.default = str(lv["drop_interval_minutes"])

    async def on_submit(self, interaction: discord.Interaction):
        try:
            m = max(0, int(self.minutes.value))
        except ValueError:
            return await interaction.response.send_message("⚠️ Please enter a whole number.", ephemeral=True)
        cfg = get_config(interaction.guild.id)
        cfg["levels"]["drop_interval_minutes"] = m
        save_config(interaction.guild.id, cfg)
        msg = f"✅ Auto-drops every {m} minute(s)." if m else "✅ Auto-drops disabled — use 'Send Drop Now' instead."
        await self._finish(interaction, msg)


class AfkTimeoutModal(_BaseModal):
    minutes = discord.ui.TextInput(label="Minutes muted before being moved", placeholder="15", max_length=4)

    def __init__(self, panel_view, afk):
        super().__init__(panel_view, "Configure AFK Timeout")
        self.minutes.default = str(afk["mute_timeout_minutes"])

    async def on_submit(self, interaction: discord.Interaction):
        try:
            m = max(1, int(self.minutes.value))
        except ValueError:
            return await interaction.response.send_message("⚠️ Please enter a whole number.", ephemeral=True)
        cfg = get_config(interaction.guild.id)
        cfg["afk_vc"]["mute_timeout_minutes"] = m
        save_config(interaction.guild.id, cfg)
        await self._finish(interaction, f"✅ AFK mute timeout set to {m}m.")


class SetXPModal(_BaseModal):
    xp = discord.ui.TextInput(label="New total XP for this level", placeholder="0", max_length=8)

    def __init__(self, panel_view, member: discord.Member):
        super().__init__(panel_view, f"Set XP for {member.display_name}"[:45])
        self.member = member

    async def on_submit(self, interaction: discord.Interaction):
        try:
            new_xp = max(0, int(self.xp.value))
        except ValueError:
            return await interaction.response.send_message("⚠️ Enter a whole number.", ephemeral=True)
        data = get_levels(interaction.guild.id)
        entry = data.setdefault(str(self.member.id), {"xp": 0, "level": 0})
        entry["xp"] = new_xp
        save_levels(interaction.guild.id, data)
        await self._finish(interaction, f"✅ Set {self.member.mention}'s XP to {new_xp}.")


class LevelRewardLevelModal(_BaseModal):
    level = discord.ui.TextInput(label="Level number", placeholder="5", max_length=4)

    def __init__(self, panel_view):
        super().__init__(panel_view, "Add a Role Reward")

    async def on_submit(self, interaction: discord.Interaction):
        try:
            lvl = int(self.level.value)
            if lvl < 1:
                raise ValueError
        except ValueError:
            return await interaction.response.send_message("⚠️ Enter a whole number ≥ 1.", ephemeral=True)

        view = discord.ui.View(timeout=120)
        select = discord.ui.RoleSelect(placeholder=f"Pick the role for level {lvl}...", min_values=1, max_values=1)

        async def _cb(inner_interaction: discord.Interaction):
            role = select.values[0]
            cfg = get_config(inner_interaction.guild.id)
            cfg["levels"]["role_rewards"][str(lvl)] = role.id
            save_config(inner_interaction.guild.id, cfg)
            await inner_interaction.response.edit_message(
                content=f"✅ Level **{lvl}** now rewards {role.mention}.", view=None
            )
            await self.panel_view.push_update(inner_interaction.guild)

        select.callback = _cb
        view.add_item(select)
        await interaction.response.send_message(f"Now pick a role for level **{lvl}**:", view=view, ephemeral=True)


class KeyGenerateModal(_BaseModal):
    days = discord.ui.TextInput(label="Valid for how many days", placeholder="30", max_length=4)
    send_to_id = discord.ui.TextInput(
        label="DM to this Discord user ID (optional)", placeholder="e.g. 123456789012345678",
        required=False, max_length=20,
    )

    def __init__(self, panel_view):
        super().__init__(panel_view, "Generate a New Key")
        default_days = get_config(panel_view.guild.id)["keys"].get("default_days", 30)
        self.days.default = str(default_days)

    async def on_submit(self, interaction: discord.Interaction):
        try:
            d = max(1, int(self.days.value))
        except ValueError:
            return await interaction.response.send_message("⚠️ Enter a whole number of days.", ephemeral=True)
        rec = keystore.generate_key(days=d, created_by=str(interaction.user.id), guild_id=interaction.guild.id)

        note = ""
        target_id = self.send_to_id.value.strip()
        if target_id:
            try:
                member = interaction.guild.get_member(int(target_id)) or await interaction.guild.fetch_member(int(target_id))
                embed = discord.Embed(title="🔑 Your Minecraft Server Key", color=discord.Color.green())
                embed.description = (
                    f"**Key:** `{rec['key']}`\n\n"
                    "Redeem in-game with `/key <code> <your_discord_user_id>`, "
                    "then set a password with `/register <password> <confirm>`."
                )
                await member.send(embed=embed)
                note = f" 📨 Sent to {member.mention} via DM."
            except (ValueError, discord.NotFound):
                note = " ⚠️ That Discord ID wasn't found in this server — key was still generated."
            except discord.Forbidden:
                note = " ⚠️ Couldn't DM that user (DMs closed) — key was still generated."

        await self._finish(interaction, f"✅ Generated key `{rec['key']}`, expires in {d} day(s).{note}")


class KeyExtendModal(_BaseModal):
    key = discord.ui.TextInput(label="Key code", placeholder="32-character key", max_length=32)
    days = discord.ui.TextInput(label="Extend by how many days", placeholder="30", max_length=4)

    def __init__(self, panel_view):
        super().__init__(panel_view, "Extend a Key")

    async def on_submit(self, interaction: discord.Interaction):
        try:
            d = max(1, int(self.days.value))
        except ValueError:
            return await interaction.response.send_message("⚠️ Enter a whole number of days.", ephemeral=True)
        rec = keystore.extend_key(self.key.value.strip(), days=d)
        if not rec:
            return await interaction.response.send_message("⚠️ No key found with that code.", ephemeral=True)
        note = ""
        if keystore.is_valid(rec) and rec.get("discord_id") and not rec.get("role_granted"):
            granted, _ = await role_sync.grant_key_role(rec.get("guild_id"), rec.get("discord_id"))
            keystore.mark_role_granted(rec["key"], granted)
            if granted:
                note = " Role restored."
        await self._finish(interaction, f"✅ Extended `{rec['key'][:8]}...` by {d} day(s).{note}")


class KeyShortenModal(_BaseModal):
    key = discord.ui.TextInput(label="Key code", placeholder="32-character key", max_length=32)
    days = discord.ui.TextInput(label="Shorten by how many days", placeholder="7", max_length=4)

    def __init__(self, panel_view):
        super().__init__(panel_view, "Shorten a Key")

    async def on_submit(self, interaction: discord.Interaction):
        try:
            d = max(1, int(self.days.value))
        except ValueError:
            return await interaction.response.send_message("⚠️ Enter a whole number of days.", ephemeral=True)
        rec = keystore.shorten_key(self.key.value.strip(), days=d)
        if not rec:
            return await interaction.response.send_message("⚠️ No key found with that code.", ephemeral=True)
        note = ""
        if not keystore.is_valid(rec) and rec.get("role_granted"):
            ok, _ = await role_sync.revoke_key_role(rec.get("guild_id"), rec.get("discord_id"))
            if ok:
                keystore.mark_role_granted(rec["key"], False)
                note = " Role removed (now expired)."
        await self._finish(interaction, f"✂️ Shortened `{rec['key'][:8]}...` by {d} day(s).{note}")


class KeyRemoveModal(_BaseModal):
    key = discord.ui.TextInput(label="Key code", placeholder="32-character key", max_length=32)

    def __init__(self, panel_view):
        super().__init__(panel_view, "Remove a Key's Access")

    async def on_submit(self, interaction: discord.Interaction):
        rec = keystore.revoke_key(self.key.value.strip())
        if not rec:
            return await interaction.response.send_message("⚠️ No key found with that code.", ephemeral=True)
        note = ""
        if rec.get("role_granted"):
            ok, _ = await role_sync.revoke_key_role(rec.get("guild_id"), rec.get("discord_id"))
            if ok:
                keystore.mark_role_granted(rec["key"], False)
                note = " Role removed."
        await self._finish(interaction, f"🚫 Removed access for `{rec['key'][:8]}...`.{note} "
                                         "The player can't play again until given a new key.")


# ============================================================
#  MAIN PANEL VIEW
# ============================================================
class CategorySelect(discord.ui.Select):
    def __init__(self, current: str):
        options = [
            discord.SelectOption(label=label, value=key, default=(key == current))
            for key, label in CATEGORIES
        ]
        super().__init__(placeholder="📂 Choose a section to configure...", options=options, row=0)

    async def callback(self, interaction: discord.Interaction):
        view: PanelView = self.view
        view.category = self.values[0]
        view.page = 0
        await view.rerender(interaction)


class PanelView(discord.ui.View):
    def __init__(self, guild: discord.Guild, category: str = "overview", page: int = 0):
        super().__init__(timeout=900)
        self.guild = guild
        self.category = category
        self.page = page
        self.message: discord.Message = None
        self.build()

    async def interaction_check(self, interaction: discord.Interaction) -> bool:
        if not isinstance(interaction.user, discord.Member) or not is_bot_admin_member(interaction.user):
            await interaction.response.send_message(
                "⛔ You need to be the server owner, an Administrator, or hold a bot-admin role to use this panel.",
                ephemeral=True,
            )
            return False
        return True

    async def on_timeout(self):
        if self.message:
            for item in self.children:
                item.disabled = True
            try:
                await self.message.edit(view=self)
            except discord.HTTPException:
                pass

    async def rerender(self, interaction: discord.Interaction):
        """Used by selects/buttons that can safely respond via edit_message."""
        self.build()
        embed = build_embed(self.guild, self.category, self.page)
        await interaction.response.edit_message(embed=embed, view=self)

    async def push_update(self, guild: discord.Guild):
        """Used after a modal submit (a *different* interaction) to refresh the visible panel."""
        if not self.message:
            return
        self.build()
        embed = build_embed(guild, self.category, self.page)
        try:
            await self.message.edit(embed=embed, view=self)
        except discord.HTTPException:
            pass

    def build(self):
        self.clear_items()
        self.add_item(CategorySelect(self.category))
        cfg = get_config(self.guild.id)
        getattr(self, f"_build_{self.category}")(cfg)

    def _nav_button(self, label, target_page, row):
        btn = discord.ui.Button(label=label, style=discord.ButtonStyle.secondary, row=row)

        async def _cb(interaction: discord.Interaction):
            self.page = target_page
            await self.rerender(interaction)

        btn.callback = _cb
        return btn

    def _refresh_button(self, row):
        btn = discord.ui.Button(label="🔄 Refresh", style=discord.ButtonStyle.secondary, row=row)

        async def _cb(interaction: discord.Interaction):
            await self.rerender(interaction)

        btn.callback = _cb
        return btn

    # ---------------- OVERVIEW ----------------
    def _build_overview(self, cfg):
        jump_targets = [
            ("⚙️ General", "general", 1),
            ("🛡️ Automod", "automod", 1),
            ("🎭 Auto-Role", "autorole", 1),
            ("🚨 Anti-Raid", "antiraid", 2),
            ("📈 Points", "levels", 2),
            ("🔇 AFK Voice", "afkvc", 3),
            ("🎫 Tickets", "tickets", 3),
            ("🔒 Verification", "verification", 4),
            ("🎉 Welcome", "welcome", 4),
            ("🔑 Server Keys", "keys", 4),
        ]
        for label, key, row in jump_targets:
            btn = discord.ui.Button(label=label, style=discord.ButtonStyle.primary, row=row)

            def _make_cb(target_key):
                async def _cb(interaction: discord.Interaction):
                    self.category = target_key
                    self.page = 0
                    await self.rerender(interaction)
                return _cb

            btn.callback = _make_cb(key)
            self.add_item(btn)

    # ---------------- GENERAL ----------------
    def _build_general(self, cfg):
        log_select = discord.ui.ChannelSelect(
            placeholder="📋 Set mod-log channel...",
            channel_types=[discord.ChannelType.text],
            min_values=0, max_values=1,
            default_values=_channel_defaults(cfg["log_channel"]),
            row=1,
        )

        async def _log_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["log_channel"] = log_select.values[0].id if log_select.values else None
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        log_select.callback = _log_cb
        self.add_item(log_select)

        admin_select = discord.ui.RoleSelect(
            placeholder="🔑 Set bot-admin roles (can use /panel)...",
            min_values=0, max_values=25,
            default_values=_role_defaults(cfg["admin_roles"]),
            row=2,
        )

        async def _admin_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["admin_roles"] = [r.id for r in admin_select.values]
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        admin_select.callback = _admin_cb
        self.add_item(admin_select)

        mod_select = discord.ui.RoleSelect(
            placeholder="🛡️ Set moderator roles (kick/ban/mute/purge)...",
            min_values=0, max_values=25,
            default_values=_role_defaults(cfg["mod_roles"]),
            row=3,
        )

        async def _mod_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["mod_roles"] = [r.id for r in mod_select.values]
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        mod_select.callback = _mod_cb
        self.add_item(mod_select)

        self.add_item(self._refresh_button(row=4))

    # ---------------- AUTOMOD ----------------
    def _build_automod(self, cfg):
        am = cfg["automod"]
        if self.page == 0:
            toggle_btn = discord.ui.Button(
                label="🟢 Enabled" if am["enabled"] else "🔴 Disabled",
                style=discord.ButtonStyle.success if am["enabled"] else discord.ButtonStyle.danger,
                row=1,
            )

            async def _toggle_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["automod"]["enabled"] = not c["automod"]["enabled"]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            toggle_btn.callback = _toggle_cb
            self.add_item(toggle_btn)

            numbers_btn = discord.ui.Button(label="🔢 Warn/Mute Settings", style=discord.ButtonStyle.primary, row=1)

            async def _numbers_cb(interaction: discord.Interaction):
                await interaction.response.send_modal(AutomodNumbersModal(self, get_config(interaction.guild.id)["automod"]))

            numbers_btn.callback = _numbers_cb
            self.add_item(numbers_btn)

            reload_btn = discord.ui.Button(label="♻️ Reload bad.txt", style=discord.ButtonStyle.secondary, row=1)

            async def _reload_cb(interaction: discord.Interaction):
                cog = interaction.client.get_cog("AutoMod")
                if cog:
                    cog._mtime = None
                    cog.load_bad_words()
                    await interaction.response.send_message(f"✅ Reloaded — {len(cog.bad_words)} banned words loaded.", ephemeral=True)
                else:
                    await interaction.response.send_message("⚠️ Automod cog not loaded.", ephemeral=True)

            reload_btn.callback = _reload_cb
            self.add_item(reload_btn)

            self.add_item(self._nav_button("Bypass Roles ▸", 1, row=1))

            punishment_select = discord.ui.Select(
                placeholder="⚖️ Set punishment for banned words...",
                options=[discord.SelectOption(label=p, value=p, default=(p == am["punishment"])) for p in PUNISHMENTS],
                row=2,
            )

            async def _punishment_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["automod"]["punishment"] = punishment_select.values[0]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            punishment_select.callback = _punishment_cb
            self.add_item(punishment_select)

            escalate_select = discord.ui.Select(
                placeholder="⬆️ Set escalation punishment (after warn limit)...",
                options=[discord.SelectOption(label=p, value=p, default=(p == am["escalate_punishment"])) for p in ESCALATIONS],
                row=3,
            )

            async def _escalate_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["automod"]["escalate_punishment"] = escalate_select.values[0]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            escalate_select.callback = _escalate_cb
            self.add_item(escalate_select)

            log_select = discord.ui.ChannelSelect(
                placeholder="📋 Set automod log channel...",
                channel_types=[discord.ChannelType.text],
                min_values=0, max_values=1,
                default_values=_channel_defaults(am["log_channel"]),
                row=4,
            )

            async def _log_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["automod"]["log_channel"] = log_select.values[0].id if log_select.values else None
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            log_select.callback = _log_cb
            self.add_item(log_select)
        elif self.page == 1:
            self.add_item(self._nav_button("◂ Back", 0, row=1))
            self.add_item(self._refresh_button(row=1))

            dm_btn = discord.ui.Button(
                label="📩 DM on Warn: On" if am.get("dm_on_warn", True) else "📩 DM on Warn: Off",
                style=discord.ButtonStyle.success if am.get("dm_on_warn", True) else discord.ButtonStyle.secondary,
                row=1,
            )

            async def _dm_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["automod"]["dm_on_warn"] = not c["automod"].get("dm_on_warn", True)
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            dm_btn.callback = _dm_cb
            self.add_item(dm_btn)

            self.add_item(self._nav_button("Spam Control ▸", 2, row=1))

            bypass_select = discord.ui.RoleSelect(
                placeholder="🚫 Set roles exempt from the word filter...",
                min_values=0, max_values=25,
                default_values=_role_defaults(am["bypass_roles"]),
                row=2,
            )

            async def _bypass_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["automod"]["bypass_roles"] = [r.id for r in bypass_select.values]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            bypass_select.callback = _bypass_cb
            self.add_item(bypass_select)

            clearwarns_select = discord.ui.UserSelect(
                placeholder="🧹 Pick a member to clear automod warns for...",
                min_values=1, max_values=1,
                row=3,
            )

            async def _clearwarns_cb(interaction: discord.Interaction):
                member = clearwarns_select.values[0]
                c = get_config(interaction.guild.id)
                c["automod"]["warns"].pop(str(member.id), None)
                save_config(interaction.guild.id, c)
                await interaction.response.send_message(f"✅ Cleared automod warns for {member.mention}.", ephemeral=True)

            clearwarns_select.callback = _clearwarns_cb
            self.add_item(clearwarns_select)

        elif self.page == 2:  # spam control
            sp = am.get("spam", {})
            self.add_item(self._nav_button("◂ Back", 1, row=1))
            self.add_item(self._refresh_button(row=1))

            spam_toggle_btn = discord.ui.Button(
                label="🟢 Spam: On" if sp.get("enabled") else "🔴 Spam: Off",
                style=discord.ButtonStyle.success if sp.get("enabled") else discord.ButtonStyle.danger,
                row=1,
            )

            async def _spam_toggle_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["automod"]["spam"]["enabled"] = not c["automod"]["spam"]["enabled"]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            spam_toggle_btn.callback = _spam_toggle_cb
            self.add_item(spam_toggle_btn)

            spam_numbers_btn = discord.ui.Button(label="🔢 Spam Thresholds", style=discord.ButtonStyle.primary, row=1)

            async def _spam_numbers_cb(interaction: discord.Interaction):
                await interaction.response.send_modal(SpamNumbersModal(self, get_config(interaction.guild.id)["automod"]["spam"]))

            spam_numbers_btn.callback = _spam_numbers_cb
            self.add_item(spam_numbers_btn)

            self.add_item(self._nav_button("Invite/Purge ▸", 3, row=1))

            spam_punishment_select = discord.ui.Select(
                placeholder="⚖️ Set punishment for spam...",
                options=[discord.SelectOption(label=p, value=p, default=(p == sp.get("punishment"))) for p in PUNISHMENTS],
                row=2,
            )

            async def _spam_punishment_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["automod"]["spam"]["punishment"] = spam_punishment_select.values[0]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            spam_punishment_select.callback = _spam_punishment_cb
            self.add_item(spam_punishment_select)

        else:  # page == 3 -> invite filter + raid purge
            inv = am.get("invite_filter", {})
            self.add_item(self._nav_button("◂ Back", 2, row=1))
            self.add_item(self._refresh_button(row=1))

            inv_toggle_btn = discord.ui.Button(
                label="🟢 Invite Filter: On" if inv.get("enabled") else "🔴 Invite Filter: Off",
                style=discord.ButtonStyle.success if inv.get("enabled") else discord.ButtonStyle.danger,
                row=1,
            )

            async def _inv_toggle_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["automod"]["invite_filter"]["enabled"] = not c["automod"]["invite_filter"]["enabled"]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            inv_toggle_btn.callback = _inv_toggle_cb
            self.add_item(inv_toggle_btn)

            allow_own_btn = discord.ui.Button(
                label="✅ Allow own invite" if inv.get("allow_own_server") else "❌ Flag own invite too",
                style=discord.ButtonStyle.secondary,
                row=1,
            )

            async def _allow_own_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["automod"]["invite_filter"]["allow_own_server"] = not c["automod"]["invite_filter"]["allow_own_server"]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            allow_own_btn.callback = _allow_own_cb
            self.add_item(allow_own_btn)

            purge_btn = discord.ui.Button(label="🧹 Raid Purge Window", style=discord.ButtonStyle.primary, row=1)

            async def _purge_cb(interaction: discord.Interaction):
                await interaction.response.send_modal(RaidPurgeModal(self, get_config(interaction.guild.id)["automod"]))

            purge_btn.callback = _purge_cb
            self.add_item(purge_btn)

            inv_punishment_select = discord.ui.Select(
                placeholder="⚖️ Set punishment for invite links...",
                options=[discord.SelectOption(label=p, value=p, default=(p == inv.get("punishment"))) for p in PUNISHMENTS],
                row=2,
            )

            async def _inv_punishment_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["automod"]["invite_filter"]["punishment"] = inv_punishment_select.values[0]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            inv_punishment_select.callback = _inv_punishment_cb
            self.add_item(inv_punishment_select)

    # ---------------- AUTOROLE ----------------
    def _build_autorole(self, cfg):
        ar = cfg["autorole"]
        toggle_btn = discord.ui.Button(
            label="🟢 Enabled" if ar["enabled"] else "🔴 Disabled",
            style=discord.ButtonStyle.success if ar["enabled"] else discord.ButtonStyle.danger,
            row=1,
        )

        async def _toggle_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["autorole"]["enabled"] = not c["autorole"]["enabled"]
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        toggle_btn.callback = _toggle_cb
        self.add_item(toggle_btn)
        self.add_item(self._refresh_button(row=1))

        role_select = discord.ui.RoleSelect(
            placeholder="🎭 Set roles given automatically on join...",
            min_values=0, max_values=25,
            default_values=_role_defaults(ar["role_ids"]),
            row=2,
        )

        async def _role_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["autorole"]["role_ids"] = [r.id for r in role_select.values]
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        role_select.callback = _role_cb
        self.add_item(role_select)

    # ---------------- ANTIRAID ----------------
    def _build_antiraid(self, cfg):
        ar2 = cfg["antiraid"]
        if self.page == 0:
            toggle_btn = discord.ui.Button(
                label="🟢 Raid Guard On" if ar2["enabled"] else "🔴 Raid Guard Off",
                style=discord.ButtonStyle.success if ar2["enabled"] else discord.ButtonStyle.danger,
                row=1,
            )

            async def _toggle_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["antiraid"]["enabled"] = not c["antiraid"]["enabled"]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            toggle_btn.callback = _toggle_cb
            self.add_item(toggle_btn)

            thresh_btn = discord.ui.Button(label="🔢 Thresholds", style=discord.ButtonStyle.primary, row=1)

            async def _thresh_cb(interaction: discord.Interaction):
                await interaction.response.send_modal(AntiraidThresholdModal(self, get_config(interaction.guild.id)["antiraid"]))

            thresh_btn.callback = _thresh_cb
            self.add_item(thresh_btn)

            self.add_item(self._nav_button("Anti-Nuke ▸", 1, row=1))
            self.add_item(self._refresh_button(row=1))

            action_select = discord.ui.Select(
                placeholder="🚨 Set raid action...",
                options=[discord.SelectOption(label=a, value=a, default=(a == ar2["action"])) for a in RAID_ACTIONS],
                row=2,
            )

            async def _action_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["antiraid"]["action"] = action_select.values[0]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            action_select.callback = _action_cb
            self.add_item(action_select)

            log_select = discord.ui.ChannelSelect(
                placeholder="📋 Set anti-raid log channel...",
                channel_types=[discord.ChannelType.text],
                min_values=0, max_values=1,
                default_values=_channel_defaults(ar2["log_channel"]),
                row=3,
            )

            async def _log_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["antiraid"]["log_channel"] = log_select.values[0].id if log_select.values else None
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            log_select.callback = _log_cb
            self.add_item(log_select)
        else:
            toggle_btn = discord.ui.Button(
                label="🟢 Anti-Nuke On" if ar2["antinuke_enabled"] else "🔴 Anti-Nuke Off",
                style=discord.ButtonStyle.success if ar2["antinuke_enabled"] else discord.ButtonStyle.danger,
                row=1,
            )

            async def _toggle_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["antiraid"]["antinuke_enabled"] = not c["antiraid"]["antinuke_enabled"]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            toggle_btn.callback = _toggle_cb
            self.add_item(toggle_btn)

            nuke_btn = discord.ui.Button(label="🔢 Thresholds", style=discord.ButtonStyle.primary, row=1)

            async def _nuke_cb(interaction: discord.Interaction):
                await interaction.response.send_modal(AntinukeModal(self, get_config(interaction.guild.id)["antiraid"]))

            nuke_btn.callback = _nuke_cb
            self.add_item(nuke_btn)

            lockdown_btn = discord.ui.Button(label="🔒 Toggle Lockdown Now", style=discord.ButtonStyle.danger, row=1)

            async def _lockdown_cb(interaction: discord.Interaction):
                guild = interaction.guild
                new_level = (
                    discord.VerificationLevel.medium
                    if guild.verification_level == discord.VerificationLevel.high
                    else discord.VerificationLevel.high
                )
                try:
                    await guild.edit(verification_level=new_level)
                except discord.Forbidden:
                    return await interaction.response.send_message("⚠️ I don't have permission to edit server settings.", ephemeral=True)
                await interaction.response.send_message(
                    f"✅ Verification level is now `{new_level}`.", ephemeral=True
                )

            lockdown_btn.callback = _lockdown_cb
            self.add_item(lockdown_btn)

            self.add_item(self._nav_button("◂ Back", 0, row=1))

            punishment_select = discord.ui.Select(
                placeholder="⚖️ Set anti-nuke punishment...",
                options=[discord.SelectOption(label=p, value=p, default=(p == ar2["antinuke_punishment"])) for p in NUKE_PUNISHMENTS],
                row=2,
            )

            async def _punishment_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["antiraid"]["antinuke_punishment"] = punishment_select.values[0]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            punishment_select.callback = _punishment_cb
            self.add_item(punishment_select)

    # ---------------- LEVELS ----------------
    def _build_levels(self, cfg):
        lv = cfg["levels"]
        if self.page == 0:
            toggle_btn = discord.ui.Button(
                label="🟢 Enabled" if lv["enabled"] else "🔴 Disabled",
                style=discord.ButtonStyle.success if lv["enabled"] else discord.ButtonStyle.danger,
                row=1,
            )

            async def _toggle_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["levels"]["enabled"] = not c["levels"]["enabled"]
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            toggle_btn.callback = _toggle_cb
            self.add_item(toggle_btn)

            range_btn = discord.ui.Button(label="🔢 Point Range", style=discord.ButtonStyle.primary, row=1)

            async def _range_cb(interaction: discord.Interaction):
                await interaction.response.send_modal(PointRangeModal(self, get_config(interaction.guild.id)["levels"]))

            range_btn.callback = _range_cb
            self.add_item(range_btn)

            interval_btn = discord.ui.Button(label="⏱️ Auto-Drop Interval", style=discord.ButtonStyle.primary, row=1)

            async def _interval_cb(interaction: discord.Interaction):
                await interaction.response.send_modal(DropIntervalModal(self, get_config(interaction.guild.id)["levels"]))

            interval_btn.callback = _interval_cb
            self.add_item(interval_btn)

            drop_now_btn = discord.ui.Button(label="🎁 Send Drop Now", style=discord.ButtonStyle.success, row=1)

            async def _drop_now_cb(interaction: discord.Interaction):
                cog = interaction.client.get_cog("Levels")
                c = get_config(interaction.guild.id)
                if not c["levels"]["drop_channel"]:
                    return await interaction.response.send_message("⚠️ Set a drop channel first.", ephemeral=True)
                if not cog:
                    return await interaction.response.send_message("⚠️ Levels cog not loaded.", ephemeral=True)
                msg = await cog.send_drop(interaction.guild)
                if msg:
                    await interaction.response.send_message(f"✅ Drop sent in {msg.channel.mention}!", ephemeral=True)
                else:
                    await interaction.response.send_message("⚠️ Couldn't send the drop — check my permissions in that channel.", ephemeral=True)

            drop_now_btn.callback = _drop_now_cb
            self.add_item(drop_now_btn)

            self.add_item(self._nav_button("Role Rewards ▸", 1, row=1))

            drop_channel_select = discord.ui.ChannelSelect(
                placeholder="🎁 Set the points-drop channel...",
                channel_types=[discord.ChannelType.text],
                min_values=0, max_values=1,
                default_values=_channel_defaults(lv["drop_channel"]),
                row=2,
            )

            async def _drop_channel_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["levels"]["drop_channel"] = drop_channel_select.values[0].id if drop_channel_select.values else None
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            drop_channel_select.callback = _drop_channel_cb
            self.add_item(drop_channel_select)

            levelup_select = discord.ui.ChannelSelect(
                placeholder="🎉 Set the level-up announcement channel...",
                channel_types=[discord.ChannelType.text],
                min_values=0, max_values=1,
                default_values=_channel_defaults(lv["level_up_channel"]),
                row=3,
            )

            async def _levelup_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["levels"]["level_up_channel"] = levelup_select.values[0].id if levelup_select.values else None
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            levelup_select.callback = _levelup_cb
            self.add_item(levelup_select)
        else:
            self.add_item(self._nav_button("◂ Back", 0, row=1))

            add_btn = discord.ui.Button(label="🎁 Add Role Reward", style=discord.ButtonStyle.success, row=1)

            async def _add_cb(interaction: discord.Interaction):
                await interaction.response.send_modal(LevelRewardLevelModal(self))

            add_btn.callback = _add_cb
            self.add_item(add_btn)

            clear_btn = discord.ui.Button(label="🗑️ Clear All Rewards", style=discord.ButtonStyle.danger, row=1)

            async def _clear_cb(interaction: discord.Interaction):
                c = get_config(interaction.guild.id)
                c["levels"]["role_rewards"] = {}
                save_config(interaction.guild.id, c)
                await self.rerender(interaction)

            clear_btn.callback = _clear_cb
            self.add_item(clear_btn)

            self.add_item(self._refresh_button(row=1))

            setxp_select = discord.ui.UserSelect(
                placeholder="🔧 Pick a member to manually set XP for...",
                min_values=1, max_values=1,
                row=2,
            )

            async def _setxp_cb(interaction: discord.Interaction):
                member = setxp_select.values[0]
                await interaction.response.send_modal(SetXPModal(self, member))

            setxp_select.callback = _setxp_cb
            self.add_item(setxp_select)

    # ---------------- AFK VC ----------------
    def _build_afkvc(self, cfg):
        afk = cfg["afk_vc"]
        toggle_btn = discord.ui.Button(
            label="🟢 Enabled" if afk["enabled"] else "🔴 Disabled",
            style=discord.ButtonStyle.success if afk["enabled"] else discord.ButtonStyle.danger,
            row=1,
        )

        async def _toggle_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["afk_vc"]["enabled"] = not c["afk_vc"]["enabled"]
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        toggle_btn.callback = _toggle_cb
        self.add_item(toggle_btn)

        timeout_btn = discord.ui.Button(label="⏱️ Set Timeout", style=discord.ButtonStyle.primary, row=1)

        async def _timeout_cb(interaction: discord.Interaction):
            await interaction.response.send_modal(AfkTimeoutModal(self, get_config(interaction.guild.id)["afk_vc"]))

        timeout_btn.callback = _timeout_cb
        self.add_item(timeout_btn)

        self.add_item(self._refresh_button(row=1))

        channel_select = discord.ui.ChannelSelect(
            placeholder="🔇 Set the AFK voice channel...",
            channel_types=[discord.ChannelType.voice],
            min_values=0, max_values=1,
            default_values=_channel_defaults(afk["afk_channel_id"]),
            row=2,
        )

        async def _channel_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["afk_vc"]["afk_channel_id"] = channel_select.values[0].id if channel_select.values else None
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        channel_select.callback = _channel_cb
        self.add_item(channel_select)

        ignore_select = discord.ui.RoleSelect(
            placeholder="🙈 Set roles exempt from AFK-move...",
            min_values=0, max_values=25,
            default_values=_role_defaults(afk["ignore_roles"]),
            row=3,
        )

        async def _ignore_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["afk_vc"]["ignore_roles"] = [r.id for r in ignore_select.values]
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        ignore_select.callback = _ignore_cb
        self.add_item(ignore_select)

    # ---------------- TICKETS ----------------
    def _build_tickets(self, cfg):
        tk = cfg["tickets"]

        toggle_btn = discord.ui.Button(
            label="🟢 Enabled" if tk["enabled"] else "🔴 Disabled",
            style=discord.ButtonStyle.success if tk["enabled"] else discord.ButtonStyle.danger,
            row=1,
        )

        async def _toggle_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["tickets"]["enabled"] = not c["tickets"]["enabled"]
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        toggle_btn.callback = _toggle_cb
        self.add_item(toggle_btn)

        transcript_btn = discord.ui.Button(
            label="🟢 Transcripts: On" if tk.get("transcripts_enabled", True) else "🔴 Transcripts: Off",
            style=discord.ButtonStyle.success if tk.get("transcripts_enabled", True) else discord.ButtonStyle.secondary,
            row=1,
        )

        async def _transcript_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["tickets"]["transcripts_enabled"] = not c["tickets"].get("transcripts_enabled", True)
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        transcript_btn.callback = _transcript_cb
        self.add_item(transcript_btn)

        welcome_btn = discord.ui.Button(label="✏️ Welcome Message", style=discord.ButtonStyle.primary, row=1)

        async def _welcome_cb(interaction: discord.Interaction):
            await interaction.response.send_modal(TicketWelcomeModal(self, get_config(interaction.guild.id)["tickets"]))

        welcome_btn.callback = _welcome_cb
        self.add_item(welcome_btn)

        self.add_item(self._refresh_button(row=1))

        support_select = discord.ui.RoleSelect(
            placeholder="🛠️ Set support roles (see + manage tickets)...",
            min_values=0, max_values=10,
            default_values=_role_defaults(tk["support_roles"]),
            row=2,
        )

        async def _support_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["tickets"]["support_roles"] = [r.id for r in support_select.values]
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        support_select.callback = _support_cb
        self.add_item(support_select)

        log_select = discord.ui.ChannelSelect(
            placeholder="📜 Set the transcript/log channel...",
            channel_types=[discord.ChannelType.text],
            min_values=0, max_values=1,
            default_values=_channel_defaults(tk["log_channel"]),
            row=3,
        )

        async def _log_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["tickets"]["log_channel"] = log_select.values[0].id if log_select.values else None
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        log_select.callback = _log_cb
        self.add_item(log_select)

        category_select = discord.ui.ChannelSelect(
            placeholder="📁 Set the category new tickets are created under...",
            channel_types=[discord.ChannelType.category],
            min_values=0, max_values=1,
            default_values=_channel_defaults(tk["category_id"]),
            row=4,
        )

        async def _category_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["tickets"]["category_id"] = category_select.values[0].id if category_select.values else None
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        category_select.callback = _category_cb
        self.add_item(category_select)

    # ---------------- VERIFICATION ----------------
    def _build_verification(self, cfg):
        v = cfg["verification"]

        toggle_btn = discord.ui.Button(
            label="🟢 Enabled" if v["enabled"] else "🔴 Disabled",
            style=discord.ButtonStyle.success if v["enabled"] else discord.ButtonStyle.danger,
            row=1,
        )

        async def _toggle_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["verification"]["enabled"] = not c["verification"]["enabled"]
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        toggle_btn.callback = _toggle_cb
        self.add_item(toggle_btn)

        captcha_btn = discord.ui.Button(label="🔢 Captcha Length", style=discord.ButtonStyle.primary, row=1)

        async def _captcha_cb(interaction: discord.Interaction):
            await interaction.response.send_modal(CaptchaLengthModal(self, get_config(interaction.guild.id)["verification"]))

        captcha_btn.callback = _captcha_cb
        self.add_item(captcha_btn)

        self.add_item(self._refresh_button(row=1))

        verify_channel_select = discord.ui.ChannelSelect(
            placeholder="🔒 Set the verify channel (post the panel with /verify setup)...",
            channel_types=[discord.ChannelType.text],
            min_values=0, max_values=1,
            default_values=_channel_defaults(v["verify_channel"]),
            row=2,
        )

        async def _vc_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["verification"]["verify_channel"] = verify_channel_select.values[0].id if verify_channel_select.values else None
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        verify_channel_select.callback = _vc_cb
        self.add_item(verify_channel_select)

        unverified_select = discord.ui.RoleSelect(
            placeholder="🙈 Set the Unverified role (hidden from channels)...",
            min_values=0, max_values=1,
            default_values=_role_defaults([v["unverified_role"]] if v["unverified_role"] else []),
            row=3,
        )

        async def _unverified_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["verification"]["unverified_role"] = unverified_select.values[0].id if unverified_select.values else None
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        unverified_select.callback = _unverified_cb
        self.add_item(unverified_select)

        verified_select = discord.ui.RoleSelect(
            placeholder="✅ Set the Verified role (optional — granted on pass)...",
            min_values=0, max_values=1,
            default_values=_role_defaults([v["verified_role"]] if v["verified_role"] else []),
            row=4,
        )

        async def _verified_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["verification"]["verified_role"] = verified_select.values[0].id if verified_select.values else None
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        verified_select.callback = _verified_cb
        self.add_item(verified_select)

    # ---------------- WELCOME ----------------
    def _build_welcome(self, cfg):
        w = cfg["welcome"]

        toggle_btn = discord.ui.Button(
            label="🟢 Enabled" if w["enabled"] else "🔴 Disabled",
            style=discord.ButtonStyle.success if w["enabled"] else discord.ButtonStyle.danger,
            row=1,
        )

        async def _toggle_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["welcome"]["enabled"] = not c["welcome"]["enabled"]
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        toggle_btn.callback = _toggle_cb
        self.add_item(toggle_btn)

        image_btn = discord.ui.Button(
            label="🖼️ Image: On" if w.get("use_image") else "🖼️ Image: Off",
            style=discord.ButtonStyle.success if w.get("use_image") else discord.ButtonStyle.secondary,
            row=1,
        )

        async def _image_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["welcome"]["use_image"] = not c["welcome"]["use_image"]
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        image_btn.callback = _image_cb
        self.add_item(image_btn)

        dm_btn = discord.ui.Button(
            label="📩 DM: On" if w.get("dm_enabled") else "📩 DM: Off",
            style=discord.ButtonStyle.success if w.get("dm_enabled") else discord.ButtonStyle.secondary,
            row=1,
        )

        async def _dm_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["welcome"]["dm_enabled"] = not c["welcome"]["dm_enabled"]
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        dm_btn.callback = _dm_cb
        self.add_item(dm_btn)

        msg_btn = discord.ui.Button(label="✏️ Edit Messages", style=discord.ButtonStyle.primary, row=1)

        async def _msg_cb(interaction: discord.Interaction):
            await interaction.response.send_modal(WelcomeMessagesModal(self, get_config(interaction.guild.id)["welcome"]))

        msg_btn.callback = _msg_cb
        self.add_item(msg_btn)

        self.add_item(self._refresh_button(row=1))

        channel_select = discord.ui.ChannelSelect(
            placeholder="🎉 Set the welcome channel...",
            channel_types=[discord.ChannelType.text],
            min_values=0, max_values=1,
            default_values=_channel_defaults(w["channel_id"]),
            row=2,
        )

        async def _channel_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["welcome"]["channel_id"] = channel_select.values[0].id if channel_select.values else None
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        channel_select.callback = _channel_cb
        self.add_item(channel_select)

    # ---------------- KEYS ----------------
    def _build_keys(self, cfg):
        gen_btn = discord.ui.Button(label="➕ Generate", style=discord.ButtonStyle.success, row=1)

        async def _gen_cb(interaction: discord.Interaction):
            await interaction.response.send_modal(KeyGenerateModal(self))

        gen_btn.callback = _gen_cb
        self.add_item(gen_btn)

        extend_btn = discord.ui.Button(label="⏫ Extend", style=discord.ButtonStyle.primary, row=1)

        async def _extend_cb(interaction: discord.Interaction):
            await interaction.response.send_modal(KeyExtendModal(self))

        extend_btn.callback = _extend_cb
        self.add_item(extend_btn)

        shorten_btn = discord.ui.Button(label="✂️ Shorten", style=discord.ButtonStyle.secondary, row=1)

        async def _shorten_cb(interaction: discord.Interaction):
            await interaction.response.send_modal(KeyShortenModal(self))

        shorten_btn.callback = _shorten_cb
        self.add_item(shorten_btn)

        remove_btn = discord.ui.Button(label="🗑️ Remove", style=discord.ButtonStyle.danger, row=1)

        async def _remove_cb(interaction: discord.Interaction):
            await interaction.response.send_modal(KeyRemoveModal(self))

        remove_btn.callback = _remove_cb
        self.add_item(remove_btn)

        self.add_item(self._refresh_button(row=1))

        role_select = discord.ui.RoleSelect(
            placeholder="🔑 Set the role granted while a key is valid...",
            min_values=0, max_values=1,
            default_values=_role_defaults([cfg["keys"]["key_role"]] if cfg["keys"]["key_role"] else []),
            row=2,
        )

        async def _role_cb(interaction: discord.Interaction):
            c = get_config(interaction.guild.id)
            c["keys"]["key_role"] = role_select.values[0].id if role_select.values else None
            save_config(interaction.guild.id, c)
            await self.rerender(interaction)

        role_select.callback = _role_cb
        self.add_item(role_select)


class Panel(commands.Cog):
    def __init__(self, bot: commands.Bot):
        self.bot = bot

    @app_commands.command(name="panel", description="Open the live configuration panel for this server")
    @app_commands.default_permissions(administrator=True)
    async def panel(self, interaction: discord.Interaction):
        if not isinstance(interaction.user, discord.Member) or not is_bot_admin_member(interaction.user):
            return await interaction.response.send_message(
                "⛔ You need to be the server owner, an Administrator, or hold a bot-admin role to open the panel.",
                ephemeral=True,
            )
        view = PanelView(interaction.guild)
        embed = build_embed(interaction.guild, view.category, view.page)
        await interaction.response.send_message(embed=embed, view=view)
        view.message = await interaction.original_response()


async def setup(bot: commands.Bot):
    await bot.add_cog(Panel(bot))
