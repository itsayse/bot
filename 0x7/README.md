# All-in-one Discord Mod/Community Bot

Auto-role, automod (bad-word filter from `bad.txt`), anti-raid/anti-nuke, a
drop-based points/leveling system, `/pfp` lookup, and an AFK-voice mover —
almost everything is configured **live from Discord through one interactive
panel**, no slash-command memorizing and no code editing required after setup.

## ⚠️ Before you do anything: rotate your bot token

The `.env` file that was in this project had a **real bot token** committed
to it. Treat that token as compromised:

1. Go to https://discord.com/developers/applications → your app → **Bot**.
2. Click **Reset Token** and copy the new one.
3. Put the new token in your own local `.env` (copy `.env.example` → `.env`
   first). Never commit `.env` to git or share it with anyone/anything.

The `.env` shipped in this package now only contains a placeholder.

## 1. Create the bot application

1. Go to https://discord.com/developers/applications → **New Application**.
2. Go to **Bot** → **Reset Token** → copy the token.
3. Under **Privileged Gateway Intents**, turn ON:
   - Server Members Intent
   - Message Content Intent
4. Go to **OAuth2 → URL Generator**:
   - Scopes: `bot`, `applications.commands`
   - Bot permissions: Administrator is simplest, or at minimum: Manage Roles,
     Kick Members, Ban Members, Manage Channels, Manage Messages, Moderate
     Members, Move Members, View Channels, Send Messages, Embed Links,
     Read Message History.
5. Open the generated URL and invite the bot to your server.
6. **Important:** in Server Settings → Roles, drag the bot's role **above**
   any role you want it to auto-assign or manage (Discord role hierarchy
   applies to bots too).

## 2. Run the bot

```bash
cd discord-bot
python -m venv venv
source venv/bin/activate      # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env           # then paste your new bot token into .env
python bot.py
```

Slash commands sync automatically on startup (can take up to ~1 hour to
appear globally the very first time; per-server it's usually instant).

## 3. Configuring the bot — it's all in `/panel` now

Run **`/panel`** in any channel. It opens a live, interactive control panel:

- A dropdown switches between sections: **General & Roles, Automod,
  Auto-Role, Anti-Raid/Anti-Nuke, Points & Levels, AFK Voice**.
- Channels and roles are picked with native Discord pickers — no need to
  type IDs or mentions.
- Toggles are buttons that flip on click.
- A couple of settings that need typed numbers (like mute duration or point
  ranges) open a small popup form.
- Sections with more settings than fit on one screen have a **▸ / ◂**
  button to flip to a second page.

Only the **server owner**, **Administrators**, or members holding a
**bot-admin role** (set from the panel's General section) can use `/panel`.

## 4. Your bad-word list

Edit `bad.txt` in this same folder — one word/phrase per line, lines
starting with `#` are ignored. The bot auto-reloads it every 30 seconds, or
you can force it instantly with the "♻️ Reload bad.txt" button in the panel.
Matching is whole-word and case-insensitive.

## 5. How the points/leveling system works

This isn't a passive "chat and gain XP" system — it's event-based:

1. In the panel's **Points & Levels** section, set a **drop channel**.
2. The bot posts a "💰 Points Drop!" message there — either automatically
   every N minutes (set an interval in the panel) or on demand with the
   **"🎁 Send Drop Now"** button.
3. Whoever **replies directly to that message** first (using Discord's
   native "Reply" feature) claims a spot. The first **3** repliers each win
   a random number of points (default range **10-60**, configurable).
4. Points fill an XP bar that levels members up. When someone levels up,
   an announcement is posted — with their **profile picture** — in the
   channel you set as the **level-up channel**.
5. Optionally, set **role rewards** (e.g. "level 5 → @Level5") so roles are
   granted automatically on level-up.

Members can check progress with `/rank` and `/leaderboard`.

## 6. Feature overview

| Feature | Where to configure |
|---|---|
| Mod-log channel, bot-admin & mod roles | `/panel` → General & Roles |
| Automod (bad-word filter, punishments, escalation) | `/panel` → Automod |
| Auto-role on join | `/panel` → Auto-Role |
| Anti-raid & anti-nuke | `/panel` → Anti-Raid / Anti-Nuke |
| Points drops, level-up channel, role rewards | `/panel` → Points & Levels |
| AFK voice move | `/panel` → AFK Voice |
| Member commands | `/rank`, `/leaderboard`, `/pfp`, `/help` |
| Moderation | `/kick`, `/ban`, `/unban`, `/timeout`, `/untimeout`, `/warn`, `/purge` |

## Notes on behavior

- **Anti-raid**: tracks joins-per-interval; a burst above your threshold, or
  an account younger than your configured minimum age, triggers your chosen
  action (kick/ban/lockdown).
- **Anti-nuke**: watches the audit log for rapid channel deletions, role
  deletions, or bans by the same (non-owner) member; past your threshold it
  strips their roles or bans them, per your config.
- **AFK voice move**: if a member sits in a voice channel muted or deafened
  (self or server) continuously past the configured timeout, they're moved
  to the AFK channel. Timer resets the moment they unmute/leave.
- All settings are stored in `data/guilds/<server_id>.json` and
  `data/levels/<server_id>.json` — back these up if you move hosts.

## Removed features

Ticket and giveaway commands have been removed from this build entirely.

## Hosting tips

- Keep the bot running 24/7 with a process manager (`pm2`, `systemd`,
  Docker, or a small VPS) — free "always-on" website hosts generally don't
  work well for Discord bots since they need a persistent connection.
- Never commit your `.env` file or share your bot token.
