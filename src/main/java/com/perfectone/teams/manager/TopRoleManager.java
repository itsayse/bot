package com.perfectone.teams.manager;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks and assigns the #1 bounty-hunter and #1 team-player Vault permission groups.
 * Refreshed automatically after every kill. Both roles are configurable in config.yml
 * under {@code top-roles}. If Vault is not installed or the group name is blank,
 * the feature silently does nothing.
 */
public class TopRoleManager {

    private final TeamsPlugin plugin;
    private Permission vaultPerms;

    // Last known holders so we can strip the role when someone overtakes them.
    private UUID currentTopBountyHunter;
    private String currentTopTeamKey;

    public TopRoleManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.setupVault();
    }

    // ---- Setup ----------------------------------------------------------------

    private void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Permission> rsp =
                Bukkit.getServicesManager().getRegistration(Permission.class);
        if (rsp != null) {
            this.vaultPerms = rsp.getProvider();
            if (this.vaultPerms != null) {
                this.plugin.getLogger().info("Vault found - top-roles are active.");
            }
        }
    }

    public boolean isAvailable() {
        return this.vaultPerms != null;
    }

    // ---- Public API -----------------------------------------------------------

    /**
     * Re-evaluate who holds the top-bounty-hunter and top-team-player roles,
     * stripping them from the old holders and awarding them to the new holders.
     * This is called asynchronously after every kill to avoid blocking the main thread.
     */
    public void refresh() {
        if (this.vaultPerms == null) {
            return;
        }
        this.refreshTopBountyHunter();
        this.refreshTopTeamPlayer();
    }

    // ---- Private helpers ------------------------------------------------------

    private void refreshTopBountyHunter() {
        String group = this.plugin.getConfig().getString("top-roles.top-bounty-hunter-group", "").trim();
        if (group.isEmpty()) {
            return;
        }

        Optional<PlayerData> topOpt = this.plugin.getPlayerDataManager().all().values().stream()
                .filter(p -> p.getBounty() > 0)
                .max(Comparator.comparingDouble(PlayerData::getBounty));

        UUID newTop = topOpt.map(PlayerData::getUuid).orElse(null);

        if (same(this.currentTopBountyHunter, newTop)) {
            return; // No change.
        }

        // Strip old holder.
        if (this.currentTopBountyHunter != null) {
            OfflinePlayer old = Bukkit.getOfflinePlayer(this.currentTopBountyHunter);
            this.vaultPerms.playerRemoveGroup(null, old, group);
        }

        // Award new holder.
        if (newTop != null) {
            OfflinePlayer neo = Bukkit.getOfflinePlayer(newTop);
            this.vaultPerms.playerAddGroup(null, neo, group);
        }

        this.currentTopBountyHunter = newTop;
    }

    private void refreshTopTeamPlayer() {
        String group = this.plugin.getConfig().getString("top-roles.top-team-player-group", "").trim();
        if (group.isEmpty()) {
            return;
        }

        Optional<TeamData> topOpt = this.plugin.getTeamManager().all().values().stream()
                .max(Comparator.comparingDouble(TeamData::getScore));

        String newTopKey = topOpt.map(TeamData::getKey).orElse(null);

        if (same(this.currentTopTeamKey, newTopKey)) {
            return; // No change.
        }

        // Strip role from old team's members.
        if (this.currentTopTeamKey != null) {
            final String oldKey = this.currentTopTeamKey;
            this.plugin.getPlayerDataManager().all().values().stream()
                    .filter(p -> oldKey.equalsIgnoreCase(p.getTeamKey()))
                    .forEach(p -> this.vaultPerms.playerRemoveGroup(null,
                            Bukkit.getOfflinePlayer(p.getUuid()), group));
        }

        // Award role to new team's members.
        if (newTopKey != null) {
            final String nKey = newTopKey;
            this.plugin.getPlayerDataManager().all().values().stream()
                    .filter(p -> nKey.equalsIgnoreCase(p.getTeamKey()))
                    .forEach(p -> this.vaultPerms.playerAddGroup(null,
                            Bukkit.getOfflinePlayer(p.getUuid()), group));
        }

        this.currentTopTeamKey = newTopKey;
    }

    // ---- Utilities ------------------------------------------------------------

    private static boolean same(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }
}
