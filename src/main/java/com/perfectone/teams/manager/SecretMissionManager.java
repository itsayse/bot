package com.perfectone.teams.manager;

import com.perfectone.teams.TeamsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Occasionally hands a random online player a hidden objective: eliminate a specific
 * other online player — but ONLY if that target currently has bounty (otherwise the
 * mission has no meaningful reward context). Only the player who received the mission
 * knows about it. If they land the kill before it expires, they get a bonus.
 *
 * Duration is capped at 86 400 seconds (1 day) regardless of config.
 */
public class SecretMissionManager {

    private static final long MAX_DURATION_SECONDS = 86_400L; // 1 day

    public static class Mission {
        public final UUID targetId;
        public final String targetName;
        public final long expiresAt;

        public Mission(UUID targetId, String targetName, long expiresAt) {
            this.targetId = targetId;
            this.targetName = targetName;
            this.expiresAt = expiresAt;
        }
    }

    private final TeamsPlugin plugin;
    private final Map<UUID, Mission> activeMissions = new ConcurrentHashMap<>();
    private BukkitTask task;

    public SecretMissionManager(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!this.plugin.getConfig().getBoolean("secret-missions.enabled", true)) {
            return;
        }
        long intervalTicks = this.plugin.getConfig().getLong("secret-missions.interval-seconds", 900L) * 20L;
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tryAssignOne,
                intervalTicks, intervalTicks);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    private void tryAssignOne() {
        double chance = this.plugin.getConfig().getDouble("secret-missions.chance", 0.5);
        if (ThreadLocalRandom.current().nextDouble() > chance) {
            return;
        }

        // Collect candidates: online players who don't already have an active mission.
        List<Player> candidates = new ArrayList<>(Bukkit.getOnlinePlayers());
        candidates.removeIf(p -> this.activeMissions.containsKey(p.getUniqueId()));
        if (candidates.size() < 2) {
            return;
        }

        // Shuffle for a random agent.
        Player agent = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        // Collect valid targets: must be online, must NOT be the agent, and MUST have bounty > 0.
        List<Player> possibleTargets = new ArrayList<>(Bukkit.getOnlinePlayers());
        possibleTargets.removeIf(p -> p.getUniqueId().equals(agent.getUniqueId()));
        possibleTargets.removeIf(p -> {
            var data = this.plugin.getPlayerDataManager().getOrCreate(p);
            return data.getBounty() <= 0.0;
        });

        if (possibleTargets.isEmpty()) {
            return; // No valid bounty targets online right now.
        }

        Player target = possibleTargets.get(ThreadLocalRandom.current().nextInt(possibleTargets.size()));

        long configDuration = this.plugin.getConfig().getLong("secret-missions.duration-seconds", MAX_DURATION_SECONDS);
        long durationSeconds = Math.min(configDuration, MAX_DURATION_SECONDS);
        long expiresAt = System.currentTimeMillis() + (durationSeconds * 1_000L);

        this.activeMissions.put(agent.getUniqueId(), new Mission(target.getUniqueId(), target.getName(), expiresAt));

        long displayMinutes = durationSeconds / 60;
        String timeDisplay = displayMinutes >= 1440
                ? "1 day"
                : displayMinutes >= 60
                        ? (displayMinutes / 60) + " hour(s)"
                        : displayMinutes + " minute(s)";

        double targetBounty = this.plugin.getPlayerDataManager().getOrCreate(target).getBounty();

        agent.sendMessage(ChatColor.DARK_PURPLE + "✦ SECRET MISSION ✦");
        agent.sendMessage(ChatColor.LIGHT_PURPLE + "Eliminate " + ChatColor.WHITE + target.getName()
                + ChatColor.LIGHT_PURPLE + " (bounty: " + ChatColor.GOLD + "$" + (long) targetBounty
                + ChatColor.LIGHT_PURPLE + ") within " + timeDisplay + " for a bonus. Tell no one.");
    }

    public Mission get(UUID agentId) {
        Mission mission = this.activeMissions.get(agentId);
        if (mission == null) {
            return null;
        }
        if (System.currentTimeMillis() > mission.expiresAt) {
            this.activeMissions.remove(agentId);
            return null;
        }
        return mission;
    }

    public boolean isMissionTarget(UUID agentId, UUID victimId) {
        Mission mission = this.get(agentId);
        return mission != null && mission.targetId.equals(victimId);
    }

    public void complete(UUID agentId) {
        this.activeMissions.remove(agentId);
    }
}
