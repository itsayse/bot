/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 */
package com.perfectone.teams.listeners;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener
implements Listener {
    private final TeamsPlugin plugin;

    public PlayerJoinQuitListener(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player);
        data.setUsername(player.getName());
        this.plugin.getPlayerDataManager().save(data);
        if (!data.isCivilian()) {
            Optional<TeamData> team = this.plugin.getTeamManager().getTeam(data.getTeamKey());
            team.ifPresent(t -> this.plugin.getTeamManager().addPlayerToScoreboardTeam((TeamData)t, player.getName()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.getAfkManager().clear(event.getPlayer().getUniqueId());
    }
}

