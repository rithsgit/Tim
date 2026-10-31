package com.example.addon.modules;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.example.addon.Tim;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

public class Skipper extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enums & Records
    // ═══════════════════════════════════════════════════════════════════════════

    public enum TrailEra {
        ERA_1_12,
        ERA_MIXED_1_12_TRAIL,
        ERA_1_19_TRAIL,
        ERA_1_19_FRESH
    }

    public enum TrackingFocus {
        ALL,
        PURE_1_12,
        PURE_1_19,
        MIXED
    }

    public enum RenderMode {
        GLOW,
        SPECTRAL,
        PULSE
    }

    public enum AlertSound {
        ExperienceOrb,
        Portal,
        NoteBlock,
        VillagerYes
    }

    private record PortalSite(BlockPos base, Direction.Axis axis) {}
    private record PortalPreview(PortalSite site, TrailEra era, BlockPos overworldTarget) {}
    private record TimedPortalPreview(PortalPreview preview, int ticksRemaining) {}
    
    private enum TrailChunkFlowState {
        CLEAR,
        FLOWING,
        SETTLING,
        UNLOADED
    }

    private static final int PORTAL_SEARCH_RETRY_TICKS = 20;
    private static final int MAX_ALTERNATIVE_PORTAL_PREVIEWS = 4;
    private static final long LAVA_SETTLE_QUIET_NANOS = 3_000_000_000L;
    private static final int MAX_PENDING_CHUNK_SCANS_PER_TICK = 4;
    private static final long CHUNK_SCAN_BUDGET_NANOS = 5_000_000L;

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups & Settings
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgEra        = settings.createGroup("Era Classification & Tracking Focus");
    private final SettingGroup sgPortalSkip = settings.createGroup("Portal Skips");
    private final SettingGroup sgAlerts     = settings.createGroup("Alerts & Notifications");
    private final SettingGroup sgLava       = settings.createGroup("Lava Marker & Flow");
    private final SettingGroup sgColors     = settings.createGroup("Colors");
    private final SettingGroup sgRender     = settings.createGroup("3D Render & Styles");

    // General
    private final Setting<Boolean> syncWithXaeroPlus = sgGeneral.add(new BoolSetting.Builder()
        .name("sync-xaeroplus").defaultValue(true).build()
    );
    private final Setting<Boolean> nativeXaeroMapCoexistence = sgGeneral.add(new BoolSetting.Builder()
        .name("xaero-map-coexistence")
        .description("Lets XaeroPlus natively render chunk trails and portal skips on the Minimap and World Map without Skipper painting over them.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Keybind> dismissNearestKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("dismiss-nearest-key")
        .description("Dismisses the closest active portal preview.")
        .defaultValue(Keybind.none())
        .build()
    );

    // Era Classification & Tracking Focus
    private final Setting<TrackingFocus> trackingFocus = sgEra.add(new EnumSetting.Builder<TrackingFocus>()
        .name("tracking-focus")
        .description("Adapts trail, portal, and lava filters for 1.12, 1.19+, mixed, or all trails.")
        .defaultValue(TrackingFocus.ALL)
        .build()
    );
    private final Setting<Boolean> classify112Blocks = sgEra.add(new BoolSetting.Builder()
        .name("classify-1.12-old-chunks").defaultValue(true).build()
    );
    private final Setting<Boolean> useXaeroPaletteNewChunks = sgEra.add(new BoolSetting.Builder()
        .name("use-palette-new-chunks").defaultValue(true).build()
    );

    // Portal Skips
    private final Setting<Boolean> portalSkipEnabled = sgPortalSkip.add(new BoolSetting.Builder()
        .name("enable-portal-skip").defaultValue(true).build()
    );
    private final Setting<Integer> portalRadius = sgPortalSkip.add(new IntSetting.Builder()
        .name("portal-radius").defaultValue(7).min(3).sliderMax(32).build()
    );
    private final Setting<Integer> minPortalSpacing = sgPortalSkip.add(new IntSetting.Builder()
        .name("min-portal-spacing-blocks").defaultValue(32).min(16).sliderMax(128).build()
    );
    private final Setting<Integer> portalHoldTicks = sgPortalSkip.add(new IntSetting.Builder()
        .name("portal-hold-ticks")
        .description("Combined idle and alternative hold ticks for portal skips.")
        .defaultValue(300).min(0).sliderMax(25000)
        .build()
    );
    private final Setting<Boolean> showPortalBeam = sgPortalSkip.add(new BoolSetting.Builder()
        .name("show-portal-beam").defaultValue(true).build()
    );
    private final Setting<Double> portalBeamRadius = sgPortalSkip.add(new DoubleSetting.Builder()
        .name("portal-beam-radius").defaultValue(0.12).min(0.02).sliderMax(0.5).visible(showPortalBeam::get).build()
    );
    private final Setting<Integer> portalBeamStrands = sgPortalSkip.add(new IntSetting.Builder()
        .name("portal-beam-strands").defaultValue(4).min(1).sliderMax(12).visible(showPortalBeam::get).build()
    );

    // Alerts & Notifications
    private final Setting<Boolean> alertsEnabled = sgAlerts.add(new BoolSetting.Builder()
        .name("Alerts")
        .description("Enables sound and proximity chat notifications when a portal skip is locked.")
        .defaultValue(true)
        .build()
    );
    private final Setting<AlertSound> alertSound = sgAlerts.add(new EnumSetting.Builder<AlertSound>()
        .name("alert-sound")
        .defaultValue(AlertSound.ExperienceOrb)
        .visible(alertsEnabled::get)
        .build()
    );
    private final Setting<Double> soundVolume = sgAlerts.add(new DoubleSetting.Builder()
        .name("sound-volume").defaultValue(0.8).min(0.1).sliderMax(10.0).visible(alertsEnabled::get).build()
    );
    private final Setting<Double> soundPitch = sgAlerts.add(new DoubleSetting.Builder()
        .name("sound-pitch").defaultValue(1.2).min(0.5).sliderMax(2.0).visible(alertsEnabled::get).build()
    );
    private final Setting<Integer> proximityDistance = sgAlerts.add(new IntSetting.Builder()
        .name("proximity-distance")
        .description("Distance in blocks to trigger the proximity chat alert.")
        .defaultValue(64).min(16).sliderMax(256)
        .visible(alertsEnabled::get)
        .build()
    );

    // Lava Marker & Flow
    private final Setting<Boolean> detectLiquidNewChunks = sgLava.add(new BoolSetting.Builder()
        .name("liquid-new-chunks").defaultValue(true).build()
    );
    private final Setting<Boolean> onlyAboveY0 = sgLava.add(new BoolSetting.Builder()
        .name("only-above-y0").defaultValue(true).build()
    );
    private final Setting<Boolean> lavaMarkerEnabled = sgLava.add(new BoolSetting.Builder()
        .name("enable-lava-marker").defaultValue(true).build()
    );
    private final Setting<Integer> verticalRadius = sgLava.add(new IntSetting.Builder()
        .name("vertical-radius").defaultValue(64).min(0).sliderMax(128).build()
    );
    private final Setting<Integer> minFallHeight = sgLava.add(new IntSetting.Builder()
        .name("min-fall-height").defaultValue(5).min(0).sliderMax(32).build()
    );

    // Colors
    private final Setting<SettingColor> color112 = sgColors.add(new ColorSetting.Builder()
        .name("1.12-color")
        .description("Unified color for 1.12 old chunks, old portals, and old lava markers.")
        .defaultValue(new SettingColor(255, 185, 0, 150))
        .build()
    );
    private final Setting<SettingColor> color119 = sgColors.add(new ColorSetting.Builder()
        .name("1.19-color")
        .description("Unified color for 1.19+ trails, portal skips, and trail lava markers.")
        .defaultValue(new SettingColor(0, 215, 255, 190))
        .build()
    );
    private final Setting<SettingColor> newChunkColor = sgColors.add(new ColorSetting.Builder()
        .name("new-chunk-color")
        .description("Color for fresh/new chunks and their respective lava indicators.")
        .defaultValue(new SettingColor(255, 60, 60, 75))
        .build()
    );
    private final Setting<SettingColor> mixed112On119Color = sgColors.add(new ColorSetting.Builder()
        .name("mixed-1.12-traversed-color")
        .description("Dedicated color for mixed 1.12 traversed trail states.")
        .defaultValue(new SettingColor(255, 100, 255, 150))
        .build()
    );

    // 3D Render & Styles
    private final Setting<Boolean> render3D = sgRender.add(new BoolSetting.Builder()
        .name("render-3d-boxes").defaultValue(true).build()
    );
    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
        .name("lava-render-mode").defaultValue(RenderMode.GLOW).build()
    );
    private final Setting<Integer> glowLayers = sgRender.add(new IntSetting.Builder()
        .name("glow-layers").defaultValue(3).min(1).sliderMax(6).visible(() -> renderMode.get() != RenderMode.SPECTRAL).build()
    );
    private final Setting<Double> glowSpread = sgRender.add(new DoubleSetting.Builder()
        .name("glow-spread").defaultValue(0.04).min(0.01).sliderMax(0.15).visible(() -> renderMode.get() != RenderMode.SPECTRAL).build()
    );
    private final Setting<Integer> spectralFillAlpha = sgRender.add(new IntSetting.Builder()
        .name("spectral-fill-alpha").defaultValue(40).min(0).max(200).visible(() -> renderMode.get() == RenderMode.SPECTRAL).build()
    );
    private final Setting<Integer> pulseMinAlpha = sgRender.add(new IntSetting.Builder()
        .name("pulse-min-alpha")
        .description("Lowest opacity reached during the pulse effect.")
        .defaultValue(15).min(0).max(255).sliderMax(100)
        .visible(() -> renderMode.get() == RenderMode.PULSE)
        .build()
    );
    private final Setting<Integer> pulseMaxAlpha = sgRender.add(new IntSetting.Builder()
        .name("pulse-max-alpha")
        .description("Highest opacity reached during the pulse effect.")
        .defaultValue(220).min(0).max(255).sliderMax(255)
        .visible(() -> renderMode.get() == RenderMode.PULSE)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // State Fields
    // ═══════════════════════════════════════════════════════════════════════════

    private final LongSet oldChunks112        = LongSets.synchronize(new LongOpenHashSet());
    private final LongSet paletteNewChunks119 = LongSets.synchronize(new LongOpenHashSet());
    private final LongSet liquidNewChunks     = LongSets.synchronize(new LongOpenHashSet());

    private final Map<Long, PortalPreview> portalPreviews = new ConcurrentHashMap<>();
    private final Map<Long, TimedPortalPreview> alternativePortalPreviews = new ConcurrentHashMap<>();
    private final Set<Long> completedPortalSkips = ConcurrentHashMap.newKeySet();
    private final Set<Long> proximityAlertedPreviews = ConcurrentHashMap.newKeySet();
    private final Map<ChunkPos, Set<BlockPos>> portalBlocksByChunk = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastFlowingLavaUpdateNanos = new ConcurrentHashMap<>();
    private final Queue<WorldChunk> pendingChunkScans = new ConcurrentLinkedQueue<>();
    private final Set<Long> queuedChunkScanKeys = ConcurrentHashMap.newKeySet();
    
    private Long activePortalSkipKey;
    private int portalSkipIdleTicksRemaining;
    private int portalSkipSearchRetryTicksRemaining;
    private int activePreviewValidationTicksRemaining;
    private int alternativePreviewValidationTicksRemaining;
    private int netherReentryTicksRemaining = 0;

    private final Map<ChunkPos, Set<BlockPos>> lavaFallsByChunk = new ConcurrentHashMap<>();
    private final Set<ChunkPos> scannedLavaChunks = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> dirtyLavaChunks = ConcurrentHashMap.newKeySet();

    private String lastDimension = "";
    private boolean xaeroPlusSyncedToNether;

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle & Multi-Version Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    public Skipper() {
        super(Tim.CATEGORY, "skipper", "Optimized trail investigator with re-entry portal exemption.");
    }

    @Override
    public void onActivate() {
        clearData();
        lastDimension = mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
        if (isNether()) {
            netherReentryTicksRemaining = 60;
            portalSkipIdleTicksRemaining = portalHoldTicks.get();
            refreshLoadedChunks();
        }
        if (syncWithXaeroPlus.get()) syncToXaeroPlus(isNether());
        syncPortalSkipDetection(portalSkipEnabled.get());
    }

    @Override
    public void onDeactivate() {
        clearData();
        lastDimension = "";
        if (syncWithXaeroPlus.get()) syncToXaeroPlus(false);
    }

    private void clearData() {
        oldChunks112.clear();
        paletteNewChunks119.clear();
        liquidNewChunks.clear();
        portalPreviews.clear();
        alternativePortalPreviews.clear();
        completedPortalSkips.clear();
        proximityAlertedPreviews.clear();
        portalBlocksByChunk.clear();
        lastFlowingLavaUpdateNanos.clear();
        pendingChunkScans.clear();
        queuedChunkScanKeys.clear();
        activePortalSkipKey = null;
        portalSkipIdleTicksRemaining = 0;
        portalSkipSearchRetryTicksRemaining = 0;
        activePreviewValidationTicksRemaining = 0;
        alternativePreviewValidationTicksRemaining = 0;
        netherReentryTicksRemaining = 0;
        lavaFallsByChunk.clear();
        scannedLavaChunks.clear();
        dirtyLavaChunks.clear();
    }

    private boolean isNether() {
        return mc.world != null && mc.world.getRegistryKey().equals(World.NETHER);
    }

    private static double squaredDistanceTo(BlockPos pos, double x, double y, double z) {
        double dx = (pos.getX() + 0.5) - x;
        double dy = (pos.getY() + 0.5) - y;
        double dz = (pos.getZ() + 0.5) - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double squaredDistanceBetween(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static int getChunkChebyshevDistance(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Event Handlers & Chunk Processing
    // ═══════════════════════════════════════════════════════════════════════════

    private void updateDimension() {
        if (mc.world == null) return;
        String dimension = mc.world.getRegistryKey().getValue().toString();
        if (!dimension.equals(lastDimension)) {
            clearData();
            lastDimension = dimension;
            if (mc.world.getRegistryKey().equals(World.NETHER)) {
                netherReentryTicksRemaining = 60;
                portalSkipIdleTicksRemaining = portalHoldTicks.get();
                refreshLoadedChunks();
            }
        }
    }

    private void refreshLoadedChunks() {
        if (mc.player == null || mc.world == null) return;
        ChunkPos playerChunk = mc.player.getChunkPos();
        int scanRadius = mc.options.getViewDistance().getValue() + portalRadius.get() + 2;
        for (int chunkX = playerChunk.x - scanRadius; chunkX <= playerChunk.x + scanRadius; chunkX++) {
            for (int chunkZ = playerChunk.z - scanRadius; chunkZ <= playerChunk.z + scanRadius; chunkZ++) {
                if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) continue;
                queueChunkScan(mc.world.getChunk(chunkX, chunkZ));
            }
        }
    }

    private boolean queueChunkScan(WorldChunk chunk) {
        if (chunk == null || !queuedChunkScanKeys.add(chunk.getPos().toLong())) return false;
        pendingChunkScans.add(chunk);
        return true;
    }

    private void processPendingChunkScans() {
        long startNanos = System.nanoTime();
        int processed = 0;
        while (processed < MAX_PENDING_CHUNK_SCANS_PER_TICK
            && (processed == 0 || System.nanoTime() - startNanos < CHUNK_SCAN_BUDGET_NANOS)) {
            WorldChunk chunk = pendingChunkScans.poll();
            if (chunk == null) break;
            queuedChunkScanKeys.remove(chunk.getPos().toLong());
            if (mc.world.getChunkManager().isChunkLoaded(chunk.getPos().x, chunk.getPos().z)) {
                processChunkInstant(chunk);
            }
            processed++;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null) return;
        updateDimension();
        if (!isNether()) return;

        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            handleSingleBlockUpdate(packet.getPos(), packet.getState());
        } else if (event.packet instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates(this::handleSingleBlockUpdate);
        }
    }

    private void handleSingleBlockUpdate(BlockPos pos, BlockState state) {
        if (netherReentryTicksRemaining <= 0) {
            updatePortalBlockIndex(pos, state);
            if (portalSkipEnabled.get() && state.isOf(Blocks.NETHER_PORTAL)) {
                markPortalSkipCompleted(pos);
            }
        }

        recordFlowingLavaUpdate(pos, state);
        if (!lavaMarkerEnabled.get()) return;
        if (state.isOf(Blocks.LAVA) || state.isAir()) {
            ChunkPos cp = new ChunkPos(pos);
            scannedLavaChunks.remove(cp);
            dirtyLavaChunks.add(cp);
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null) return;
        updateDimension();
        if (!isNether()) return;

        if (netherReentryTicksRemaining <= 0) {
            updatePortalBlockIndex(event.pos, event.newState);
            if (portalSkipEnabled.get() && event.newState.isOf(Blocks.NETHER_PORTAL)) {
                markPortalSkipCompleted(event.pos);
            }
        }

        recordFlowingLavaUpdate(event.pos, event.newState);
        if (!lavaMarkerEnabled.get()) return;
        BlockState ns = event.newState;
        if (ns.isOf(Blocks.LAVA) || ns.isAir()) {
            ChunkPos cp = new ChunkPos(event.pos);
            scannedLavaChunks.remove(cp);
            dirtyLavaChunks.add(cp);
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null) return;
        updateDimension();
        if (!isNether()) return;
        queueChunkScan(event.chunk());
    }

    private void processChunkInstant(WorldChunk chunk) {
        if (chunk == null) return;
        classifyChunkEra(chunk);
        scanPortalBlocks(chunk);
        if (lavaMarkerEnabled.get()) {
            scanLavaChunk(chunk);
            scannedLavaChunks.add(chunk.getPos());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        updateDimension();
        if (!isNether()) {
            syncPortalSkipDetection(false);
            if (syncWithXaeroPlus.get()) syncToXaeroPlus(false);
            return;
        }

        if (mc.currentScreen == null && dismissNearestKey.get().isPressed()) {
            dismissNearestPortalPreview();
        }

        processPendingChunkScans();

        if (netherReentryTicksRemaining > 0) {
            netherReentryTicksRemaining--;
            return;
        }

        tickAlternativePortalPreviews();
        detectCompletedPortalPreview();

        // Continuously validate active portal preview every 10 ticks so if lava
        // starts flowing in or near the trail, the preview is immediately removed!
        if (activePreviewValidationTicksRemaining > 0) {
            activePreviewValidationTicksRemaining--;
        } else if (!portalPreviews.isEmpty()) {
            activePreviewValidationTicksRemaining = 10;
            validateActivePortalPreview();
        }

        if (alertsEnabled.get() && mc.player != null) {
            double thresholdSq = (double) proximityDistance.get() * proximityDistance.get();
            BlockPos playerBlockPos = mc.player.getBlockPos();
            for (Map.Entry<Long, PortalPreview> entry : portalPreviews.entrySet()) {
                long key = entry.getKey();
                if (proximityAlertedPreviews.contains(key)) continue;
                BlockPos base = entry.getValue().site().base();
                double distSq = squaredDistanceBetween(playerBlockPos, base);
                if (distSq <= thresholdSq) {
                    proximityAlertedPreviews.add(key);
                    int blocksAway = (int) Math.sqrt(distSq);
                    try {
                        mc.inGameHud.getChatHud().addMessage(
                            Text.literal("§6[Skipper] §ePotential portal skip detected approximately §b" + blocksAway + " blocks away§e!")
                        );
                    } catch (Exception ignored) {}
                }
            }
            proximityAlertedPreviews.retainAll(portalPreviews.keySet());
        }

        if (lavaMarkerEnabled.get() && !dirtyLavaChunks.isEmpty()) {
            ChunkPos cp = dirtyLavaChunks.iterator().next();
            dirtyLavaChunks.remove(cp);
            scannedLavaChunks.remove(cp);
            if (mc.world.getChunkManager().isChunkLoaded(cp.x, cp.z)) {
                WorldChunk chunk = mc.world.getChunk(cp.x, cp.z);
                classifyChunkEra(chunk);
                scanLavaChunk(chunk);
                scannedLavaChunks.add(cp);
            }
        }

        syncPortalSkipDetection(portalSkipEnabled.get());
        if (!portalSkipEnabled.get()) {
            portalPreviews.clear();
            alternativePortalPreviews.clear();
            activePortalSkipKey = null;
            activePreviewValidationTicksRemaining = 0;
            alternativePreviewValidationTicksRemaining = 0;
            if (syncWithXaeroPlus.get()) syncToXaeroPlus(true);
            return;
        }

        if (portalSkipIdleTicksRemaining > 0) {
            portalSkipIdleTicksRemaining--;
            return;
        }

        if (syncWithXaeroPlus.get()) syncToXaeroPlus(true);
        if (portalSkipSearchRetryTicksRemaining > 0) {
            portalSkipSearchRetryTicksRemaining--;
            return;
        }

        scanPortalSkipCandidates();
        portalSkipSearchRetryTicksRemaining = PORTAL_SEARCH_RETRY_TICKS;
    }

    private void dismissNearestPortalPreview() {
        if (portalPreviews.isEmpty() && alternativePortalPreviews.isEmpty()) return;
        
        long nearestKey = -1;
        double minDistanceSq = Double.MAX_VALUE;
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        for (Map.Entry<Long, PortalPreview> entry : portalPreviews.entrySet()) {
            double distSq = squaredDistanceTo(entry.getValue().site().base(), px, py, pz);
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq;
                nearestKey = entry.getKey();
            }
        }

        for (Map.Entry<Long, TimedPortalPreview> entry : alternativePortalPreviews.entrySet()) {
            double distSq = squaredDistanceTo(entry.getValue().preview().site().base(), px, py, pz);
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq;
                nearestKey = entry.getKey();
            }
        }

        if (nearestKey != -1) {
            completePortalPreview(nearestKey, portalPreviews.containsKey(nearestKey));
            try {
                mc.inGameHud.getChatHud().addMessage(
                    Text.literal("§6[Skipper] §eNearest portal preview dismissed manually.")
                );
            } catch (Exception ignored) {}
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Portal Previews & Safety Evaluation
    // ═══════════════════════════════════════════════════════════════════════════

    private void tickAlternativePortalPreviews() {
        if (alternativePortalPreviews.isEmpty()) return;

        alternativePortalPreviews.replaceAll((key, timed) ->
            new TimedPortalPreview(timed.preview(), timed.ticksRemaining() - 1));
        alternativePortalPreviews.entrySet().removeIf(entry -> entry.getValue().ticksRemaining() <= 0);

        if (alternativePortalPreviews.isEmpty()) return;
        if (alternativePreviewValidationTicksRemaining > 0) {
            alternativePreviewValidationTicksRemaining--;
            return;
        }
        alternativePreviewValidationTicksRemaining = 10;

        alternativePortalPreviews.entrySet().removeIf(entry -> isPortalPreviewUnsafe(entry.getKey(), entry.getValue().preview()));
    }

    private boolean isPortalPreviewUnsafe(long previewKey, PortalPreview preview) {
        int centerX = ChunkPos.getPackedX(previewKey);
        int centerZ = ChunkPos.getPackedZ(previewKey);
        int width = portalRadius.get();
        long now = System.nanoTime();
        int startOffset = -(width / 2);
        for (int xOffset = startOffset; xOffset < startOffset + width; xOffset++) {
            for (int zOffset = startOffset; zOffset < startOffset + width; zOffset++) {
                if (hasTrailChunkFlowOrUnloaded(centerX + xOffset, centerZ + zOffset, now)) {
                    return true;
                }
            }
        }

        if (findNearbyPortal(previewKey) != null) return true;
        if (!isPortalSiteLoaded(preview.site())) return true;
        return !isLavaSafePortalSite(preview.site().base(), preview.site().axis());
    }

    private boolean hasTrailChunkFlowOrUnloaded(int chunkX, int chunkZ, long now) {
        long chunkKey = ChunkPos.toLong(chunkX, chunkZ);
        if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) return true;
        long lastFlowUpdate = lastFlowingLavaUpdateNanos.getOrDefault(chunkKey, 0L);
        if (lastFlowUpdate > 0 && now - lastFlowUpdate < LAVA_SETTLE_QUIET_NANOS) return true;

        WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
        if (lavaFallsByChunk.containsKey(chunk.getPos())) return true;

        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;
            if (section.hasAny(blockState -> {
                FluidState fluid = blockState.getFluidState();
                return fluid.isIn(FluidTags.LAVA) && !fluid.isStill();
            })) {
                return true;
            }
        }
        return false;
    }

    private boolean isPortalSiteLoaded(PortalSite site) {
        BlockPos base = site.base();
        for (int horizontal = 0; horizontal < 4; horizontal++) {
            int x = base.getX() + (site.axis() == Direction.Axis.X ? horizontal : 0);
            int z = base.getZ() + (site.axis() == Direction.Axis.Z ? horizontal : 0);
            if (!mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return false;
        }
        return true;
    }

    private void recordFlowingLavaUpdate(BlockPos pos, BlockState state) {
        FluidState fluid = state.getFluidState();
        if (fluid.isIn(FluidTags.LAVA) && !fluid.isStill()) {
            long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
            lastFlowingLavaUpdateNanos.put(chunkKey, System.nanoTime());
            // Immediately invalidate any active preview if lava flows within its radius
            if (!portalPreviews.isEmpty()) {
                activePreviewValidationTicksRemaining = 0;
            }
        }
    }

    private static boolean isModernNetherBlock(BlockState state) {
        return state.isOf(Blocks.NETHER_GOLD_ORE)
            || state.isOf(Blocks.ANCIENT_DEBRIS)
            || state.isOf(Blocks.BLACKSTONE)
            || state.isOf(Blocks.BASALT)
            || state.isOf(Blocks.SOUL_SOIL)
            || state.isOf(Blocks.CRIMSON_NYLIUM)
            || state.isOf(Blocks.WARPED_NYLIUM)
            || state.isOf(Blocks.CRIMSON_STEM)
            || state.isOf(Blocks.WARPED_STEM)
            || state.isOf(Blocks.SHROOMLIGHT)
            || state.isOf(Blocks.CRYING_OBSIDIAN)
            || state.isOf(Blocks.GILDED_BLACKSTONE);
    }

    private void classifyChunkEra(WorldChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        long key = cpos.toLong();

        ChunkSection[] sections = chunk.getSectionArray();
        boolean has116Blocks = false;
        boolean hasNetherTerrain = false;

        for (ChunkSection section : sections) {
            if (section == null || section.isEmpty()) continue;
            if (section.hasAny(Skipper::isModernNetherBlock)) {
                has116Blocks = true;
                break;
            }
            if (section.hasAny(s -> s.isOf(Blocks.NETHERRACK) || s.isOf(Blocks.NETHER_QUARTZ_ORE) || s.isOf(Blocks.SOUL_SAND))) {
                hasNetherTerrain = true;
            }
        }

        boolean isXaeroPaletteNew = useXaeroPaletteNewChunks.get() && isXaeroPaletteNewChunk(cpos.x, cpos.z);
        boolean isXaeroOld = isXaeroOldChunk(cpos.x, cpos.z);
        boolean isXaeroLiquidNew = isXaeroLiquidNewChunk(cpos.x, cpos.z);

        if (isXaeroLiquidNew) {
            liquidNewChunks.add(key);
        }

        if ((classify112Blocks.get() && hasNetherTerrain && !has116Blocks) || isXaeroOld) {
            oldChunks112.add(key);
            if (isXaeroPaletteNew || isXaeroLiquidNew) {
                paletteNewChunks119.add(key);
            } else {
                paletteNewChunks119.remove(key);
            }
            return;
        }

        if (has116Blocks || isXaeroPaletteNew) {
            paletteNewChunks119.add(key);
            oldChunks112.remove(key);
            return;
        }

        oldChunks112.remove(key);
    }

    public TrailEra getChunkEra(long chunkKey) {
        int cx = ChunkPos.getPackedX(chunkKey);
        int cz = ChunkPos.getPackedZ(chunkKey);
        boolean is112 = oldChunks112.contains(chunkKey) || isXaeroOldChunk(cx, cz);
        boolean isReloaded119 = (useXaeroPaletteNewChunks.get() && isXaeroPaletteNewChunk(cx, cz))
            || liquidNewChunks.contains(chunkKey)
            || isXaeroLiquidNewChunk(cx, cz)
            || paletteNewChunks119.contains(chunkKey);

        if (is112 && isReloaded119) return TrailEra.ERA_MIXED_1_12_TRAIL;
        if (is112) return TrailEra.ERA_1_12;
        if (isReloaded119) return TrailEra.ERA_1_19_FRESH;
        return TrailEra.ERA_1_19_TRAIL;
    }

    /**
     * Determines if a chunk era should be filtered out based on the user's TrackingFocus.
     * In ALL mode, no eras are skipped so lava columns highlight on both 1.12 and 1.19+ trails!
     */
    public boolean shouldSkipEra(TrailEra era) {
        return switch (trackingFocus.get()) {
            case PURE_1_12 -> era != TrailEra.ERA_1_12 && era != TrailEra.ERA_MIXED_1_12_TRAIL;
            case PURE_1_19 -> era != TrailEra.ERA_1_19_FRESH && era != TrailEra.ERA_1_19_TRAIL;
            case MIXED -> era != TrailEra.ERA_MIXED_1_12_TRAIL && era != TrailEra.ERA_1_12;
            case ALL -> false;
        };
    }

    private TrailEra detectClusterEra(long centerChunkKey) {
        int cx = ChunkPos.getPackedX(centerChunkKey);
        int cz = ChunkPos.getPackedZ(centerChunkKey);
        boolean seen112 = false;
        boolean seen119 = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                TrailEra e = getChunkEra(ChunkPos.toLong(cx + dx, cz + dz));
                if (e == TrailEra.ERA_MIXED_1_12_TRAIL) return TrailEra.ERA_MIXED_1_12_TRAIL;
                if (e == TrailEra.ERA_1_12) seen112 = true;
                if (e == TrailEra.ERA_1_19_FRESH || e == TrailEra.ERA_1_19_TRAIL) seen119 = true;
            }
        }
        if (seen112 && seen119) return TrailEra.ERA_MIXED_1_12_TRAIL;
        if (seen112) return TrailEra.ERA_1_12;
        return TrailEra.ERA_1_19_TRAIL;
    }

    private void scanPortalSkipCandidates() {
        Object detector = getPortalSkipDetectionModule();
        if (detector == null || !isXaeroModuleEnabled(detector)) {
            portalPreviews.clear();
            alternativePortalPreviews.clear();
            activePortalSkipKey = null;
            return;
        }

        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;
        int searchRange = Math.max(48, mc.options.getViewDistance().getValue() + portalRadius.get() + 16);
        
        LongSet currentHighlights = new LongOpenHashSet();
        Iterable<?> highlights = getPortalSkipHighlights(detector);
        
        for (Object highlight : highlights) {
            if (!(highlight instanceof Number number)) continue;
            long key = number.longValue();
            int chunkX = ChunkPos.getPackedX(key);
            int chunkZ = ChunkPos.getPackedZ(key);
            if (Math.max(Math.abs(chunkX - playerChunkX), Math.abs(chunkZ - playerChunkZ)) > searchRange) continue;
            currentHighlights.add(key);
        }

        completedPortalSkips.removeIf(key -> !currentHighlights.contains(key.longValue()));
        if (activePortalSkipKey != null && portalPreviews.containsKey(activePortalSkipKey)) return;

        List<Long> candidates = new ArrayList<>();
        int requiredTrailWidth = portalRadius.get();
        
        for (long key : currentHighlights) {
            if (completedPortalSkips.contains(key)) continue;
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);

            // Strictly require the full portal trail width of highlight chunks around (cx, cz).
            // This prevents portal suggestions from spawning on the side edges of a trail!
            if (hasFullPortalTrailWidth(cx, cz, requiredTrailWidth, currentHighlights)) {
                TrailEra clusterEra = detectClusterEra(key);
                if (!shouldSkipEra(clusterEra)) {
                    candidates.add(key);
                }
            }
        }

        Vec3d lookVec = mc.player.getRotationVector();
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        candidates.sort((k1, k2) -> {
            BlockPos site1 = new BlockPos(ChunkPos.getPackedX(k1) * 16 + 8, (int) py, ChunkPos.getPackedZ(k1) * 16 + 8);
            BlockPos site2 = new BlockPos(ChunkPos.getPackedX(k2) * 16 + 8, (int) py, ChunkPos.getPackedZ(k2) * 16 + 8);

            Vec3d dir1 = new Vec3d(site1.getX() - px, 0, site1.getZ() - pz).normalize();
            Vec3d dir2 = new Vec3d(site2.getX() - px, 0, site2.getZ() - pz).normalize();
            Vec3d flatLook = new Vec3d(lookVec.x, 0, lookVec.z).normalize();

            return Double.compare(dir2.dotProduct(flatLook), dir1.dotProduct(flatLook));
        });

        Map<Long, TrailChunkFlowState> trailFlowStateCache = new HashMap<>();
        Map<Long, PortalPreview> viablePreviews = new LinkedHashMap<>();

        for (long key : candidates) {
            if (viablePreviews.size() >= MAX_ALTERNATIVE_PORTAL_PREVIEWS + 1) break;
            if (getTrailLavaRejection(key, requiredTrailWidth, trailFlowStateCache) != null) continue;
            if (findNearbyPortal(key) != null) continue;

            PortalSite selectedSite = findPortalSite(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
            if (selectedSite != null) {
                if (!isPortalSiteDistinct(selectedSite, viablePreviews.values())) continue;
                TrailEra era = detectClusterEra(key);
                BlockPos netherPos = selectedSite.base();
                BlockPos overworldTarget = new BlockPos(netherPos.getX() * 8, netherPos.getY(), netherPos.getZ() * 8);
                viablePreviews.put(key, new PortalPreview(selectedSite, era, overworldTarget));
            }
        }

        Long primaryKey = activePortalSkipKey != null && portalPreviews.containsKey(activePortalSkipKey)
            ? activePortalSkipKey
            : viablePreviews.keySet().stream().findFirst().orElse(null);

        if (primaryKey == null) {
            portalPreviews.clear();
            return;
        }

        boolean newPrimary = !portalPreviews.containsKey(primaryKey);
        PortalPreview primaryPreview = viablePreviews.get(primaryKey);
        activePortalSkipKey = primaryKey;
        if (newPrimary) {
            triggerAlerts();
            portalSkipIdleTicksRemaining = portalHoldTicks.get();
            activePreviewValidationTicksRemaining = 10;
        }
        if (primaryPreview == null) primaryPreview = portalPreviews.get(primaryKey);

        Map<Long, TimedPortalPreview> retainedAlternatives = new HashMap<>();
        for (Map.Entry<Long, PortalPreview> entry : viablePreviews.entrySet()) {
            if (entry.getKey().equals(primaryKey) || retainedAlternatives.size() >= MAX_ALTERNATIVE_PORTAL_PREVIEWS) continue;
            TimedPortalPreview existing = alternativePortalPreviews.get(entry.getKey());
            int ticksRemaining = existing == null ? portalHoldTicks.get() : existing.ticksRemaining();
            if (ticksRemaining > 0) {
                retainedAlternatives.put(entry.getKey(), new TimedPortalPreview(entry.getValue(), ticksRemaining));
            }
        }
        alternativePortalPreviews.keySet().retainAll(retainedAlternatives.keySet());
        alternativePortalPreviews.putAll(retainedAlternatives);
        portalPreviews.clear();
        portalPreviews.put(primaryKey, primaryPreview);
    }

    private boolean isPortalSiteDistinct(PortalSite candidate, Iterable<PortalPreview> retained) {
        BlockPos candidateBase = candidate.base();
        long minimumDistanceSquared = 16L * 16L;
        for (PortalPreview preview : retained) {
            BlockPos retainedBase = preview.site().base();
            long dx = (long) candidateBase.getX() - retainedBase.getX();
            long dz = (long) candidateBase.getZ() - retainedBase.getZ();
            if (dx * dx + dz * dz < minimumDistanceSquared) return false;
        }
        return true;
    }

    private boolean hasFullPortalTrailWidth(int centerChunkX, int centerChunkZ, int width, LongSet highlights) {
        int startOffset = -(width / 2);
        for (int xOffset = startOffset; xOffset < startOffset + width; xOffset++) {
            for (int zOffset = startOffset; zOffset < startOffset + width; zOffset++) {
                if (!highlights.contains(ChunkPos.toLong(centerChunkX + xOffset, centerChunkZ + zOffset))) {
                    return false;
                }
            }
        }
        return true;
    }

    private String getTrailLavaRejection(long centerChunkKey, int width,
                                         Map<Long, TrailChunkFlowState> stateCache) {
        int centerChunkX = ChunkPos.getPackedX(centerChunkKey);
        int centerChunkZ = ChunkPos.getPackedZ(centerChunkKey);
        long now = System.nanoTime();
        int startOffset = -(width / 2);

        for (int xOffset = startOffset; xOffset < startOffset + width; xOffset++) {
            for (int zOffset = startOffset; zOffset < startOffset + width; zOffset++) {
                String rejection = checkTrailChunkForFlow(
                    centerChunkX + xOffset, centerChunkZ + zOffset, now, stateCache);
                if (rejection != null) return rejection;
            }
        }
        return null;
    }

    private String checkTrailChunkForFlow(int chunkX, int chunkZ, long now,
                                          Map<Long, TrailChunkFlowState> stateCache) {
        long chunkKey = ChunkPos.toLong(chunkX, chunkZ);
        TrailChunkFlowState state = stateCache.get(chunkKey);
        if (state == null) {
            if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                state = TrailChunkFlowState.UNLOADED;
            } else {
                long lastFlowUpdate = lastFlowingLavaUpdateNanos.getOrDefault(chunkKey, 0L);
                if (lastFlowUpdate > 0 && now - lastFlowUpdate < LAVA_SETTLE_QUIET_NANOS) {
                    state = TrailChunkFlowState.SETTLING;
                } else {
                    WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
                    if (lavaFallsByChunk.containsKey(chunk.getPos())) {
                        state = TrailChunkFlowState.FLOWING;
                    } else {
                        state = TrailChunkFlowState.CLEAR;
                        for (ChunkSection section : chunk.getSectionArray()) {
                            if (section == null || section.isEmpty()) continue;
                            if (section.hasAny(blockState -> {
                                FluidState fluid = blockState.getFluidState();
                                return fluid.isIn(FluidTags.LAVA) && !fluid.isStill();
                            })) {
                                state = TrailChunkFlowState.FLOWING;
                                break;
                            }
                        }
                    }
                }
            }
            stateCache.put(chunkKey, state);
        }

        if (state == TrailChunkFlowState.UNLOADED || state == TrailChunkFlowState.SETTLING) return "unsettled lava";
        if (state == TrailChunkFlowState.FLOWING) return "flowing lava";
        return null;
    }

    private void triggerAlerts() {
        if (!alertsEnabled.get() || mc.player == null) return;
        
        try {
            SoundEvent event = switch (alertSound.get()) {
                case Portal -> SoundEvents.BLOCK_PORTAL_TRIGGER;
                case NoteBlock -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
                case VillagerYes -> SoundEvents.ENTITY_VILLAGER_YES;
                default -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            };

            mc.world.playSound(
                mc.player,
                mc.player.getBlockPos(),
                event,
                SoundCategory.PLAYERS,
                (float) soundVolume.get().doubleValue(),
                (float) soundPitch.get().doubleValue()
            );
        } catch (Exception ignored) {}

        try {
            mc.inGameHud.getChatHud().addMessage(
                Text.literal("§6[Skipper] §aForward Portal Skip locked ahead!")
            );
        } catch (Exception ignored) {}
    }

    private BlockPos findNearbyPortal(long chunkKey) {
        int centerX = ChunkPos.getPackedX(chunkKey) * 16 + 8;
        int centerZ = ChunkPos.getPackedZ(chunkKey) * 16 + 8;
        int spacing = minPortalSpacing.get();
        long spacingSquared = (long) spacing * spacing;
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        for (Set<BlockPos> portalBlocks : portalBlocksByChunk.values()) {
            for (BlockPos portal : portalBlocks) {
                if (netherReentryTicksRemaining > 0 && squaredDistanceTo(portal, px, py, pz) <= 256.0) continue;
                long dx = (long) portal.getX() - centerX;
                long dz = (long) portal.getZ() - centerZ;
                if (dx * dx + dz * dz <= spacingSquared) return portal;
            }
        }
        return null;
    }

    private void scanPortalBlocks(WorldChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        Set<BlockPos> portalBlocks = ConcurrentHashMap.newKeySet();
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomSectionCoord = chunk.getBottomY() >> 4;

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || section.isEmpty() || !section.hasAny(state -> state.isOf(Blocks.NETHER_PORTAL))) continue;

            int sectionMinY = (bottomSectionCoord + sectionIndex) << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (section.getBlockState(x, y, z).isOf(Blocks.NETHER_PORTAL)) {
                            portalBlocks.add(new BlockPos(chunkPos.getStartX() + x, sectionMinY + y, chunkPos.getStartZ() + z));
                        }
                    }
                }
            }
        }

        if (portalBlocks.isEmpty()) portalBlocksByChunk.remove(chunkPos);
        else portalBlocksByChunk.put(chunkPos, portalBlocks);
    }

    private void updatePortalBlockIndex(BlockPos pos, BlockState state) {
        ChunkPos chunkPos = new ChunkPos(pos);
        if (state.isOf(Blocks.NETHER_PORTAL)) {
            portalBlocksByChunk.computeIfAbsent(chunkPos, ignored -> ConcurrentHashMap.newKeySet()).add(pos.toImmutable());
            return;
        }
        Set<BlockPos> portalBlocks = portalBlocksByChunk.get(chunkPos);
        if (portalBlocks == null) return;
        portalBlocks.remove(pos);
        if (portalBlocks.isEmpty()) portalBlocksByChunk.remove(chunkPos, portalBlocks);
    }

    private void markPortalSkipCompleted(BlockPos portalBlock) {
        for (Map.Entry<Long, PortalPreview> entry : portalPreviews.entrySet()) {
            if (portalBlockIsInsidePreview(portalBlock, entry.getValue().site())) {
                completePortalPreview(entry.getKey(), true);
                return;
            }
        }
        for (Map.Entry<Long, TimedPortalPreview> entry : alternativePortalPreviews.entrySet()) {
            if (portalBlockIsInsidePreview(portalBlock, entry.getValue().preview().site())) {
                completePortalPreview(entry.getKey(), false);
                return;
            }
        }
    }

    private boolean portalBlockIsInsidePreview(BlockPos portalBlock, PortalSite site) {
        BlockPos base = site.base();
        return portalBlock.getY() >= base.getY() && portalBlock.getY() < base.getY() + 4
            && (site.axis() == Direction.Axis.X
                ? portalBlock.getZ() == base.getZ() && portalBlock.getX() >= base.getX() + 1 && portalBlock.getX() <= base.getX() + 2
                : portalBlock.getX() == base.getX() && portalBlock.getZ() >= base.getZ() + 1 && portalBlock.getZ() <= base.getZ() + 2);
    }

    private void completePortalPreview(long key, boolean wasPrimary) {
        completedPortalSkips.add(key);
        portalPreviews.remove(key);
        alternativePortalPreviews.remove(key);
        if (activePortalSkipKey != null && key == activePortalSkipKey.longValue()) activePortalSkipKey = null;
        if (wasPrimary) {
            portalSkipIdleTicksRemaining = portalHoldTicks.get();
            activePreviewValidationTicksRemaining = 0;
        }
    }

    private void detectCompletedPortalPreview() {
        for (Map.Entry<Long, PortalPreview> entry : portalPreviews.entrySet()) {
            if (detectPortalInPreview(entry.getKey(), entry.getValue())) return;
        }
        for (Map.Entry<Long, TimedPortalPreview> entry : alternativePortalPreviews.entrySet()) {
            if (detectPortalInPreview(entry.getKey(), entry.getValue().preview())) return;
        }
    }

    private boolean detectPortalInPreview(long key, PortalPreview preview) {
        PortalSite site = preview.site();
        BlockPos base = site.base();
        for (int horizontal = 1; horizontal <= 2; horizontal++) {
            for (int y = 1; y <= 3; y++) {
                BlockPos interior = new BlockPos(
                    base.getX() + (site.axis() == Direction.Axis.X ? horizontal : 0),
                    base.getY() + y,
                    base.getZ() + (site.axis() == Direction.Axis.Z ? horizontal : 0)
                );
                if (mc.world.getBlockState(interior).isOf(Blocks.NETHER_PORTAL)) {
                    completePortalPreview(key, portalPreviews.containsKey(key));
                    return true;
                }
            }
        }
        return false;
    }

    private void validateActivePortalPreview() {
        Map.Entry<Long, PortalPreview> activePreview = portalPreviews.entrySet().stream().findFirst().orElse(null);
        if (activePreview == null) return;
        long previewKey = activePreview.getKey();
        Object detector = getPortalSkipDetectionModule();
        if (detector == null || !isXaeroModuleEnabled(detector) || isPortalPreviewUnsafe(previewKey, activePreview.getValue())) {
            portalPreviews.remove(previewKey);
            if (activePortalSkipKey != null && previewKey == activePortalSkipKey.longValue()) {
                activePortalSkipKey = null;
            }
            portalSkipIdleTicksRemaining = 0;
        }
    }

    private PortalSite findPortalSite(int centerChunkX, int centerChunkZ) {
        int centerX = centerChunkX * 16 + 8;
        int centerZ = centerChunkZ * 16 + 8;

        for (int horizontalRadius = 0; horizontalRadius <= 6; horizontalRadius++) {
            for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != horizontalRadius) continue;
                    for (int baseY = 32; baseY <= 116; baseY += 2) {
                        BlockPos base = new BlockPos(centerX + dx, baseY, centerZ + dz);
                        if (isLavaSafePortalSite(base, Direction.Axis.X)) return new PortalSite(base, Direction.Axis.X);
                        if (isLavaSafePortalSite(base, Direction.Axis.Z)) return new PortalSite(base, Direction.Axis.Z);
                    }
                }
            }
        }
        return null;
    }

    private boolean isLavaSafePortalSite(BlockPos base, Direction.Axis axis) {
        int bottomY = mc.world.getBottomY();
        int topYExclusive = bottomY + mc.world.getHeight();
        if (base.getY() <= bottomY || base.getY() + 4 >= topYExclusive) return false;
        for (int horizontal = 0; horizontal < 4; horizontal++) {
            int x = base.getX() + (axis == Direction.Axis.X ? horizontal : 0);
            int z = base.getZ() + (axis == Direction.Axis.Z ? horizontal : 0);
            BlockPos floor = new BlockPos(x, base.getY() - 1, z);
            if (!isChunkLoaded(floor)) return false;
            BlockState floorState = mc.world.getBlockState(floor);
            if (!floorState.isSideSolidFullSquare(mc.world, floor, Direction.UP)) return false;
            if (mc.world.getFluidState(floor).isIn(FluidTags.LAVA)) return false;

            for (int y = 0; y < 5; y++) {
                BlockPos frameOrInterior = new BlockPos(x, base.getY() + y, z);
                if (!isChunkLoaded(frameOrInterior)) return false;
                if (mc.world.getFluidState(frameOrInterior).isIn(FluidTags.LAVA)) return false;
                if (!mc.world.getBlockState(frameOrInterior).isReplaceable()) return false;
            }
        }

        // Check a 2-block safety margin around the portal frame so portals don't spawn right next to lava
        for (int dx = -2; dx <= 5; dx++) {
            for (int dz = -2; dz <= 5; dz++) {
                for (int dy = -1; dy <= 5; dy++) {
                    int checkX = base.getX() + (axis == Direction.Axis.X ? dx : dz);
                    int checkZ = base.getZ() + (axis == Direction.Axis.Z ? dx : dz);
                    BlockPos checkPos = new BlockPos(checkX, base.getY() + dy, checkZ);
                    if (!isChunkLoaded(checkPos)) return false;
                    FluidState fs = mc.world.getFluidState(checkPos);
                    if (fs.isIn(FluidTags.LAVA) && !fs.isStill()) return false;
                }
            }
        }
        return true;
    }

    private boolean isChunkLoaded(BlockPos pos) {
        return mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lava Marker Scan & Processing
    // ═══════════════════════════════════════════════════════════════════════════

    private void scanLavaChunk(Chunk chunk) {
        if (chunk == null || mc.player == null || mc.world == null) return;
        ChunkPos cp = chunk.getPos();
        if (isNaturalBasaltSpring(chunk)) return;

        int vRadius = verticalRadius.get();
        int playerY = (int) mc.player.getY();
        int bottomY = mc.world.getBottomY();
        int minY = Math.max(bottomY, playerY - vRadius);
        int maxY = Math.min(bottomY + mc.world.getHeight(), playerY + vRadius);
        int bottomSectionCoord = chunk.getBottomY() >> 4;

        Set<BlockPos> fallTips = new HashSet<>();
        ChunkSection[] sections = chunk.getSectionArray();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            int sectionMinY = (bottomSectionCoord + i) << 4;
            if (sectionMinY + 15 < minY || sectionMinY > maxY) continue;
            if (!section.hasAny(s -> s.getFluidState().isIn(FluidTags.LAVA))) continue;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        int worldY = sectionMinY + y;
                        if (worldY < minY || worldY > maxY) continue;
                        FluidState fs = section.getBlockState(x, y, z).getFluidState();
                        if (!fs.isIn(FluidTags.LAVA) || !fs.contains(Properties.FALLING) || !fs.get(Properties.FALLING)) continue;
                        BlockPos pos = new BlockPos(cp.getStartX() + x, worldY, cp.getStartZ() + z);
                        if (!isFalling(pos.down())) fallTips.add(pos);
                    }
                }
            }
        }

        Set<BlockPos> allValidFallBlocks = new HashSet<>();
        Set<BlockPos> visitedInScan = new HashSet<>();
        for (BlockPos tip : fallTips) {
            if (visitedInScan.contains(tip)) continue;
            Set<BlockPos> currentFall = new HashSet<>();
            bfsLavaFall(tip, currentFall, visitedInScan);
            if (currentFall.isEmpty()) continue;

            int fallMinY = currentFall.stream().mapToInt(BlockPos::getY).min().orElse(0);
            int fallMaxY = currentFall.stream().mapToInt(BlockPos::getY).max().orElse(0);
            if (fallMaxY - fallMinY + 1 < minFallHeight.get()) continue;

            for (BlockPos pos : currentFall) {
                FluidState fs = mc.world.getFluidState(pos);
                if (fs.isIn(FluidTags.LAVA) && !fs.isStill()) allValidFallBlocks.add(pos);
            }
        }

        if (!allValidFallBlocks.isEmpty()) lavaFallsByChunk.put(cp, allValidFallBlocks);
        else lavaFallsByChunk.remove(cp);
    }

    private boolean isNaturalBasaltSpring(Chunk chunk) {
        int basaltCount = 0;
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section != null && !section.isEmpty() && section.hasAny(s -> s.isOf(Blocks.BASALT))) basaltCount++;
        }
        return basaltCount >= 6;
    }

    private boolean isFalling(BlockPos pos) {
        FluidState fs = mc.world.getFluidState(pos);
        return fs.isIn(FluidTags.LAVA) && fs.contains(Properties.FALLING) && fs.get(Properties.FALLING);
    }

    private void bfsLavaFall(BlockPos start, Set<BlockPos> result, Set<BlockPos> visited) {
        if (visited.contains(start)) return;
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            result.add(cur);

            for (BlockPos nb : new BlockPos[]{cur.north(), cur.south(), cur.east(), cur.west(), cur.down()}) {
                if (!visited.contains(nb) && isChunkLoaded(nb)) {
                    FluidState ns = mc.world.getFluidState(nb);
                    if (ns.isIn(FluidTags.LAVA) && !ns.isStill()) {
                        visited.add(nb);
                        queue.add(nb);
                    }
                }
            }
            BlockPos up = cur.up();
            if (!visited.contains(up) && isChunkLoaded(up) && isFalling(up)) {
                visited.add(up);
                queue.add(up);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // XaeroPlus Reflection Bridge (Version-Resilient 1.21.4 - 1.21.11)
    // ═══════════════════════════════════════════════════════════════════════════

    private static Object getXaeroModule(String className) {
        try {
            Class<?> managerClass = Class.forName("xaeroplus.module.ModuleManager");
            Class<?> moduleClass = Class.forName(className);
            Method getModule = managerClass.getMethod("getModule", Class.class);
            return getModule.invoke(null, moduleClass);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isXaeroModuleEnabled(Object module) {
        if (module == null) return false;
        try {
            Method isEnabled = module.getClass().getMethod("isEnabled");
            return Boolean.TRUE.equals(isEnabled.invoke(module));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void setXaeroModuleEnabled(Object module, boolean enabled) {
        if (module == null) return;
        try {
            Method setEnabled = module.getClass().getMethod("setEnabled", boolean.class);
            setEnabled.invoke(module, enabled);
        } catch (Throwable ignored) {}
    }

    private void syncToXaeroPlus(boolean inNether) {
        try {
            Object liquidNewChunksMod = getXaeroModule("xaeroplus.module.impl.LiquidNewChunks");
            if (!inNether) {
                if (xaeroPlusSyncedToNether) {
                    setXaeroModuleEnabled(liquidNewChunksMod, false);
                    xaeroPlusSyncedToNether = false;
                }
                return;
            }
            setXaeroModuleEnabled(liquidNewChunksMod, detectLiquidNewChunks.get());
            if (useXaeroPaletteNewChunks.get()) {
                setXaeroModuleEnabled(getXaeroModule("xaeroplus.module.impl.PaletteNewChunks"), true);
            }
            if (classify112Blocks.get()) {
                setXaeroModuleEnabled(getXaeroModule("xaeroplus.module.impl.OldChunks"), true);
            }
            setXaeroOnlyAboveY0(onlyAboveY0.get());
            xaeroPlusSyncedToNether = true;
        } catch (Throwable ignored) {}
    }

    private static void setXaeroOnlyAboveY0(boolean value) {
        try {
            Class<?> settingsClass = Class.forName("xaeroplus.settings.Settings");
            Field registryField = settingsClass.getField("REGISTRY");
            Object registry = registryField.get(null);
            Field settingField = registry.getClass().getField("liquidNewChunksOnlyAboveY0Setting");
            Object settingObj = settingField.get(registry);
            for (Method m : settingObj.getClass().getMethods()) {
                if (m.getName().equals("setValue") && m.getParameterCount() == 1) {
                    m.invoke(settingObj, value);
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean isXaeroPaletteNewChunk(int chunkX, int chunkZ) {
        try {
            Object pnc = getXaeroModule("xaeroplus.module.impl.PaletteNewChunks");
            if (!isXaeroModuleEnabled(pnc)) return false;
            Method isNewChunk = pnc.getClass().getMethod("isNewChunk", int.class, int.class, RegistryKey.class);
            return Boolean.TRUE.equals(isNewChunk.invoke(pnc, chunkX, chunkZ, World.NETHER));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isXaeroLiquidNewChunk(int chunkX, int chunkZ) {
        try {
            Object lnc = getXaeroModule("xaeroplus.module.impl.LiquidNewChunks");
            if (!isXaeroModuleEnabled(lnc)) return false;
            Method isNewChunk = lnc.getClass().getMethod("isNewChunk", int.class, int.class, RegistryKey.class);
            return Boolean.TRUE.equals(isNewChunk.invoke(lnc, chunkX, chunkZ, World.NETHER));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isXaeroOldChunk(int chunkX, int chunkZ) {
        try {
            Object oc = getXaeroModule("xaeroplus.module.impl.OldChunks");
            if (!isXaeroModuleEnabled(oc)) return false;
            Method isOldChunk = oc.getClass().getMethod("isOldChunk", int.class, int.class, RegistryKey.class);
            return Boolean.TRUE.equals(isOldChunk.invoke(oc, chunkX, chunkZ, World.NETHER));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void syncPortalSkipDetection(boolean enabled) {
        Object detector = getPortalSkipDetectionModule();
        if (detector == null) return;
        if (!isXaeroModuleEnabled(detector)) {
            setXaeroModuleEnabled(detector, true);
        }
        try {
            Method setRadius = detector.getClass().getMethod("setPortalRadius", int.class);
            setRadius.invoke(detector, portalRadius.get());
        } catch (Throwable ignored) {}
        try {
            Method setDelay = detector.getClass().getMethod("setSearchDelayTicks", int.class);
            setDelay.invoke(detector, 1);
        } catch (Throwable ignored) {}
    }

    private Object getPortalSkipDetectionModule() {
        return getXaeroModule("xaeroplus.module.impl.PortalSkipDetection");
    }

    private Iterable<?> getPortalSkipHighlights(Object detector) {
        try {
            for (Method method : detector.getClass().getMethods()) {
                if (method.getName().equals("getHighlightsState") && method.getParameterCount() == 1) {
                    Object highlights = method.invoke(detector, World.NETHER);
                    if (highlights instanceof Map<?, ?> map) return map.keySet();
                } else if (method.getName().equals("getHighlightsSnapshot") && method.getParameterCount() == 4) {
                    Object highlights = method.invoke(detector, 0, 0, 0, World.NETHER);
                    if (highlights instanceof Iterable<?> iterable) return iterable;
                }
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    public int getXaeroColor(int chunkX, int chunkZ) {
        if (!isNether()) return 0;
        // When nativeXaeroMapCoexistence is true (default), return 0 so XaeroPlus's
        // built-in OldChunks, PaletteNewChunks, LiquidNewChunks, and PortalSkipDetection
        // render natively on the Minimap and World Map without Skipper painting over them.
        if (nativeXaeroMapCoexistence.get()) return 0;

        long key = ChunkPos.toLong(chunkX, chunkZ);
        TrailEra era = getChunkEra(key);

        if (shouldSkipEra(era)) return 0;

        if (lavaFallsByChunk.containsKey(new ChunkPos(chunkX, chunkZ))) {
            return era == TrailEra.ERA_1_12 ? toRgba(color112.get()) : toRgba(color119.get());
        }
        if (era == TrailEra.ERA_MIXED_1_12_TRAIL) return toRgba(mixed112On119Color.get());
        if (era == TrailEra.ERA_1_12) return toRgba(color112.get());
        if (era == TrailEra.ERA_1_19_FRESH) return toRgba(newChunkColor.get());
        return 0;
    }

    private int toRgba(SettingColor c) {
        return ((c.r & 0xFF) << 24) | ((c.g & 0xFF) << 16) | ((c.b & 0xFF) << 8) | (c.a & 0xFF);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3D Render & Styling Methods
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render3D.get() || mc.player == null || !isNether()) return;

        ChunkPos playerChunk = mc.player.getChunkPos();

        if (portalSkipEnabled.get()) {
            int maxPreviewDistance = Math.max(48, mc.options.getViewDistance().getValue() + portalRadius.get() + 10);
            for (Map.Entry<Long, PortalPreview> entry : portalPreviews.entrySet()) {
                PortalPreview preview = entry.getValue();
                BlockPos base = preview.site().base();
                if (getChunkChebyshevDistance(playerChunk, new ChunkPos(base)) <= maxPreviewDistance) {
                    Box portalBox = new Box(base.getX(), base.getY(), base.getZ(), base.getX() + 4, base.getY() + 5, base.getZ() + 4);
                    if (isInRenderBounds(portalBox)) {
                        SettingColor portalColor = getEraColor(preview.era());
                        renderGhostPortal(event, preview.site(), portalColor);
                    }
                }
            }

            for (Map.Entry<Long, TimedPortalPreview> entry : alternativePortalPreviews.entrySet()) {
                PortalPreview preview = entry.getValue().preview();
                BlockPos base = preview.site().base();
                if (getChunkChebyshevDistance(playerChunk, new ChunkPos(base)) <= maxPreviewDistance) {
                    Box portalBox = new Box(base.getX(), base.getY(), base.getZ(), base.getX() + 4, base.getY() + 5, base.getZ() + 4);
                    if (isInRenderBounds(portalBox)) {
                        SettingColor portalColor = getEraColor(preview.era());
                        renderGhostPortal(event, preview.site(), withAlpha(portalColor, Math.max(20, portalColor.a / 2)));
                    }
                }
            }
        }

        if (lavaMarkerEnabled.get()) {
            BlockPos playerBlockPos = mc.player.getBlockPos();
            double maxLavaDistSq = 96.0 * 96.0;

            for (Map.Entry<ChunkPos, Set<BlockPos>> entry : lavaFallsByChunk.entrySet()) {
                ChunkPos cp = entry.getKey();
                TrailEra era = getChunkEra(cp.toLong());
                
                if (shouldSkipEra(era)) continue;

                SettingColor markerColor = getEraColor(era);

                Map<Long, List<Integer>> columns = new HashMap<>();
                for (BlockPos pos : entry.getValue()) {
                    if (squaredDistanceBetween(playerBlockPos, pos) > maxLavaDistSq) continue;
                    FluidState fs = mc.world.getFluidState(pos);
                    if (!fs.isIn(FluidTags.LAVA) || fs.isStill()) continue;

                    long key = (((long) pos.getX()) & 0xFFFFFFFFL) | (((long) pos.getZ()) << 32);
                    columns.computeIfAbsent(key, k -> new ArrayList<>()).add(pos.getY());
                }

                for (Map.Entry<Long, List<Integer>> colEntry : columns.entrySet()) {
                    List<Integer> yCoords = colEntry.getValue();
                    yCoords.sort(Integer::compareTo);

                    long packed = colEntry.getKey();
                    int x = (int) (packed & 0xFFFFFFFFL);
                    int z = (int) (packed >>> 32);

                    int startY = yCoords.get(0);
                    int prevY = startY;

                    for (int i = 1; i < yCoords.size(); i++) {
                        int y = yCoords.get(i);
                        if (y == prevY + 1) {
                            prevY = y;
                        } else {
                            Box columnBox = new Box(x, startY, z, x + 1.0, prevY + 1.0, z + 1.0);
                            if (isInRenderBounds(columnBox)) {
                                renderStyledBox(event, columnBox, markerColor);
                            }
                            startY = y;
                            prevY = y;
                        }
                    }
                    Box columnBox = new Box(x, startY, z, x + 1.0, prevY + 1.0, z + 1.0);
                    if (isInRenderBounds(columnBox)) {
                        renderStyledBox(event, columnBox, markerColor);
                    }
                }
            }
        }
    }

    private SettingColor getEraColor(TrailEra era) {
        return switch (era) {
            case ERA_1_12 -> color112.get();
            case ERA_MIXED_1_12_TRAIL -> mixed112On119Color.get();
            case ERA_1_19_FRESH, ERA_1_19_TRAIL -> color119.get();
        };
    }

    private void renderGhostPortal(Render3DEvent event, PortalSite site, SettingColor color) {
        BlockPos base = site.base();
        Direction.Axis axis = site.axis();

        for (int horizontal = 0; horizontal < 4; horizontal++) {
            int x = base.getX() + (axis == Direction.Axis.X ? horizontal : 0);
            int z = base.getZ() + (axis == Direction.Axis.Z ? horizontal : 0);
            renderStyledBox(event, new Box(x, base.getY(), z, x + 1, base.getY() + 1, z + 1), color);
            renderStyledBox(event, new Box(x, base.getY() + 4, z, x + 1, base.getY() + 5, z + 1), color);
        }

        for (int y = 1; y < 4; y++) {
            renderStyledBox(event, new Box(base.getX(), base.getY() + y, base.getZ(), base.getX() + 1, base.getY() + y + 1, base.getZ() + 1), color);
            renderStyledBox(event, new Box(
                base.getX() + (axis == Direction.Axis.X ? 3 : 0),
                base.getY() + y,
                base.getZ() + (axis == Direction.Axis.Z ? 3 : 0),
                base.getX() + (axis == Direction.Axis.X ? 4 : 1),
                base.getY() + y + 1,
                base.getZ() + (axis == Direction.Axis.Z ? 4 : 1)
            ), color);
        }

        Box interior = axis == Direction.Axis.X
            ? new Box(base.getX() + 1, base.getY() + 1, base.getZ() + 0.45, base.getX() + 3, base.getY() + 4, base.getZ() + 0.55)
            : new Box(base.getX() + 0.45, base.getY() + 1, base.getZ() + 1, base.getX() + 0.55, base.getY() + 4, base.getZ() + 3);
        
        event.renderer.box(interior, withAlpha(color, Math.min(color.a, 45)), withAlpha(color, 0), ShapeMode.Sides, 0);

        if (showPortalBeam.get()) {
            renderPortalBeam(event, site, color);
        }
    }

    private void renderStyledBox(Render3DEvent event, Box box, SettingColor color) {
        if (renderMode.get() == RenderMode.SPECTRAL) {
            event.renderer.box(box, withAlpha(color, spectralFillAlpha.get()), color, ShapeMode.Both, 0);
        } else if (renderMode.get() == RenderMode.PULSE) {
            renderPulseBox(event, box, color);
        } else {
            renderGlowLayers(event, box, color);
            event.renderer.box(box, withAlpha(color, color.a), color, ShapeMode.Both, 0);
        }
    }

    private boolean isInRenderBounds(Box box) {
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return false;
        Vec3d camPos = camera.getPos();
        Box renderZone = new Box(
            camPos.x - 256, camPos.y - 256, camPos.z - 256,
            camPos.x + 256, camPos.y + 256, camPos.z + 256
        );
        return renderZone.intersects(box);
    }

    private void renderPortalBeam(Render3DEvent event, PortalSite site, SettingColor color) {
        BlockPos base = site.base();
        double centerX = base.getX() + (site.axis() == Direction.Axis.X ? 2.0 : 0.5);
        double centerZ = base.getZ() + (site.axis() == Direction.Axis.Z ? 2.0 : 0.5);
        double radius = portalBeamRadius.get();
        int strands = portalBeamStrands.get();
        double rotation = (System.currentTimeMillis() % 6000L) / 6000.0 * Math.PI * 2.0;
        int worldBottom = mc.world.getBottomY();
        int worldTop = worldBottom + mc.world.getHeight();

        Box core = new Box(centerX - 0.025, worldBottom, centerZ - 0.025, centerX + 0.025, worldTop, centerZ + 0.025);
        event.renderer.box(core, withAlpha(color, Math.min(color.a, 18)), withAlpha(color, 0), ShapeMode.Sides, 0);

        for (int i = 0; i < strands; i++) {
            double angle = rotation + Math.PI * 2.0 * i / strands;
            double strandX = centerX + Math.cos(angle) * radius;
            double strandZ = centerZ + Math.sin(angle) * radius;
            Box strand = new Box(strandX - 0.015, worldBottom, strandZ - 0.015, strandX + 0.015, worldTop, strandZ + 0.015);
            event.renderer.box(strand, withAlpha(color, Math.min(color.a, 35)), withAlpha(color, Math.min(color.a, 70)), ShapeMode.Both, 0);
        }
    }

    private void renderGlowLayers(Render3DEvent event, Box box, SettingColor color) {
        int layers = glowLayers.get();
        double spread = glowSpread.get();
        for (int i = layers; i >= 1; i--) {
            double expansion = spread * i;
            int layerAlpha = Math.max(4, (int) (40 * (1.0 - (double) (i - 1) / layers)));
            event.renderer.box(box.expand(expansion), withAlpha(color, layerAlpha), withAlpha(color, 0), ShapeMode.Sides, 0);
        }
    }

    private void renderPulseBox(Render3DEvent event, Box box, SettingColor base) {
        double t = System.currentTimeMillis() / 1000.0;
        float f = (float) ((Math.sin(t * Math.PI * 2.0) + 1.0) * 0.5);
        
        int minAlpha = Math.min(pulseMinAlpha.get(), pulseMaxAlpha.get());
        int maxAlpha = Math.max(pulseMinAlpha.get(), pulseMaxAlpha.get());
        int pa = Math.min(maxAlpha, Math.max(minAlpha, (int) (minAlpha + (maxAlpha - minAlpha) * f)));
        
        SettingColor pColor = withAlpha(base, pa);
        int layers = glowLayers.get();
        double spread = glowSpread.get();
        for (int i = layers; i >= 1; i--) {
            double expansion = spread * i;
            double taper = 1.0 - ((double)(i - 1) / layers) * 0.6;
            int layerAlpha = Math.max(4, (int)(pa * taper));
            event.renderer.box(box.expand(expansion), withAlpha(pColor, layerAlpha), withAlpha(pColor, 0), ShapeMode.Sides, 0);
        }
        event.renderer.box(box, withAlpha(pColor, pa / 3), pColor, ShapeMode.Both, 0);
    }

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }
}