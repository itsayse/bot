/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  org.bukkit.Bukkit
 *  org.bukkit.plugin.Plugin
 */
package com.perfectone.teams.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class DiscordBridge {
    private final TeamsPlugin plugin;
    private final HttpClient httpClient;

    public DiscordBridge(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
    }

    public boolean isEnabled() {
        return this.plugin.getConfig().getBoolean("discord-bridge.enabled", false);
    }

    public void pushSnapshotAsync() {
        if (!this.isEnabled()) {
            return;
        }
        String json = this.buildSnapshotJson();
        String baseUrl = this.plugin.getConfig().getString("discord-bridge.api-base-url", "http://127.0.0.1:8787");
        String secret = this.plugin.getConfig().getString("discord-bridge.api-secret", "");
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/teams/sync")).header("Content-Type", "application/json").header("X-API-Key", secret).timeout(Duration.ofSeconds(5L)).POST(HttpRequest.BodyPublishers.ofString(json)).build();
                HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    this.plugin.getLogger().warning("Discord bridge sync failed (HTTP " + response.statusCode() + "): " + response.body());
                }
            }
            catch (Exception e) {
                this.plugin.getLogger().warning("Discord bridge sync failed: " + e.getMessage());
            }
        });
    }

    private String buildSnapshotJson() {
        JsonObject root = new JsonObject();
        JsonArray teamsArray = new JsonArray();
        for (Map.Entry<String, TeamData> entry : this.plugin.getTeamManager().all().entrySet()) {
            TeamData t = entry.getValue();
            long memberCount = this.plugin.getPlayerDataManager().all().values().stream().filter(p -> t.getKey().equalsIgnoreCase(p.getTeamKey())).count();
            JsonObject teamObj = new JsonObject();
            teamObj.addProperty("name", t.getDisplayName());
            teamObj.addProperty("color", t.getPrefixColor());
            teamObj.addProperty("score", (Number)t.getScore());
            teamObj.addProperty("member_count", (Number)memberCount);
            teamsArray.add((JsonElement)teamObj);
        }
        root.add("teams", (JsonElement)teamsArray);
        JsonArray playersArray = new JsonArray();
        for (PlayerData p2 : this.plugin.getPlayerDataManager().all().values()) {
            JsonObject playerObj = new JsonObject();
            playerObj.addProperty("name", p2.getUsername());
            playerObj.addProperty("team", p2.isCivilian() ? null : this.displayNameFor(p2.getTeamKey()));
            playerObj.addProperty("bounty", (Number)p2.getBounty());
            playerObj.addProperty("kills", (Number)p2.getKills());
            playerObj.addProperty("deaths", (Number)p2.getDeaths());
            playerObj.addProperty("personal_score", (Number)p2.getPersonalScore());
            playersArray.add((JsonElement)playerObj);
        }
        root.add("players", (JsonElement)playersArray);
        return root.toString();
    }

    private String displayNameFor(String teamKey) {
        return this.plugin.getTeamManager().getTeam(teamKey).map(TeamData::getDisplayName).orElse(teamKey);
    }
}

