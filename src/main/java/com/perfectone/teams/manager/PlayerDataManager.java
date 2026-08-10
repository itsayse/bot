/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 */
package com.perfectone.teams.manager;

import com.perfectone.teams.data.Database;
import com.perfectone.teams.data.PlayerData;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public class PlayerDataManager {
    private final Database database;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<UUID, PlayerData>();

    public PlayerDataManager(Database database) {
        this.database = database;
    }

    public void loadAll() {
        this.cache.clear();
        for (PlayerData p : this.database.loadAllPlayers()) {
            this.cache.put(p.getUuid(), p);
        }
    }

    public PlayerData getOrCreate(Player player) {
        return this.cache.computeIfAbsent(player.getUniqueId(), id -> new PlayerData((UUID)id, player.getName(), null, 0.0, 0, 0, 0.0));
    }

    public PlayerData getOrCreate(UUID uuid, String name) {
        return this.cache.computeIfAbsent(uuid, id -> new PlayerData((UUID)id, name, null, 0.0, 0, 0, 0.0));
    }

    public void save(PlayerData data) {
        this.database.upsertPlayer(data);
    }

    public Map<UUID, PlayerData> all() {
        return this.cache;
    }
}

