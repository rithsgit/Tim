package com.example.addon.utils;

public final class WaypearlDropRotation {
    private static final long RETRY_INTERVAL_MS = 1000;
    private long nextAttemptMs;
    private long generation;
    private boolean completed;

    public boolean ready(long now, long deadline) {
        return !completed && now < deadline && now >= nextAttemptMs;
    }

    public long begin(long now) {
        nextAttemptMs = now + RETRY_INTERVAL_MS;
        return ++generation;
    }

    public boolean accept(long attempt, long now, long deadline) {
        return !completed && attempt == generation && now < deadline;
    }

    public void complete() {
        completed = true;
    }
}
