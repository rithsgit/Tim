package com.example.addon.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of entity IDs that should render with a glow outline.
 *
 * Each entry stores a packed ARGB color so callers (Mobanom, Illushine, etc.)
 * can register different colors per entity without knowing about each other.
 *
 * Populated by modules; read by EntityGlowingMixin and
 * IllushineRenderDispatcherMixin (or whichever mixin resolves outline color).
 */
public final class GlowingRegistry {
    private GlowingRegistry() {}

    /** entity ID → packed ARGB color */
    private static final Map<Integer, Integer> GLOWING_IDS = new ConcurrentHashMap<>();

    /** Default opaque white — used when a caller registers an ID without a color. */
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;

    // ── Registration ──────────────────────────────────────────────────────────

    /** Register an entity with a specific packed ARGB color. */
    public static void add(int entityId, int argbColor) {
        GLOWING_IDS.put(entityId, argbColor);
    }

    /** Register an entity with the default white outline (backwards-compatible). */
    public static void add(int entityId) {
        GLOWING_IDS.put(entityId, DEFAULT_COLOR);
    }

    public static void remove(int entityId) {
        GLOWING_IDS.remove(entityId);
    }

    public static void clear() {
        GLOWING_IDS.clear();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public static boolean isGlowing(int entityId) {
        return GLOWING_IDS.containsKey(entityId);
    }

    /**
     * Returns the packed ARGB color for this entity, or DEFAULT_COLOR if not
     * registered. Always call isGlowing() first if you only want to act on
     * registered entities.
     */
    public static int getColor(int entityId) {
        return GLOWING_IDS.getOrDefault(entityId, DEFAULT_COLOR);
    }
}