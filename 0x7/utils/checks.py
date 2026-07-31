import discord
from discord import app_commands
from utils.storage import get_config


def _has_any_role(member: discord.Member, role_ids):
    if not role_ids:
        return False
    member_role_ids = {r.id for r in member.roles}
    return any(rid in member_role_ids for rid in role_ids)


def is_bot_admin_member(member: discord.Member) -> bool:
    """Server owner or Administrator perm can always configure the bot.
    Additionally, anyone holding a role in `admin_roles` (set by the owner) can configure it."""
    if member.guild.owner_id == member.id:
        return True
    if member.guild_permissions.administrator:
        return True
    config = get_config(member.guild.id)
    return _has_any_role(member, config["admin_roles"])


def is_mod_member(member: discord.Member) -> bool:
    """Administrator, or anyone with a configured mod role, or anyone with a bot-admin role."""
    if is_bot_admin_member(member):
        return True
    if member.guild_permissions.kick_members or member.guild_permissions.ban_members:
        return True
    config = get_config(member.guild.id)
    return _has_any_role(member, config["mod_roles"])


def is_bot_admin():
    async def predicate(interaction: discord.Interaction) -> bool:
        if not isinstance(interaction.user, discord.Member):
            return False
        ok = is_bot_admin_member(interaction.user)
        if not ok:
            raise app_commands.CheckFailure(
                "You need to be the server owner, an Administrator, or hold a configured bot-admin role to do this."
            )
        return ok
    return app_commands.check(predicate)


def is_mod():
    async def predicate(interaction: discord.Interaction) -> bool:
        if not isinstance(interaction.user, discord.Member):
            return False
        ok = is_mod_member(interaction.user)
        if not ok:
            raise app_commands.CheckFailure(
                "You need a configured moderator role (or Kick/Ban/Admin permissions) to do this."
            )
        return ok
    return app_commands.check(predicate)
