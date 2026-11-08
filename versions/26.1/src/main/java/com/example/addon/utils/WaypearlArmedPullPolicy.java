package com.example.addon.utils;

public final class WaypearlArmedPullPolicy {
    public enum Decision { CANCEL, WAIT, PULL }

    private WaypearlArmedPullPolicy() {}

    public static Decision evaluate(boolean connected, boolean whitelisted, boolean enabled,
                                    boolean sameWorld, boolean online, boolean idle) {
        if (!connected || !whitelisted || !enabled || !sameWorld) return Decision.CANCEL;
        if (!online || !idle) return Decision.WAIT;
        return Decision.PULL;
    }
}
