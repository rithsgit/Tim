package com.example.addon.modules;

import com.example.addon.Tim;
import com.example.addon.mixin.LightmapTextureManagerAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;

public class Illumood extends Module {
    public enum LightMode {
        NORMAL("Normal"),
        HALF_BRIGHT("Half-Bright"),
        FULL_BRIGHT("Fullbright"),
        NIGHT_VISION("Night Vision");

        private final String label;

        LightMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum MoodPreset {
        VANILLA("Vanilla (Off)", -1, -1, -1, -1, -1),
        VIBRANT_JUNGLE("Vibrant Jungle", 0x59C93C, 0x30BB0B, 0x14A2C5, 0x7BA4FF, 0xC0D8FF),
        CHERRY_GROVE("Cherry Grove", 0xB6DB61, 0xB6DB61, 0x5DB7EF, 0x88B1FF, 0xF2C8E1),
        PALE_GARDEN("Pale Garden", 0x778272, 0x878D76, 0x76889D, 0xB9B9B9, 0x817770),
        GOLDEN_AUTUMN("Golden Autumn", 0xD49B35, 0xC95B27, 0x3F76E4, 0x8CA4FF, 0xD9C8B4),
        ALPHA_NEON("Alpha Neon", 0x71FF33, 0x4DFF14, 0x2B55FF, 0x99CCFF, 0xBFD9FF),
        MIDNIGHT_AZURE("Midnight Azure", 0x2E6F73, 0x225560, 0x1B4B7A, 0x101829, 0x19253D),
        CUSTOM("Custom RGB", -1, -1, -1, -1, -1);

        private final String label;
        private final int grass;
        private final int foliage;
        private final int water;
        private final int sky;
        private final int fog;

        MoodPreset(String label, int grass, int foliage, int water, int sky, int fog) {
            this.label = label;
            this.grass = grass;
            this.foliage = foliage;
            this.water = water;
            this.sky = sky;
            this.fog = fog;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final SettingGroup sgLighting = settings.createGroup("Lighting");
    private final SettingGroup sgMood = settings.createGroup("Biome Mood");
    private final SettingGroup sgCleanView = settings.createGroup("Clean View & World");

    // --- 1. Lighting Settings ---
    private final Setting<LightMode> lightMode = sgLighting.add(new EnumSetting.Builder<LightMode>()
        .name("light-mode")
        .description("Choose a minimum world light level or the night vision visual effect.")
        .defaultValue(LightMode.NORMAL)
        .onChanged(value -> refreshRenderers())
        .build()
    );

    private final Setting<Double> halfBrightFloor = sgLighting.add(new DoubleSetting.Builder()
        .name("half-bright-floor")
        .description("Minimum light level used by Half-Bright, from 0% to 100%.")
        .defaultValue(0.38)
        .min(0)
        .max(1)
        .sliderMax(1)
        .visible(() -> lightMode.get() == LightMode.HALF_BRIGHT)
        .onChanged(value -> refreshRenderers())
        .build()
    );

    private final Setting<Boolean> dynamicCaveBoost = sgLighting.add(new BoolSetting.Builder()
        .name("dynamic-cave-boost")
        .description("Raises the minimum light level below Y=48.")
        .defaultValue(false)
        .onChanged(value -> refreshRenderers())
        .build()
    );

    // --- 2. Biome Mood Settings ---
    private final Setting<MoodPreset> moodPreset = sgMood.add(new EnumSetting.Builder<MoodPreset>()
        .name("mood-preset")
        .description("Overrides biome foliage, grass, water, sky, and fog colors.")
        .defaultValue(MoodPreset.VANILLA)
        .onChanged(value -> refreshRenderers())
        .build()
    );

    private final Setting<Boolean> overrideGrass = sgMood.add(new BoolSetting.Builder()
        .name("override-grass")
        .description("Apply the selected mood to grass.")
        .defaultValue(true)
        .visible(() -> moodPreset.get() != MoodPreset.VANILLA)
        .onChanged(value -> refreshRenderers())
        .build()
    );

    private final Setting<Boolean> overrideFoliage = sgMood.add(new BoolSetting.Builder()
        .name("override-foliage")
        .description("Apply the selected mood to leaves and foliage.")
        .defaultValue(true)
        .visible(() -> moodPreset.get() != MoodPreset.VANILLA)
        .onChanged(value -> refreshRenderers())
        .build()
    );

    private final Setting<Boolean> overrideWater = sgMood.add(new BoolSetting.Builder()
        .name("override-water")
        .description("Apply the selected mood to water.")
        .defaultValue(true)
        .visible(() -> moodPreset.get() != MoodPreset.VANILLA)
        .onChanged(value -> refreshRenderers())
        .build()
    );

    private final Setting<Boolean> overrideSkyAndFog = sgMood.add(new BoolSetting.Builder()
        .name("override-sky-and-fog")
        .description("Apply the selected mood to sky and fog.")
        .defaultValue(true)
        .visible(() -> moodPreset.get() != MoodPreset.VANILLA)
        .onChanged(value -> refreshRenderers())
        .build()
    );

    private final Setting<SettingColor> customGrassColor = sgMood.add(new ColorSetting.Builder()
        .name("custom-grass")
        .description("Custom grass color when Custom RGB is selected.")
        .defaultValue(color(0x59C93C))
        .visible(() -> moodPreset.get() == MoodPreset.CUSTOM && overrideGrass.get())
        .onChanged(value -> refreshRenderers())
        .build()
    );

    private final Setting<SettingColor> customFoliageColor = sgMood.add(new ColorSetting.Builder()
        .name("custom-foliage")
        .description("Custom foliage color when Custom RGB is selected.")
        .defaultValue(color(0x30BB0B))
        .visible(() -> moodPreset.get() == MoodPreset.CUSTOM && overrideFoliage.get())
        .onChanged(value -> refreshRenderers())
        .build()
    );

    private final Setting<SettingColor> customWaterColor = sgMood.add(new ColorSetting.Builder()
        .name("custom-water")
        .description("Custom water color when Custom RGB is selected.")
        .defaultValue(color(0x44AFF5))
        .visible(() -> moodPreset.get() == MoodPreset.CUSTOM && overrideWater.get())
        .onChanged(value -> refreshRenderers())
        .build()
    );

    private final Setting<SettingColor> customSkyColor = sgMood.add(new ColorSetting.Builder()
        .name("custom-sky")
        .description("Custom sky color when Custom RGB is selected.")
        .defaultValue(color(0x78A7FF))
        .visible(() -> moodPreset.get() == MoodPreset.CUSTOM && overrideSkyAndFog.get())
        .onChanged(value -> refreshRenderers())
        .build()
    );

    private final Setting<SettingColor> customFogColor = sgMood.add(new ColorSetting.Builder()
        .name("custom-fog")
        .description("Custom fog color when Custom RGB is selected.")
        .defaultValue(color(0xC0D8FF))
        .visible(() -> moodPreset.get() == MoodPreset.CUSTOM && overrideSkyAndFog.get())
        .onChanged(value -> refreshRenderers())
        .build()
    );

    // --- 3. Clean View Settings ---
    private final Setting<Boolean> noFog = sgCleanView.add(new BoolSetting.Builder().name("no-fog").defaultValue(false).build());
    private final Setting<Boolean> noClouds = sgCleanView.add(new BoolSetting.Builder().name("no-clouds").defaultValue(false).build());
    private final Setting<Boolean> noWeather = sgCleanView.add(new BoolSetting.Builder().name("no-weather").defaultValue(false).build());
    private final Setting<Boolean> cleanOverlays = sgCleanView.add(new BoolSetting.Builder().name("clean-overlays").defaultValue(false).build());
    private final Setting<Boolean> obfuscateTerrain = sgCleanView.add(new BoolSetting.Builder().name("obfuscate-terrain").defaultValue(false).onChanged(v -> refreshRenderers()).build());

    public Illumood() {
        super(Tim.CATEGORY, "illumood", "Centralized lighting, biome atmosphere, and world clarity module.");
    }

    public static Illumood get() {
        return Modules.get().get(Illumood.class);
    }

    @Override
    public void onActivate() {
        refreshRenderers();
    }

    @Override
    public void onDeactivate() {
        refreshRenderers();
    }

    // --- Lighting Helpers ---
    public float applyLightFloor(float original) {
        if (!isActive()) return original;

        float floor = switch (lightMode.get()) {
            case NORMAL, NIGHT_VISION -> 0;
            case HALF_BRIGHT -> halfBrightFloor.get().floatValue();
            case FULL_BRIGHT -> 1;
        };

        if (dynamicCaveBoost.get() && mc.player != null && mc.world != null && mc.player.getBlockY() < 48) {
            floor = Math.max(floor, 0.55f);
        }

        return Math.max(original, floor);
    }

    // --- Mixin Getters ---
    public boolean useNightVision() { return isActive() && lightMode.get() == LightMode.NIGHT_VISION; }
    public boolean shouldDisableFog() { return isActive() && noFog.get(); }
    public boolean shouldDisableClouds() { return isActive() && noClouds.get(); }
    public boolean shouldDisableWeather() { return isActive() && noWeather.get(); }
    public boolean shouldCleanOverlays() { return isActive() && cleanOverlays.get(); }
    public boolean shouldObfuscateTerrain() { return isActive() && obfuscateTerrain.get(); }

    // --- Biome Color Helpers ---
    public boolean shouldOverrideBiomeColors() {
        return isActive() && moodPreset.get() != MoodPreset.VANILLA;
    }

    public int getGrassColor(int original) {
        if (!shouldOverrideBiomeColors() || !overrideGrass.get()) return original;
        return moodPreset.get() == MoodPreset.CUSTOM ? rgb(customGrassColor.get()) : moodPreset.get().grass;
    }

    public int getFoliageColor(int original) {
        if (!shouldOverrideBiomeColors() || !overrideFoliage.get()) return original;
        return moodPreset.get() == MoodPreset.CUSTOM ? rgb(customFoliageColor.get()) : moodPreset.get().foliage;
    }

    public int getWaterColor(int original) {
        if (!shouldOverrideBiomeColors() || !overrideWater.get()) return original;
        return moodPreset.get() == MoodPreset.CUSTOM ? rgb(customWaterColor.get()) : moodPreset.get().water;
    }

    public int getSkyColor(int original) {
        if (!shouldOverrideBiomeColors() || !overrideSkyAndFog.get()) return original;
        return moodPreset.get() == MoodPreset.CUSTOM ? rgb(customSkyColor.get()) : moodPreset.get().sky;
    }

    public int getFogColor(int original) {
        if (!shouldOverrideBiomeColors() || !overrideSkyAndFog.get()) return original;
        return moodPreset.get() == MoodPreset.CUSTOM ? rgb(customFogColor.get()) : moodPreset.get().fog;
    }

    private void refreshRenderers() {
        MinecraftClient client = mc;
        if (client == null || client.world == null || client.worldRenderer == null || client.gameRenderer == null) return;
        client.execute(() -> {
            try {
                ((LightmapTextureManagerAccessor) client.gameRenderer.getLightmapTextureManager()).tim$setDirty(true);
                client.worldRenderer.reload();
            } catch (Exception ignored) {}
        });
    }

    private static SettingColor color(int rgb) {
        return new SettingColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
    }

    private static int rgb(SettingColor color) {
        return color.r << 16 | color.g << 8 | color.b;
    }
}