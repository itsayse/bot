package dev.observer.keyauth.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerDataManager {
    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration data;

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create players.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save players.yml", e);
        }
    }

    public synchronized boolean isRegistered(UUID uuid) {
        return data.contains(uuid.toString());
    }

    public synchronized PlayerRecord get(UUID uuid) {
        String path = uuid.toString();
        if (!data.contains(path)) {
            return null;
        }
        PlayerRecord rec = new PlayerRecord();
        rec.uuid = uuid.toString();
        rec.passwordHash = data.getString(path + ".passwordHash");
        rec.salt = data.getString(path + ".salt");
        rec.keyUsed = data.getString(path + ".keyUsed");
        rec.registeredAt = data.getLong(path + ".registeredAt");
        rec.discordId = data.getString(path + ".discordId");
        return rec;
    }

    public synchronized void save(PlayerRecord rec) {
        String path = rec.uuid;
        data.set(path + ".passwordHash", rec.passwordHash);
        data.set(path + ".salt", rec.salt);
        data.set(path + ".keyUsed", rec.keyUsed);
        data.set(path + ".registeredAt", rec.registeredAt);
        data.set(path + ".discordId", rec.discordId);
        save();
    }
}