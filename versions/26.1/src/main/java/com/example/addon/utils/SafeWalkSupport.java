package com.example.addon.utils;

public final class SafeWalkSupport {
    private SafeWalkSupport() {
    }

    public static boolean pausedForTweakerPath(boolean surfacePathActive, boolean enderChestPathActive) {
        return surfacePathActive || enderChestPathActive;
    }

}
