package dev.observer.keyauth.tasks;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.session.AuthState;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class PeriodicKeyCheckTask extends BukkitRunnable {
   private final KeyAuthPlugin plugin;

   public PeriodicKeyCheckTask(KeyAuthPlugin plugin) {
      this.plugin = plugin;
   }

   public void run() {
      for(Player player : this.plugin.getServer().getOnlinePlayers()) {
         AuthState state = this.plugin.sessions().get(player.getUniqueId());
         if (state == AuthState.AUTHENTICATED || state == AuthState.KEY_LOCKED) {
            UUID uuid = player.getUniqueId();
            this.plugin.api().check(uuid.toString()).thenAccept((result) -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                  if (player.isOnline()) {
                     if (!result.networkError) {
                        if (result.ok) {
                           if (this.plugin.sessions().get(uuid) == AuthState.KEY_LOCKED) {
                              this.plugin.sessions().set(uuid, AuthState.AUTHENTICATED);
                              this.plugin.sessions().clearKeyGrace(uuid);
                              player.sendMessage(this.plugin.msg("renewkey-success"));
                           }

                        } else if ("revoked".equals(result.reason)) {
                           if (this.plugin.sessions().get(uuid) != AuthState.KEY_LOCKED) {
                              this.plugin.sessions().set(uuid, AuthState.KEY_LOCKED);
                              long timeoutMs = this.plugin.getConfig().getLong("auth.locked-timeout-minutes", 10L) * 60000L;
                              this.plugin.sessions().startKeyGrace(uuid, System.currentTimeMillis() + timeoutMs);
                              player.sendMessage(this.plugin.msg("key-locked-revoked"));
                           } else if (this.plugin.sessions().keyGraceExpired(uuid)) {
                              this.plugin.sessions().clearKeyGrace(uuid);
                              player.kickPlayer(this.plugin.msg("key-check-failed-revoked"));
                           }

                        } else {
                           if (this.plugin.sessions().get(uuid) != AuthState.KEY_LOCKED) {
                              this.plugin.sessions().set(uuid, AuthState.KEY_LOCKED);
                              long timeoutMs = this.plugin.getConfig().getLong("auth.locked-timeout-minutes", 10L) * 60000L;
                              this.plugin.sessions().startKeyGrace(uuid, System.currentTimeMillis() + timeoutMs);
                              player.sendMessage(this.plugin.msg("key-locked-expired"));
                           } else if (this.plugin.sessions().keyGraceExpired(uuid)) {
                              this.plugin.sessions().clearKeyGrace(uuid);
                              player.kickPlayer(this.plugin.msg("key-check-failed-expired"));
                           } else {
                              player.sendMessage(this.plugin.msg("key-locked-reminder"));
                           }

                        }
                     }
                  }
               }));
         }
      }

   }
}
