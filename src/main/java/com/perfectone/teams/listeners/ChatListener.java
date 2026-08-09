/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.AsyncPlayerChatEvent
 */
package com.perfectone.teams.listeners;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import java.util.Optional;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener
implements Listener {
    private final TeamsPlugin plugin;

    public ChatListener(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        String prefix;
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(event.getPlayer());
        if (data.isCivilian()) {
            prefix = ChatColor.translateAlternateColorCodes((char)'&', (String)this.plugin.getConfig().getString("chat.civilian-prefix", "&7[Civilian]&r "));
        } else {
            Optional<TeamData> team = this.plugin.getTeamManager().getTeam(data.getTeamKey());
            prefix = team.map(t -> this.plugin.getTeamManager().colorizedPrefix((TeamData)t) + " ").orElse("");
        }
        String format = ChatColor.translateAlternateColorCodes((char)'&', (String)this.plugin.getConfig().getString("chat.format", "{prefix}&r{player}&f: {message}"));
        format = format.replace("{prefix}", prefix).replace("{player}", "%1$s").replace("{message}", "%2$s");
        event.setFormat(format);
    }
}

