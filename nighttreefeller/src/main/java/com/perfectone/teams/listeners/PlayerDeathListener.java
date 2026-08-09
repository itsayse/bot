package com.perfectone.teams.listeners;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Optional;

/**
 * Implements the scoring rules:
 *  - Kill an enemy team's player  -> killer's team score UP, killer personal score UP, killer gets bounty, kill count UP.
 *  - Kill a civilian              -> killer's team score DOWN (penalty). No bounty.
 *  - Kill your own teammate       -> no score change (configurable).
 *  - On death, the victim's PERSONAL score resets to zero. Team score is untouched (it's a running total).
 */
public class PlayerDeathListener implements Listener {

    private final TeamsPlugin plugin;

    public PlayerDeathListener(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        PlayerData victimData = plugin.getPlayerDataManager().getOrCreate(victim);

        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            handlePvpKill(killer, victim, victimData);
        }

        // Regardless of who killed them (or environmental death): their personal score resets to zero.
        victimData.setPersonalScore(0.0);
        victimData.incrementDeaths();
        plugin.getPlayerDataManager().save(victimData);

        plugin.getDiscordBridge().pushSnapshotAsync();
    }

    private void handlePvpKill(Player killer, Player victim, PlayerData victimData) {
        PlayerData killerData = plugin.getPlayerDataManager().getOrCreate(killer);

        Optional<TeamData> killerTeamOpt = killerData.isCivilian()
                ? Optional.empty()
                : plugin.getTeamManager().getTeam(killerData.getTeamKey());

        boolean sameTeam = !killerData.isCivilian()
                && !victimData.isCivilian()
                && killerData.getTeamKey().equalsIgnoreCase(victimData.getTeamKey());

        double enemyKillScore = plugin.getConfig().getDouble("scoring.kill-enemy-team-score", 10.0);
        double civilianPenalty = plugin.getConfig().getDouble("scoring.kill-civilian-penalty", 5.0);
        double bountyPerKill = plugin.getConfig().getDouble("scoring.bounty-per-kill", 50.0);
        boolean ignoreFriendlyFire = plugin.getConfig().getBoolean("scoring.ignore-friendly-fire-scoring", true);

        if (sameTeam && ignoreFriendlyFire) {
            killer.sendMessage(ChatColor.YELLOW + "That was a teammate - no score change.");
            killerData.incrementKills();
            plugin.getPlayerDataManager().save(killerData);
            return;
        }

        killerData.incrementKills();

        if (killerTeamOpt.isPresent()) {
            TeamData killerTeam = killerTeamOpt.get();

            if (victimData.isCivilian()) {
                // Killing a civilian penalizes the killer's team.
                killerTeam.addScore(-civilianPenalty);
                plugin.getTeamManager().saveTeam(killerTeam);
                killer.sendMessage(ChatColor.RED + "You killed a civilian! " + ChatColor.GOLD + killerTeam.getDisplayName()
                        + ChatColor.RED + " lost " + civilianPenalty + " team score.");
            } else {
                // Enemy team kill: reward team score, personal score, and bounty.
                killerTeam.addScore(enemyKillScore);
                killerData.addPersonalScore(enemyKillScore);
                killerData.addBounty(bountyPerKill);
                plugin.getTeamManager().saveTeam(killerTeam);

                killer.sendMessage(ChatColor.GREEN + "+" + enemyKillScore + " team score, +" + bountyPerKill
                        + " bounty for eliminating " + ChatColor.GOLD + victim.getName());
                Bukkit.broadcastMessage(ChatColor.DARK_RED + victim.getName() + ChatColor.GRAY + " was eliminated by "
                        + ChatColor.GOLD + killer.getName() + ChatColor.GRAY + " ("
                        + plugin.getTeamManager().colorizedPrefix(killerTeam) + ChatColor.GRAY + ")");
            }
        }
        // Civilian killer: no team to credit/penalize, so no score changes either way.

        plugin.getPlayerDataManager().save(killerData);
    }
}
