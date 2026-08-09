package com.perfectone.teams.manager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bare-bones AFK toggle. In-memory only - just tracks who's currently marked AFK.
 * No side effects on scoring, chat, or tab list; that's intentionally out of scope.
 */
public class AfkManager {

    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();

    /** Flips AFK state for this player. Returns the NEW state (true = now AFK). */
    public boolean toggle(UUID playerId) {
        if (afkPlayers.contains(playerId)) {
            afkPlayers.remove(playerId);
            return false;
        } else {
            afkPlayers.add(playerId);
            return true;
        }
    }

    public boolean isAfk(UUID playerId) {
        return afkPlayers.contains(playerId);
    }

    public void clear(UUID playerId) {
        afkPlayers.remove(playerId);
    }
}
