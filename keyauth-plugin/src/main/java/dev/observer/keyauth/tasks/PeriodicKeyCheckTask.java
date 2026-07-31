package dev.observer.keyauth.tasks;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.session.AuthState;
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
            // Skip players who've never logged in at all (still doing /key,
            // /register, /login). Players who are AUTHENTICATED or already
            // KEY_LOCKED both still need to be re-checked here.
            AuthState state = plugin.sessions().get(player.getUniqueId());
            if (state != AuthState.AUTHENTICATED && state != AuthState.KEY_LOCKED) {
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
                            // Covers the case where the key was fixed on the API side
                            // (e.g. staff re-issued it) without the player running
                            // /renewkey themselves.
                            if (plugin.sessions().get(uuid) == AuthState.KEY_LOCKED) {
                                plugin.sessions().set(uuid, AuthState.AUTHENTICATED);
                                plugin.sessions().clearKeyGrace(uuid);
                                player.sendMessage(plugin.msg("renewkey-success"));
                            }
                            return;
                        }
                        if ("revoked".equals(result.reason)) {
                            // Freeze in place immediately — no kick. They stay frozen
                            // until /renewkey succeeds.
                            if (plugin.sessions().get(uuid) != AuthState.KEY_LOCKED) {
                                plugin.sessions().set(uuid, AuthState.KEY_LOCKED);
                                long timeoutMs = plugin.getConfig().getLong("auth.locked-timeout-minutes", 10) * 60_000L;
                                plugin.sessions().startKeyGrace(uuid, System.currentTimeMillis() + timeoutMs);
                                player.sendMessage(plugin.msg("key-locked-revoked"));
                            } else if (plugin.sessions().keyGraceExpired(uuid)) {
                                plugin.sessions().clearKeyGrace(uuid);
                                player.kickPlayer(plugin.msg("key-check-failed-revoked"));
                            }
                            return;
                        }
                        // Expired (or record missing) — freeze in place immediately
                        // instead of letting them keep playing. They stay frozen until
                        // /renewkey succeeds, or get kicked if they never fix it within
                        // the configured timeout.
                        if (plugin.sessions().get(uuid) != AuthState.KEY_LOCKED) {
                            plugin.sessions().set(uuid, AuthState.KEY_LOCKED);
                            long timeoutMs = plugin.getConfig().getLong("auth.locked-timeout-minutes", 10) * 60_000L;
                            plugin.sessions().startKeyGrace(uuid, System.currentTimeMillis() + timeoutMs);
                            player.sendMessage(plugin.msg("key-locked-expired"));
                        } else if (plugin.sessions().keyGraceExpired(uuid)) {
                            plugin.sessions().clearKeyGrace(uuid);
                            player.kickPlayer(plugin.msg("key-check-failed-expired"));
                        } else {
                            player.sendMessage(plugin.msg("key-locked-reminder"));
                        }
                    }));
        }
    }
}
