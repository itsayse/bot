package dev.observer.keyauth.listeners;

import dev.observer.keyauth.KeyAuthPlugin;
import dev.observer.keyauth.session.AuthState;
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

import java.util.Locale;
import java.util.Set;

public class ProtectionListener implements Listener {
    private final KeyAuthPlugin plugin;
    private static final Set<String> ALLOWED_COMMANDS = Set.of("/key", "/register", "/login", "/renewkey");

    public ProtectionListener(KeyAuthPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean locked(Player player) {
        return !plugin.sessions().isAuthenticated(player.getUniqueId());
    }

    /** Picks a message that matches why the player is locked right now. */
    private String lockedMessage(Player player) {
        if (plugin.sessions().get(player.getUniqueId()) == AuthState.KEY_LOCKED) {
            return plugin.msg("key-locked-action-blocked");
        }
        return plugin.msg("still-locked");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (!locked(event.getPlayer())) {
            return;
        }
        if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(lockedMessage(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!locked(event.getPlayer())) {
            return;
        }
        String cmd = event.getMessage().split(" ")[0].toLowerCase(Locale.ROOT);
        if (!ALLOWED_COMMANDS.contains(cmd)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(lockedMessage(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && locked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && locked(player)) {
            event.setCancelled(true);
        }
    }
}
