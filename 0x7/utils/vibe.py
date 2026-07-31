"""
Flavor text for the bot's themed 'signal / terminal' aesthetic.
Used by automod DMs and the bot's rotating presence.
"""
import random
import discord

# ---------- rotating bot presence ----------
# (activity_type, text) — mix of watching/playing/listening for variety
PRESENCE_ROTATION = [
    (discord.ActivityType.watching, "for signal.exe"),
    (discord.ActivityType.playing, "404: status not found"),
    (discord.ActivityType.listening, "the static"),
    (discord.ActivityType.watching, "the archive"),
    (discord.ActivityType.playing, "ACCESS GRANTED"),
    (discord.ActivityType.listening, "an incoming transmission"),
    (discord.ActivityType.watching, "over the server // /help"),
    (discord.ActivityType.playing, "connection stable"),
    (discord.ActivityType.watching, "decrypting..."),
    (discord.ActivityType.playing, "signal received"),
]


def random_presence():
    return random.choice(PRESENCE_ROTATION)


# ---------- DM flavor ----------
BADWORD_DM_LINES = [
    "> ANOMALY FLAGGED in **{guild}** — your last transmission got scrubbed. warning `{count}/{limit}`.",
    "> signal interference detected in **{guild}**. message purged. `{count}/{limit}` strikes logged.",
    "> intercepted + deleted in **{guild}**. keep it clean or the archive locks you out. `{count}/{limit}`.",
]

SPAM_DM_LINES = [
    "> flood detected in **{guild}**. channel's rate-limiting you. `{count}/{limit}`.",
    "> too many pings on the line in **{guild}** — signal throttled. `{count}/{limit}`.",
    "> transmission loop detected in **{guild}**. slow down. `{count}/{limit}`.",
]

ESCALATION_DM_LINES = [
    "> limit breached in **{guild}**. containment protocol engaged: `{action}`.",
    "> too many flags in **{guild}**. system response: `{action}`.",
    "> archive lockout triggered in **{guild}** — `{action}` applied.",
]


INVITE_DM_LINES = [
    "> outbound link flagged in **{guild}** — invites to other channels aren't allowed here. `{count}/{limit}`.",
    "> foreign signal blocked in **{guild}**. no redirect links. `{count}/{limit}`.",
    "> transmission rerouted in **{guild}** — invite link scrubbed. `{count}/{limit}`.",
]


def invite_dm(guild_name, count, limit):
    return random.choice(INVITE_DM_LINES).format(guild=guild_name, count=count, limit=limit)


BADWORD_DELETED_LINES = [
    "> transmission scrubbed in **{guild}** — your message got flagged and removed.",
    "> intercepted in **{guild}**. message deleted, no strike logged this time.",
    "> anomaly caught in **{guild}** — message wiped from the record.",
]

INVITE_DELETED_LINES = [
    "> outbound link removed in **{guild}** — invite links aren't allowed here.",
    "> foreign signal blocked in **{guild}**. link deleted.",
]


def badword_deleted_dm(guild_name):
    return random.choice(BADWORD_DELETED_LINES).format(guild=guild_name)


def invite_deleted_dm(guild_name):
    return random.choice(INVITE_DELETED_LINES).format(guild=guild_name)


def manual_warn_dm(guild_name, reason, count, limit):
    return f"> flagged by a moderator in **{guild_name}**: {reason} — `{count}/{limit}`."


def badword_dm(guild_name, count, limit):
    return random.choice(BADWORD_DM_LINES).format(guild=guild_name, count=count, limit=limit)


def spam_dm(guild_name, count, limit):
    return random.choice(SPAM_DM_LINES).format(guild=guild_name, count=count, limit=limit)


def escalation_dm(guild_name, action):
    return random.choice(ESCALATION_DM_LINES).format(guild=guild_name, action=action)


async def try_dm(member: discord.Member, text: str):
    """Send a themed DM, silently give up if DMs are closed/blocked."""
    try:
        await member.send(text)
    except discord.HTTPException:
        pass
