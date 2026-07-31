"""
Server-key system for the Minecraft integration.
Keys are 32-char, expire 30 days after creation (or last extension),
and are bound to exactly one Minecraft account on first redemption.
Each key can also be linked to a Discord user id, which is what the
key-role grant/removal is driven off of. Stored globally (not per-guild)
in data/keys.json — but each record remembers which guild it was
generated in, since that's where the key-role lives.
"""
import json
import os
import secrets
import string
import threading
from datetime import datetime, timedelta, timezone

from utils.storage import BASE_DIR

KEYS_PATH = os.path.join(BASE_DIR, "data", "keys.json")
_lock = threading.Lock()

ALPHABET = string.ascii_uppercase + string.digits


def _load():
    if not os.path.exists(KEYS_PATH):
        return {}
    with open(KEYS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def _save(data):
    os.makedirs(os.path.dirname(KEYS_PATH), exist_ok=True)
    tmp = KEYS_PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    os.replace(tmp, KEYS_PATH)


def _now():
    return datetime.now(timezone.utc)


def generate_key(days=30, created_by=None, guild_id=None):
    with _lock:
        data = _load()
        while True:
            key = "".join(secrets.choice(ALPHABET) for _ in range(32))
            if key not in data:
                break
        now = _now()
        record = {
            "key": key,
            "created_at": now.isoformat(),
            "expires_at": (now + timedelta(days=days)).isoformat(),
            "revoked": False,
            "created_by": created_by,
            "guild_id": str(guild_id) if guild_id else None,
            "redeemed_by": None,      # Minecraft UUID (str) once claimed
            "redeemed_name": None,    # Minecraft username at time of claim
            "redeemed_at": None,
            "discord_id": None,       # Discord user id linked at redemption (drives the key-role)
            "role_granted": False,    # whether the key-role is currently applied for discord_id
        }
        data[key] = record
        _save(data)
        return record


def get_key(key: str):
    return _load().get(key.strip().upper())


def extend_key(key: str, days: int = 30):
    with _lock:
        data = _load()
        rec = data.get(key.strip().upper())
        if not rec:
            return None
        base = datetime.fromisoformat(rec["expires_at"])
        base = max(base, _now())  # extend from now if it already expired
        rec["expires_at"] = (base + timedelta(days=days)).isoformat()
        rec["revoked"] = False  # extending un-revokes
        _save(data)
        return rec


def shorten_key(key: str, days: int = 7):
    """Pull a key's expiry closer by `days`. Never moves it earlier than right now
    (i.e. the floor is 'expires immediately', not 'expired in the past')."""
    with _lock:
        data = _load()
        rec = data.get(key.strip().upper())
        if not rec:
            return None
        current = datetime.fromisoformat(rec["expires_at"])
        new_expiry = current - timedelta(days=days)
        now = _now()
        if new_expiry < now:
            new_expiry = now
        rec["expires_at"] = new_expiry.isoformat()
        _save(data)
        return rec


def revoke_key(key: str):
    with _lock:
        data = _load()
        rec = data.get(key.strip().upper())
        if not rec:
            return None
        rec["revoked"] = True
        _save(data)
        return rec


def delete_key(key: str):
    with _lock:
        data = _load()
        rec = data.pop(key.strip().upper(), None)
        if rec:
            _save(data)
        return rec


def is_valid(rec) -> bool:
    if not rec or rec.get("revoked"):
        return False
    return _now() < datetime.fromisoformat(rec["expires_at"])


def redeem_key(key: str, mc_uuid: str, mc_name: str, discord_id: str = None):
    """One key -> one player. Returns (ok, reason, record)."""
    with _lock:
        data = _load()
        rec = data.get(key.strip().upper())
        if not rec:
            return False, "invalid_key", None
        if rec.get("revoked"):
            return False, "revoked", rec
        if not is_valid(rec):
            return False, "expired", rec
        if rec["redeemed_by"] and rec["redeemed_by"] != mc_uuid:
            return False, "already_used", rec
        if not rec["redeemed_by"]:
            rec["redeemed_by"] = mc_uuid
            rec["redeemed_name"] = mc_name
            rec["redeemed_at"] = _now().isoformat()
        if discord_id and not rec.get("discord_id"):
            rec["discord_id"] = str(discord_id)
        _save(data)
        return True, "ok", rec


def set_discord_id(key: str, discord_id: str):
    with _lock:
        data = _load()
        rec = data.get(key.strip().upper())
        if not rec:
            return None
        rec["discord_id"] = str(discord_id) if discord_id else None
        _save(data)
        return rec


def mark_role_granted(key: str, granted: bool):
    with _lock:
        data = _load()
        rec = data.get(key.strip().upper())
        if not rec:
            return None
        rec["role_granted"] = bool(granted)
        _save(data)
        return rec


def get_key_for_uuid(mc_uuid: str):
    for rec in _load().values():
        if rec.get("redeemed_by") == mc_uuid:
            return rec
    return None


def get_key_for_discord(discord_id: str):
    discord_id = str(discord_id)
    for rec in _load().values():
        if rec.get("discord_id") == discord_id:
            return rec
    return None


def list_keys():
    return list(_load().values())


def status_label(rec) -> str:
    if rec.get("revoked"):
        return "🔴 Revoked"
    if not is_valid(rec):
        return "⏳ Expired"
    if rec.get("redeemed_by"):
        return "🟢 Active (claimed)"
    return "🟡 Active (unclaimed)"
