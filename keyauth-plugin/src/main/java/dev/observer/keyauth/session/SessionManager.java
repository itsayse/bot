package dev.observer.keyauth.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private final Map<UUID, AuthState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> loginAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingKey = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingDiscordId = new ConcurrentHashMap<>();
    // Deadline for a KEY_LOCKED (frozen) player to fix their key with
    // /renewkey before being kicked as a last resort. Set the moment a
    // player is frozen for an expired or revoked key.
    private final Map<UUID, Long> keyGraceDeadline = new ConcurrentHashMap<>();

    public AuthState get(UUID uuid) {
        return states.getOrDefault(uuid, AuthState.AWAITING_KEY);
    }

    public void set(UUID uuid, AuthState state) {
        states.put(uuid, state);
    }

    public void remove(UUID uuid) {
        states.remove(uuid);
        loginAttempts.remove(uuid);
        pendingKey.remove(uuid);
        pendingDiscordId.remove(uuid);
        keyGraceDeadline.remove(uuid);
    }

    /** Starts a grace window (if one isn't already running) ending at the given epoch millis. */
    public void startKeyGrace(UUID uuid, long deadlineMillis) {
        keyGraceDeadline.putIfAbsent(uuid, deadlineMillis);
    }

    public boolean isInKeyGrace(UUID uuid) {
        return keyGraceDeadline.containsKey(uuid);
    }

    public boolean keyGraceExpired(UUID uuid) {
        Long deadline = keyGraceDeadline.get(uuid);
        return deadline != null && System.currentTimeMillis() > deadline;
    }

    public void clearKeyGrace(UUID uuid) {
        keyGraceDeadline.remove(uuid);
    }

    public boolean isAuthenticated(UUID uuid) {
        return get(uuid) == AuthState.AUTHENTICATED;
    }

    public int incrementLoginAttempts(UUID uuid) {
        return loginAttempts.merge(uuid, 1, Integer::sum);
    }

    public void resetLoginAttempts(UUID uuid) {
        loginAttempts.remove(uuid);
    }

    public void setPendingKey(UUID uuid, String key) {
        pendingKey.put(uuid, key);
    }

    public String getPendingKey(UUID uuid) {
        return pendingKey.get(uuid);
    }

    public void setPendingDiscordId(UUID uuid, String discordId) {
        pendingDiscordId.put(uuid, discordId);
    }

    public String getPendingDiscordId(UUID uuid) {
        return pendingDiscordId.get(uuid);
    }
}
