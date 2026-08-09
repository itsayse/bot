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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the scoring rules:
 *  - Kill an enemy team's player  -> killer's team score UP, killer personal score UP, killer gets bounty, kill count UP.
 *  - Kill a civilian              -> killer's team score DOWN (penalty). No bounty.
 *  - Kill your own teammate       -> no score change (configurable).
 *  - Kill an ALLIED team's player -> no score change, same as a teammate kill.
 *  - Anti-farm: repeat kills of the SAME victim by the SAME killer within a cooldown window earn no reward.
 *  - On ANY death (any cause): the victim's PERSONAL score resets to zero, their TEAM's score takes a
 *    small penalty (if they're in a team), and their bounty is reduced - discourages just staying alive
 *    and hoarding bounty forever, and gives dying real weight beyond "reset to zero".
 */
public class PlayerDeathListener implements Listener {

    private final TeamsPlugin plugin;

    // "<killerUuid>:<victimUuid>" -> epoch millis of the last REWARDED kill of that victim by that killer.
    // In-memory only - resets on restart, which is fine for an anti-farm cooldown.
    private final Map<String, Long> lastRewardedKill = new ConcurrentHashMap<>();

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

        applyDeathPenalties(victim, victimData);

        plugin.getPlayerDataManager().save(victimData);
        plugin.getDiscordBridge().pushSnapshotAsync();
    }

    /** Runs on every death, regardless of cause: personal score reset, team score dip, bounty dip. */
    private void applyDeathPenalties(Player victim, PlayerData victimData) {
        double deathTeamPenalty = plugin.getConfig().getDouble("scoring.death-team-score-penalty", 5.0);
        double deathBountyPenalty = plugin.getConfig().getDouble("scoring.death-bounty-penalty", 20.0);

        victimData.setPersonalScore(0.0);
        victimData.incrementDeaths();

        if (deathBountyPenalty > 0 && victimData.getBounty() > 0) {
            victimData.addBounty(-Math.min(deathBountyPenalty, victimData.getBounty()));
        }

        if (!victimData.isCivilian() && deathTeamPenalty > 0) {
            plugin.getTeamManager().getTeam(victimData.getTeamKey()).ifPresent(team -> {
                team.addScore(-deathTeamPenalty);
                plugin.getTeamManager().saveTeam(team);
            });
        }
    }

    private void handlePvpKill(Player killer, Player victim, PlayerData victimData) {
        PlayerData killerData = plugin.getPlayerDataManager().getOrCreate(killer);

        Optional<TeamData> killerTeamOpt = killerData.isCivilian()
                ? Optional.empty()
                : plugin.getTeamManager().getTeam(killerData.getTeamKey());
        Optional<TeamData> victimTeamOpt = victimData.isCivilian()
                ? Optional.empty()
                : plugin.getTeamManager().getTeam(victimData.getTeamKey());

        boolean sameTeam = !killerData.isCivilian()
                && !victimData.isCivilian()
                && killerData.getTeamKey().equalsIgnoreCase(victimData.getTeamKey());

        boolean allied = !sameTeam && killerTeamOpt.isPresent() && victimTeamOpt.isPresent()
                && plugin.getTeamManager().areAllied(killerTeamOpt.get().getKey(), victimTeamOpt.get().getKey());

        double enemyKillScore = plugin.getConfig().getDouble("scoring.kill-enemy-team-score", 10.0);
        double civilianPenalty = plugin.getConfig().getDouble("scoring.kill-civilian-penalty", 5.0);
        double bountyPerKill = plugin.getConfig().getDouble("scoring.bounty-per-kill", 50.0);
        boolean ignoreFriendlyFire = plugin.getConfig().getBoolean("scoring.ignore-friendly-fire-scoring", true);
        long cooldownSeconds = plugin.getConfig().getLong("scoring.same-victim-cooldown-seconds", 300);

        if (sameTeam && ignoreFriendlyFire) {
            killer.sendMessage(ChatColor.YELLOW + "That was a teammate - no score change.");
            killerData.incrementKills();
            plugin.getPlayerDataManager().save(killerData);
            return;
        }

        if (allied) {
            killer.sendMessage(ChatColor.YELLOW + "That team is allied with yours - no score change.");
            killerData.incrementKills();
            plugin.getPlayerDataManager().save(killerData);
            return;
        }

        killerData.incrementKills();

        if (killerTeamOpt.isPresent()) {
            TeamData killerTeam = killerTeamOpt.get();

            if (victimData.isCivilian()) {
                // Killing a civilian penalizes the killer's team. Not subject to the anti-farm
                // cooldown since it's a penalty, not a reward - nothing to farm here.
                killerTeam.addScore(-civilianPenalty);
                plugin.getTeamManager().saveTeam(killerTeam);
                killer.sendMessage(ChatColor.RED + "You killed a civilian! " + ChatColor.GOLD + killerTeam.getDisplayName()
                        + ChatColor.RED + " lost " + civilianPenalty + " team score.");
            } else {
                // Enemy team kill: reward team score, personal score, and bounty - unless
                // this killer recently already got rewarded for killing this same victim.
                String cooldownKey = killer.getUniqueId() + ":" + victim.getUniqueId();
                long remaining = secondsRemaining(cooldownKey, cooldownSeconds);

                if (cooldownSeconds > 0 && remaining > 0) {
                    killer.sendMessage(ChatColor.YELLOW + "You killed " + victim.getName() + " again too soon - no reward ("
                            + remaining + "s cooldown left on this target).");
                } else {
                    killerTeam.addScore(enemyKillScore);
                    killerData.addPersonalScore(enemyKillScore);
                    killerData.addBounty(bountyPerKill);
                    plugin.getTeamManager().saveTeam(killerTeam);
                    lastRewardedKill.put(cooldownKey, System.currentTimeMillis());

                    killer.sendMessage(ChatColor.GREEN + "+" + enemyKillScore + " team score, +" + bountyPerKill
                            + " bounty for eliminating " + ChatColor.GOLD + victim.getName());
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + victim.getName() + ChatColor.GRAY + " was eliminated by "
                            + ChatColor.GOLD + killer.getName() + ChatColor.GRAY + " ("
                            + plugin.getTeamManager().colorizedPrefix(killerTeam) + ChatColor.GRAY + ")");
                }
            }
        }
        // Civilian killer: no team to credit/penalize, so no score changes either way.

        plugin.getPlayerDataManager().save(killerData);
    }

    /** Returns how many cooldown seconds remain for this killer/victim pair (0 if none, or cooldown disabled). */
    private long secondsRemaining(String cooldownKey, long cooldownSeconds) {
        if (cooldownSeconds <= 0) return 0;
        Long last = lastRewardedKill.get(cooldownKey);
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        return Math.max(0, cooldownSeconds - elapsed);
    }
}

