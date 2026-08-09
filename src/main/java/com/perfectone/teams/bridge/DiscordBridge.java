package com.perfectone.teams.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.perfectone.teams.TeamsPlugin;
import com.perfectone.teams.data.PlayerData;
import com.perfectone.teams.data.TeamData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Pushes a JSON snapshot of team scores and player bounty to the Discord bot's
 * existing aiohttp API (the same one utils/mc_api.py already serves for the key
 * system), so /leaderboard and /bounty can be answered from Discord too.
 *
 * POST {api-base-url}/teams/sync
 * Header: X-API-Key: <api-secret>
 * Body: { "teams": [...], "players": [...] }
 */
public class DiscordBridge {

    private final TeamsPlugin plugin;
    private final HttpClient httpClient;

    public DiscordBridge(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("discord-bridge.enabled", false);
    }

    /** Builds the current snapshot and POSTs it asynchronously. Safe to call from the main thread. */
    public void pushSnapshotAsync() {
        if (!isEnabled()) return;

        String json = buildSnapshotJson();
        String baseUrl = plugin.getConfig().getString("discord-bridge.api-base-url", "http://127.0.0.1:8787");
        String secret = plugin.getConfig().getString("discord-bridge.api-secret", "");

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/teams/sync"))
                        .header("Content-Type", "application/json")
                        .header("X-API-Key", secret)
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    plugin.getLogger().warning("Discord bridge sync failed (HTTP " + response.statusCode() + "): " + response.body());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Discord bridge sync failed: " + e.getMessage());
            }
        });
    }

    private String buildSnapshotJson() {
        JsonObject root = new JsonObject();

        JsonArray teamsArray = new JsonArray();
        for (Map.Entry<String, TeamData> entry : plugin.getTeamManager().all().entrySet()) {
            TeamData t = entry.getValue();
            long memberCount = plugin.getPlayerDataManager().all().values().stream()
                    .filter(p -> t.getKey().equalsIgnoreCase(p.getTeamKey()))
                    .count();

            JsonObject teamObj = new JsonObject();
            teamObj.addProperty("name", t.getDisplayName());
            teamObj.addProperty("color", t.getPrefixColor());
            teamObj.addProperty("score", t.getScore());
            teamObj.addProperty("member_count", memberCount);
            teamsArray.add(teamObj);
        }
        root.add("teams", teamsArray);

        JsonArray playersArray = new JsonArray();
        for (PlayerData p : plugin.getPlayerDataManager().all().values()) {
            JsonObject playerObj = new JsonObject();
            playerObj.addProperty("name", p.getUsername());
            playerObj.addProperty("team", p.isCivilian() ? null : displayNameFor(p.getTeamKey()));
            playerObj.addProperty("bounty", p.getBounty());
            playerObj.addProperty("kills", p.getKills());
            playerObj.addProperty("deaths", p.getDeaths());
            playerObj.addProperty("personal_score", p.getPersonalScore());
            playersArray.add(playerObj);
        }
        root.add("players", playersArray);

        return root.toString();
    }

    private String displayNameFor(String teamKey) {
        return plugin.getTeamManager().getTeam(teamKey).map(TeamData::getDisplayName).orElse(teamKey);
    }
}
