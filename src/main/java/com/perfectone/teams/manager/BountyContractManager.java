package com.perfectone.teams.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active "bounty hunter" contracts started with /bounty hunt <player>.
 * A player can have at most one active contract at a time. If they land the
 * killing blow on their contracted target before it expires, PlayerDeathListener
 * awards a bonus and clears the contract.
 */
public class BountyContractManager {

    public static class Contract {
        public final UUID targetId;
        public final String targetName;
        public final long expiresAt;

        public Contract(UUID targetId, String targetName, long expiresAt) {
            this.targetId = targetId;
            this.targetName = targetName;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<UUID, Contract> contracts = new ConcurrentHashMap<>();

    public void issue(UUID hunterId, UUID targetId, String targetName, long durationSeconds) {
        long expiresAt = durationSeconds > 0 ? System.currentTimeMillis() + (durationSeconds * 1000L) : Long.MAX_VALUE;
        this.contracts.put(hunterId, new Contract(targetId, targetName, expiresAt));
    }

    public Contract get(UUID hunterId) {
        Contract contract = this.contracts.get(hunterId);
        if (contract == null) {
            return null;
        }
        if (System.currentTimeMillis() > contract.expiresAt) {
            this.contracts.remove(hunterId);
            return null;
        }
        return contract;
    }

    public boolean hasContractOn(UUID hunterId, UUID targetId) {
        Contract contract = this.get(hunterId);
        return contract != null && contract.targetId.equals(targetId);
    }

    public void clear(UUID hunterId) {
        this.contracts.remove(hunterId);
    }
}
