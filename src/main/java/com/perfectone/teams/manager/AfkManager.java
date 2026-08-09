/*
 * Decompiled with CFR 0.152.
 */
package com.perfectone.teams.manager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkManager {
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();

    public boolean toggle(UUID playerId) {
        if (this.afkPlayers.contains(playerId)) {
            this.afkPlayers.remove(playerId);
            return false;
        }
        this.afkPlayers.add(playerId);
        return true;
    }

    public boolean isAfk(UUID playerId) {
        return this.afkPlayers.contains(playerId);
    }

    public void clear(UUID playerId) {
        this.afkPlayers.remove(playerId);
    }
}

