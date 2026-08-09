package com.perfectone.teams.integration;

import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Exposes team data as PlaceholderAPI placeholders, so tab-list / nametag /
 * chat plugins that don't read vanilla scoreboard teams (most don't - they
 * render tab lists themselves) can still show PerfectTeams data by adding
 * these placeholders to their own format config.
 *
 * Only loaded if PlaceholderAPI is installed (see TeamsPlugin#onEnable).
 *
 * Placeholders:
 *   %perfectteams_prefix%   - colored "[TeamName]" prefix, or the civilian tag, with color codes translated
 *   %perfectteams_team%     - team display name, or "Civilian"
 *   %perfectteams_color%    - the team's raw color name (e.g. "GOLD"), blank for civilians
 *   %perfectteams_score%    - the player's team's total score, blank for civilians
 *   %perfectteams_bounty%   - the player's own bounty
 *   %perfectteams_kills%    - the player's own kill count
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final TeamsPlugin plugin;

    public PlaceholderHook(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "perfectteams";
    }

    @Override
    public @NotNull String getAuthor() {
        return "PerfectOne";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // stay registered across /papi reload
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId(),
                player.getName() == null ? "Unknown" : player.getName());

        Optional<TeamData> teamOpt = data.isCivilian()
                ? Optional.empty()
                : plugin.getTeamManager().getTeam(data.getTeamKey());

        return switch (params.toLowerCase()) {
            case "prefix" -> teamOpt.map(t -> ChatColor.translateAlternateColorCodes('&',
                            plugin.getTeamManager().colorizedPrefix(t)))
                    .orElseGet(() -> ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("chat.civilian-prefix", "&7[Civilian]&r ")).trim());
            case "team" -> teamOpt.map(TeamData::getDisplayName).orElse("Civilian");
            case "color" -> teamOpt.map(TeamData::getPrefixColor).orElse("");
            case "score" -> teamOpt.map(t -> String.valueOf(t.getScore())).orElse("");
            case "bounty" -> String.valueOf(data.getBounty());
            case "kills" -> String.valueOf(data.getKills());
            default -> null; // unknown placeholder
        };
    }
}
