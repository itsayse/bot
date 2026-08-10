/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  me.clip.placeholderapi.expansion.PlaceholderExpansion
 *  org.bukkit.ChatColor
 *  org.bukkit.OfflinePlayer
 *  org.jetbrains.annotations.NotNull
 */
package com.perfectone.teams.integration;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import com.perfectone.teams.util.MoneyUtil;
import java.util.Optional;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PlaceholderHook
extends PlaceholderExpansion {
    private final TeamsPlugin plugin;

    public PlaceholderHook(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @NotNull
    public String getIdentifier() {
        return "perfectteams";
    }

    @NotNull
    public String getAuthor() {
        return "PerfectOne";
    }

    @NotNull
    public String getVersion() {
        return "1.0.0";
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        PlayerData data = this.plugin.getPlayerDataManager().getOrCreate(player.getUniqueId(), player.getName() == null ? "Unknown" : player.getName());
        Optional<TeamData> teamOpt = data.isCivilian() ? Optional.empty() : this.plugin.getTeamManager().getTeam(data.getTeamKey());
        return switch (params.toLowerCase()) {
            case "prefix" -> teamOpt.map(t -> ChatColor.translateAlternateColorCodes((char)'&', (String)this.plugin.getTeamManager().colorizedPrefix((TeamData)t))).orElseGet(() -> ChatColor.translateAlternateColorCodes((char)'&', (String)this.plugin.getConfig().getString("chat.civilian-prefix", "&7[Civilian]&r ")).trim());
            case "team" -> teamOpt.map(TeamData::getDisplayName).orElse("Civilian");
            case "color" -> teamOpt.map(TeamData::getPrefixColor).orElse("");
            case "score" -> teamOpt.map(t -> String.valueOf(t.getScore())).orElse("");
            case "bounty" -> MoneyUtil.format(data.getBounty());
            case "bounty_raw" -> String.valueOf(data.getBounty());
            case "kills" -> String.valueOf(data.getKills());
            default -> null;
        };
    }
}

