package dev.observer.keyauth.tasks;

import dev.observer.keyauth.KeyAuthPlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

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
            plugin.api().check(player.getUniqueId().toString())
                    .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (result.networkError) {
                            return;
                        }
                        if (!result.ok) {
                            String reasonKey = "revoked".equals(result.reason) ? "key-check-failed-revoked" : "key-check-failed-expired";
                            player.kickPlayer(plugin.msg(reasonKey));
                        }
                    }));
        }
    }
}
