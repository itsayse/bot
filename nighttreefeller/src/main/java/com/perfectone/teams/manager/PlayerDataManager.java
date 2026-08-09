package com.perfectone.teams.manager;

import com.perfectone.teams.data.Database;
import com.perfectone.teams.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final Database database;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(Database database) {
        this.database = database;
    }

    public void loadAll() {
        cache.clear();
        for (PlayerData p : database.loadAllPlayers()) {
            cache.put(p.getUuid(), p);
        }
    }

    /** Gets existing data or creates a fresh civilian record for this player. */
    public PlayerData getOrCreate(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), id ->
                new PlayerData(id, player.getName(), null, 0.0, 0, 0, 0.0));
    }

    public PlayerData getOrCreate(UUID uuid, String name) {
        return cache.computeIfAbsent(uuid, id -> new PlayerData(id, name, null, 0.0, 0, 0, 0.0));
    }

    public void save(PlayerData data) {
        database.upsertPlayer(data);
    }

    public Map<UUID, PlayerData> all() {
        return cache;
    }
}
