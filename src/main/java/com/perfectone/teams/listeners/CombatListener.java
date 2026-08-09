package com.perfectone.teams.listeners;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import java.util.UUID;
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

    public CombatListener(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!this.plugin.getConfig().getBoolean("combat-log.enabled", true)) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = this.resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
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
