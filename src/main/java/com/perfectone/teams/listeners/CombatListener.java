package com.perfectone.teams.listeners;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Combat-tags players while they're mid-PvP-fight and punishes them for logging out
 * while tagged (a "combat log"). Only players who are in a team get tagged - civilians
 * are free to disconnect mid-fight with no consequence.
 */
public class CombatListener implements Listener {

    private final TeamsPlugin plugin;

    // Tracks recent attackers of each victim (most-recent-last) so a death can be
    // credited with an "assist" for whoever else was hitting the victim recently,
    // separate from the CombatTagManager (which only tracks teamed players).
    private static final long ASSIST_WINDOW_MILLIS = 30_000L;
    private final Map<UUID, LinkedHashMap<UUID, Long>> recentDamagers = new ConcurrentHashMap<>();

    public CombatListener(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = this.resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        this.recordDamager(victim.getUniqueId(), attacker.getUniqueId());

        if (!this.plugin.getConfig().getBoolean("combat-log.enabled", true)) {
            return;
        }

        long durationSeconds = this.plugin.getConfig().getLong("combat-log.tag-duration-seconds", 15L);
        PlayerData victimData = this.plugin.getPlayerDataManager().getOrCreate(victim);
        PlayerData attackerData = this.plugin.getPlayerDataManager().getOrCreate(attacker);

        // Only players who are in a team get combat-tagged - civilians can log freely.
        if (!victimData.isCivilian()) {
            this.plugin.getCombatTagManager().tag(victim.getUniqueId(), attacker.getUniqueId(), durationSeconds);
        }
        if (!attackerData.isCivilian()) {
            this.plugin.getCombatTagManager().tag(attacker.getUniqueId(), victim.getUniqueId(), durationSeconds);
        }
    }

    private void recordDamager(UUID victimId, UUID attackerId) {
        LinkedHashMap<UUID, Long> map = this.recentDamagers.computeIfAbsent(victimId, k -> new LinkedHashMap<>());
        synchronized (map) {
            map.remove(attackerId);
            map.put(attackerId, System.currentTimeMillis());
            long cutoff = System.currentTimeMillis() - ASSIST_WINDOW_MILLIS;
            map.values().removeIf(t -> t < cutoff);
        }
    }

    /**
     * Returns the most recent player (other than the killer) who damaged the victim
     * within the assist window, or null if nobody qualifies. Called right after a
     * death is resolved, so the killer is already known and excluded.
     */
    public UUID getAssister(UUID victimId, UUID killerId) {
        LinkedHashMap<UUID, Long> map = this.recentDamagers.get(victimId);
        if (map == null) {
            return null;
        }
        long cutoff = System.currentTimeMillis() - ASSIST_WINDOW_MILLIS;
        synchronized (map) {
            UUID best = null;
            long bestTime = -1L;
            for (Map.Entry<UUID, Long> entry : map.entrySet()) {
                if (entry.getKey().equals(killerId)) continue;
                if (entry.getValue() < cutoff) continue;
                if (entry.getValue() > bestTime) {
                    bestTime = entry.getValue();
                    best = entry.getKey();
                }
            }
            return best;
        }
    }

    public void clearDamagers(UUID victimId) {
        this.recentDamagers.remove(victimId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!this.plugin.getCombatTagManager().isTagged(player.getUniqueId())) {
            return;
        }

        UUID opponentId = this.plugin.getCombatTagManager().getLastOpponent(player.getUniqueId());
        Player killer = opponentId != null ? Bukkit.getPlayer(opponentId) : null;
        this.plugin.getCombatTagManager().clear(player.getUniqueId());

        Bukkit.broadcastMessage(ChatColor.DARK_RED + player.getName() + ChatColor.GRAY
                + " combat logged and was struck down for it!");

        // Applies the same death penalties (and PvP kill reward to the opponent, if still
        // online) as a normal death - see PlayerDeathListener.resolveDeath.
        this.plugin.getPlayerDeathListener().resolveDeath(player, killer);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }
}
