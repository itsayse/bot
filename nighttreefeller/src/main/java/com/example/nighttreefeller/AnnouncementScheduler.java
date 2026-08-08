package com.example.nighttreefeller;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runs a configured /tellraw command on a repeating timer (e.g. an hourly
 * server announcement), executed as the console so it works regardless of
 * which players are online or their permissions.
 */
public class AnnouncementScheduler {

    private final NightTreeFellerPlugin plugin;
    private BukkitTask task;

    public AnnouncementScheduler(NightTreeFellerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop(); // in case start() is ever called twice, e.g. on a config reload

        if (!plugin.getConfig().getBoolean("announcements.tellraw.enabled", false)) {
            return;
        }

        String message = plugin.getConfig().getString("announcements.tellraw.message", "");
        if (message == null || message.isEmpty()) {
            plugin.getLogger().warning("announcements.tellraw.enabled is true but no message is configured - skipping.");
            return;
        }

        int intervalMinutes = Math.max(1, plugin.getConfig().getInt("announcements.tellraw.interval-minutes", 60));
        long periodTicks = intervalMinutes * 60L * 20L;

        int initialDelayMinutes = plugin.getConfig().getInt("announcements.tellraw.initial-delay-minutes", intervalMinutes);
        long delayTicks = Math.max(0, initialDelayMinutes) * 60L * 20L;

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw @a " + message);
        }, delayTicks, periodTicks);

        plugin.getLogger().info("Tellraw announcements scheduled every " + intervalMinutes + " minute(s).");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
