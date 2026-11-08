package com.example.addon.utils;

import java.util.Set;
import java.util.UUID;

public final class WaypearlFollowupPolicy {
    private WaypearlFollowupPolicy() {}

    public static boolean pullConfirmed(boolean correctWorld, boolean chunkLoaded, boolean pearlPresent,
                                        boolean requesterPresent, double requesterDistanceSquared) {
        return correctWorld && chunkLoaded && !pearlPresent && requesterPresent && requesterDistanceSquared <= 144;
    }

    public static boolean isReplacement(boolean correctWorld, boolean ownerMatches, UUID pearl,
                                        Set<UUID> existing, boolean trapdoorValid, boolean pearlPresent) {
        return correctWorld && ownerMatches && !existing.contains(pearl) && trapdoorValid && pearlPresent;
    }

    public static boolean canDrop(boolean correctWorld, boolean requesterPresent, double distanceSquared,
                                  boolean playerInventoryOpen) {
        return correctWorld && requesterPresent && distanceSquared <= 9 && playerInventoryOpen;
    }

    public static boolean reminderDue(boolean pending, long now, long deadline) {
        return pending && now >= deadline;
    }
}
