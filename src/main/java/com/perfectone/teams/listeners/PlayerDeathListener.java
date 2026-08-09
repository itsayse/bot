package com.perfectone.teams.listeners;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {

    private final TeamsPlugin plugin;
    private final Map<String, Long> lastRewardedKill = new ConcurrentHashMap<>();

    public PlayerDeathListener(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        this.resolveDeath(event.getEntity(), event.getEntity().getKiller());
    }

    /**
     * Applies scoring/bounty/death penalties for a victim, crediting a killer if one is
     * known. Shared by natural deaths (onDeath above) and combat-log kills (CombatListener),
     * so both paths use identical scoring.
     */
    public void resolveDeath(Player victim, Player killer) {
        PlayerData victimData = this.plugin.getPlayerDataManager().getOrCreate(victim);
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            this.handlePvpKill(killer, victim, victimData);
        }
        this.applyDeathPenalties(victim, victimData);
        this.plugin.getPlayerDataManager().save(victimData);
        this.plugin.getDiscordBridge().pushSnapshotAsync();
    }

    private void applyDeathPenalties(Player victim, PlayerData victimData) {
        double deathTeamPenalty = this.plugin.getConfig().getDouble("scoring.death-team-score-penalty", 5.0);
        double deathBountyPenalty = this.plugin.getConfig().getDouble("scoring.death-bounty-penalty", 20.0);
        victimData.setPersonalScore(0.0);
        victimData.incrementDeaths();
        if (deathBountyPenalty > 0.0 && victimData.getBounty() > 0.0) {
            victimData.addBounty(-Math.min(deathBountyPenalty, victimData.getBounty()));
        }
        if (!victimData.isCivilian() && deathTeamPenalty > 0.0) {
            this.plugin.getTeamManager().getTeam(victimData.getTeamKey()).ifPresent(team -> {
                team.addScore(-deathTeamPenalty);
                this.plugin.getTeamManager().saveTeam(team);
            });
        }
    }

    private void handlePvpKill(Player killer, Player victim, PlayerData victimData) {
        PlayerData killerData = this.plugin.getPlayerDataManager().getOrCreate(killer);
        Optional<TeamData> killerTeamOpt = killerData.isCivilian() ? Optional.empty() : this.plugin.getTeamManager().getTeam(killerData.getTeamKey());
        Optional<TeamData> victimTeamOpt = victimData.isCivilian() ? Optional.empty() : this.plugin.getTeamManager().getTeam(victimData.getTeamKey());
        boolean sameTeam = !killerData.isCivilian() && !victimData.isCivilian() && killerData.getTeamKey().equalsIgnoreCase(victimData.getTeamKey());
        boolean allied = !sameTeam && killerTeamOpt.isPresent() && victimTeamOpt.isPresent()
                && this.plugin.getTeamManager().areAllied(killerTeamOpt.get().getKey(), victimTeamOpt.get().getKey());

        double enemyKillScore = this.plugin.getConfig().getDouble("scoring.kill-enemy-team-score", 10.0);
        double civilianPenalty = this.plugin.getConfig().getDouble("scoring.kill-civilian-penalty", 5.0);
        double bountyPerKill = this.plugin.getConfig().getDouble("scoring.bounty-per-kill", 50.0);
        boolean ignoreFriendlyFire = this.plugin.getConfig().getBoolean("scoring.ignore-friendly-fire-scoring", true);
        long cooldownSeconds = this.plugin.getConfig().getLong("scoring.same-victim-cooldown-seconds", 300L);

        if (sameTeam && ignoreFriendlyFire) {
            killer.sendMessage(ChatColor.YELLOW + "That was a teammate - no score change.");
            killerData.incrementKills();
            this.plugin.getPlayerDataManager().save(killerData);
            return;
        }
        if (allied) {
            killer.sendMessage(ChatColor.YELLOW + "That team is allied with yours - no score change.");
            killerData.incrementKills();
            this.plugin.getPlayerDataManager().save(killerData);
            return;
        }

        killerData.incrementKills();
        if (killerTeamOpt.isPresent()) {
            TeamData killerTeam = killerTeamOpt.get();
            if (victimData.isCivilian()) {
                killerTeam.addScore(-civilianPenalty);
                this.plugin.getTeamManager().saveTeam(killerTeam);
                killer.sendMessage(ChatColor.RED + "You killed a civilian! " + ChatColor.GOLD + killerTeam.getDisplayName()
                        + ChatColor.RED + " lost " + civilianPenalty + " team score.");
            } else {
                String cooldownKey = killer.getUniqueId() + ":" + victim.getUniqueId();
                long remaining = this.secondsRemaining(cooldownKey, cooldownSeconds);
                if (cooldownSeconds > 0L && remaining > 0L) {
                    killer.sendMessage(ChatColor.YELLOW + "You killed " + victim.getName() + " again too soon - no reward ("
                            + remaining + "s cooldown left on this target).");
                } else {
                    killerTeam.addScore(enemyKillScore);
                    killerData.addPersonalScore(enemyKillScore);
                    killerData.addBounty(bountyPerKill);
                    this.plugin.getTeamManager().saveTeam(killerTeam);
                    this.lastRewardedKill.put(cooldownKey, System.currentTimeMillis());
                    killer.sendMessage(ChatColor.GREEN + "+" + enemyKillScore + " team score, +" + bountyPerKill
                            + " bounty for eliminating " + ChatColor.GOLD + victim.getName());
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + victim.getName() + ChatColor.GRAY + " was eliminated by "
                            + ChatColor.GOLD + killer.getName() + ChatColor.GRAY + " ("
                            + this.plugin.getTeamManager().colorizedPrefix(killerTeam) + ChatColor.GRAY + ")");
                }
            }
        }
        this.plugin.getPlayerDataManager().save(killerData);
    }

    private long secondsRemaining(String cooldownKey, long cooldownSeconds) {
        if (cooldownSeconds <= 0L) {
            return 0L;
        }
        Long last = this.lastRewardedKill.get(cooldownKey);
        if (last == null) {
            return 0L;
        }
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0L, cooldownSeconds - elapsed);
    }
}
