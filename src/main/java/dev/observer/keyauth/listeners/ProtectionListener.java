package dev.observer.keyauth.listeners;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.session.AuthState;
import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class ProtectionListener implements Listener {
   private final KeyAuthPlugin plugin;
   private static final Set<String> ALLOWED_COMMANDS = Set.of("/key", "/renewkey");

   public ProtectionListener(KeyAuthPlugin plugin) {
      this.plugin = plugin;
   }

   private boolean locked(Player player) {
      return !this.plugin.sessions().isAuthenticated(player.getUniqueId());
   }

   private String lockedMessage(Player player) {
      return this.plugin.sessions().get(player.getUniqueId()) == AuthState.KEY_LOCKED ? this.plugin.msg("key-locked-action-blocked") : this.plugin.msg("still-locked");
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onMove(PlayerMoveEvent event) {
      if (this.locked(event.getPlayer())) {
         if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
         }

      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onChat(AsyncPlayerChatEvent event) {
      if (this.locked(event.getPlayer())) {
         event.setCancelled(true);
         event.getPlayer().sendMessage(this.lockedMessage(event.getPlayer()));
      }

   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      if (this.locked(event.getPlayer())) {
         String cmd = event.getMessage().split(" ")[0].toLowerCase(Locale.ROOT);
         if (!ALLOWED_COMMANDS.contains(cmd)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(this.lockedMessage(event.getPlayer()));
         }

      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onBreak(BlockBreakEvent event) {
      if (this.locked(event.getPlayer())) {
         event.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onPlace(BlockPlaceEvent event) {
      if (this.locked(event.getPlayer())) {
         event.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onInteract(PlayerInteractEvent event) {
      if (this.locked(event.getPlayer())) {
         event.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDamage(EntityDamageEvent event) {
      Entity var3 = event.getEntity();
      if (var3 instanceof Player player) {
         if (this.locked(player)) {
            event.setCancelled(true);
         }
      }

   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onDamageByEntity(EntityDamageByEntityEvent event) {
      Entity var3 = event.getDamager();
      if (var3 instanceof Player player) {
         if (this.locked(player)) {
            event.setCancelled(true);
         }
      }

   }
}
