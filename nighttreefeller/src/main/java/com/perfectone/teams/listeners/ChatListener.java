package com.perfectone.teams.listeners;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Optional;

/**
 * Prepends the player's colored team prefix (or a civilian tag) to their chat messages.
 * Uses the legacy AsyncPlayerChatEvent for the widest Spigot/Paper compatibility.
 */
public class ChatListener implements Listener {

    private final TeamsPlugin plugin;

    public ChatListener(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(event.getPlayer());

        String prefix;
        if (data.isCivilian()) {
            prefix = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("chat.civilian-prefix", "&7[Civilian]&r "));
        } else {
            Optional<TeamData> team = plugin.getTeamManager().getTeam(data.getTeamKey());
            prefix = team.map(t -> plugin.getTeamManager().colorizedPrefix(t) + " ").orElse("");
        }

        String format = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("chat.format", "{prefix}&r{player}&f: {message}"));
        format = format.replace("{prefix}", prefix)
                        .replace("{player}", "%1$s")
                        .replace("{message}", "%2$s");

        event.setFormat(format);
    }
}
