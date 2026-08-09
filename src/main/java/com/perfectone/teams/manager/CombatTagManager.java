package com.perfectone.teams.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are currently "combat tagged" (recently exchanged PvP damage)
 * and who they last fought, so we can punish combat-logging.
 *
 * Only ever populated for players who are in a team - see CombatListener, which is
 * the sole caller of tag(). Civilians are never tagged and are therefore free to log
 * out mid-fight with no penalty.
 */
public class CombatTagManager {

    private static class TagInfo {
        long expiresAt;
        UUID lastOpponent;
    }

    private final Map<UUID, TagInfo> tagged = new ConcurrentHashMap<>();

    public void tag(UUID playerId, UUID opponentId, long durationSeconds) {
        TagInfo info = this.tagged.computeIfAbsent(playerId, k -> new TagInfo());
        info.expiresAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        info.lastOpponent = opponentId;
    }

    public boolean isTagged(UUID playerId) {
        TagInfo info = this.tagged.get(playerId);
        if (info == null) {
            return false;
        }
        if (System.currentTimeMillis() > info.expiresAt) {
            this.tagged.remove(playerId);
            return false;
        }
        return true;
    }

    public UUID getLastOpponent(UUID playerId) {
        TagInfo info = this.tagged.get(playerId);
        return info == null ? null : info.lastOpponent;
    }

    public long secondsRemaining(UUID playerId) {
        TagInfo info = this.tagged.get(playerId);
        if (info == null) {
            return 0L;
        }
        long remaining = (info.expiresAt - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, remaining);
    }

    public void clear(UUID playerId) {
        this.tagged.remove(playerId);
    }
}
