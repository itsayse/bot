"""
Small HTTP API so the Minecraft plugin can validate/redeem keys and manage
the Discord-account link that drives the key-role. Runs alongside the bot
via aiohttp (already a discord.py dependency), on the bot's own event loop
so it can grant/revoke Discord roles in real time.

Configure with these .env vars (see .env.example):
    MC_API_HOST   default 0.0.0.0
    MC_API_PORT   default 8787
    MC_API_SECRET required — the plugin sends this back as X-API-Key
"""
import logging
import os
import re

from aiohttp import web

from utils import keystore, role_sync

log = logging.getLogger("mc_api")

API_SECRET = os.getenv("MC_API_SECRET")
API_HOST = os.getenv("MC_API_HOST", "0.0.0.0")
API_PORT = int(os.getenv("MC_API_PORT", "8787"))

DISCORD_ID_RE = re.compile(r"^\d{15,20}$")


def _authed(request: web.Request) -> bool:
    if not API_SECRET:
        return False  # refuse everything if no secret is configured
    return request.headers.get("X-API-Key") == API_SECRET


async def handle_redeem(request: web.Request):
    if not _authed(request):
        return web.json_response({"ok": False, "reason": "unauthorized"}, status=401)
    try:
        body = await request.json()
        key = str(body["key"]).strip()
        uuid = str(body["uuid"]).strip()
        name = str(body.get("name", "")).strip()
        discord_id = str(body.get("discord_id", "")).strip() or None
    except (KeyError, ValueError, TypeError):
        return web.json_response({"ok": False, "reason": "bad_request"}, status=400)

    if not key or not uuid:
        return web.json_response({"ok": False, "reason": "bad_request"}, status=400)
    if discord_id and not DISCORD_ID_RE.match(discord_id):
        return web.json_response({"ok": False, "reason": "bad_discord_id"}, status=400)

    ok, reason, rec = keystore.redeem_key(key, uuid, name, discord_id)
    resp = {"ok": ok, "reason": reason}
    if rec:
        resp["expires_at"] = rec["expires_at"]

    if ok and rec.get("discord_id") and rec.get("guild_id"):
        granted, grant_reason = await role_sync.grant_key_role(rec["guild_id"], rec["discord_id"])
        keystore.mark_role_granted(rec["key"], granted)
        resp["role_granted"] = granted
        if not granted:
            log.info("Key %s redeemed but role not granted: %s", rec["key"][:8], grant_reason)

    return web.json_response(resp)


async def handle_check(request: web.Request):
    if not _authed(request):
        return web.json_response({"ok": False, "reason": "unauthorized"}, status=401)
    uuid = request.query.get("uuid", "").strip()
    if not uuid:
        return web.json_response({"ok": False, "reason": "bad_request"}, status=400)

    rec = keystore.get_key_for_uuid(uuid)
    if not rec:
        return web.json_response({"ok": False, "reason": "not_registered"})

    valid = keystore.is_valid(rec)
    reason = "ok" if valid else ("revoked" if rec.get("revoked") else "expired")

    # If it's gone invalid and we still think the role is on, strip it now
    # rather than waiting for the periodic sweep — this fires on every
    # authenticated login/recheck, so it's a fast path for the common case.
    if not valid and rec.get("role_granted"):
        await role_sync.revoke_key_role(rec.get("guild_id"), rec.get("discord_id"))
        keystore.mark_role_granted(rec["key"], False)

    return web.json_response({"ok": valid, "reason": reason, "expires_at": rec["expires_at"]})


async def handle_changeuser(request: web.Request):
    """Player wants to re-link their Minecraft account to a different Discord
    user id. Strips the key-role from the old id (if it had it) and grants it
    to the new one (if the key is still valid)."""
    if not _authed(request):
        return web.json_response({"ok": False, "reason": "unauthorized"}, status=401)
    try:
        body = await request.json()
        uuid = str(body["uuid"]).strip()
        new_discord_id = str(body["new_discord_id"]).strip()
    except (KeyError, ValueError, TypeError):
        return web.json_response({"ok": False, "reason": "bad_request"}, status=400)

    if not uuid or not DISCORD_ID_RE.match(new_discord_id):
        return web.json_response({"ok": False, "reason": "bad_discord_id"}, status=400)

    rec = keystore.get_key_for_uuid(uuid)
    if not rec:
        return web.json_response({"ok": False, "reason": "not_registered"}, status=404)

    old_discord_id = rec.get("discord_id")
    guild_id = rec.get("guild_id")

    if old_discord_id and rec.get("role_granted"):
        await role_sync.revoke_key_role(guild_id, old_discord_id)
        keystore.mark_role_granted(rec["key"], False)

    keystore.set_discord_id(rec["key"], new_discord_id)

    resp = {"ok": True, "reason": "ok"}
    if keystore.is_valid(rec) and guild_id:
        granted, grant_reason = await role_sync.grant_key_role(guild_id, new_discord_id)
        keystore.mark_role_granted(rec["key"], granted)
        resp["role_granted"] = granted
        if not granted:
            resp["role_reason"] = grant_reason
    return web.json_response(resp)


async def handle_health(request: web.Request):
    return web.json_response({"ok": True})


def build_app() -> web.Application:
    app = web.Application()
    app.router.add_post("/redeem", handle_redeem)
    app.router.add_get("/check", handle_check)
    app.router.add_post("/changeuser", handle_changeuser)
    app.router.add_get("/health", handle_health)
    return app


async def start_api_server(bot=None):
    if bot is not None:
        role_sync.set_bot(bot)
    if not API_SECRET:
        log.warning("MC_API_SECRET is not set — the Minecraft key API will refuse all requests. Set it in .env.")
    app = build_app()
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, API_HOST, API_PORT)
    await site.start()
    log.info("Minecraft key API listening on %s:%s", API_HOST, API_PORT)
    return runner
