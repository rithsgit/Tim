package com.example.addon.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.example.addon.Tim;
import com.example.addon.render.RenderCompat;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

public class Raidar extends Module {

    private static final int    CHUNK_SCAN_LIMIT_PER_TICK        = 64;
    private static final int    CLEANUP_INTERVAL_TICKS           = 60;

    public enum HighlightStyle { GLOW, SPECTRAL, PULSE }
    public enum BeamStyle { BOX, GUARDIAN }
    public enum YFilterMode { NONE, ABOVE_Y, BELOW_Y, BETWEEN }

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgStorage    = settings.createGroup("Storage");
    private final SettingGroup sgObsidian   = settings.createGroup("Obsidian");
    private final SettingGroup sgUtility    = settings.createGroup("Utility");
    private final SettingGroup sgDecorative = settings.createGroup("Decorative");
    private final SettingGroup sgRender     = settings.createGroup("Render");
    private final SettingGroup sgBeam       = settings.createGroup("Beam");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range").description("Scan range in blocks.").defaultValue(128).min(16).max(256).sliderMax(128).build());

    private final Setting<YFilterMode> yFilterMode = sgGeneral.add(new EnumSetting.Builder<YFilterMode>()
        .name("y-filter-mode").description("Filter which Y levels are scanned.").defaultValue(YFilterMode.NONE).build());

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y").description("Minimum Y level to scan.").defaultValue(-64).sliderMin(-64).sliderMax(320)
        .visible(() -> yFilterMode.get() == YFilterMode.ABOVE_Y || yFilterMode.get() == YFilterMode.BETWEEN).build());

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y").description("Maximum Y level to scan.").defaultValue(320).sliderMin(-64).sliderMax(320)
        .visible(() -> yFilterMode.get() == YFilterMode.BELOW_Y || yFilterMode.get() == YFilterMode.BETWEEN).build());

    private final Setting<Keybind> clearBeamsKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("clear-beams").description("Toggles rendering of beams on and off.").defaultValue(Keybind.none()).action(this::toggleBeams).build());

    private final Setting<Boolean> notification = sgGeneral.add(new BoolSetting.Builder()
        .name("notification").description("Send chat messages and play sound when entities/stacked minecarts are found.")
        .defaultValue(true).build());

    private final Setting<List<Item>> customItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("custom-items").description("Additional items to highlight in item frames.")
        .defaultValue(List.of(Items.ENCHANTED_GOLDEN_APPLE, Items.ELYTRA)).build());

    private final Setting<Boolean> scanChests = sgStorage.add(new BoolSetting.Builder()
        .name("chests").description("Scan for standard and trapped chests.").defaultValue(true).build());
    private final Setting<SettingColor> chestColor = sgStorage.add(new ColorSetting.Builder()
        .name("chest-color").defaultValue(new SettingColor(255, 215, 0, 255)).visible(scanChests::get).build());

    private final Setting<Boolean> scanBarrels = sgStorage.add(new BoolSetting.Builder()
        .name("barrels").description("Scan for barrels.").defaultValue(true).build());
    private final Setting<SettingColor> barrelColor = sgStorage.add(new ColorSetting.Builder()
        .name("barrel-color").defaultValue(new SettingColor(139, 69, 19, 255)).visible(scanBarrels::get).build());

    private final Setting<Boolean> scanShulkers = sgStorage.add(new BoolSetting.Builder()
        .name("shulkers").description("Scan for shulker boxes.").defaultValue(true).build());
    private final Setting<SettingColor> shulkerColor = sgStorage.add(new ColorSetting.Builder()
        .name("shulker-color").defaultValue(new SettingColor(160, 32, 240, 255)).visible(scanShulkers::get).build());

    private final Setting<Boolean> scanEnderChests = sgStorage.add(new BoolSetting.Builder()
        .name("ender-chests").description("Scan for ender chests.").defaultValue(true).build());
    private final Setting<SettingColor> enderColor = sgStorage.add(new ColorSetting.Builder()
        .name("ender-color").defaultValue(new SettingColor(75, 0, 130, 255)).visible(scanEnderChests::get).build());

    private final Setting<Boolean> scanChestMinecarts = sgStorage.add(new BoolSetting.Builder()
        .name("chest-minecarts").description("Detect chest minecarts.")
        .defaultValue(true).build());
    private final Setting<SettingColor> chestMinecartColor = sgStorage.add(new ColorSetting.Builder()
        .name("chest-minecart-color").defaultValue(new SettingColor(255, 180, 0, 200))
        .visible(scanChestMinecarts::get).build());

    private final Setting<Integer> stackedMinecartThreshold = sgStorage.add(new IntSetting.Builder()
        .name("stacked-threshold")
        .description("How many minecarts at the same position count as 'stacked' and trigger immediate attention.")
        .defaultValue(2).min(2).max(10).sliderRange(2, 5)
        .visible(scanChestMinecarts::get).build());

    private final Setting<SettingColor> stackedMinecartColor = sgStorage.add(new ColorSetting.Builder()
        .name("stacked-minecart-color").description("Highlight color for stacked chest minecarts.")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .visible(scanChestMinecarts::get).build());

    private final Setting<Boolean> scanObsidian = sgObsidian.add(new BoolSetting.Builder()
        .name("nether-obsidian")
        .description("Detects unnatural obsidian clusters in the Nether only. Ignores ruined portals automatically.")
        .defaultValue(true).build());
    
    private final Setting<SettingColor> obsidianColor = sgObsidian.add(new ColorSetting.Builder()
        .name("obsidian-color").defaultValue(new SettingColor(30, 30, 30, 255)).visible(scanObsidian::get).build());

    private final Setting<Integer> maxObsidianCluster = sgObsidian.add(new IntSetting.Builder()
        .name("max-cluster-size")
        .description("Maximum obsidian in a cluster before ignoring it (e.g. nether highways). 15 matches a full portal. 0 to disable.")
        .defaultValue(15).min(0).sliderMax(50).visible(scanObsidian::get).build());

    private final Setting<Boolean> scanUtility = sgUtility.add(new BoolSetting.Builder()
        .name("utility-blocks").description("Detect furnaces, hoppers, dispensers, etc.").defaultValue(true).build());
    private final Setting<SettingColor> utilityColor = sgUtility.add(new ColorSetting.Builder()
        .name("utility-color").defaultValue(new SettingColor(150, 150, 150, 255)).visible(scanUtility::get).build());

    private final Setting<Boolean> scanDecorative = sgDecorative.add(new BoolSetting.Builder()
        .name("decorative-blocks").description("Detect brewing stands, crafters, pots, etc.").defaultValue(true).build());
    private final Setting<SettingColor> decorativeColor = sgDecorative.add(new ColorSetting.Builder()
        .name("decorative-color").defaultValue(new SettingColor(180, 100, 220, 255)).visible(scanDecorative::get).build());

    private final Setting<Boolean> scanItemFramesSetting = sgDecorative.add(new BoolSetting.Builder()
        .name("item-frames").description("Detect item frames holding shulker boxes or custom items.")
        .defaultValue(true).build());
    private final Setting<SettingColor> itemFrameColor = sgDecorative.add(new ColorSetting.Builder()
        .name("item-frame-color").defaultValue(new SettingColor(255, 100, 255, 200))
        .visible(scanItemFramesSetting::get).build());

    private final Setting<Boolean> scanBeds = sgDecorative.add(new BoolSetting.Builder()
        .name("beds").description("Highlight all coloured beds in the surrounding area using their matching dye colour.")
        .defaultValue(false).build());

    private final Setting<Integer> bedFillAlpha = sgDecorative.add(new IntSetting.Builder()
        .name("bed-fill-alpha").description("Fill transparency for bed highlights (0 = outline only).")
        .defaultValue(50).min(0).max(200).sliderMax(150)
        .visible(scanBeds::get).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());

    private final Setting<HighlightStyle> highlightStyle = sgRender.add(new EnumSetting.Builder<HighlightStyle>()
        .name("highlight-style").defaultValue(HighlightStyle.GLOW).build());

    private final Setting<Integer> glowLayers = sgRender.add(new IntSetting.Builder()
        .name("glow-layers").defaultValue(4).min(1).sliderMax(8)
        .visible(() -> highlightStyle.get() == HighlightStyle.GLOW || highlightStyle.get() == HighlightStyle.PULSE).build());

    private final Setting<Double> glowSpread = sgRender.add(new DoubleSetting.Builder()
        .name("glow-spread").defaultValue(0.05).min(0.01).sliderMax(0.2)
        .visible(() -> highlightStyle.get() == HighlightStyle.GLOW || highlightStyle.get() == HighlightStyle.PULSE).build());

    private final Setting<Integer> glowBaseAlpha = sgRender.add(new IntSetting.Builder()
        .name("glow-base-alpha").defaultValue(50).min(4).sliderMax(150).visible(() -> highlightStyle.get() == HighlightStyle.GLOW).build());

    private final Setting<Integer> spectralLineAlpha = sgRender.add(new IntSetting.Builder()
        .name("line-alpha").defaultValue(255).visible(() -> highlightStyle.get() == HighlightStyle.SPECTRAL).build());

    private final Setting<Integer> spectralFillAlpha = sgRender.add(new IntSetting.Builder()
        .name("fill-alpha").defaultValue(15).visible(() -> highlightStyle.get() == HighlightStyle.SPECTRAL).build());

    private final Setting<Double> spectralExpand = sgRender.add(new DoubleSetting.Builder()
        .name("expand").defaultValue(0.05).visible(() -> highlightStyle.get() == HighlightStyle.SPECTRAL).build());

    private final Setting<Double> pulseSpeed = sgRender.add(new DoubleSetting.Builder()
        .name("pulse-speed").defaultValue(1.0).min(0.1).max(5.0).sliderMax(3.0)
        .visible(() -> highlightStyle.get() == HighlightStyle.PULSE).build());

    private final Setting<Integer> pulseMinAlpha = sgRender.add(new IntSetting.Builder()
        .name("pulse-min-alpha").defaultValue(15).min(0).max(255).sliderMax(100)
        .visible(() -> highlightStyle.get() == HighlightStyle.PULSE).build());

    private final Setting<Integer> pulseMaxAlpha = sgRender.add(new IntSetting.Builder()
        .name("pulse-max-alpha").defaultValue(220).min(0).max(255).sliderMax(255)
        .visible(() -> highlightStyle.get() == HighlightStyle.PULSE).build());

    private final Setting<Integer> minChestsForBeam = sgBeam.add(new IntSetting.Builder()
        .name("min-chests-for-beam").description("Minimum chests in a cluster to trigger a beam.").defaultValue(4).min(1).sliderMax(20).build());

    private final Setting<Integer> minBarrelsForBeam = sgBeam.add(new IntSetting.Builder()
        .name("min-barrels-for-beam").description("Minimum barrels in a cluster to trigger a beam.").defaultValue(4).min(1).sliderMax(20).build());

    private final Setting<Integer> minShulkersForBeam = sgBeam.add(new IntSetting.Builder()
        .name("min-shulkers-for-beam").description("Minimum shulkers in a cluster to trigger a beam.").defaultValue(1).min(1).sliderMax(20).build());

    private final Setting<Integer> minEnderChestsForBeam = sgBeam.add(new IntSetting.Builder()
        .name("min-ender-chests-for-beam").description("Minimum ender chests in a cluster to trigger a beam.").defaultValue(2).min(1).sliderMax(20).build());

    private final Setting<Integer> minObsidianForBeam = sgBeam.add(new IntSetting.Builder()
        .name("min-obsidian-for-beam").description("Minimum obsidian blocks in a cluster to trigger a beam.").defaultValue(1).min(1).sliderMax(20).build());

    private final Setting<Boolean> mergeBeams = sgBeam.add(new BoolSetting.Builder()
        .name("merge-beams").description("Merge beams for nearby clusters to reduce clutter.").defaultValue(true).build());

    private final Setting<Double> mergeDistance = sgBeam.add(new DoubleSetting.Builder()
        .name("merge-distance").description("Distance within which beams are merged.").defaultValue(3.0).min(0).sliderMax(10).visible(mergeBeams::get).build());

    private final Setting<BeamStyle> beamStyle = sgBeam.add(new EnumSetting.Builder<BeamStyle>()
        .name("beam-style").defaultValue(BeamStyle.GUARDIAN).build());

    private final Setting<Integer> beamWidth = sgBeam.add(new IntSetting.Builder()
        .name("beam-width").defaultValue(15).min(1).sliderMax(100)
        .visible(() -> beamStyle.get() == BeamStyle.BOX).build());

    private final Setting<Double> guardianRadius = sgBeam.add(new DoubleSetting.Builder()
        .name("guardian-radius").defaultValue(0.08).min(0.01).sliderMax(1.0)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN).build());

    private final Setting<Integer> guardianStrands = sgBeam.add(new IntSetting.Builder()
        .name("guardian-strands").defaultValue(4).min(2).sliderMax(16)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN).build());

    private final Setting<Double> guardianSpinSpeed = sgBeam.add(new DoubleSetting.Builder()
        .name("guardian-spin-speed").defaultValue(1.0).min(0.1).sliderMax(5.0)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN).build());

    private final Map<BlockPos, StashType> stashes = new ConcurrentHashMap<>();
    private final Map<BlockPos, StashCluster> stashClusterMap = new ConcurrentHashMap<>();
    private final Set<ChunkPos> scannedChunks = new HashSet<>();
    private final Set<ChunkPos> dirtyChunks = new HashSet<>();

    private final Map<UUID, StackedState> knownStackedMinecarts = new HashMap<>();
    private final Map<Vec3d, ItemFrameEntity> itemFrameEntities = new HashMap<>();
    private final Map<Vec3d, GlowItemFrameEntity> glowItemFrameEntities = new HashMap<>();
    private final Set<Vec3d> notifiedItemFrames = new HashSet<>();
    private final Map<BlockPos, DyeColor> bedPositions = new HashMap<>();

    private boolean stashesDirty = false;
    private boolean beamsHidden = false;
    private int cleanupTimer = 0;

    public Raidar() {
        super(Tim.CATEGORY, "raidar", "Finds and highlights stashes with advanced scanning, custom Y limits, beam thresholds, minecarts, frames, and beds.");
    }

    @Override
    public void onActivate() { clearAllState(); }

    @Override
    public void onDeactivate() { clearAllState(); }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) { clearAllState(); }

    private void clearAllState() {
        stashes.clear();
        stashClusterMap.clear();
        scannedChunks.clear();
        dirtyChunks.clear();
        knownStackedMinecarts.clear();
        itemFrameEntities.clear();
        glowItemFrameEntities.clear();
        notifiedItemFrames.clear();
        bedPositions.clear();
        stashesDirty = false;
        beamsHidden = false;
    }

    private void toggleBeams() {
        beamsHidden = !beamsHidden;
        info("Beams " + (beamsHidden ? "hidden" : "visible"));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (!dirtyChunks.isEmpty()) { 
            scannedChunks.removeAll(dirtyChunks); 
            dirtyChunks.clear(); 
        }

        BlockPos playerPos = mc.player.getBlockPos();
        scanNewChunks(playerPos.getX() >> 4, playerPos.getZ() >> 4);
        scanChestMinecarts();
        scanItemFrames();
        if (scanBeds.get()) scanDecorativeWorldBlocks();

        if (stashesDirty) {
            stashesDirty = false;
            groupStashes();
        }

        if (++cleanupTimer >= CLEANUP_INTERVAL_TICKS) {
            cleanupTimer = 0;
            cleanupDistantStashes();
        }
    }

    private boolean isYAllowed(int y) {
        return switch (yFilterMode.get()) {
            case NONE -> true;
            case ABOVE_Y -> y >= minY.get();
            case BELOW_Y -> y <= maxY.get();
            case BETWEEN -> y >= minY.get() && y <= maxY.get();
        };
    }

    private void scanNewChunks(int centerChunkX, int centerChunkZ) {
        int r = range.get() >> 4;
        int rSq = r * r, scanned = 0;
        for (int d = 0; d <= r; d++) {
            for (int x = -d; x <= d; x++) {
                if (tryScanChunk(centerChunkX + x, centerChunkZ - d, rSq, centerChunkX, centerChunkZ)) {
                    if (++scanned >= CHUNK_SCAN_LIMIT_PER_TICK) return;
                }
                if (d > 0 && tryScanChunk(centerChunkX + x, centerChunkZ + d, rSq, centerChunkX, centerChunkZ)) {
                    if (++scanned >= CHUNK_SCAN_LIMIT_PER_TICK) return;
                }
            }
            for (int z = -d + 1; z < d; z++) {
                if (tryScanChunk(centerChunkX - d, centerChunkZ + z, rSq, centerChunkX, centerChunkZ)) {
                    if (++scanned >= CHUNK_SCAN_LIMIT_PER_TICK) return;
                }
                if (tryScanChunk(centerChunkX + d, centerChunkZ + z, rSq, centerChunkX, centerChunkZ)) {
                    if (++scanned >= CHUNK_SCAN_LIMIT_PER_TICK) return;
                }
            }
        }
    }

    private boolean tryScanChunk(int cx, int cz, int rSq, int centerCX, int centerCZ) {
        int dx = cx - centerCX, dz = cz - centerCZ;
        if (dx * dx + dz * dz > rSq) return false;

        ChunkPos cp = new ChunkPos(cx, cz);
        if (scannedChunks.contains(cp)) return false;

        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
        if (chunk != null) {
            scanChunk(chunk);
            scannedChunks.add(cp);
            return true;
        }
        return false;
    }

    private void scanChunk(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        int chunkX = chunk.getPos().x << 4;
        int chunkZ = chunk.getPos().z << 4;

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;

            boolean hasStash = false;
            try {
                hasStash = section.hasAny(state -> classifyBlock(state.getBlock()) != null);
            } catch (Exception e) {
                hasStash = false;
            }

            if (!hasStash) continue;

            int sectionMinY = (chunk.getBottomSectionCoord() + i) * 16;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int worldY = sectionMinY + y;
                    if (!isYAllowed(worldY)) continue;
                    for (int z = 0; z < 16; z++) {
                        var state = section.getBlockState(x, y, z);
                        BlockPos pos = new BlockPos(chunkX + x, worldY, chunkZ + z);
                        StashType type = classifyBlock(state.getBlock());
                        if (type != null) {
                            stashes.put(pos, type);
                            stashesDirty = true;
                        }
                    }
                }
            }
        }
    }

    private StashType classifyBlock(Block block) {
        if (scanChests.get() && (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST)) return StashType.CHEST;
        if (scanBarrels.get() && block == Blocks.BARREL) return StashType.BARREL;
        if (scanEnderChests.get() && block == Blocks.ENDER_CHEST) return StashType.ENDER_CHEST;
        if (scanShulkers.get() && block instanceof ShulkerBoxBlock) return StashType.SHULKER;
        if (scanUtility.get() && (block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER || block == Blocks.HOPPER || block == Blocks.DISPENSER || block == Blocks.DROPPER)) return StashType.UTILITY;
        if (scanDecorative.get() && (block == Blocks.BREWING_STAND || block == Blocks.CRAFTER || block == Blocks.CHISELED_BOOKSHELF || block == Blocks.DECORATED_POT)) return StashType.DECORATIVE;
        
        if (scanObsidian.get() && block == Blocks.OBSIDIAN) {
            if (mc.world != null && mc.world.getRegistryKey().equals(World.NETHER)) {
                return StashType.OBSIDIAN;
            }
        }
        return null;
    }

    private void scanChestMinecarts() {
        if (!scanChestMinecarts.get()) return;
        BlockPos playerPos = mc.player.getBlockPos();
        int scanRange = range.get();
        Box searchBox = new Box(
            playerPos.getX() - scanRange, playerPos.getY() - scanRange, playerPos.getZ() - scanRange,
            playerPos.getX() + scanRange, playerPos.getY() + scanRange, playerPos.getZ() + scanRange
        );

        List<ChestMinecartEntity> minecarts = mc.world.getEntitiesByClass(ChestMinecartEntity.class, searchBox, e -> true);
        List<Set<ChestMinecartEntity>> clusters = new ArrayList<>();
        Set<ChestMinecartEntity> assigned = new HashSet<>();
        
        for (ChestMinecartEntity m1 : minecarts) {
            if (assigned.contains(m1)) continue;
            Set<ChestMinecartEntity> cluster = new HashSet<>();
            cluster.add(m1);
            assigned.add(m1);
            
            for (ChestMinecartEntity m2 : minecarts) {
                if (assigned.contains(m2)) continue;
                if (m1.squaredDistanceTo(m2) < 0.5) {
                    cluster.add(m2);
                    assigned.add(m2);
                }
            }
            clusters.add(cluster);
        }

        Set<UUID> seenClusterIds = new HashSet<>();

        for (Set<ChestMinecartEntity> cluster : clusters) {
            if (cluster.isEmpty()) continue;
            
            int count = cluster.size();
            Vec3d centroid = new Vec3d(0, 0, 0);
            UUID clusterId = null;
            
            for (ChestMinecartEntity m : cluster) {
                centroid = centroid.add(com.example.addon.utils.MinecraftCompat.position(m));
                if (clusterId == null || m.getUuid().compareTo(clusterId) < 0) {
                    clusterId = m.getUuid();
                }
            }
            centroid = centroid.multiply(1.0 / count);
            BlockPos bpos = BlockPos.ofFloored(centroid);
            
            seenClusterIds.add(clusterId);
            updateStackedMinecartState(clusterId, bpos, centroid, count);
        }

        var it = knownStackedMinecarts.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            Map.Entry<UUID, StackedState> e = it.next();
            UUID id = e.getKey();
            StackedState s = e.getValue();
            if (seenClusterIds.contains(id)) continue;

            if (++s.missingTicks < 3) continue;

            boolean wasStacked = s.stacked;
            it.remove();
            if (wasStacked) removed++;
        }
        if (removed > 0 && notification.get() && mc.player != null) {
            info("§7Stacked minecart group(s) cleared.");
        }
    }

    private void updateStackedMinecartState(UUID id, BlockPos bpos, Vec3d centroid, int count) {
        final int entryThreshold = stackedMinecartThreshold.get();
        final int exitThreshold  = Math.max(1, entryThreshold - 1);

        StackedState s = knownStackedMinecarts.get(id);
        if (s == null && count < entryThreshold) return; 
        
        if (s == null) s = new StackedState();
        knownStackedMinecarts.put(id, s);
        
        s.observedCount = count;
        s.lastBlockPos  = bpos;
        s.lastCentroid  = centroid;
        s.missingTicks  = 0;

        boolean meetsEntry = count >= entryThreshold;
        boolean meetsExit  = count <= exitThreshold;

        if (!s.stacked) {
            if (meetsEntry) {
                if (++s.entryDebounce >= 3) {
                    s.stacked = true;
                    s.confirmedCount = count;
                    if (notification.get() && mc.player != null) {
                        info("§dStacked minecarts detected! §f%d§d minecarts at one position.", count);
                        mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
                    }
                }
            } else {
                s.entryDebounce = 0;
            }
        } else {
            if (meetsExit) {
                if (++s.exitDebounce >= 3) {
                    s.stacked = false;
                    knownStackedMinecarts.remove(id);
                    if (notification.get() && mc.player != null) {
                        info("§7Stacked minecart group resolved.");
                    }
                }
            } else {
                s.exitDebounce = 0;
            }
        }
    }

    private void scanItemFrames() {
        if (!scanItemFramesSetting.get()) return;
        BlockPos playerPos = mc.player.getBlockPos();
        int scanRange = range.get();
        Box searchBox = new Box(
            playerPos.getX() - scanRange, playerPos.getY() - scanRange, playerPos.getZ() - scanRange,
            playerPos.getX() + scanRange, playerPos.getY() + scanRange, playerPos.getZ() + scanRange
        );
        Set<Vec3d> currentFramePositions = new HashSet<>();
        for (ItemFrameEntity frame : mc.world.getEntitiesByClass(ItemFrameEntity.class, searchBox, entity -> true)) {
            ItemStack heldStack = frame.getHeldItemStack();
            if (heldStack.isEmpty()) continue;
            boolean isShulker = heldStack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
            boolean isCustom  = customItems.get().contains(heldStack.getItem());
            if (!isShulker && !isCustom) continue;
            Vec3d pos = com.example.addon.utils.MinecraftCompat.position(frame); currentFramePositions.add(pos);
            if (frame instanceof GlowItemFrameEntity glow) glowItemFrameEntities.put(pos, glow);
            else itemFrameEntities.put(pos, frame);
            if (notifiedItemFrames.add(pos) && notification.get()) {
                if (isShulker) info("Shulker found in item frame!");
                else           info("Tracked item found in item frame!");
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
        itemFrameEntities.entrySet().removeIf(e -> !currentFramePositions.contains(e.getKey()));
        glowItemFrameEntities.entrySet().removeIf(e -> !currentFramePositions.contains(e.getKey()));
        notifiedItemFrames.removeIf(pos -> !itemFrameEntities.containsKey(pos) && !glowItemFrameEntities.containsKey(pos));
    }

    private void scanDecorativeWorldBlocks() {
        if (mc.player == null || mc.world == null) return;
        if (!scanBeds.get()) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int rangeBlocks    = range.get();
        int chunkRange     = (rangeBlocks >> 4) + 1;
        int centerChunkX   = playerPos.getX() >> 4;
        int centerChunkZ   = playerPos.getZ() >> 4;
        int chunkRangeSq   = chunkRange * chunkRange;
        int maxDistSq      = rangeBlocks * rangeBlocks;

        bedPositions.clear();

        for (int cx = centerChunkX - chunkRange; cx <= centerChunkX + chunkRange; cx++) {
            for (int cz = centerChunkZ - chunkRange; cz <= centerChunkZ + chunkRange; cz++) {
                int dx = cx - centerChunkX, dz = cz - centerChunkZ;
                if (dx * dx + dz * dz > chunkRangeSq) continue;
                WorldChunk chunk = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                ChunkSection[] sections = chunk.getSectionArray();
                for (int sectionIdx = 0; sectionIdx < sections.length; sectionIdx++) {
                    ChunkSection section = sections[sectionIdx];
                    if (section == null || section.isEmpty()) continue;
                    if (!section.hasAny(state -> state.getBlock() instanceof BedBlock)) continue;

                    int baseY = chunk.sectionIndexToCoord(sectionIdx) << 4;
                    int baseX = cx << 4;
                    int baseZ = cz << 4;

                    for (int lx = 0; lx < 16; lx++) {
                        for (int ly = 0; ly < 16; ly++) {
                            for (int lz = 0; lz < 16; lz++) {
                                BlockState state = section.getBlockState(lx, ly, lz);
                                Block block = state.getBlock();
                                BlockPos pos = new BlockPos(baseX + lx, baseY + ly, baseZ + lz);
                                if (pos.getSquaredDistance(playerPos) > maxDistSq) continue;

                                if (block instanceof BedBlock) {
                                    try {
                                        if (state.get(BedBlock.PART) != BedPart.HEAD) continue;
                                    } catch (Exception ignored) { continue; }
                                    DyeColor color = ((BedBlock) block).getColor();
                                    bedPositions.put(pos.toImmutable(), color);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private SettingColor dyeToColor(DyeColor dye, int alpha) {
        return switch (dye) {
            case WHITE      -> new SettingColor(255, 255, 255, alpha);
            case ORANGE     -> new SettingColor(255, 140,   0, alpha);
            case MAGENTA    -> new SettingColor(255,   0, 255, alpha);
            case LIGHT_BLUE -> new SettingColor(100, 200, 255, alpha);
            case YELLOW     -> new SettingColor(255, 240,   0, alpha);
            case LIME       -> new SettingColor(100, 230,  50, alpha);
            case PINK       -> new SettingColor(255, 150, 180, alpha);
            case GRAY       -> new SettingColor(100, 100, 100, alpha);
            case LIGHT_GRAY -> new SettingColor(190, 190, 190, alpha);
            case CYAN       -> new SettingColor(  0, 200, 200, alpha);
            case PURPLE     -> new SettingColor(150,   0, 200, alpha);
            case BLUE       -> new SettingColor( 30,  80, 200, alpha);
            case BROWN      -> new SettingColor(130,  80,  30, alpha);
            case GREEN      -> new SettingColor( 50, 160,  50, alpha);
            case RED        -> new SettingColor(220,  30,  30, alpha);
            case BLACK      -> new SettingColor( 30,  30,  30, alpha);
        };
    }

    private void groupStashes() {
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> active = new HashSet<>();

        List<BlockPos> stashKeys = List.copyOf(stashes.keySet());
        
        for (BlockPos startPos : stashKeys) {
            if (visited.contains(startPos)) continue;
            StashType type = stashes.get(startPos);
            if (type == null) continue;
            
            Set<BlockPos> component = new HashSet<>();
            Queue<BlockPos> queue = new LinkedList<>();
            Box structureBox = new Box(startPos);
            queue.add(startPos); visited.add(startPos);
            
            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                component.add(current);
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.offset(dir);
                    if (stashes.get(neighbor) == type && visited.add(neighbor)) {
                        queue.add(neighbor);
                        structureBox = structureBox.union(new Box(neighbor));
                    }
                }
            }

            if (type == StashType.OBSIDIAN) {
                int maxSz = maxObsidianCluster.get();
                if (maxSz > 0 && component.size() > maxSz) continue;

                if (hasCryingObsidianNearby(structureBox.expand(8.0))) {
                    continue;
                }
            }
            
            BlockPos anchor = componentAnchor(component);
            active.add(anchor);
            
            StashCluster cluster = new StashCluster(structureBox.expand(0.02), component, type);
            stashClusterMap.put(anchor, cluster);
        }

        stashClusterMap.keySet().retainAll(active);
    }

    private boolean hasCryingObsidianNearby(Box searchBox) {
        int minX = (int) Math.floor(searchBox.minX);
        int minY = (int) Math.floor(searchBox.minY);
        int minZ = (int) Math.floor(searchBox.minZ);
        int maxX = (int) Math.ceil(searchBox.maxX);
        int maxY = (int) Math.ceil(searchBox.maxY);
        int maxZ = (int) Math.ceil(searchBox.maxZ);
        
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (mc.world.getBlockState(mutable.set(x, y, z)).getBlock() == Blocks.CRYING_OBSIDIAN) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private BlockPos componentAnchor(Set<BlockPos> comp) {
        BlockPos anchor = null;
        for (BlockPos p : comp) {
            if (anchor == null || p.getY() < anchor.getY() || (p.getY() == anchor.getY() && p.getX() < anchor.getX())) {
                anchor = p;
            }
        }
        return anchor;
    }

    private void cleanupDistantStashes() {
        if (mc.player == null) return;
        double distSq = Math.pow(range.get() + 64, 2);
        
        stashes.entrySet().removeIf(e -> e.getKey().getSquaredDistance(com.example.addon.utils.MinecraftCompat.position(mc.player)) > distSq);
        stashClusterMap.entrySet().removeIf(e -> e.getValue().boundingBox.getCenter().squaredDistanceTo(com.example.addon.utils.MinecraftCompat.position(mc.player)) > distSq);

        int px = mc.player.getBlockPos().getX() >> 4, pz = mc.player.getBlockPos().getZ() >> 4;
        int rSq = (range.get() >> 4) * (range.get() >> 4);
        scannedChunks.removeIf(cp -> (cp.x - px) * (cp.x - px) + (cp.z - pz) * (cp.z - pz) > rSq);
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null || mc.player == null) return;
        
        StashType type = classifyBlock(event.newState.getBlock());
        if (type != null) { 
            if (!isYAllowed(event.pos.getY())) return;
            stashes.put(event.pos, type); 
            stashesDirty = true; 
        } else if (stashes.remove(event.pos) != null) { 
            stashesDirty = true; 
        }

        if (event.newState.getBlock() == Blocks.CRYING_OBSIDIAN || event.oldState.getBlock() == Blocks.CRYING_OBSIDIAN) {
            stashesDirty = true;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        List<BeamData> beamsToRender = new ArrayList<>();
        Set<BlockPos> renderedDoubleChests = new HashSet<>();

        for (StashCluster cluster : stashClusterMap.values()) {
            SettingColor color = getStructureColor(cluster.type);
            if (color == null) continue;

            for (BlockPos pos : cluster.blocks) {
                if (renderedDoubleChests.contains(pos)) continue;

                BlockState state = mc.world.getBlockState(pos);
                Box renderBox;

                if (cluster.type == StashType.CHEST && state.getBlock() instanceof ChestBlock) {
                    try {
                        ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
                        if (chestType != ChestType.SINGLE) {
                            Direction facing = state.get(ChestBlock.FACING);
                            Direction neighborDir = chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
                            BlockPos neighborPos = pos.offset(neighborDir);
                            if (cluster.blocks.contains(neighborPos)) {
                                renderBox = createPaddedDoubleChestBox(pos, neighborPos);
                                renderedDoubleChests.add(neighborPos);
                            } else {
                                renderBox = new Box(pos).expand(0.02);
                            }
                        } else {
                            renderBox = new Box(pos).expand(0.02);
                        }
                    } catch (Exception e) {
                        renderBox = new Box(pos).expand(0.02);
                    }
                } else {
                    renderBox = new Box(pos).expand(0.02);
                }

                if (highlightStyle.get() == HighlightStyle.SPECTRAL) {
                    event.renderer.box(renderBox.expand(spectralExpand.get()), withAlpha(color, spectralFillAlpha.get()), withAlpha(color, spectralLineAlpha.get()), ShapeMode.Both, 0);
                } else if (highlightStyle.get() == HighlightStyle.PULSE) {
                    renderPulseBox(event, renderBox, color);
                } else {
                    renderGlowLayers(event, renderBox, color);
                    event.renderer.box(renderBox, withAlpha(color, 0), color, shapeMode.get(), 0);
                }
            }

            if (!beamsHidden) {
                int count = cluster.blocks.size();
                boolean shouldBeam = false;
                switch (cluster.type) {
                    case CHEST -> shouldBeam = count >= minChestsForBeam.get();
                    case BARREL -> shouldBeam = count >= minBarrelsForBeam.get();
                    case SHULKER -> shouldBeam = count >= minShulkersForBeam.get();
                    case ENDER_CHEST -> shouldBeam = count >= minEnderChestsForBeam.get();
                    case OBSIDIAN -> shouldBeam = count >= minObsidianForBeam.get();
                    default -> shouldBeam = false;
                }

                if (shouldBeam) {
                    SettingColor beamColor = (highlightStyle.get() == HighlightStyle.PULSE) ? pulseColor(color) : color;
                    beamsToRender.add(new BeamData(cluster.boundingBox, beamColor));
                }
            }
        }

        if (scanChestMinecarts.get()) {
            for (Map.Entry<UUID, StackedState> entry : knownStackedMinecarts.entrySet()) {
                StackedState state = entry.getValue();
                if (state.lastBlockPos == null) continue;
                List<ChestMinecartEntity> minecarts = mc.world.getEntitiesByClass(
                    ChestMinecartEntity.class, new Box(state.lastBlockPos), entity -> true);
                
                Box renderBox = minecarts.isEmpty() ? new Box(state.lastBlockPos).expand(0.0625) : getMinecartChestBox(minecarts.get(0));
                SettingColor color = state.stacked ? stackedMinecartColor.get() : chestMinecartColor.get();

                if (highlightStyle.get() == HighlightStyle.SPECTRAL) {
                    event.renderer.box(renderBox, withAlpha(color, spectralFillAlpha.get()), withAlpha(color, spectralLineAlpha.get()), ShapeMode.Both, 0);
                } else if (highlightStyle.get() == HighlightStyle.PULSE) {
                    renderPulseBox(event, renderBox, color);
                } else {
                    renderGlowLayers(event, renderBox, color);
                    event.renderer.box(renderBox, withAlpha(color, 0), color, shapeMode.get(), 0);
                }

                if (!beamsHidden && state.stacked) {
                    beamsToRender.add(new BeamData(renderBox, color));
                }
            }
        }

        renderItemFrames(event, beamsToRender);

        if (scanBeds.get()) renderBeds(event);

        if (!beamsToRender.isEmpty()) {
            renderBeams(event, beamsToRender);
        }
    }

    private void renderItemFrames(Render3DEvent event, List<BeamData> beams) {
        if (!scanItemFramesSetting.get()) return;
        SettingColor color = itemFrameColor.get();
        for (ItemFrameEntity frame : itemFrameEntities.values()) {
            if (frame == null || frame.isRemoved()) continue;
            renderEntityBox(event, frame.getBoundingBox(), color);
            ItemStack held = frame.getHeldItemStack();
            if (held.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) {
                if (!beamsHidden) beams.add(new BeamData(frame.getBoundingBox(), color));
            }
        }
        for (GlowItemFrameEntity frame : glowItemFrameEntities.values()) {
            if (frame == null || frame.isRemoved()) continue;
            renderEntityBox(event, frame.getBoundingBox(), color);
            ItemStack held = frame.getHeldItemStack();
            if (held.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) {
                if (!beamsHidden) beams.add(new BeamData(frame.getBoundingBox(), color));
            }
        }
    }

    private void renderEntityBox(Render3DEvent event, Box box, SettingColor color) {
        if (highlightStyle.get() == HighlightStyle.SPECTRAL) {
            event.renderer.box(box, withAlpha(color, spectralFillAlpha.get()), withAlpha(color, spectralLineAlpha.get()), ShapeMode.Both, 0);
        } else if (highlightStyle.get() == HighlightStyle.PULSE) {
            renderPulseBox(event, box, color);
        } else {
            renderGlowLayers(event, box, color);
            event.renderer.box(box, withAlpha(color, 0), color, shapeMode.get(), 0);
        }
    }

    private void renderBeds(Render3DEvent event) {
        if (mc.world == null) return;
        int fill = bedFillAlpha.get();

        for (Map.Entry<BlockPos, DyeColor> entry : bedPositions.entrySet()) {
            BlockPos pos = entry.getKey();
            DyeColor dye = entry.getValue();

            BlockState state = mc.world.getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock)) continue;

            Direction facing = state.get(BedBlock.FACING);
            BlockPos footPos = pos.offset(facing.getOpposite());
            BlockState footState = mc.world.getBlockState(footPos);
            boolean hasFootBlock = footState.getBlock() instanceof BedBlock;

            Box renderBox;
            if (hasFootBlock) {
                double minX = Math.min(pos.getX(), footPos.getX());
                double minZ = Math.min(pos.getZ(), footPos.getZ());
                double maxX = Math.max(pos.getX(), footPos.getX()) + 1.0;
                double maxZ = Math.max(pos.getZ(), footPos.getZ()) + 1.0;
                renderBox = new Box(minX + 0.0625, pos.getY(), minZ + 0.0625, maxX - 0.0625, pos.getY() + 0.5625, maxZ - 0.0625);
            } else {
                renderBox = new Box(pos.getX() + 0.0625, pos.getY(), pos.getZ() + 0.0625, pos.getX() + 0.9375, pos.getY() + 0.5625, pos.getZ() + 0.9375);
            }

            SettingColor color = dyeToColor(dye, 200);

            if (highlightStyle.get() == HighlightStyle.SPECTRAL) {
                event.renderer.box(renderBox, withAlpha(color, fill), withAlpha(color, spectralLineAlpha.get()), ShapeMode.Both, 0);
            } else if (highlightStyle.get() == HighlightStyle.PULSE) {
                renderPulseBox(event, renderBox, color);
            } else {
                renderGlowLayers(event, renderBox, color);
                event.renderer.box(renderBox, withAlpha(color, fill), color, ShapeMode.Both, 0);
            }
        }
    }

    private void renderGlowLayers(Render3DEvent event, Box box, SettingColor color) {
        int layers = glowLayers.get(); 
        double spread = glowSpread.get(); 
        int baseAlpha = glowBaseAlpha.get();
        for (int i = layers; i >= 1; i--) {
            int layerAlpha = Math.max(4, (int)(baseAlpha * (1.0 - (double)(i-1) / layers)));
            event.renderer.box(box.expand(spread * i), withAlpha(color, layerAlpha), withAlpha(color, 0), ShapeMode.Sides, 0);
        }
    }

    private float getPulseFactor() {
        double speed = pulseSpeed.get();
        double t = System.currentTimeMillis() / 1000.0;
        double phase = t * speed * Math.PI * 2.0;
        return (float)((Math.sin(phase) + 1.0) * 0.5);
    }

    private int applyPulse(int baseAlpha) {
        float f = getPulseFactor();
        int min = pulseMinAlpha.get();
        int max = pulseMaxAlpha.get();
        return Math.min(255, Math.max(0, (int)(min + (max - min) * f)));
    }

    private SettingColor pulseColor(SettingColor base) {
        return withAlpha(base, applyPulse(base.a));
    }

    private void renderPulseBox(Render3DEvent event, Box box, SettingColor base) {
        int pa = applyPulse(base.a);
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

    private void renderBeams(Render3DEvent event, List<BeamData> beams) {
        if (beams.isEmpty()) return;
        if (mergeBeams.get()) {
            List<BeamData> merged = new ArrayList<>();
            double distSq = Math.pow(mergeDistance.get(), 2);
            for (BeamData beam : beams) {
                boolean skip = false;
                double bx = (beam.box.minX + beam.box.maxX) / 2.0;
                double bz = (beam.box.minZ + beam.box.maxZ) / 2.0;
                for (BeamData m : merged) {
                    double mx = (m.box.minX + m.box.maxX) / 2.0;
                    double mz = (m.box.minZ + m.box.maxZ) / 2.0;
                    if (Math.pow(bx - mx, 2) + Math.pow(bz - mz, 2) <= distSq) { skip = true; break; }
                }
                if (!skip) merged.add(beam);
            }
            beams = merged;
        }
        for (BeamData beam : beams) {
            if (beamStyle.get() == BeamStyle.GUARDIAN) renderGuardianBeam(event, beam.box, beam.color);
            else renderBoxBeam(event, beam.box, beam.color);
        }
    }

    private void renderBoxBeam(Render3DEvent event, Box anchorBox, SettingColor color) {
        double beamSize = Math.max(0.01, beamWidth.get() / 100.0);
        double centerX = (anchorBox.minX + anchorBox.maxX) / 2.0;
        double centerZ = (anchorBox.minZ + anchorBox.maxZ) / 2.0;
        int worldBot = mc.world.getBottomY(), worldTop = worldBot + mc.world.getHeight();
        Box beamBox = new Box(centerX - beamSize, worldBot, centerZ - beamSize, centerX + beamSize, worldTop, centerZ + beamSize);
        renderGlowLayers(event, beamBox, color);
        event.renderer.box(beamBox, withAlpha(color, 60), color, ShapeMode.Both, 0);
    }

    private void renderGuardianBeam(Render3DEvent event, Box anchorBox, SettingColor color) {
        if (mc.world == null) return;
        double cx = (anchorBox.minX + anchorBox.maxX) / 2.0;
        double cz = (anchorBox.minZ + anchorBox.maxZ) / 2.0;
        int worldBot = mc.world.getBottomY();
        int worldTop = worldBot + mc.world.getHeight();

        double radius = Math.max(0.01, guardianRadius.get());
        int strands = guardianStrands.get();
        double speed = guardianSpinSpeed.get();

        double rotationRad = (System.currentTimeMillis() % (long)(6000.0 / speed)) / (6000.0 / speed) * Math.PI * 2.0;

        RenderCompat.renderGuardianBeam(event, cx, cz, worldBot, worldTop, radius,
            strands, rotationRad, color, 160);

        double coreR = radius * 0.25;
        Box coreBox = new Box(cx - coreR, worldBot, cz - coreR, cx + coreR, worldTop, cz + coreR);
        event.renderer.box(coreBox, withAlpha(color, 90), withAlpha(color, 130), ShapeMode.Both, 0);
    }

    private Box getMinecartChestBox(ChestMinecartEntity minecart) {
        Box entityBox = minecart.getBoundingBox(); 
        double chestSz = 14.0 / 16.0;
        double xPad = (entityBox.getLengthX() - chestSz) / 2.0;
        double zPad = (entityBox.getLengthZ() - chestSz) / 2.0;
        double minY = entityBox.maxY - (10.0 / 16.0);
        return newBox(entityBox.minX + xPad, minY, entityBox.minZ + zPad,
                      entityBox.maxX - xPad, entityBox.maxY, entityBox.maxZ - zPad);
    }

    private Box newBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Box createPaddedDoubleChestBox(BlockPos pos1, BlockPos pos2) {
        double p = 0.0625;
        double minX = Math.min(pos1.getX(), pos2.getX()), minY = Math.min(pos1.getY(), pos2.getY()), minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxX = Math.max(pos1.getX(), pos2.getX())+1, maxY = Math.max(pos1.getY(), pos2.getY())+1, maxZ = Math.max(pos1.getZ(), pos2.getZ())+1;
        return new Box(minX+p, minY+p, minZ+p, maxX-p, maxY-p, maxZ-p);
    }

    private SettingColor getStructureColor(StashType type) {
        return switch (type) {
            case CHEST -> chestColor.get();
            case BARREL -> barrelColor.get();
            case SHULKER -> shulkerColor.get();
            case ENDER_CHEST -> enderColor.get();
            case OBSIDIAN -> obsidianColor.get();
            case UTILITY -> utilityColor.get();
            case DECORATIVE -> decorativeColor.get();
        };
    }

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }

    public void markChunkDirty(ChunkPos cp) { scannedChunks.remove(cp); dirtyChunks.add(cp); stashesDirty = true; }

    private enum StashType { CHEST, BARREL, SHULKER, ENDER_CHEST, OBSIDIAN, UTILITY, DECORATIVE }
    
    private static class StashCluster {
        final Box boundingBox; 
        final Set<BlockPos> blocks; 
        final StashType type;
        
        StashCluster(Box bb, Set<BlockPos> pb, StashType t) {
            this.boundingBox = bb; 
            this.blocks = pb; 
            this.type = t;
        }
    }

    private static final class StackedState {
        boolean stacked         = false;
        int     observedCount   = 0;
        int     confirmedCount  = 0;
        int     entryDebounce   = 0;
        int     exitDebounce    = 0;
        int     missingTicks    = 0;
        BlockPos lastBlockPos   = null;
        Vec3d   lastCentroid    = null;
    }
    
    private record BeamData(Box box, SettingColor color) {}
}