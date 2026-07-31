package dev.observer.keyauth.listeners;

import dev.observer.keyauth.KeyAuthPlugin;
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

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean registered = plugin.playerData().isRegistered(player.getUniqueId());

        if (registered) {
            plugin.sessions().set(player.getUniqueId(), AuthState.AWAITING_LOGIN);
            player.sendMessage(plugin.msg("need-login"));
        } else {
            plugin.sessions().set(player.getUniqueId(), AuthState.AWAITING_KEY);
            player.sendMessage(plugin.msg("need-key"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.sessions().remove(event.getPlayer().getUniqueId());
    }
}
