package com.perfectone.teams.listeners;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import com.perfectone.teams.manager.BountyContractManager;
import com.perfectone.teams.manager.SecretMissionManager;
import com.perfectone.teams.util.MoneyUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDeathListener implements Listener {

    private final TeamsPlugin plugin;

    // cooldownKey ("killerUuid:victimUuid") -> tally of rewarded kills within the current window.
    private static class RepeatTally {
        int rewardedCount = 0;
        long windowStartedAt = 0L;
    }
    private final Map<String, RepeatTally> repeatKillTally = new ConcurrentHashMap<>();

    // victimUuid -> the UUID of whoever most recently killed them, for revenge tracking.
    private final Map<UUID, UUID> revengeTargets = new ConcurrentHashMap<>();

    public PlayerDeathListener(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        this.resolveDeath(event.getEntity(), event.getEntity().getKiller());
    }

    /**
     * Applies scoring/bounty/death penalties for a victim, crediting a killer if one
     * is known. Shared by natural deaths (onDeath above) and combat-log kills
     * (CombatListener), so both paths use identical scoring.
     */
    public void resolveDeath(Player victim, Player killer) {
        PlayerData victimData = this.plugin.getPlayerDataManager().getOrCreate(victim);
        double victimBountyBeforeDeath = victimData.getBounty();

        UUID assisterId = this.plugin.getCombatListener() != null
                ? this.plugin.getCombatListener().getAssister(
                        victim.getUniqueId(), killer == null ? null : killer.getUniqueId())
                : null;
        Player assister = assisterId != null ? Bukkit.getPlayer(assisterId) : null;

        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            this.handlePvpKill(killer, victim, victimData, assister, victimBountyBeforeDeath);
        }
        this.applyDeathPenalties(victim, victimData);
        this.plugin.getPlayerDataManager().save(victimData);

        if (this.plugin.getCombatListener() != null) {
            this.plugin.getCombatListener().clearDamagers(victim.getUniqueId());
        }

        // Set revenge target: the victim can now get a bonus for killing the killer.
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            this.revengeTargets.put(victim.getUniqueId(), killer.getUniqueId());
            // Broadcast the revenge target opportunity to everyone.
            Bukkit.broadcastMessage(
                    ChatColor.DARK_RED + "☠ REVENGE TARGET" + ChatColor.GRAY
                    + " Defeat " + ChatColor.GOLD + killer.getName()
                    + ChatColor.GRAY + " to receive a bonus bounty.");
        }
    }

    // ---- Death penalties ------------------------------------------------------

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

    // ---- PvP kill logic -------------------------------------------------------

    private void handlePvpKill(Player killer, Player victim, PlayerData victimData,
                                Player assister, double victimBountyBeforeDeath) {
        PlayerData killerData = this.plugin.getPlayerDataManager().getOrCreate(killer);
        Optional<TeamData> killerTeamOpt = killerData.isCivilian()
                ? Optional.empty()
                : this.plugin.getTeamManager().getTeam(killerData.getTeamKey());
        Optional<TeamData> victimTeamOpt = victimData.isCivilian()
                ? Optional.empty()
                : this.plugin.getTeamManager().getTeam(victimData.getTeamKey());

        boolean sameTeam = !killerData.isCivilian() && !victimData.isCivilian()
                && killerData.getTeamKey().equalsIgnoreCase(victimData.getTeamKey());
        boolean allied = !sameTeam && killerTeamOpt.isPresent() && victimTeamOpt.isPresent()
                && this.plugin.getTeamManager().areAllied(
                        killerTeamOpt.get().getKey(), victimTeamOpt.get().getKey());

        double enemyKillScore      = this.plugin.getConfig().getDouble("scoring.kill-enemy-team-score", 10.0);
        double civilianPenalty     = this.plugin.getConfig().getDouble("scoring.kill-civilian-penalty", 5.0);
        double bountyPerKill       = this.plugin.getConfig().getDouble("scoring.bounty-per-kill", 50.0);
        boolean ignoreFriendly     = this.plugin.getConfig().getBoolean("scoring.ignore-friendly-fire-scoring", true);
        long cooldownSeconds       = this.plugin.getConfig().getLong("scoring.same-victim-cooldown-seconds", 300L);
        double reducedMultiplier   = this.plugin.getConfig().getDouble("scoring.repeat-kill-reduced-multiplier", 0.5);
        double assistBountyShare   = this.plugin.getConfig().getDouble("scoring.assist-bounty-share", 0.25);
        double revengeBonusBounty  = this.plugin.getConfig().getDouble("scoring.revenge-bonus-bounty", 100.0);
        double bountyClaimPercent  = this.plugin.getConfig().getDouble("scoring.bounty-claim-percent", 0.5);

        // Friendly fire
        if (sameTeam && ignoreFriendly) {
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

        // Civilian kill penalty
        if (victimData.isCivilian()) {
            if (killerTeamOpt.isPresent()) {
                TeamData killerTeam = killerTeamOpt.get();
                killerTeam.addScore(-civilianPenalty);
                this.plugin.getTeamManager().saveTeam(killerTeam);
                killer.sendMessage(ChatColor.RED + "You killed a civilian! "
                        + ChatColor.GOLD + killerTeam.getDisplayName()
                        + ChatColor.RED + " lost " + civilianPenalty + " team score.");
            }
            this.plugin.getPlayerDataManager().save(killerData);
            return;
        }

        // At this point victim is in a team; need killer team for scoring
        if (killerTeamOpt.isEmpty()) {
            this.plugin.getPlayerDataManager().save(killerData);
            return;
        }

        TeamData killerTeam = killerTeamOpt.get();

        // Anti-farming multiplier
        String cooldownKey = killer.getUniqueId() + ":" + victim.getUniqueId();
        double rewardMultiplier = this.repeatKillRewardMultiplier(cooldownKey, cooldownSeconds, reducedMultiplier);

        if (rewardMultiplier <= 0.0) {
            long remaining = this.secondsRemaining(cooldownKey, cooldownSeconds);
            killer.sendMessage(ChatColor.YELLOW + "You killed " + victim.getName()
                    + " again too soon - no reward (" + remaining + "s cooldown left on this target).");
        } else {
            double scoreGain  = enemyKillScore * rewardMultiplier;
            double bountyGain = bountyPerKill  * rewardMultiplier;

            killerTeam.addScore(scoreGain);
            killerData.addPersonalScore(scoreGain);
            killerData.addBounty(bountyGain);
            this.plugin.getTeamManager().saveTeam(killerTeam);

            String reducedTag = rewardMultiplier < 1.0 ? ChatColor.GRAY + " (reduced - repeat kill)" : "";
            killer.sendMessage(ChatColor.GREEN + "+" + (int) scoreGain + " team score, +"
                    + MoneyUtil.format(bountyGain) + " bounty for eliminating "
                    + ChatColor.GOLD + victim.getName() + reducedTag);

            // Assist credit
            if (assister != null) {
                PlayerData assisterData = this.plugin.getPlayerDataManager().getOrCreate(assister);
                double assistBounty = bountyPerKill * assistBountyShare;
                assisterData.addBounty(assistBounty);
                this.plugin.getPlayerDataManager().save(assisterData);
                assister.sendMessage(ChatColor.AQUA + "Assist! +" + MoneyUtil.format(assistBounty)
                        + " bounty for helping take down " + victim.getName());
            }

            // Kill announcement (⚔ / 🤝 format)
            this.announceKill(killer, victim, killerTeam, assister);

            // Revenge bonus: did the killer just defeat their own revenge target?
            UUID killerRevengeTarget = this.revengeTargets.get(killer.getUniqueId());
            if (killerRevengeTarget != null && killerRevengeTarget.equals(victim.getUniqueId())) {
                killerData.addBounty(revengeBonusBounty);
                this.revengeTargets.remove(killer.getUniqueId());
                Bukkit.broadcastMessage(
                        ChatColor.DARK_RED + "☠ REVENGE! " + ChatColor.RESET
                        + ChatColor.GOLD + killer.getName()
                        + ChatColor.GRAY + " has defeated their revenge target "
                        + ChatColor.GOLD + victim.getName()
                        + ChatColor.GRAY + " for a bonus "
                        + ChatColor.YELLOW + "+" + MoneyUtil.format(revengeBonusBounty) + " bounty"
                        + ChatColor.GRAY + "!");
            }

            // Bounty claim: victim was carrying bounty when they died
            if (victimBountyBeforeDeath > 0.0) {
                double claimBonus = victimBountyBeforeDeath * bountyClaimPercent;
                killerData.addBounty(claimBonus);
                Bukkit.broadcastMessage(
                        ChatColor.DARK_RED + "☠ BOUNTY CLAIMED "
                        + ChatColor.GOLD + killer.getName()
                        + ChatColor.GRAY + " has defeated "
                        + ChatColor.GOLD + victim.getName()
                        + ChatColor.GRAY + "! "
                        + ChatColor.YELLOW + "+" + MoneyUtil.format(claimBonus) + " Bounty");
            }

            // Bounty hunter contract bonus
            BountyContractManager contracts = this.plugin.getBountyContractManager();
            if (contracts != null && contracts.hasContractOn(killer.getUniqueId(), victim.getUniqueId())) {
                double huntBonus = this.plugin.getConfig().getDouble("bounty-hunter.bonus-bounty", 200.0);
                killerData.addBounty(huntBonus);
                contracts.clear(killer.getUniqueId());
                killer.sendMessage(ChatColor.GOLD + "Contract complete! You hunted down "
                        + victim.getName() + " for a bonus +" + MoneyUtil.format(huntBonus) + " bounty.");
            }

            // Secret mission bonus
            SecretMissionManager missions = this.plugin.getSecretMissionManager();
            if (missions != null && missions.isMissionTarget(killer.getUniqueId(), victim.getUniqueId())) {
                double missionBonus = this.plugin.getConfig().getDouble("secret-missions.bonus-bounty", 150.0);
                killerData.addBounty(missionBonus);
                missions.complete(killer.getUniqueId());
                killer.sendMessage(ChatColor.DARK_PURPLE + "✦ Secret mission complete! ✦ +"
                        + MoneyUtil.format(missionBonus) + " bounty.");
            }
        }

        this.plugin.getPlayerDataManager().save(killerData);

        // Refresh top-roles asynchronously so we don't block the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin,
                () -> this.plugin.getTopRoleManager().refresh());
    }

    // ---- Kill announcement ----------------------------------------------------

    private void announceKill(Player killer, Player victim, TeamData killerTeam, Player assister) {
        StringBuilder msg = new StringBuilder();

        // Header: victim eliminated
        msg.append(ChatColor.DARK_RED).append(ChatColor.BOLD).append(victim.getName())
           .append(ChatColor.RESET).append(ChatColor.GRAY).append(" was eliminated!");

        // Killer line
        msg.append("\n")
           .append(ChatColor.RED).append("⚔ ")       // ⚔
           .append(ChatColor.GOLD).append(killer.getName())
           .append(ChatColor.GRAY).append(" — Kill ")  // — Kill
           .append(this.plugin.getTeamManager().colorizedPrefix(killerTeam));

        // Assist line (if any)
        if (assister != null) {
            msg.append("\n")
               .append(ChatColor.AQUA).append("🤝 ")  // 🤝
               .append(ChatColor.GOLD).append(assister.getName())
               .append(ChatColor.GRAY).append(" — Assist");   // — Assist
        }

        Bukkit.broadcastMessage(msg.toString());
    }

    // ---- Anti-farming ---------------------------------------------------------

    /**
     * Returns the reward multiplier for the current kill:
     *   1st kill in window → 1.0 (full)
     *   2nd kill in window → reducedMultiplier (partial)
     *   3rd+ kill in window → 0.0 (none)
     * The window resets after cooldownSeconds have elapsed since it started.
     */
    private double repeatKillRewardMultiplier(String cooldownKey, long cooldownSeconds,
                                               double reducedMultiplier) {
        if (cooldownSeconds <= 0L) {
            return 1.0;
        }
        long now = System.currentTimeMillis();
        RepeatTally tally = this.repeatKillTally.computeIfAbsent(cooldownKey, k -> new RepeatTally());
        synchronized (tally) {
            if (tally.windowStartedAt == 0L || now - tally.windowStartedAt > cooldownSeconds * 1000L) {
                tally.windowStartedAt = now;
                tally.rewardedCount = 0;
            }
            double multiplier;
            if (tally.rewardedCount == 0) {
                multiplier = 1.0;
            } else if (tally.rewardedCount == 1) {
                multiplier = reducedMultiplier;
            } else {
                multiplier = 0.0;
            }
            if (multiplier > 0.0) {
                tally.rewardedCount++;
            }
            return multiplier;
        }
    }

    private long secondsRemaining(String cooldownKey, long cooldownSeconds) {
        if (cooldownSeconds <= 0L) {
            return 0L;
        }
        RepeatTally tally = this.repeatKillTally.get(cooldownKey);
        if (tally == null || tally.windowStartedAt == 0L) {
            return 0L;
        }
        long elapsed = (System.currentTimeMillis() - tally.windowStartedAt) / 1000L;
        return Math.max(0L, cooldownSeconds - elapsed);
    }
}
