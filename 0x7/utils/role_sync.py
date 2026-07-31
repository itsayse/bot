"""
Grants/revokes the configured "key role" for a guild, on behalf of the
Minecraft key API and the periodic expiry checker. Needs a live reference
to the running bot, set once from bot.py at startup.
"""
import logging

from utils.storage import get_config

log = logging.getLogger("role_sync")

_bot = None


def set_bot(bot):
    global _bot
    _bot = bot


async def _get_guild_member(guild_id, discord_id):
    if _bot is None or not guild_id or not discord_id:
        return None, None
    try:
        guild = _bot.get_guild(int(guild_id))
    except (TypeError, ValueError):
        return None, None
    if guild is None:
        return None, None
    member = guild.get_member(int(discord_id))
    if member is None:
        try:
            member = await guild.fetch_member(int(discord_id))
        except Exception:
            return guild, None
    return guild, member


async def grant_key_role(guild_id, discord_id):
    if not guild_id or not discord_id:
        return False, "missing_ids"
    cfg = get_config(int(guild_id))
    role_id = cfg.get("keys", {}).get("key_role")
    if not role_id:
        return False, "no_role_configured"
    guild, member = await _get_guild_member(guild_id, discord_id)
    if guild is None:
        return False, "guild_not_found"
    if member is None:
        return False, "member_not_found"
    role = guild.get_role(int(role_id))
    if role is None:
        return False, "role_not_found"
    try:
        await member.add_roles(role, reason="Minecraft server key redeemed")
        return True, "ok"
    except Exception as e:
        log.warning("Failed to add key role to %s in %s: %s", discord_id, guild_id, e)
        return False, "discord_error"


async def revoke_key_role(guild_id, discord_id):
    if not guild_id or not discord_id:
        return False, "missing_ids"
    cfg = get_config(int(guild_id))
    role_id = cfg.get("keys", {}).get("key_role")
    guild, member = await _get_guild_member(guild_id, discord_id)
    if guild is None:
        return False, "guild_not_found"
    if member is None:
        return False, "member_not_found"
    if not role_id:
        return False, "no_role_configured"
    role = guild.get_role(int(role_id))
    if role is None:
        return False, "role_not_found"
    try:
        await member.remove_roles(role, reason="Minecraft server key expired, revoked, or unlinked")
        return True, "ok"
    except Exception as e:
        log.warning("Failed to remove key role from %s in %s: %s", discord_id, guild_id, e)
        return False, "discord_error"
