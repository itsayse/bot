package dev.observer.keyauth.listeners;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.data.PlayerRecord;
import dev.observer.keyauth.session.AuthState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {
   private final KeyAuthPlugin plugin;

   public JoinQuitListener(KeyAuthPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.NORMAL
   )
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      PlayerRecord rec = this.plugin.playerData().get(player.getUniqueId());
      if (rec == null) {
         // Never redeemed a key on this account — locked until they run /key.
         this.plugin.sessions().set(player.getUniqueId(), AuthState.AWAITING_KEY);
         player.sendMessage(this.plugin.msg("need-key"));
         return;
      }

      // Has a key on file — lock them until it's re-verified against the bot.
      this.plugin.sessions().set(player.getUniqueId(), AuthState.AWAITING_KEY);
      player.sendMessage(this.plugin.msg("rechecking-key"));
      this.plugin.api().check(player.getUniqueId().toString()).thenAccept((result) -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
               if (result.networkError) {
                  player.sendMessage(this.plugin.msg("api-unreachable"));
               } else if (result.ok) {
                  this.plugin.sessions().set(player.getUniqueId(), AuthState.AUTHENTICATED);
                  player.sendMessage(this.plugin.msg("key-success"));
               } else {
                  this.plugin.sessions().set(player.getUniqueId(), AuthState.AWAITING_KEY);
                  player.sendMessage("revoked".equals(result.reason) ? this.plugin.msg("key-revoked") : this.plugin.msg("key-expired"));
                  player.sendMessage(this.plugin.msg("need-key"));
               }
            }
         }));
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.plugin.sessions().remove(event.getPlayer().getUniqueId());
   }
}
