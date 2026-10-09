package com.example.addon.modules;

import com.example.addon.Tim;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class Timethrottle extends Module {

    public static final double NORMAL_SPEED = 1.0;
    private static final int GRACE_PERIOD_TICKS = 80;
    private static final int TICKS_PER_SECOND = 20;

    public enum FocusMode {
        CHUNKS("Chunks"),
        PING("Ping"),
        TPS("TPS"),
        BALANCED("Balanced");

        private final String title;
        FocusMode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum SafetyReason {
        NONE("None"),
        HURT("Damage Sustained"),
        TARGETED("Incoming Attack"),
        HOSTILE_NEARBY("Hostile In Proximity"),
        PLAYER_NEARBY("Player In Proximity"),
        ATTACKING("Weapon Swing"),
        HAZARD("Environmental Hazard / Falling");

        private final String title;
        SafetyReason(String title) { this.title = title; }
        public String getTitle() { return title; }
    }

    // ── Setting Groups ──────────────────────────────────────────────────────────

    private final SettingGroup sgGeneral      = settings.getDefaultGroup();
    private final SettingGroup sgChunkLoading = settings.createGroup("Chunk Throttling");
    private final SettingGroup sgPing         = settings.createGroup("Ping Throttling");
    private final SettingGroup sgTps          = settings.createGroup("TPS Throttling");
    private final SettingGroup sgSafety       = settings.createGroup("Safety & Combat");
    private final SettingGroup sgChunkBoost   = settings.createGroup("Chunk Pipeline");
    private final SettingGroup sgFps          = settings.createGroup("Client Performance");

    // ── General Settings ────────────────────────────────────────────────────────

    private final Setting<FocusMode> focusMode = sgGeneral.add(new EnumSetting.Builder<FocusMode>()
        .name("focus-mode")
        .description("Selects which operational vector drives time regulation.")
        .defaultValue(FocusMode.CHUNKS)
        .build()
    );

    private final Setting<Double> slowDownSmoothing = sgGeneral.add(new DoubleSetting.Builder()
        .name("slow-down-smoothing")
        .description("Easing coefficient when entering throttle states.")
        .defaultValue(0.12).min(0.0).max(0.95).sliderMax(0.5)
        .build()
    );

    private final Setting<Double> speedUpSmoothing = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-up-smoothing")
        .description("Easing coefficient when recovering to standard speed.")
        .defaultValue(0.35).min(0.0).max(0.95).sliderMax(0.5)
        .build()
    );

    private final Setting<Double> absoluteMinSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("absolute-min-speed")
        .description("Hard floor multiplier that game speed will never cross.")
        .defaultValue(0.20).min(0.05).max(0.80).sliderMax(0.5)
        .build()
    );

    // ── Dynamic UI Visibility Predicates ────────────────────────────────────────

    private boolean isChunkVisible() {
        return focusMode.get() == FocusMode.CHUNKS || focusMode.get() == FocusMode.BALANCED;
    }

    private boolean isPingVisible() {
        return focusMode.get() == FocusMode.PING || focusMode.get() == FocusMode.BALANCED;
    }

    private boolean isTpsVisible() {
        return focusMode.get() == FocusMode.TPS || focusMode.get() == FocusMode.BALANCED;
    }

    // ── Chunk Loading Settings ──────────────────────────────────────────────────

    private final Setting<Double> chunkLoadSlowdown = sgChunkLoading.add(new DoubleSetting.Builder()
        .name("chunk-min-speed")
        .description("Speed limit applied under heavy missing chunk loads.")
        .defaultValue(0.60).min(0.10).max(1.0).sliderMax(1.0)
        .visible(this::isChunkVisible)
        .build()
    );

    private final Setting<Boolean> directionalLookahead = sgChunkLoading.add(new BoolSetting.Builder()
        .name("velocity-lookahead")
        .description("Weights missing chunks in your travel direction higher than chunks behind you.")
        .defaultValue(true)
        .visible(this::isChunkVisible)
        .build()
    );

    private final Setting<Boolean> pingAdaptiveChunks = sgChunkLoading.add(new BoolSetting.Builder()
        .name("ping-adaptive-scaling")
        .description("Dynamically shifts chunk thresholds down under elevated latency.")
        .defaultValue(true)
        .visible(this::isChunkVisible)
        .build()
    );

    private final Setting<Integer> chunkOverloadThreshold = sgChunkLoading.add(new IntSetting.Builder()
        .name("overload-threshold")
        .description("Unloaded chunk count that flags an active pipeline overload.")
        .defaultValue(70).min(10).sliderMax(300)
        .visible(this::isChunkVisible)
        .build()
    );

    private final Setting<Integer> chunkOverloadTimeout = sgChunkLoading.add(new IntSetting.Builder()
        .name("overload-timeout-sec")
        .description("Duration in seconds an overload state can persist before tripping abort.")
        .defaultValue(10).min(2).sliderMax(60)
        .visible(this::isChunkVisible)
        .build()
    );

    private final Setting<Integer> giveUpCooldown = sgChunkLoading.add(new IntSetting.Builder()
        .name("recovery-cooldown-sec")
        .description("Forced duration at 1.0x baseline speed following an overload abort.")
        .defaultValue(4).min(1).sliderMax(30)
        .visible(this::isChunkVisible)
        .build()
    );

    private final Setting<Boolean> dimensionOverride = sgChunkLoading.add(new BoolSetting.Builder()
        .name("per-dimension-tuning")
        .description("Isolates unloaded chunk thresholds per vanilla dimension.")
        .defaultValue(true)
        .visible(this::isChunkVisible)
        .build()
    );

    private final Setting<Integer> owStart = sgChunkLoading.add(new IntSetting.Builder()
        .name("overworld-start").defaultValue(12).min(1).sliderMax(100)
        .visible(() -> isChunkVisible() && dimensionOverride.get())
        .build()
    );

    private final Setting<Integer> owMax = sgChunkLoading.add(new IntSetting.Builder()
        .name("overworld-max").defaultValue(75).min(10).sliderMax(400)
        .visible(() -> isChunkVisible() && dimensionOverride.get())
        .build()
    );

    private final Setting<Integer> netherStart = sgChunkLoading.add(new IntSetting.Builder()
        .name("nether-start").defaultValue(35).min(1).sliderMax(200)
        .visible(() -> isChunkVisible() && dimensionOverride.get())
        .build()
    );

    private final Setting<Integer> netherMax = sgChunkLoading.add(new IntSetting.Builder()
        .name("nether-max").defaultValue(160).min(20).sliderMax(800)
        .visible(() -> isChunkVisible() && dimensionOverride.get())
        .build()
    );

    private final Setting<Integer> endStart = sgChunkLoading.add(new IntSetting.Builder()
        .name("end-start").defaultValue(8).min(1).sliderMax(100)
        .visible(() -> isChunkVisible() && dimensionOverride.get())
        .build()
    );

    private final Setting<Integer> endMax = sgChunkLoading.add(new IntSetting.Builder()
        .name("end-max").defaultValue(60).min(10).sliderMax(400)
        .visible(() -> isChunkVisible() && dimensionOverride.get())
        .build()
    );

    private final Setting<Integer> genericStart = sgChunkLoading.add(new IntSetting.Builder()
        .name("generic-start").defaultValue(10).min(1).sliderMax(100)
        .visible(() -> isChunkVisible() && !dimensionOverride.get())
        .build()
    );

    private final Setting<Integer> genericMax = sgChunkLoading.add(new IntSetting.Builder()
        .name("generic-max").defaultValue(80).min(10).sliderMax(500)
        .visible(() -> isChunkVisible() && !dimensionOverride.get())
        .build()
    );

    // ── Ping Throttling Settings ────────────────────────────────────────────────

    private final Setting<Integer> pingThreshold = sgPing.add(new IntSetting.Builder()
        .name("ping-start-ms")
        .defaultValue(120).min(20).sliderMax(400)
        .visible(this::isPingVisible)
        .build()
    );

    private final Setting<Integer> pingMax = sgPing.add(new IntSetting.Builder()
        .name("ping-max-ms")
        .defaultValue(350).min(50).sliderMax(1000)
        .visible(this::isPingVisible)
        .build()
    );

    private final Setting<Double> pingMinSpeed = sgPing.add(new DoubleSetting.Builder()
        .name("ping-min-speed")
        .defaultValue(0.65).min(0.10).max(1.0)
        .visible(this::isPingVisible)
        .build()
    );

    // ── TPS Throttling Settings ─────────────────────────────────────────────────

    private final Setting<Double> targetTps = sgTps.add(new DoubleSetting.Builder()
        .name("target-tps")
        .defaultValue(19.0).min(5.0).max(20.0)
        .visible(this::isTpsVisible)
        .build()
    );

    private final Setting<Double> minTps = sgTps.add(new DoubleSetting.Builder()
        .name("min-tps")
        .defaultValue(11.0).min(1.0).max(20.0)
        .visible(this::isTpsVisible)
        .build()
    );

    private final Setting<Double> tpsMinSpeed = sgTps.add(new DoubleSetting.Builder()
        .name("tps-min-speed")
        .defaultValue(0.50).min(0.10).max(1.0)
        .visible(this::isTpsVisible)
        .build()
    );

    // ── Combat & Hazard Safety Settings ─────────────────────────────────────────

    private final Setting<Boolean> combatSafety = sgSafety.add(new BoolSetting.Builder()
        .name("combat-safety")
        .description("Locks speed to 1.0x baseline during combat encounters.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hazardSafety = sgSafety.add(new BoolSetting.Builder()
        .name("hazard-safeguard")
        .description("Forces 1.0x speed when falling fast, on fire, or in lava.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectSwing = sgSafety.add(new BoolSetting.Builder()
        .name("detect-hand-swing")
        .defaultValue(true)
        .visible(combatSafety::get)
        .build()
    );

    private final Setting<Integer> safetyRange = sgSafety.add(new IntSetting.Builder()
        .name("safety-radius")
        .defaultValue(16).min(1).sliderMax(32)
        .visible(combatSafety::get)
        .build()
    );

    private final Setting<Integer> safetyDuration = sgSafety.add(new IntSetting.Builder()
        .name("cooldown-ticks")
        .defaultValue(80).min(10).sliderMax(200)
        .visible(() -> combatSafety.get() || hazardSafety.get())
        .build()
    );

    // ── Chunk Pipeline & Client Performance ────────────────────────────────────

    private final Setting<Boolean> overrideRate = sgChunkBoost.add(new BoolSetting.Builder()
        .name("override-chunk-rate")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> chunkRate = sgChunkBoost.add(new DoubleSetting.Builder()
        .name("chunks-per-tick")
        .defaultValue(20.0).min(1.0).max(64.0)
        .visible(overrideRate::get)
        .build()
    );

    private final Setting<Boolean> onlyRaise = sgChunkBoost.add(new BoolSetting.Builder()
        .name("only-raise")
        .defaultValue(true)
        .visible(overrideRate::get)
        .build()
    );

    private final Setting<Boolean> limitUnfocused = sgFps.add(new BoolSetting.Builder()
        .name("limit-unfocused-fps")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> unfocusedFps = sgFps.add(new IntSetting.Builder()
        .name("unfocused-target-fps")
        .defaultValue(1).min(1).sliderMax(30)
        .visible(limitUnfocused::get)
        .build()
    );

    // ── Runtime State ───────────────────────────────────────────────────────────

    private double currentSpeed = NORMAL_SPEED;
    private int safetyTicks = 0;
    private int graceTicks = 0;
    private SafetyReason lastSafetyReason = SafetyReason.NONE;

    private int overloadCounterTicks = 0;
    private int giveUpRemainingTicks = 0;
    private int cachedUnloadedChunks = 0;
    private double smoothedUnloaded = -1.0;
    private String activeDominantSource = "None";

    public Timethrottle() {
        super(Tim.CATEGORY, "time-throttle", "Intelligent game speed regulator driven by chunk pipelines, server latency, and tick rates.");
    }

    @Override
    public void onActivate() {
        resetState();
        graceTicks = GRACE_PERIOD_TICKS;
    }

    @Override
    public void onDeactivate() {
        applySpeed(NORMAL_SPEED);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetState();
    }

    private void resetState() {
        currentSpeed = NORMAL_SPEED;
        safetyTicks = 0;
        graceTicks = 0;
        overloadCounterTicks = 0;
        giveUpRemainingTicks = 0;
        cachedUnloadedChunks = 0;
        smoothedUnloaded = -1.0;
        lastSafetyReason = SafetyReason.NONE;
        activeDominantSource = "None";
        applySpeed(NORMAL_SPEED);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        if (!mc.world.getChunkManager().isChunkLoaded(mc.player.getChunkPos().x, mc.player.getChunkPos().z)) {
            applySpeed(NORMAL_SPEED);
            return;
        }

        if (graceTicks > 0) {
            graceTicks--;
            applySpeed(NORMAL_SPEED);
            return;
        }

        updateSafety();
        if (safetyTicks > 0) {
            safetyTicks--;
            applySpeed(NORMAL_SPEED);
            return;
        }

        if (giveUpRemainingTicks > 0) {
            giveUpRemainingTicks--;
            applySpeed(NORMAL_SPEED);
            activeDominantSource = "Recovery";
            return;
        }

        updateChunkCount();
        manageOverloadTimeout();

        double speedChunk = isChunkVisible() ? evalChunkThrottle() : NORMAL_SPEED;
        double speedPing  = isPingVisible()  ? evalPingThrottle()  : NORMAL_SPEED;
        double speedTps   = isTpsVisible()   ? evalTpsThrottle()   : NORMAL_SPEED;

        double targetSpeed = calculateSpeedByFocus(speedChunk, speedPing, speedTps);
        targetSpeed = Math.max(targetSpeed, absoluteMinSpeed.get());

        double smoothing = (targetSpeed < currentSpeed) ? slowDownSmoothing.get() : speedUpSmoothing.get();
        currentSpeed = MathHelper.lerp(1.0 - smoothing, currentSpeed, targetSpeed);
        applySpeed(currentSpeed);
    }

    private void updateSafety() {
        SafetyReason detected = SafetyReason.NONE;

        // Environmental / Fall hazard checks
        if (hazardSafety.get()) {
            if (mc.player.isOnFire() || mc.player.isInLava() || (mc.player.getVelocity().y < -0.65 && !mc.player.isGliding())) {
                detected = SafetyReason.HAZARD;
            }
        }

        // Combat checks
        if (detected == SafetyReason.NONE && combatSafety.get()) {
            if (mc.player.hurtTime > 0 || mc.player.getAttacker() != null) {
                detected = SafetyReason.HURT;
            } else if (detectSwing.get() && mc.player.handSwingTicks > 0) {
                detected = SafetyReason.ATTACKING;
            } else if (safetyRange.get() > 0) {
                Box box = mc.player.getBoundingBox().expand(safetyRange.get());
                if (!mc.world.getEntitiesByClass(PlayerEntity.class, box, p -> p != mc.player && p.isAlive()).isEmpty()) {
                    detected = SafetyReason.PLAYER_NEARBY;
                } else if (!mc.world.getEntitiesByClass(HostileEntity.class, box, Entity::isAlive).isEmpty()) {
                    detected = SafetyReason.HOSTILE_NEARBY;
                }
            }
        }

        if (detected != SafetyReason.NONE) {
            lastSafetyReason = detected;
            safetyTicks = safetyDuration.get();
            activeDominantSource = detected == SafetyReason.HAZARD ? "Hazard Safeguard" : "Combat Safety";
        }
    }

    private void updateChunkCount() {
        int raw = countMissingChunks();
        if (smoothedUnloaded < 0) smoothedUnloaded = raw;
        else smoothedUnloaded = (smoothedUnloaded * 0.5) + (raw * 0.5);
        cachedUnloadedChunks = (int) Math.round(smoothedUnloaded);
    }

    private void manageOverloadTimeout() {
        if (!isChunkVisible()) {
            overloadCounterTicks = 0;
            return;
        }

        if (cachedUnloadedChunks >= chunkOverloadThreshold.get()) {
            overloadCounterTicks++;
            if (overloadCounterTicks >= chunkOverloadTimeout.get() * TICKS_PER_SECOND) {
                giveUpRemainingTicks = giveUpCooldown.get() * TICKS_PER_SECOND;
                overloadCounterTicks = 0;
            }
        } else {
            overloadCounterTicks = Math.max(0, overloadCounterTicks - 2);
        }
    }

    private double evalChunkThrottle() {
        if (giveUpRemainingTicks > 0) return NORMAL_SPEED;

        int start;
        int max;

        if (dimensionOverride.get()) {
            if (mc.world.getRegistryKey() == World.NETHER) {
                start = netherStart.get();
                max = netherMax.get();
            } else if (mc.world.getRegistryKey() == World.END) {
                start = endStart.get();
                max = endMax.get();
            } else {
                start = owStart.get();
                max = owMax.get();
            }
        } else {
            start = genericStart.get();
            max = genericMax.get();
        }

        if (pingAdaptiveChunks.get()) {
            int ping = getPlayerPing();
            if (ping > 100) {
                double latencyDamping = Math.max(0.40, 1.0 - ((ping - 100) / 600.0));
                start = Math.max(2, (int) (start * latencyDamping));
                max = Math.max(start + 5, (int) (max * latencyDamping));
            }
        }

        if (cachedUnloadedChunks <= start) return NORMAL_SPEED;
        if (cachedUnloadedChunks >= max) return chunkLoadSlowdown.get();
        return MathHelper.map(cachedUnloadedChunks, start, max, NORMAL_SPEED, chunkLoadSlowdown.get());
    }

    private double evalPingThrottle() {
        int ping = getPlayerPing();
        if (ping <= pingThreshold.get()) return NORMAL_SPEED;
        if (ping >= pingMax.get()) return pingMinSpeed.get();
        return MathHelper.map(ping, pingThreshold.get(), pingMax.get(), NORMAL_SPEED, pingMinSpeed.get());
    }

    private double evalTpsThrottle() {
        double tps = TickRate.INSTANCE.getTickRate();
        if (tps >= targetTps.get()) return NORMAL_SPEED;
        if (tps <= minTps.get()) return tpsMinSpeed.get();
        return MathHelper.map(tps, minTps.get(), targetTps.get(), tpsMinSpeed.get(), NORMAL_SPEED);
    }

    private double calculateSpeedByFocus(double chunkSpd, double pingSpd, double tpsSpd) {
        FocusMode mode = focusMode.get();
        double speed;

        switch (mode) {
            case CHUNKS -> {
                activeDominantSource = (chunkSpd < 0.98) ? "Chunks" : "None";
                speed = chunkSpd;
            }
            case PING -> {
                activeDominantSource = (pingSpd < 0.98) ? "Ping" : "None";
                speed = pingSpd;
            }
            case TPS -> {
                activeDominantSource = (tpsSpd < 0.98) ? "TPS" : "None";
                speed = tpsSpd;
            }
            case BALANCED -> {
                speed = Math.min(chunkSpd, Math.min(pingSpd, tpsSpd));
                if (speed == chunkSpd && speed < 0.98) activeDominantSource = "Chunks";
                else if (speed == pingSpd && speed < 0.98) activeDominantSource = "Ping";
                else if (speed == tpsSpd && speed < 0.98) activeDominantSource = "TPS";
                else activeDominantSource = "None";
            }
            default -> speed = NORMAL_SPEED;
        }

        if (speed >= 0.99) activeDominantSource = "None";
        return speed;
    }

    private void applySpeed(double speed) {
        if (Double.isNaN(speed) || Double.isInfinite(speed) || speed <= 0.0) speed = NORMAL_SPEED;
        currentSpeed = speed;
        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(speed);
    }

    private int countMissingChunks() {
        if (mc.world == null || mc.player == null) return 0;
        int vd = mc.options.getClampedViewDistance();
        int cx = mc.player.getChunkPos().x;
        int cz = mc.player.getChunkPos().z;

        Vec3d vel = mc.player.getVelocity();
        boolean lookahead = directionalLookahead.get() && (Math.abs(vel.x) > 0.08 || Math.abs(vel.z) > 0.08);
        double normX = lookahead ? vel.x : 0;
        double normZ = lookahead ? vel.z : 0;

        double weightedMissing = 0.0;

        for (int x = -vd; x <= vd; x++) {
            for (int z = -vd; z <= vd; z++) {
                if (!mc.world.getChunkManager().isChunkLoaded(cx + x, cz + z)) {
                    if (lookahead) {
                        double dot = (x * normX) + (z * normZ);
                        weightedMissing += (dot > 0) ? 1.0 : 0.5;
                    } else {
                        weightedMissing += 1.0;
                    }
                }
            }
        }
        return (int) Math.round(weightedMissing);
    }

    // ── Public Accessors ────────────────────────────────────────────────────────

    public int getPlayerPing() {
        if (mc.getNetworkHandler() == null || mc.player == null) return 0;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry != null ? entry.getLatency() : 0;
    }

    public float modifyChunkRate(float vanilla) {
        if (!overrideRate.get()) return vanilla;
        float target = chunkRate.get().floatValue();
        return onlyRaise.get() ? Math.max(vanilla, target) : target;
    }

    public int getUnfocusedFpsLimit() {
        return limitUnfocused.get() ? unfocusedFps.get() : -1;
    }

    public double getCurrentSpeed() { return currentSpeed; }
    public String getActiveDominantSource() { return activeDominantSource; }
    public int getCachedUnloadedChunks() { return cachedUnloadedChunks; }
    public boolean isSafetyActive() { return safetyTicks > 0; }
    public boolean isOverloadTimeoutActive() { return giveUpRemainingTicks > 0; }
    public SafetyReason getLastSafetyReason() { return lastSafetyReason; }
    public FocusMode getFocusMode() { return focusMode.get(); }

    @Override
    public String getInfoString() {
        if (isSafetyActive()) return lastSafetyReason == SafetyReason.HAZARD ? "HAZARD" : "SAFETY";
        if (giveUpRemainingTicks > 0) return "TIMEOUT";
        return String.format("%.0f%% [%s]", currentSpeed * 100.0, focusMode.get().name().substring(0, 1));
    }
}