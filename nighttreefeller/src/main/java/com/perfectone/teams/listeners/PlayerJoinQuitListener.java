package com.perfectone.teams.listeners;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;

/**
 * Keeps the vanilla scoreboard team entry list (tab list / above-head prefix) in sync
 * with each player's stored team membership.
 */
public class PlayerJoinQuitListener implements Listener {

    private final TeamsPlugin plugin;

    public PlayerJoinQuitListener(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player);
        data.setUsername(player.getName());
        plugin.getPlayerDataManager().save(data);

        if (!data.isCivilian()) {
            Optional<TeamData> team = plugin.getTeamManager().getTeam(data.getTeamKey());
            team.ifPresent(t -> plugin.getTeamManager().addPlayerToScoreboardTeam(t, player.getName()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Scoreboard team entries persist across sessions by name, so nothing to clean up here.
    }
}
