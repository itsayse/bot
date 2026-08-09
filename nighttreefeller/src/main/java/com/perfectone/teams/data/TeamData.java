package com.perfectone.teams.data;

import java.util.UUID;

/**
 * Plain data holder for a team row.
 */
public class TeamData {

    public enum JoinMode { OPEN, INVITE }

    private final String key;          // lowercase, unique lookup key
    private String displayName;        // original-case name shown to players
    private String prefixColor;        // Bukkit ChatColor name, e.g. "GOLD"
    private UUID owner;
    private double score;
    private long createdAt;
    private JoinMode joinMode;

    public TeamData(String key, String displayName, String prefixColor, UUID owner, double score,
                     long createdAt, JoinMode joinMode) {
        this.key = key;
        this.displayName = displayName;
        this.prefixColor = prefixColor;
        this.owner = owner;
        this.score = score;
        this.createdAt = createdAt;
        this.joinMode = joinMode == null ? JoinMode.OPEN : joinMode;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPrefixColor() {
        return prefixColor;
    }

    public void setPrefixColor(String prefixColor) {
        this.prefixColor = prefixColor;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public void addScore(double delta) {
        this.score += delta;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public JoinMode getJoinMode() {
        return joinMode;
    }

    public void setJoinMode(JoinMode joinMode) {
        this.joinMode = joinMode == null ? JoinMode.OPEN : joinMode;
    }

    public boolean isInviteOnly() {
        return joinMode == JoinMode.INVITE;
    }
}

