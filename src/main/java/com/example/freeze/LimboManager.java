package com.example.freeze;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds not-yet-authenticated players in a fixed "limbo" location and
 * restores their real position once they finish /login or /register.
 *
 * Hook points (call these from your existing auth plugin):
 *   - onPlayerJoinBeforeAuth(player)  -> call from your PlayerJoinEvent
 *     handler, BEFORE showing the "enter your key" / "log in" message.
 *   - onLoginSuccess(player)          -> call at the exact point your
 *     auth plugin currently sends the "login-success" message.
 */
public class LimboManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final FileConfiguration data;

    // Configure this to a safe, enclosed spot on your server.
    // Example: a 1-block platform inside a void/superflat world, walled
    // in with barrier blocks so players can't move even if freeze fails.
    private static final String LIMBO_WORLD = "limbo";
    private static final double LIMBO_X = 0.5;
    private static final double LIMBO_Y = 200.0;
    private static final double LIMBO_Z = 0.5;

    // In case the auth plugin doesn't already freeze movement, track who's in limbo.
    private final Map<UUID, Boolean> inLimbo = new HashMap<>();

    public LimboManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "limbo-locations.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create limbo-locations.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    /** Call the moment a player joins, before they've authenticated. */
    public void onPlayerJoinBeforeAuth(Player player) {
        UUID uuid = player.getUniqueId();

        // Save their real location to disk so it survives a server restart
        // if they disconnect mid-login.
        Location real = player.getLocation();
        String path = uuid.toString();
        data.set(path + ".world", real.getWorld().getName());
        data.set(path + ".x", real.getX());
        data.set(path + ".y", real.getY());
        data.set(path + ".z", real.getZ());
        data.set(path + ".yaw", real.getYaw());
        data.set(path + ".pitch", real.getPitch());
        saveData();

        World limboWorld = Bukkit.getWorld(LIMBO_WORLD);
        if (limboWorld == null) {
            plugin.getLogger().warning("Limbo world '" + LIMBO_WORLD + "' not found — player not teleported to limbo.");
            return;
        }

        player.teleport(new Location(limboWorld, LIMBO_X, LIMBO_Y, LIMBO_Z));
        inLimbo.put(uuid, true);
    }

    /** Call at the exact point your auth plugin confirms a successful login. */
    public void onLoginSuccess(Player player) {
        UUID uuid = player.getUniqueId();
        String path = uuid.toString();

        if (!data.contains(path)) {
            // No saved location (e.g. brand-new account that registered
            // without ever having a real position) — leave them where they are.
            inLimbo.remove(uuid);
            return;
        }

        String worldName = data.getString(path + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World '" + worldName + "' not found — could not restore " + player.getName() + "'s location.");
            inLimbo.remove(uuid);
            return;
        }

        Location real = new Location(
                world,
                data.getDouble(path + ".x"),
                data.getDouble(path + ".y"),
                data.getDouble(path + ".z"),
                (float) data.getDouble(path + ".yaw"),
                (float) data.getDouble(path + ".pitch")
        );

        player.teleport(real);
        inLimbo.remove(uuid);

        data.set(path, null);
        saveData();
    }

    public boolean isInLimbo(UUID uuid) {
        return inLimbo.getOrDefault(uuid, false);
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save limbo-locations.yml: " + e.getMessage());
        }
    }
}
