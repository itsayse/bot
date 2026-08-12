package com.example.freeze;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central store of who is currently frozen, why, and where they were
 * standing when frozen (used to snap them back if they try to move).
 */
public class FreezeManager {

    private final Set<UUID> frozen = new HashSet<>();
    private final Map<UUID, String> reasons = new HashMap<>();
    private final Map<UUID, Location> anchorLocations = new HashMap<>();

    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }

    public void freeze(UUID uuid, Location anchor, String reason) {
        frozen.add(uuid);
        anchorLocations.put(uuid, anchor.clone());
        reasons.put(uuid, reason == null || reason.isBlank() ? "No reason given" : reason);
    }

    public void unfreeze(UUID uuid) {
        frozen.remove(uuid);
        anchorLocations.remove(uuid);
        reasons.remove(uuid);
    }

    public Location getAnchor(UUID uuid) {
        return anchorLocations.get(uuid);
    }

    public String getReason(UUID uuid) {
        return reasons.getOrDefault(uuid, "No reason given");
    }
}
