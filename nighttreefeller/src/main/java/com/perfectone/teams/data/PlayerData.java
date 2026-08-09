package com.perfectone.teams.data;

import java.util.UUID;

/**
 * Plain data holder for a player's row (their team membership + stats).
 */
public class PlayerData {

    private final UUID uuid;
    private String username;
    private String teamKey;     // null/blank => civilian
    private double personalScore;
    private int kills;
    private int deaths;
    private double bounty;

    public PlayerData(UUID uuid, String username, String teamKey, double personalScore,
                       int kills, int deaths, double bounty) {
        this.uuid = uuid;
        this.username = username;
        this.teamKey = teamKey;
        this.personalScore = personalScore;
        this.kills = kills;
        this.deaths = deaths;
        this.bounty = bounty;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTeamKey() {
        return teamKey;
    }

    public void setTeamKey(String teamKey) {
        this.teamKey = teamKey;
    }

    public boolean isCivilian() {
        return teamKey == null || teamKey.isBlank();
    }

    public double getPersonalScore() {
        return personalScore;
    }

    public void setPersonalScore(double personalScore) {
        this.personalScore = personalScore;
    }

    public void addPersonalScore(double delta) {
        this.personalScore += delta;
    }

    public int getKills() {
        return kills;
    }

    public void incrementKills() {
        this.kills++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void incrementDeaths() {
        this.deaths++;
    }

    public double getBounty() {
        return bounty;
    }

    public void addBounty(double delta) {
        this.bounty += delta;
    }
}
