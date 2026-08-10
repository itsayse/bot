/*
 * Decompiled with CFR 0.152.
 */
package com.perfectone.teams.data;

import java.util.UUID;

public class TeamData {
    private final String key;
    private String displayName;
    private String prefixColor;
    private UUID owner;
    private double score;
    private long createdAt;
    private JoinMode joinMode;

    public TeamData(String key, String displayName, String prefixColor, UUID owner, double score, long createdAt, JoinMode joinMode) {
        this.key = key;
        this.displayName = displayName;
        this.prefixColor = prefixColor;
        this.owner = owner;
        this.score = score;
        this.createdAt = createdAt;
        this.joinMode = joinMode == null ? JoinMode.OPEN : joinMode;
    }

    public String getKey() {
        return this.key;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPrefixColor() {
        return this.prefixColor;
    }

    public void setPrefixColor(String prefixColor) {
        this.prefixColor = prefixColor;
    }

    public UUID getOwner() {
        return this.owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public double getScore() {
        return this.score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public void addScore(double delta) {
        this.score += delta;
    }

    public long getCreatedAt() {
        return this.createdAt;
    }

    public JoinMode getJoinMode() {
        return this.joinMode;
    }

    public void setJoinMode(JoinMode joinMode) {
        this.joinMode = joinMode == null ? JoinMode.OPEN : joinMode;
    }

    public boolean isInviteOnly() {
        return this.joinMode == JoinMode.INVITE;
    }

    public static enum JoinMode {
        OPEN,
        INVITE;

    }
}

