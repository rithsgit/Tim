package com.example.addon.utils;

public final class EnderChestWaterRecovery {
    public enum Phase { Routing, Stopping, Recovering, Resuming }
    private Phase phase = Phase.Routing;
    private int recoveries;
    private int ticks;

    public Phase phase() {
        return phase;
    }

    public boolean interrupt() {
        if (phase != Phase.Routing || recoveries >= 2) return false;
        recoveries++;
        phase = Phase.Stopping;
        ticks = 0;
        return true;
    }

    public void recovering() {
        phase = Phase.Recovering;
        ticks = 0;
    }

    public void resuming() {
        phase = Phase.Resuming;
        ticks = 0;
    }

    public void resumed() {
        phase = Phase.Routing;
        ticks = 0;
    }

    public boolean timedOut(int limit) {
        return ++ticks > limit;
    }
}
