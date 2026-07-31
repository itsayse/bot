import json
import os
import copy
import threading

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUILD_DIR = os.path.join(BASE_DIR, "data", "guilds")
LEVEL_DIR = os.path.join(BASE_DIR, "data", "levels")
os.makedirs(GUILD_DIR, exist_ok=True)
os.makedirs(LEVEL_DIR, exist_ok=True)

_lock = threading.Lock()

DEFAULT_CONFIG = {
    "admin_roles": [],       # roles allowed to CONFIGURE the bot (set by server owner)
    "mod_roles": [],         # roles allowed to use moderation commands (ban/kick/mute/purge)
    "log_channel": None,     # general mod-log channel

    "autorole": {
        "enabled": False,
        "role_ids": []
    },

    "automod": {
        "enabled": True,
        "punishment": "delete",   # delete | warn | mute | kick | ban
        "mute_minutes": 10,
        "warn_limit": 3,          # after this many warns, escalate
        "escalate_punishment": "mute",
        "bypass_roles": [],
        "log_channel": None,
        "warns": {},          # user_id(str) -> count
        "dm_on_warn": True,   # DM the user (themed message) when they get a bad-word warn or spam warn

        "spam": {
            "enabled": True,
            "message_limit": 5,        # N messages...
            "interval_seconds": 5,     # ...within this many seconds -> spam
            "duplicate_limit": 3,      # N identical messages in a row -> spam
            "punishment": "warn",      # delete | warn | mute | kick | ban
            "mute_minutes": 10
        },

        "invite_filter": {
            "enabled": True,
            "punishment": "warn",      # delete | warn | mute | kick | ban
            "mute_minutes": 10,
            "allow_own_server": True   # don't flag invites pointing back to this server
        },

        # when spam or an invite link triggers automod, also purge that member's
        # recent messages in the channel (0 = don't purge, just handle the one message)
        "raid_purge_minutes": 30
    },

    "antiraid": {
        "enabled": True,
        "join_threshold": 6,      # N joins
        "join_interval": 15,      # within this many seconds -> raid mode
        "min_account_age_hours": 24,
        "action": "kick",         # kick | ban | lockdown
        "lockdown_verification": "high",
        "log_channel": None,
        "antinuke_enabled": True,
        "antinuke_action_limit": 4,   # N destructive actions
        "antinuke_interval": 10,      # within seconds
        "antinuke_punishment": "strip_roles"  # strip_roles | ban
    },

    "levels": {
        "enabled": True,
        "min_points": 10,
        "max_points": 60,
        "xp_per_level_base": 100,
        "xp_per_level_growth": 50,
        "level_up_channel": None,     # channel where level-up announcements are posted
        "drop_channel": None,         # channel where the bot posts "points drop" messages
        "drop_interval_minutes": 0,   # 0 = only manual drops via the panel; >0 = auto-post every N minutes
        "role_rewards": {}            # level(str) -> role_id
    },

    "afk_vc": {
        "enabled": False,
        "afk_channel_id": None,
        "mute_timeout_minutes": 15,
        "check_interval_seconds": 60,
        "ignore_roles": []
    },

    "tickets": {
        "enabled": False,
        "panel_channel": None,
        "panel_message": None,
        "category_id": None,
        "support_roles": [],
        "log_channel": None,
        "open_tickets": {},   # channel_id(str) -> {owner, number, opened_at}
        "counter": 0,
        "transcripts_enabled": True,   # generate an HTML transcript on close
        "welcome_message": "Hey {member}! Support will be with you shortly.\nUse the button below when this is resolved."
    },

    "verification": {
        "enabled": False,
        "verify_channel": None,
        "panel_message": None,
        "unverified_role": None,   # assigned on join; hides everything but the verify channel (set up via channel permissions)
        "verified_role": None,     # granted after passing the captcha (optional)
        "log_channel": None,
        "captcha_length": 6
    },

    "welcome": {
        "enabled": False,
        "channel_id": None,
        "message": "Welcome {member} to **{guild}**! You're member #{membercount}. 🎉",
        "use_image": True,          # attach assets/welcome.png as the embed image, if present
        "dm_enabled": False,
        "dm_message": "Welcome to **{guild}**! Glad to have you."
    },

    "giveaways": {},   # message_id(str) -> giveaway data

    "keys": {
        "key_role": None,   # role granted to a player's linked Discord account while their MC key is valid
        "default_days": 30  # default validity for newly generated keys
    }
}


def _deep_merge(default, current):
    """Fill in any missing keys in `current` using `default`, recursively."""
    if not isinstance(current, dict):
        return copy.deepcopy(default)
    merged = copy.deepcopy(current)
    for k, v in default.items():
        if k not in merged:
            merged[k] = copy.deepcopy(v)
        elif isinstance(v, dict):
            merged[k] = _deep_merge(v, merged.get(k, {}))
    return merged


def _guild_path(guild_id):
    return os.path.join(GUILD_DIR, f"{guild_id}.json")


def _level_path(guild_id):
    return os.path.join(LEVEL_DIR, f"{guild_id}.json")


def get_config(guild_id):
    path = _guild_path(guild_id)
    with _lock:
        if os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    data = json.load(f)
            except (json.JSONDecodeError, OSError):
                data = {}
        else:
            data = {}
        merged = _deep_merge(DEFAULT_CONFIG, data)
        if merged != data:
            _save_config_unlocked(guild_id, merged)
        return merged


def _save_config_unlocked(guild_id, config):
    path = _guild_path(guild_id)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2)
    os.replace(tmp, path)


def save_config(guild_id, config):
    with _lock:
        _save_config_unlocked(guild_id, config)


def get_levels(guild_id):
    path = _level_path(guild_id)
    with _lock:
        if os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    return json.load(f)
            except (json.JSONDecodeError, OSError):
                return {}
        return {}


def save_levels(guild_id, data):
    path = _level_path(guild_id)
    with _lock:
        tmp = path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        os.replace(tmp, path)
