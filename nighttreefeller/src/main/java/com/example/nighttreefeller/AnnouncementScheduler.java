package com.example.nighttreefeller;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs through a list of configured /tellraw messages on a repeating timer,
 * posting one message per interval and cycling back to the start once it
 * reaches the end. Executed as console so it works regardless of which
 * players are online or their permissions.
 */
public class AnnouncementScheduler {

    private final NightTreeFellerPlugin plugin;
    private BukkitTask task;
    private int nextIndex = 0;

    public AnnouncementScheduler(NightTreeFellerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop(); // in case start() is ever called twice, e.g. on a config reload
        nextIndex = 0;

        if (!plugin.getConfig().getBoolean("announcements.tellraw.enabled", false)) {
            return;
        }

        List<String> messages = new ArrayList<>(plugin.getConfig().getStringList("announcements.tellraw.messages"));
        if (messages.isEmpty()) {
            plugin.getLogger().warning("announcements.tellraw.enabled is true but no messages are configured - skipping.");
            return;
        }

        int intervalMinutes = Math.max(1, plugin.getConfig().getInt("announcements.tellraw.interval-minutes", 20));
        long periodTicks = intervalMinutes * 60L * 20L;

        int initialDelayMinutes = plugin.getConfig().getInt("announcements.tellraw.initial-delay-minutes", intervalMinutes);
        long delayTicks = Math.max(0, initialDelayMinutes) * 60L * 20L;

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            String message = messages.get(nextIndex);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw @a " + message);
            nextIndex = (nextIndex + 1) % messages.size();
        }, delayTicks, periodTicks);

        plugin.getLogger().info("Tellraw announcements scheduled: " + messages.size()
                + " message(s) cycling every " + intervalMinutes + " minute(s).");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        nextIndex = 0;
    }
}
