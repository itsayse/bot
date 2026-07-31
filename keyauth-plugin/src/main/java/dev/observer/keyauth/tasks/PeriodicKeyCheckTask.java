package dev.observer.keyauth.tasks;

import dev.observer.keyauth.KeyAuthPlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class PeriodicKeyCheckTask extends BukkitRunnable {
    private final KeyAuthPlugin plugin;

    public PeriodicKeyCheckTask(KeyAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.sessions().isAuthenticated(player.getUniqueId())) {
                continue;
            }
            UUID uuid = player.getUniqueId();
            plugin.api().check(uuid.toString())
                    .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (result.networkError) {
                            return;
                        }
                        if (result.ok) {
                            plugin.sessions().clearKeyGrace(uuid);
                            return;
                        }
                        if ("revoked".equals(result.reason)) {
                            player.kickPlayer(plugin.msg("key-check-failed-revoked"));
                            return;
                        }
                        // Expired (or record missing) — grace window instead of an
                        // immediate kick, so the player can /renewkey and keep playing.
                        if (plugin.sessions().isInKeyGrace(uuid)) {
                            if (plugin.sessions().keyGraceExpired(uuid)) {
                                plugin.sessions().clearKeyGrace(uuid);
                                player.kickPlayer(plugin.msg("key-check-failed-expired"));
                            } else {
                                player.sendMessage(plugin.msg("key-expired-grace-reminder"));
                            }
                        } else {
                            long graceMs = plugin.getConfig().getLong("auth.expired-grace-minutes", 10) * 60_000L;
                            plugin.sessions().startKeyGrace(uuid, System.currentTimeMillis() + graceMs);
                            player.sendMessage(plugin.msg("key-expired-grace"));
                        }
                    }));
        }
    }
}
