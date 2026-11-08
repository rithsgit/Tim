package com.example.addon.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.addon.Tim;
import com.example.addon.render.RenderCompat;
import com.example.addon.utils.GlowingRegistry;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
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
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class DungeonAssistant extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enums
    // ═══════════════════════════════════════════════════════════════════════════

    public enum TargetType {
        SPAWNER,
        CHEST,
        CHEST_MINECART,
        MISROTATED_CHEST_MINECART,
        DISPLACED_CHEST_MINECART,
        CUSTOM_BLOCK,
        MISROTATED_DEEPSLATE,
        LOW_Y_STONE_DIRT
    }

    public enum RenderMode {
        GLOW,
        SPECTRAL,
        PULSE
    }

    public enum ChestBeamMode {
        NONE,
        NEAREST,
        ALL
    }

    public enum SpawnerBeamMode {
        NONE,
        ALL
    }

    public enum BeamStyle {
        BOX,
        GUARDIAN
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Constants
    // ═══════════════════════════════════════════════════════════════════════════

    private static final int DIMENSION_CHANGE_COOLDOWN_TICKS = 40;
    private static final int INTERACT_TIMEOUT_TICKS          = 20;
    private static final int SILENT_SLOT_READ_MAX_RETRIES    = 5;

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    private final Map<BlockPos, TargetType>  targets               = new ConcurrentHashMap<>();
    private final Set<ChunkPos>              scannedChunks         = new HashSet<>();
    private final Set<BlockPos>              checkedContainers     = new HashSet<>();
    private final List<Endermite>      endermiteTargets      = new ArrayList<>();
    private final List<ExperienceOrb>  xpOrbTargets          = new ArrayList<>();
    private final List<Mob>            spawnerMobTargets     = new ArrayList<>();
    private final Set<Integer>               notifiedEndermites    = new HashSet<>();
    private final Set<Integer>               notifiedSpawnerMobs   = new HashSet<>();
    private final Set<Integer>               spawnerMobGlowingIds  = new HashSet<>();
    private final Set<Integer>               checkedEntityIds      = new HashSet<>();
    private final Set<Integer>               notifiedAnomalousMinecarts = new HashSet<>();
    private final Set<BlockPos>              spawnerTorches        = new HashSet<>();
    private final Set<BlockPos>              activeSpawners        = new HashSet<>();
    private int                              spawnerActionBarCooldown = 0;

    // Breaking
    private boolean  isBreaking        = false;
    private boolean  isBreakingEntity  = false;
    private boolean  isBreakingChest   = false;
    private BlockPos blockToBreak      = null;
    private Entity   entityToBreak     = null;
    private int      breakDelayTimer   = 0;
    private int      previousSlot      = -1;
    private int      brokenChestsCount = 0;
    private int      lootFoundCount    = 0;

    // Auto-open
    private boolean  wasAutoOpened                  = false;
    private boolean  hasPlayedSoundForCurrentScreen = false;
    private BlockPos lastOpenedContainer            = null;
    private Entity   lastOpenedEntity               = null;
    private int      interactTimeoutTimer           = 0;

    // Silent open
    private boolean silentOpenPending        = false;
    private boolean silentFoundWhitelisted   = false;
    private boolean pendingBreakCheck        = false;
    private int     silentSlotReadRetryTimer = 0;

    // Dimension tracking
    private String lastDimension          = "";
    private int    dimensionChangeCooldown = 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups (Consolidated)
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgBlocks     = settings.createGroup("Targets - Blocks");
    private final SettingGroup sgAnomalies  = settings.createGroup("Targets - Anomalies");
    private final SettingGroup sgEntities   = settings.createGroup("Targets - Entities");
    private final SettingGroup sgAutomation = settings.createGroup("Automation");
    private final SettingGroup sgSafety     = settings.createGroup("Safety");
    private final SettingGroup sgBeam       = settings.createGroup("Beams");

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — General
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Detection range in chunks (1 chunk = 16 blocks). High values impact performance.")
        .defaultValue(16).min(1).max(128).sliderMin(1).sliderMax(64)
        .build()
    );

    private final Setting<Integer> dungeonYLevel = sgGeneral.add(new IntSetting.Builder()
        .name("dungeon-y-level")
        .description("Maximum Y level to scan. Anything above this is ignored.")
        .defaultValue(100).min(-64).max(320).sliderMin(-64).sliderMax(320)
        .onChanged(v -> {
            scannedChunks.clear();
            final int maxY = v; 
            targets.entrySet().removeIf(entry -> {
                TargetType type = entry.getValue();
                if (type == TargetType.CHEST_MINECART || type == TargetType.MISROTATED_CHEST_MINECART || type == TargetType.DISPLACED_CHEST_MINECART) return false;
                return entry.getKey().getY() > maxY;
            });
        })
        .build()
    );

    private final Setting<RenderMode> renderMode = sgGeneral.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode")
        .description("GLOW = layered bloom boxes. SPECTRAL = outline shader for entities, subtle fill for blocks. PULSE = fading in/out highlight.")
        .defaultValue(RenderMode.GLOW)
        .onChanged(v -> rebuildSpectralRegistry())
        .build()
    );

    private final Setting<Integer> glowLayers = sgGeneral.add(new IntSetting.Builder()
        .name("glow-layers").description("Number of bloom layers rendered around each target.")
        .defaultValue(4).min(1).sliderMax(8)
        .visible(() -> renderMode.get() == RenderMode.GLOW || renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Setting<Double> glowSpread = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-spread").description("How far each bloom layer expands outward (in blocks).")
        .defaultValue(0.04).min(0.01).sliderMax(0.15)
        .visible(() -> renderMode.get() == RenderMode.GLOW || renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Setting<Integer> glowBaseAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("glow-base-alpha").description("Alpha of the innermost glow layer (0-255).")
        .defaultValue(60).min(10).sliderMax(150)
        .visible(() -> renderMode.get() == RenderMode.GLOW)
        .build()
    );

    private final Setting<Integer> spectralBlockFillAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("spectral-block-fill-alpha")
        .description("Fill alpha for block targets in SPECTRAL mode (0 = invisible, 30 = subtle).")
        .defaultValue(30).min(0).max(120).sliderMax(80)
        .visible(() -> renderMode.get() == RenderMode.SPECTRAL)
        .build()
    );

    private final Setting<Double> pulseSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("pulse-speed")
        .description("Pulse cycle speed. 1.0 = one full fade in/out per second.")
        .defaultValue(1.0).min(0.1).max(5.0).sliderMax(3.0)
        .visible(() -> renderMode.get() == RenderMode.PULSE).build()
    );

    private final Setting<Integer> pulseMinAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("pulse-min-alpha")
        .description("Lowest alpha reached during the pulse (0 = invisible).")
        .defaultValue(15).min(0).max(255).sliderMax(100)
        .visible(() -> renderMode.get() == RenderMode.PULSE).build()
    );

    private final Setting<Integer> pulseMaxAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("pulse-max-alpha")
        .description("Peak alpha reached during the pulse.")
        .defaultValue(220).min(15).max(255).sliderMax(255)
        .visible(() -> renderMode.get() == RenderMode.PULSE).build()
    );

    private final Setting<Boolean> stealDumpButtons = sgGeneral.add(new BoolSetting.Builder()
        .name("steal-dump-buttons")
        .description("Show steal and dump buttons on container screens.")
        .defaultValue(true)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Beams
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<BeamStyle> beamStyle = sgBeam.add(new EnumSetting.Builder<BeamStyle>()
        .name("beam-style")
        .description("BOX = simple axis-aligned box beam. GUARDIAN = spinning guardian-style beam.")
        .defaultValue(BeamStyle.GUARDIAN)
        .build()
    );

    private final Setting<Integer> beamWidth = sgBeam.add(new IntSetting.Builder()
        .name("beam-width").description("Box beam width (in hundredths of a block).")
        .defaultValue(15).min(5).max(50).sliderMin(5).sliderMax(50)
        .visible(() -> beamStyle.get() == BeamStyle.BOX)
        .build()
    );

    private final Setting<Boolean> mergeBeams = sgBeam.add(new BoolSetting.Builder()
        .name("merge-beams").description("Merge beams for nearby targets to reduce clutter.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> mergeDistance = sgBeam.add(new DoubleSetting.Builder()
        .name("merge-distance").description("Distance within which beams are merged.")
        .defaultValue(2.0).min(0).sliderMax(10).visible(mergeBeams::get)
        .build()
    );

    private final Setting<Double> guardianBeamRadius = sgBeam.add(new DoubleSetting.Builder()
        .name("guardian-radius")
        .description("Radius of the guardian beam strands from centre (blocks).")
        .defaultValue(0.08).min(0.01).max(0.6).sliderMax(0.3)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN)
        .build()
    );

    private final Setting<Integer> guardianStrands = sgBeam.add(new IntSetting.Builder()
        .name("guardian-strands")
        .description("Number of spinning flat quads that make up the beam (2-8).")
        .defaultValue(4).min(2).max(8).sliderMax(8)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN)
        .build()
    );

    private final Setting<Double> guardianSpinSpeed = sgBeam.add(new DoubleSetting.Builder()
        .name("guardian-spin-speed")
        .description("How fast the beam rotates. 1.0 = one full revolution every ~6 seconds.")
        .defaultValue(1.0).min(0.1).max(5.0).sliderMax(3.0)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN)
        .build()
    );

    private final Setting<Integer> guardianCoreAlpha = sgBeam.add(new IntSetting.Builder()
        .name("guardian-core-alpha")
        .description("Alpha of the solid centre core of the guardian beam (0 = no core).")
        .defaultValue(90).min(0).max(255).sliderMax(200)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN)
        .build()
    );

    private final Setting<Integer> guardianStrandAlpha = sgBeam.add(new IntSetting.Builder()
        .name("guardian-strand-alpha")
        .description("Alpha of the outer spinning strands.")
        .defaultValue(160).min(10).max(255).sliderMax(255)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN)
        .build()
    );

    private final Setting<Boolean> guardianGlow = sgBeam.add(new BoolSetting.Builder()
        .name("guardian-glow")
        .description("Add a soft bloom halo around the guardian beam.")
        .defaultValue(true)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN)
        .build()
    );

    private final Setting<Double> guardianGlowRadius = sgBeam.add(new DoubleSetting.Builder()
        .name("guardian-glow-radius")
        .description("Radius of the bloom halo around the guardian beam.")
        .defaultValue(0.18).min(0.02).max(1.0).sliderMax(0.5)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN && guardianGlow.get())
        .build()
    );

    private final Setting<ChestBeamMode> chestBeamMode = sgBeam.add(new EnumSetting.Builder<ChestBeamMode>()
        .name("chest-beam-mode")
        .description("Connecting beams from chests to the sky. NONE = disabled. NEAREST = beam on closest chest only. ALL = beams on every chest in range.")
        .defaultValue(ChestBeamMode.NONE)
        .build()
    );

    private final Setting<Integer> chestBeamMinY = sgBeam.add(new IntSetting.Builder()
        .name("chest-beam-y-level")
        .description("Only renders beams on chests at or above this Y level.")
        .defaultValue(-64).min(-64).max(320).sliderMin(-64).sliderMax(320)
        .visible(() -> chestBeamMode.get() != ChestBeamMode.NONE)
        .build()
    );

    private final Setting<SettingColor> chestBeamColor = sgBeam.add(new ColorSetting.Builder()
        .name("chest-beam-color")
        .description("Color of the beams drawn on chests.")
        .defaultValue(new SettingColor(255, 215, 0, 180))
        .visible(() -> chestBeamMode.get() != ChestBeamMode.NONE)
        .build()
    );

    private final Setting<SpawnerBeamMode> spawnerBeamMode = sgBeam.add(new EnumSetting.Builder<SpawnerBeamMode>()
        .name("spawner-beam-mode")
        .description("Renders beams to the sky for active spawners to notify where they actually are.")
        .defaultValue(SpawnerBeamMode.ALL)
        .build()
    );

    private final Setting<SettingColor> spawnerBeamColor = sgBeam.add(new ColorSetting.Builder()
        .name("spawner-beam-color")
        .description("Color of the active spawner beams.")
        .defaultValue(new SettingColor(255, 50, 50, 180))
        .visible(() -> spawnerBeamMode.get() != SpawnerBeamMode.NONE)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Targets - Blocks
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> trackSpawners = sgBlocks.add(new BoolSetting.Builder()
        .name("track-spawners").description("Highlight monster spawners.").defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> spawnerColor = sgBlocks.add(new ColorSetting.Builder()
        .name("spawner-color").description("Monster spawner highlight color.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(trackSpawners::get).build()
    );

    private final Setting<Boolean> highlightSpawnerTorches = sgBlocks.add(new BoolSetting.Builder()
        .name("highlight-spawner-torches").description("Highlights torches within 5 blocks of a spawner.")
        .defaultValue(true).visible(trackSpawners::get)
        .build()
    );

    private final Setting<SettingColor> spawnerTorchColor = sgBlocks.add(new ColorSetting.Builder()
        .name("spawner-torch-color").description("Color for torches near spawners.")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .visible(() -> trackSpawners.get() && highlightSpawnerTorches.get()).build()
    );

    private final Setting<Boolean> trackChests = sgBlocks.add(new BoolSetting.Builder()
        .name("track-chests").description("Highlight chests and count broken ones.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> chestColor = sgBlocks.add(new ColorSetting.Builder()
        .name("chest-color").description("Chest highlight color.")
        .defaultValue(new SettingColor(255, 215, 0, 255))
        .visible(trackChests::get).build()
    );

    private final Setting<Boolean> scanCustomBlocks = sgBlocks.add(new BoolSetting.Builder()
        .name("scan-blocks")
        .description("Highlight selected blocks in the surrounding area.")
        .defaultValue(true)
        .onChanged(v -> { targets.entrySet().removeIf(e -> e.getValue() == TargetType.CUSTOM_BLOCK); scannedChunks.clear(); })
        .build()
    );

    private final Setting<List<Block>> filterBlocks = sgBlocks.add(new BlockListSetting.Builder()
        .name("blocks").description("Blocks to search for and highlight in the world.")
        .defaultValue(List.of(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLED_DEEPSLATE, Blocks.NETHERRACK))
        .onChanged(v -> { targets.entrySet().removeIf(e -> e.getValue() == TargetType.CUSTOM_BLOCK); scannedChunks.clear(); })
        .visible(scanCustomBlocks::get).build()
    );

    private final Setting<SettingColor> customBlockColor = sgBlocks.add(new ColorSetting.Builder()
        .name("block-color").description("Highlight color for the selected blocks.")
        .defaultValue(new SettingColor(128, 200, 128, 255))
        .visible(scanCustomBlocks::get).build()
    );

    private final Setting<Keybind> toggleBlocksKey = sgBlocks.add(new KeybindSetting.Builder()
        .name("toggle-key").description("Key to toggle custom block scanning on/off.")
        .defaultValue(Keybind.none())
        .action(() -> {
            if (mc.screen != null) return;
            boolean newValue = !scanCustomBlocks.get();
            scanCustomBlocks.set(newValue);
            if (mc.player != null) info("Custom Blocks Highlight toggled %s.", newValue ? "§aON" : "§cOFF");
        })
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Targets - Anomalies
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> trackMisrotatedDeepslate = sgAnomalies.add(new BoolSetting.Builder()
        .name("misrotated-deepslate")
        .description("Highlights Deepslate blocks facing the wrong direction (axis ≠ Y).")
        .defaultValue(false)
        .onChanged(v -> { targets.entrySet().removeIf(e -> e.getValue() == TargetType.MISROTATED_DEEPSLATE); scannedChunks.clear(); })
        .build()
    );

    private final Setting<SettingColor> misrotatedDeepslateColor = sgAnomalies.add(new ColorSetting.Builder()
        .name("misrotated-deepslate-color").description("Highlight color for misrotated Deepslate blocks.")
        .defaultValue(new SettingColor(0, 180, 255, 255))
        .visible(trackMisrotatedDeepslate::get).build()
    );

    private final Setting<Boolean> trackLowYStoneDirt = sgAnomalies.add(new BoolSetting.Builder()
        .name("low-y-stone-dirt")
        .description("Highlights Stone and Dirt below a specified Y level.")
        .defaultValue(false)
        .onChanged(v -> { targets.entrySet().removeIf(e -> e.getValue() == TargetType.LOW_Y_STONE_DIRT); scannedChunks.clear(); })
        .build()
    );

    private final Setting<Integer> lowYLevel = sgAnomalies.add(new IntSetting.Builder()
        .name("low-y-level")
        .description("The Y level below which Stone and Dirt will be highlighted.")
        .defaultValue(-5).min(-64).max(320)
        .visible(trackLowYStoneDirt::get)
        .onChanged(v -> { targets.entrySet().removeIf(e -> e.getValue() == TargetType.LOW_Y_STONE_DIRT); scannedChunks.clear(); })
        .build()
    );

    private final Setting<SettingColor> lowYStoneDirtColor = sgAnomalies.add(new ColorSetting.Builder()
        .name("low-y-color")
        .description("Highlight color for Stone and Dirt below the Y level.")
        .defaultValue(new SettingColor(128, 128, 128, 255))
        .visible(trackLowYStoneDirt::get).build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Targets - Entities
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> trackChestMinecarts = sgEntities.add(new BoolSetting.Builder()
        .name("track-chest-minecarts").description("Highlight chest minecarts.").defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> chestMinecartColor = sgEntities.add(new ColorSetting.Builder()
        .name("chest-minecart-color").description("Chest minecart highlight color.")
        .defaultValue(new SettingColor(255, 180, 0, 255))
        .visible(trackChestMinecarts::get).build()
    );

    private final Setting<Boolean> trackAnomalousMinecarts = sgEntities.add(new BoolSetting.Builder()
        .name("minecart-anomalies")
        .description("Highlights chest minecarts that are physically displaced or facing wrong angles.")
        .defaultValue(true).visible(trackChestMinecarts::get)
        .build()
    );

    private final Setting<SettingColor> misrotatedMinecartColor = sgEntities.add(new ColorSetting.Builder()
        .name("misrotated-minecart-color").description("Color for misrotated chest minecarts.")
        .defaultValue(new SettingColor(180, 0, 255, 255)) // Purple
        .visible(() -> trackChestMinecarts.get() && trackAnomalousMinecarts.get()).build()
    );

    private final Setting<SettingColor> displacedMinecartColor = sgEntities.add(new ColorSetting.Builder()
        .name("displaced-minecart-color").description("Color for physically displaced chest minecarts.")
        .defaultValue(new SettingColor(0, 255, 255, 255)) // Cyan
        .visible(() -> trackChestMinecarts.get() && trackAnomalousMinecarts.get()).build()
    );

    private final Setting<Boolean> trackEndermites = sgEntities.add(new BoolSetting.Builder()
        .name("track-endermites").description("Highlights Endermites in the Overworld.").defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> endermiteColor = sgEntities.add(new ColorSetting.Builder()
        .name("endermite-color").description("The highlight color for Endermites.")
        .defaultValue(new SettingColor(138, 43, 226, 255))
        .visible(trackEndermites::get).build()
    );

    private final Setting<Boolean> trackXpOrbs = sgEntities.add(new BoolSetting.Builder()
        .name("track-xp-orbs")
        .description("Highlights Experience Orbs in the world.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> xpOrbColor = sgEntities.add(new ColorSetting.Builder()
        .name("xp-orb-color")
        .description("The highlight color for Experience Orbs.")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .visible(trackXpOrbs::get).build()
    );

    private final Setting<Boolean> trackSpawnerMobs = sgEntities.add(new BoolSetting.Builder()
        .name("track-spawner-mobs")
        .description("Highlights mobs within 5 blocks of a spawner, indicating activity without leaking coords.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> spawnerChestRadius = sgEntities.add(new DoubleSetting.Builder()
        .name("spawner-chest-radius")
        .description("Only treats a spawner as active if a chest is found within this radius (to prevent false flags).")
        .defaultValue(6.0).min(1.0).sliderMax(15.0)
        .visible(trackSpawnerMobs::get)
        .build()
    );

    private final Setting<SettingColor> spawnerMobColor = sgEntities.add(new ColorSetting.Builder()
        .name("spawner-mob-color")
        .description("The highlight color for mobs near a spawner.")
        .defaultValue(new SettingColor(255, 100, 0, 255))
        .visible(trackSpawnerMobs::get).build()
    );

    private final Setting<SettingColor> activeSpawnerColor = sgEntities.add(new ColorSetting.Builder()
        .name("active-spawner-color")
        .description("Highlight color for spawners that currently have mobs near them.")
        .defaultValue(new SettingColor(255, 50, 50, 255)) // Bright Red
        .visible(trackSpawnerMobs::get)
        .build());

    private final Setting<Double> spawnerAlertVolume = sgEntities.add(new DoubleSetting.Builder()
        .name("alert-volume")
        .description("Volume of the spawner activation alert sound.")
        .defaultValue(1.0).min(0).sliderMax(1.0)
        .visible(trackSpawnerMobs::get)
        .build());

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Automation
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> autoOpen = sgAutomation.add(new BoolSetting.Builder()
        .name("auto-open")
        .description("Automatically opens and checks nearby containers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> silentMode = sgAutomation.add(new BoolSetting.Builder()
        .name("silent-mode")
        .description("Open containers invisibly and switch tools silently.")
        .defaultValue(true).visible(autoOpen::get)
        .build()
    );

    private final Setting<Boolean> autoBreak = sgAutomation.add(new BoolSetting.Builder()
        .name("auto-break")
        .description("Automatically break empty containers after opening them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> breakDelay = sgAutomation.add(new IntSetting.Builder()
        .name("break-delay").description("Ticks to wait before breaking an empty container.")
        .defaultValue(5).min(0).max(40).sliderMin(0).sliderMax(20)
        .visible(autoBreak::get)
        .build()
    );

    private final Setting<List<Item>> whitelistedItems = sgAutomation.add(new ItemListSetting.Builder()
        .name("whitelisted-items")
        .description("Items to look for — if found the container is left open and a sound plays.")
        .defaultValue(List.of(
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.ENDER_CHEST,
            Items.SHULKER_BOX,
            Items.WHITE_SHULKER_BOX,      Items.ORANGE_SHULKER_BOX,
            Items.MAGENTA_SHULKER_BOX,    Items.LIGHT_BLUE_SHULKER_BOX,
            Items.YELLOW_SHULKER_BOX,     Items.LIME_SHULKER_BOX,
            Items.PINK_SHULKER_BOX,       Items.GRAY_SHULKER_BOX,
            Items.LIGHT_GRAY_SHULKER_BOX, Items.CYAN_SHULKER_BOX,
            Items.PURPLE_SHULKER_BOX,     Items.BLUE_SHULKER_BOX,
            Items.BROWN_SHULKER_BOX,      Items.GREEN_SHULKER_BOX,
            Items.RED_SHULKER_BOX,        Items.BLACK_SHULKER_BOX
        ))
        .visible(autoOpen::get)
        .build()
    );

    private final Setting<Boolean> autoBreakSpawners = sgAutomation.add(new BoolSetting.Builder()
        .name("auto-break-spawners").description("Automatically break spawners in range.").defaultValue(false)
        .build()
    );

    private final Setting<Integer> spawnerBreakRange = sgAutomation.add(new IntSetting.Builder()
        .name("spawner-break-range").description("Range in blocks to break spawners.")
        .defaultValue(5).min(1).max(10).sliderRange(1, 10)
        .visible(autoBreakSpawners::get).build()
    );

    private final Setting<Integer> spawnerBreakDelay = sgAutomation.add(new IntSetting.Builder()
        .name("spawner-break-delay").description("Ticks to wait before breaking a spawner.")
        .defaultValue(5).min(0).max(20)
        .visible(autoBreakSpawners::get).build()
    );

    private final Setting<Boolean> prioritizeSpawners = sgAutomation.add(new BoolSetting.Builder()
        .name("prioritize-spawners")
        .description("Break spawners before opening chests when both auto-break and auto-open are active.")
        .defaultValue(true).visible(() -> autoOpen.get() && autoBreakSpawners.get())
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Safety
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> autoDisableOnLowHealth = sgSafety.add(new BoolSetting.Builder()
        .name("auto-disable-on-low-health")
        .description("Automatically disables the module if health is critically low with a totem equipped.")
        .defaultValue(true).build()
    );

    private final Setting<Integer> lowHealthThreshold = sgSafety.add(new IntSetting.Builder()
        .name("low-health-threshold").description("Health level (in hearts) to trigger auto-disable.")
        .defaultValue(3).min(1).max(10).sliderRange(1, 5)
        .visible(autoDisableOnLowHealth::get).build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Multi-Version Registry Helpers (1.21.4 - 1.21.11)
    // ═══════════════════════════════════════════════════════════════════════════
    private static Item optItem(String path) {
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.withDefaultNamespace(path);
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id) ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id) : null;
    }

    private static Block optBlock(String path) {
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.withDefaultNamespace(path);
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id) ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(id) : null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public DungeonAssistant() {
        super(Tim.CATEGORY, "dungeon-assistant",
            "Highlights dungeon elements: spawners, chests, and dungeon blocks.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        targets.clear();
        scannedChunks.clear();
        checkedContainers.clear();
        endermiteTargets.clear();
        xpOrbTargets.clear();
        spawnerMobTargets.clear();
        notifiedEndermites.clear();
        notifiedSpawnerMobs.clear();
        spawnerMobGlowingIds.clear();
        checkedEntityIds.clear();
        notifiedAnomalousMinecarts.clear();
        spawnerTorches.clear();
        activeSpawners.clear();
        spawnerActionBarCooldown = 0;
        brokenChestsCount = 0;
        lootFoundCount    = 0;
        isBreakingChest = false;
        hasPlayedSoundForCurrentScreen = false;
        GlowingRegistry.clear();

        if (mc.player != null && mc.level != null) {
            info("§6Dungeon Assistant activated");
            if (mc.level.dimension() != null) {
                lastDimension = mc.level.dimension().identifier().toString();
            }
        }
        
        rebuildSpectralRegistry();
    }

    @Override
    public void onDeactivate() {
        if (isBreaking && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
        restoreSlot();
        GlowingRegistry.clear();

        targets.clear();
        scannedChunks.clear();
        checkedContainers.clear();
        endermiteTargets.clear();
        xpOrbTargets.clear();
        spawnerMobTargets.clear();
        notifiedEndermites.clear();
        notifiedSpawnerMobs.clear();
        spawnerMobGlowingIds.clear();
        checkedEntityIds.clear();
        notifiedAnomalousMinecarts.clear();
        spawnerTorches.clear();
        activeSpawners.clear();
        spawnerActionBarCooldown = 0;

        resetSoftState();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Event Handlers
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (wasAutoOpened) {
            interactTimeoutTimer = 0;
            if (autoOpen.get() && silentMode.get()
                    && event.screen instanceof AbstractContainerScreen<?>
                    && !(event.screen instanceof InventoryScreen)) {
                silentOpenPending = true;
                silentSlotReadRetryTimer = 0;
            }
            return;
        }

        HitResult hit = mc.hitResult;
        if (hit != null) {
            if (hit.getType() == HitResult.Type.BLOCK) {
                lastOpenedContainer = ((BlockHitResult) hit).getBlockPos();
                lastOpenedEntity = null;
            } else if (hit.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHit = (EntityHitResult) hit;
                if (entityHit.getEntity() instanceof MinecartChest) {
                    lastOpenedEntity    = entityHit.getEntity();
                    lastOpenedContainer = null;
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        if (performSafetyChecks()) return;
        updateBreakingLogic();
        updateContainerLogic();
        updateScanningLogic();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        boolean isSpectral = renderMode.get() == RenderMode.SPECTRAL;
        boolean isPulse    = renderMode.get() == RenderMode.PULSE;
        Set<BlockPos> toRemove = new HashSet<>();
        List<BeamData> beamsToRender = new ArrayList<>();

        for (Map.Entry<BlockPos, TargetType> entry : targets.entrySet()) {
            BlockPos   pos  = entry.getKey();
            TargetType type = entry.getValue();

            AABB renderBox;
            SettingColor color;

            if (type == TargetType.CHEST_MINECART || type == TargetType.MISROTATED_CHEST_MINECART || type == TargetType.DISPLACED_CHEST_MINECART) {
                AABB queryBox = new AABB(pos).inflate(0.5);
                List<MinecartChest> minecarts = mc.level.getEntitiesOfClass(
                    MinecartChest.class, queryBox, entity -> true);
                if (minecarts.isEmpty()) { toRemove.add(pos); continue; }

                MinecartChest cart = minecarts.get(0);
                renderBox = getMinecartChestBox(cart);
                color = getColor(type);

                if (type == TargetType.MISROTATED_CHEST_MINECART || type == TargetType.DISPLACED_CHEST_MINECART) {
                    beamsToRender.add(new BeamData(renderBox, color));
                }

            } else {
                if (!mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                if (mc.level.getBlockState(pos).isAir()) { toRemove.add(pos); continue; }

                Block currentBlock = mc.level.getBlockState(pos).getBlock();
                if (type == TargetType.SPAWNER || type == TargetType.CHEST || type == TargetType.MISROTATED_DEEPSLATE || type == TargetType.LOW_Y_STONE_DIRT) {
                    if (!validateBlockType(currentBlock, type)) { toRemove.add(pos); continue; }
                }

                renderBox = createPaddedBox(pos);
                color = getColor(type);

                if (type == TargetType.SPAWNER && activeSpawners.contains(pos)) {
                    color = activeSpawnerColor.get();
                }
            }

            if (color == null) continue;

            if (isSpectral) {
                int fillAlpha = (type == TargetType.CHEST_MINECART || type == TargetType.MISROTATED_CHEST_MINECART || type == TargetType.DISPLACED_CHEST_MINECART) ? 0 : spectralBlockFillAlpha.get();
                int outlineAlpha = (type == TargetType.CHEST_MINECART || type == TargetType.MISROTATED_CHEST_MINECART || type == TargetType.DISPLACED_CHEST_MINECART) ? 200 : 0;
                event.renderer.box(renderBox, withAlpha(color, fillAlpha), withAlpha(color, outlineAlpha), ShapeMode.Sides, 0);
            } else if (isPulse) {
                renderPulseBox(event, renderBox, color);
            } else {
                renderGlowLayers(event, renderBox, color);
                event.renderer.box(renderBox, withAlpha(color, 0), color, ShapeMode.Lines, 0);
            }
        }

        for (BlockPos pos : toRemove) {
            targets.remove(pos);
        }

        if (!spawnerTorches.isEmpty() && trackSpawners.get() && highlightSpawnerTorches.get()) {
            SettingColor torchColor = spawnerTorchColor.get();
            for (BlockPos pos : spawnerTorches) {
                if (!mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                AABB torchBox = createPaddedBox(pos);
                
                if (isSpectral) {
                    event.renderer.box(torchBox, withAlpha(torchColor, spectralBlockFillAlpha.get()), withAlpha(torchColor, 0), ShapeMode.Sides, 0);
                } else if (isPulse) {
                    renderPulseBox(event, torchBox, torchColor);
                } else {
                    renderGlowLayers(event, torchBox, torchColor);
                    event.renderer.box(torchBox, withAlpha(torchColor, 0), torchColor, ShapeMode.Lines, 0);
                }
            }
        }

        if (trackEndermites.get() && !endermiteTargets.isEmpty()) {
            SettingColor color = endermiteColor.get();
            for (Endermite endermite : endermiteTargets) {
                if (!endermite.isAlive()) continue;

                AABB entityBox = endermite.getBoundingBox();
                beamsToRender.add(new BeamData(entityBox, color));

                if (isSpectral) {
                    event.renderer.box(entityBox, withAlpha(color, 0), withAlpha(color, 200), ShapeMode.Lines, 0);
                } else if (isPulse) {
                    renderPulseBox(event, entityBox, color);
                } else {
                    renderGlowLayers(event, entityBox, color);
                    event.renderer.box(entityBox, withAlpha(color, 0), color, ShapeMode.Lines, 0);
                }
            }
        }

        if (trackXpOrbs.get() && !xpOrbTargets.isEmpty()) {
            SettingColor color = xpOrbColor.get();
            for (ExperienceOrb orb : xpOrbTargets) {
                if (!orb.isAlive()) continue;
                AABB orbBox = orb.getBoundingBox();

                if (isSpectral) {
                    event.renderer.box(orbBox, withAlpha(color, 0), withAlpha(color, 200), ShapeMode.Lines, 0);
                } else if (isPulse) {
                    renderPulseBox(event, orbBox, color);
                } else {
                    renderGlowLayers(event, orbBox, color);
                    event.renderer.box(orbBox, withAlpha(color, 0), color, ShapeMode.Lines, 0);
                }
            }
        }

        if (trackSpawnerMobs.get() && !spawnerMobTargets.isEmpty()) {
            SettingColor color = spawnerMobColor.get();
            for (Mob mob : spawnerMobTargets) {
                if (!mob.isAlive()) continue;
                AABB entityBox = mob.getBoundingBox();

                if (isSpectral) {
                    event.renderer.box(entityBox, withAlpha(color, 0), withAlpha(color, 200), ShapeMode.Lines, 0);
                } else if (isPulse) {
                    renderPulseBox(event, entityBox, color);
                } else {
                    renderGlowLayers(event, entityBox, color);
                    event.renderer.box(entityBox, withAlpha(color, 0), color, ShapeMode.Lines, 0);
                }
            }
        }

        // Chest Beams Collection
        if (chestBeamMode.get() != ChestBeamMode.NONE) {
            double maxDistSq = Math.pow(range.get() * 16, 2);
            SettingColor beamColor = chestBeamColor.get();
            int minY = chestBeamMinY.get();

            List<BlockPos> chests = targets.entrySet().stream()
                .filter(e -> e.getValue() == TargetType.CHEST)
                .map(Map.Entry::getKey)
                .filter(pos -> pos.distToCenterSqr(com.example.addon.utils.MinecraftCompat.position(mc.player)) <= maxDistSq)
                .filter(pos -> pos.getY() >= minY)
                .filter(pos -> mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4))
                .filter(pos -> !mc.level.getBlockState(pos).isAir())
                .sorted(Comparator.comparingDouble(pos -> pos.distToCenterSqr(com.example.addon.utils.MinecraftCompat.position(mc.player))))
                .toList();

            if (!chests.isEmpty()) {
                if (chestBeamMode.get() == ChestBeamMode.NEAREST) {
                    beamsToRender.add(new BeamData(createPaddedBox(chests.get(0)), beamColor));
                } else { // ALL
                    for (BlockPos pos : chests) {
                        beamsToRender.add(new BeamData(createPaddedBox(pos), beamColor));
                    }
                }
            }
        }

        // Active Spawner Beams Collection
        if (trackSpawnerMobs.get() && spawnerBeamMode.get() == SpawnerBeamMode.ALL && !activeSpawners.isEmpty()) {
            SettingColor activeBeamColor = spawnerBeamColor.get();
            double maxDistSq = Math.pow(range.get() * 16, 2);
            
            for (BlockPos pos : activeSpawners) {
                if (!mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                if (pos.distToCenterSqr(com.example.addon.utils.MinecraftCompat.position(mc.player)) > maxDistSq) continue;
                if (mc.level.getBlockState(pos).isAir()) continue;
                
                beamsToRender.add(new BeamData(createPaddedBox(pos), activeBeamColor));
            }
        }

        renderBeams(event, beamsToRender);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Beam Dispatch & Rendering
    // ═══════════════════════════════════════════════════════════════════════════

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
            else                                        renderBoxBeam(event, beam.box, beam.color);
        }
    }

    private void renderBoxBeam(Render3DEvent event, AABB anchorBox, SettingColor color) {
        double beamSize = beamWidth.get() / 100.0;
        double centerX  = (anchorBox.minX + anchorBox.maxX) / 2.0;
        double centerZ  = (anchorBox.minZ + anchorBox.maxZ) / 2.0;
        int    worldBot = mc.level.getMinY();
        int    worldTop = worldBot + mc.level.getHeight();
        AABB beamBox = new AABB(
            centerX - beamSize, worldBot, centerZ - beamSize,
            centerX + beamSize, worldTop, centerZ + beamSize);
        event.renderer.box(beamBox, withAlpha(color, 80), color, ShapeMode.Both, 0);
        for (int i = 1; i <= 2; i++) {
            double exp   = beamSize * i * 1.5;
            int    alpha = Math.max(4, 30 / i);
            AABB bloom = new AABB(
                centerX - beamSize - exp, worldBot, centerZ - beamSize - exp,
                centerX + beamSize + exp, worldTop, centerZ + beamSize + exp);
            event.renderer.box(bloom, withAlpha(color, alpha), withAlpha(color, 0), ShapeMode.Sides, 0);
        }
    }

    private void renderGuardianBeam(Render3DEvent event, AABB anchorBox, SettingColor color) {
        if (mc.level == null) return;

        double cx = (anchorBox.minX + anchorBox.maxX) / 2.0;
        double cz = (anchorBox.minZ + anchorBox.maxZ) / 2.0;
        int worldBot = mc.level.getMinY();
        int worldTop = worldBot + mc.level.getHeight();

        double radius  = guardianBeamRadius.get();
        int    strands = guardianStrands.get();
        double speed   = guardianSpinSpeed.get();

        double rotationRad = (System.currentTimeMillis() % (long)(6000.0 / speed))
                             / (6000.0 / speed) * Math.PI * 2.0;

        RenderCompat.renderGuardianBeam(event, cx, cz, worldBot, worldTop, radius,
            strands, rotationRad, color, guardianStrandAlpha.get());

        int coreAlpha = guardianCoreAlpha.get();
        if (coreAlpha > 0) {
            double coreR = radius * 0.25;
            AABB coreBox = new AABB(
                cx - coreR, worldBot, cz - coreR,
                cx + coreR, worldTop, cz + coreR);
            event.renderer.box(coreBox,
                withAlpha(color, coreAlpha),
                withAlpha(color, Math.min(255, coreAlpha + 40)),
                ShapeMode.Both, 0);
        }

        if (guardianGlow.get()) {
            double glowR = guardianGlowRadius.get();
            for (int ring = 1; ring <= 2; ring++) {
                double expansion = glowR * ring;
                int    alpha     = Math.max(4, 22 / ring);
                AABB bloomBox = new AABB(
                    cx - radius - expansion, worldBot, cz - radius - expansion,
                    cx + radius + expansion, worldTop, cz + radius + expansion);
                event.renderer.box(bloomBox,
                    withAlpha(color, alpha),
                    withAlpha(color, 0),
                    ShapeMode.Sides, 0);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Spectral Registry Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private void rebuildSpectralRegistry() {
        GlowingRegistry.clear();
        spawnerMobGlowingIds.clear();
        if (renderMode.get() != RenderMode.SPECTRAL) return;

        if (mc.level != null && mc.player != null && (trackChestMinecarts.get() || trackAnomalousMinecarts.get())) {
            int  blockRange  = range.get() * 16;
            int  worldHeight = mc.level.getHeight();
            AABB  searchBox   = new AABB(mc.player.blockPosition()).inflate(blockRange, worldHeight, blockRange);
            for (MinecartChest minecart : mc.level.getEntitiesOfClass(MinecartChest.class, searchBox, e -> true)) {
                TargetType type = getMinecartType(minecart);
                if (type == TargetType.DISPLACED_CHEST_MINECART && trackAnomalousMinecarts.get()) {
                    GlowingRegistry.add(minecart.getId(), toArgb(displacedMinecartColor.get()));
                } else if (type == TargetType.MISROTATED_CHEST_MINECART && trackAnomalousMinecarts.get()) {
                    GlowingRegistry.add(minecart.getId(), toArgb(misrotatedMinecartColor.get()));
                } else if (trackChestMinecarts.get()) {
                    GlowingRegistry.add(minecart.getId(), toArgb(chestMinecartColor.get()));
                }
            }
        }

        if (trackEndermites.get()) {
            for (Endermite e : endermiteTargets) {
                if (e.isAlive()) GlowingRegistry.add(e.getId(), toArgb(endermiteColor.get()));
            }
        }

        if (trackXpOrbs.get()) {
            for (ExperienceOrb orb : xpOrbTargets) {
                if (orb.isAlive()) GlowingRegistry.add(orb.getId(), toArgb(xpOrbColor.get()));
            }
        }

        if (trackSpawnerMobs.get()) {
            for (Mob mob : spawnerMobTargets) {
                if (mob.isAlive()) {
                    GlowingRegistry.add(mob.getId(), toArgb(spawnerMobColor.get()));
                    spawnerMobGlowingIds.add(mob.getId());
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Bloom & Pulse Rendering
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderGlowLayers(Render3DEvent event, AABB box, SettingColor color) {
        int    layers    = glowLayers.get();
        double spread    = glowSpread.get();
        int    baseAlpha = glowBaseAlpha.get();

        for (int i = layers; i >= 1; i--) {
            double expansion  = spread * i;
            int    layerAlpha = Math.max(4, (int) (baseAlpha * (1.0 - (double)(i - 1) / layers)));
            event.renderer.box(
                box.inflate(expansion),
                withAlpha(color, layerAlpha),
                withAlpha(color, 0),
                ShapeMode.Sides, 0
            );
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

    private void renderPulseBox(Render3DEvent event, AABB box, SettingColor base) {
        int pa = applyPulse(base.a);
        SettingColor pColor = withAlpha(base, pa);
        int layers = glowLayers.get();
        double spread = glowSpread.get();
        for (int i = layers; i >= 1; i--) {
            double expansion = spread * i;
            double taper = 1.0 - ((double)(i - 1) / layers) * 0.6;
            int layerAlpha = Math.max(4, (int)(pa * taper));
            event.renderer.box(box.inflate(expansion),
                withAlpha(pColor, layerAlpha), withAlpha(pColor, 0), ShapeMode.Sides, 0);
        }
        event.renderer.box(box, withAlpha(pColor, pa / 3), pColor, ShapeMode.Both, 0);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Safety
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean performSafetyChecks() {
        if (!autoDisableOnLowHealth.get()) return false;
        boolean hasTotem = mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
            || mc.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
        if (hasTotem && mc.player.getHealth() <= lowHealthThreshold.get() * 2) {
            error("Health is critical (%.1f), disabling to prevent totem pop.", mc.player.getHealth());
            toggle();
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Breaking Logic
    // ═══════════════════════════════════════════════════════════════════════════

    private void updateBreakingLogic() {
        if (breakDelayTimer > 0) {
            breakDelayTimer--;
            if (breakDelayTimer == 0) {
                if (blockToBreak != null) {
                    Block targetBlock = mc.level.getBlockState(blockToBreak).getBlock();
                    if (targetBlock == Blocks.CHEST || targetBlock == Blocks.TRAPPED_CHEST || targetBlock == Blocks.SPAWNER || isCopperChestBlock(targetBlock)) {
                        isBreaking = true;
                        isBreakingChest = (targetBlock == Blocks.CHEST || targetBlock == Blocks.TRAPPED_CHEST || isCopperChestBlock(targetBlock));
                        if (silentMode.get()) previousSlot = com.example.addon.utils.MinecraftCompat.selectedSlot(mc.player.getInventory());
                    } else {
                        blockToBreak = null;
                    }
                } else if (entityToBreak != null) {
                    if (entityToBreak instanceof MinecartChest) {
                        isBreakingEntity = true;
                        if (silentMode.get()) previousSlot = com.example.addon.utils.MinecraftCompat.selectedSlot(mc.player.getInventory());
                    } else {
                        entityToBreak = null;
                    }
                }
            }
        }

        if (isBreaking && blockToBreak != null && !mc.player.isInWater()) {
            Block currentBreakTarget = mc.level.getBlockState(blockToBreak).getBlock();

            boolean blockIsNowAir = mc.level.getBlockState(blockToBreak).isAir();
            boolean done = blockIsNowAir
                || (currentBreakTarget != Blocks.CHEST && currentBreakTarget != Blocks.TRAPPED_CHEST
                        && currentBreakTarget != Blocks.SPAWNER && !isCopperChestBlock(currentBreakTarget))
                || Math.sqrt(mc.player.distanceToSqr(blockToBreak.getCenter())) > 6;

            if (done) {
                if (isBreakingChest && blockIsNowAir && trackChests.get()) {
                    brokenChestsCount++;
                    info("Chests broken: " + brokenChestsCount);
                }
                isBreaking = false;
                blockToBreak = null;
                isBreakingChest = false;
                mc.gameMode.stopDestroyBlock();
                restoreSlot();
            } else {
                if (isBreakingChest) {
                    int axeSlot = findAxe();
                    if (axeSlot != -1) mc.player.getInventory().setSelectedSlot(axeSlot);
                } else {
                    int pickaxeSlot = findPickaxe();
                    if (pickaxeSlot != -1) mc.player.getInventory().setSelectedSlot(pickaxeSlot);
                }
                Rotations.rotate(Rotations.getYaw(blockToBreak), Rotations.getPitch(blockToBreak), () -> {
                    mc.gameMode.continueDestroyBlock(blockToBreak, Direction.UP);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                });
            }
        }

        if (isBreakingEntity && entityToBreak != null && !mc.player.isInWater()) {
            boolean gone = !(entityToBreak instanceof MinecartChest)
                || !entityToBreak.isAlive()
                || mc.player.distanceTo(entityToBreak) > 6;

            if (gone) {
                isBreakingEntity = false;
                entityToBreak = null;
                restoreSlot();
            } else {
                int swordSlot = findSword();
                if (swordSlot != -1) mc.player.getInventory().setSelectedSlot(swordSlot);
                if (mc.player.getAttackStrengthScale(0f) >= 1.0f) {
                    Rotations.rotate(Rotations.getYaw(entityToBreak), Rotations.getPitch(entityToBreak), () -> {
                        mc.gameMode.attack(mc.player, entityToBreak);
                        mc.player.swing(InteractionHand.MAIN_HAND);
                    });
                }
            }
        }
    }

    private boolean isCopperChestBlock(Block block) {
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return name.contains("copper_chest");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Container Logic
    // ═══════════════════════════════════════════════════════════════════════════

    private void updateContainerLogic() {
        if (interactTimeoutTimer > 0) {
            interactTimeoutTimer--;
            if (interactTimeoutTimer == 0 && wasAutoOpened && mc.screen == null) {
                if (lastOpenedContainer != null) checkedContainers.remove(lastOpenedContainer);
                if (lastOpenedEntity != null)    checkedEntityIds.remove(lastOpenedEntity.getId());
                resetSoftState();
            }
        }

        if (silentOpenPending && mc.screen instanceof AbstractContainerScreen
                && !(mc.screen instanceof InventoryScreen)) {

            AbstractContainerScreen<?> silentScreen = (AbstractContainerScreen<?>) mc.screen;
            int numSlots       = silentScreen.getMenu().slots.size();
            int containerSlots = Math.max(0, numSlots - 36);

            if (containerSlots > 0) {
                boolean anyNonEmpty = false;
                for (int i = 0; i < containerSlots; i++) {
                    if (!silentScreen.getMenu().slots.get(i).getItem().isEmpty()) {
                        anyNonEmpty = true;
                        break;
                    }
                }

                boolean retriesExhausted = silentSlotReadRetryTimer >= SILENT_SLOT_READ_MAX_RETRIES;
                if (anyNonEmpty || retriesExhausted) {
                    silentFoundWhitelisted = false;
                    for (int i = 0; i < containerSlots; i++) {
                        Item item = silentScreen.getMenu().slots.get(i).getItem().getItem();
                        if (whitelistedItems.get().contains(item)) { silentFoundWhitelisted = true; break; }
                    }
                    pendingBreakCheck = true;
                    mc.player.closeContainer();
                    silentOpenPending = false;
                    silentSlotReadRetryTimer = 0;
                    return;
                } else {
                    silentSlotReadRetryTimer++;
                    return;
                }
            }
        }

        if (pendingBreakCheck && mc.screen == null && !silentOpenPending) {
            pendingBreakCheck = false;
            wasAutoOpened = false;
            hasPlayedSoundForCurrentScreen = false;

            if (!silentFoundWhitelisted) {
                if (autoBreak.get()) {
                    if (lastOpenedContainer != null) {
                        blockToBreak = lastOpenedContainer;
                        removeNeighborFromChecked(lastOpenedContainer);
                        breakDelayTimer = getRandomizedDelay(breakDelay.get());
                    } else if (lastOpenedEntity != null) {
                        entityToBreak = lastOpenedEntity;
                        breakDelayTimer = getRandomizedDelay(breakDelay.get());
                    }
                }
            } else {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            return;
        }

        if (mc.screen instanceof AbstractContainerScreen && !(mc.screen instanceof InventoryScreen)) {
            if (!wasAutoOpened) return;
            if (lastOpenedContainer == null && lastOpenedEntity == null) return;
            if (lastOpenedEntity != null && !(lastOpenedEntity instanceof MinecartChest)) return;

            AbstractContainerScreen<?> screen    = (AbstractContainerScreen<?>) mc.screen;
            int numSlots       = screen.getMenu().slots.size();
            int containerSlots = Math.max(0, numSlots - 36);

            if (containerSlots > 0) {
                boolean found = false;
                for (int i = 0; i < containerSlots; i++) {
                    if (whitelistedItems.get().contains(screen.getMenu().slots.get(i).getItem().getItem())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    mc.player.closeContainer();
                    wasAutoOpened = false;
                    if (autoBreak.get()) {
                        if (lastOpenedContainer != null) {
                            blockToBreak = lastOpenedContainer;
                            removeNeighborFromChecked(lastOpenedContainer);
                            breakDelayTimer = getRandomizedDelay(breakDelay.get());
                        } else if (lastOpenedEntity != null) {
                            entityToBreak = lastOpenedEntity;
                            breakDelayTimer = getRandomizedDelay(breakDelay.get());
                        }
                    }
                } else {
                    wasAutoOpened = false;
                    if (!hasPlayedSoundForCurrentScreen) {
                        boolean isChestOrMinecart = lastOpenedEntity != null
                            || (lastOpenedContainer != null
                                && (mc.level.getBlockState(lastOpenedContainer).getBlock() == Blocks.CHEST
                                ||  mc.level.getBlockState(lastOpenedContainer).getBlock() == Blocks.TRAPPED_CHEST
                                ||  isCopperChestBlock(mc.level.getBlockState(lastOpenedContainer).getBlock())));
                        if (isChestOrMinecart) {
                            lootFoundCount++;
                            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                            hasPlayedSoundForCurrentScreen = true;
                        }
                    }
                }
            }

        } else if (mc.screen == null && !isBreaking && !isBreakingEntity
                && breakDelayTimer == 0 && !pendingBreakCheck
                && !silentOpenPending && !wasAutoOpened) {

            hasPlayedSoundForCurrentScreen = false;

            if (autoOpen.get() || autoBreakSpawners.get()) {
                if (prioritizeSpawners.get() && autoBreakSpawners.get() && isSpawnerInBreakRange()) {
                    if (runSpawnerCheck()) return;
                    if (autoOpen.get() && runMinecartCheck()) return;
                    if (autoOpen.get() && runChestCheck()) return;
                } else {
                    if (autoOpen.get() && runMinecartCheck()) return;
                    if (autoOpen.get() && runChestCheck()) return;
                    if (autoBreakSpawners.get() && runSpawnerCheck()) return;
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Scanning Logic
    // ═══════════════════════════════════════════════════════════════════════════

    private void updateScanningLogic() {
        if (mc.level.dimension() == null) return;

        if (dimensionChangeCooldown > 0) { dimensionChangeCooldown--; return; }

        String currDim = mc.level.dimension().identifier().toString();
        if (!currDim.equals(lastDimension)) {
            dimensionChangeCooldown = DIMENSION_CHANGE_COOLDOWN_TICKS;
            lastDimension = currDim;
            resetScanningState();
            return;
        }

        BlockPos playerPos    = mc.player.blockPosition();
        int      centerChunkX = playerPos.getX() >> 4;
        int      centerChunkZ = playerPos.getZ() >> 4;

        cleanupDistantTargets(playerPos);
        scanChestMinecarts();
        pruneBlockTargets();
        scanNewChunks(centerChunkX, centerChunkZ);
        scanEndermites();
        scanXpOrbs();
        scanSpawnerTorches();
        scanSpawnerMobs();
        pruneCheckedEntityIds();
        pruneCheckedContainers();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Auto-Open Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean isSpawnerInBreakRange() {
        double rangeSq = Math.pow(spawnerBreakRange.get(), 2);
        for (Map.Entry<BlockPos, TargetType> entry : targets.entrySet()) {
            if (entry.getValue() == TargetType.SPAWNER
                    && entry.getKey().distToCenterSqr(com.example.addon.utils.MinecraftCompat.position(mc.player)) <= rangeSq) return true;
        }
        return false;
    }

    private boolean runSpawnerCheck() {
        if (!autoBreakSpawners.get() || areMobsNearby()) return false;

        BlockPos bestPos   = null;
        double   minDistSq = Double.MAX_VALUE;
        double   rangeSq   = Math.pow(spawnerBreakRange.get(), 2);

        for (Map.Entry<BlockPos, TargetType> entry : targets.entrySet()) {
            if (entry.getValue() == TargetType.SPAWNER) {
                double distSq = entry.getKey().distToCenterSqr(com.example.addon.utils.MinecraftCompat.position(mc.player));
                if (distSq <= rangeSq && distSq < minDistSq) { minDistSq = distSq; bestPos = entry.getKey(); }
            }
        }

        if (bestPos == null) return false;
        blockToBreak    = bestPos;
        breakDelayTimer = getRandomizedDelay(spawnerBreakDelay.get());
        return true;
    }

    private boolean areMobsNearby() {
        if (mc.player == null || mc.level == null) return false;
        double radius = spawnerBreakRange.get();
        return !mc.level.getEntitiesOfClass(Monster.class,
            new AABB(mc.player.blockPosition()).inflate(radius), Entity::isAlive).isEmpty();
    }

    private boolean runMinecartCheck() {
        if (!trackChestMinecarts.get() && !trackAnomalousMinecarts.get()) return false;

        List<MinecartChest> minecarts = mc.level.getEntitiesOfClass(
            MinecartChest.class,
            new AABB(mc.player.blockPosition()).inflate(4.5),
            e -> !checkedEntityIds.contains(e.getId())
        );
        if (minecarts.isEmpty()) return false;

        minecarts.sort(Comparator.comparingDouble(e -> mc.player.distanceToSqr(e)));
        MinecartChest cart = minecarts.get(0);
        if (mc.player.distanceTo(cart) > 4.5) return false;

        lastOpenedEntity    = cart;
        lastOpenedContainer = null;
        checkedEntityIds.add(cart.getId());
        wasAutoOpened        = true;
        interactTimeoutTimer = INTERACT_TIMEOUT_TICKS;

        Rotations.rotate(Rotations.getYaw(cart), Rotations.getPitch(cart), () -> {
            mc.gameMode.interact(mc.player, cart, new net.minecraft.world.phys.EntityHitResult(cart), InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);
        });
        return true;
    }

    private boolean runChestCheck() {
        if (!trackChests.get()) return false;

        List<BlockPos> nearbyChests = targets.entrySet().stream()
            .filter(e -> e.getValue() == TargetType.CHEST)
            .map(Map.Entry::getKey)
            .filter(pos -> !checkedContainers.contains(pos))
            .filter(pos -> Math.sqrt(pos.distToCenterSqr(com.example.addon.utils.MinecraftCompat.position(mc.player))) <= 4.5)
            .sorted(Comparator.comparingDouble(pos -> pos.distToCenterSqr(com.example.addon.utils.MinecraftCompat.position(mc.player))))
            .toList();

        if (nearbyChests.isEmpty()) return false;

        BlockPos pos   = nearbyChests.get(0);
        Block    block = mc.level.getBlockState(pos).getBlock();

        checkedContainers.add(pos);
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || isCopperChestBlock(block)) {
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                BlockPos neighbor = pos.relative(dir);
                if (mc.level.getBlockState(neighbor).getBlock() == block) {
                    checkedContainers.add(neighbor);
                    break;
                }
            }
        }

        lastOpenedContainer  = pos;
        lastOpenedEntity     = null;
        wasAutoOpened        = true;
        interactTimeoutTimer = INTERACT_TIMEOUT_TICKS;

        Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            mc.player.swing(InteractionHand.MAIN_HAND);
        });
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Scanning
    // ═══════════════════════════════════════════════════════════════════════════

    private void resetScanningState() {
        targets.clear();
        scannedChunks.clear();
        checkedContainers.clear();
        checkedEntityIds.clear();
        notifiedAnomalousMinecarts.clear();
        GlowingRegistry.clear();
        activeSpawners.clear();
    }

    private void scanEndermites() {
        endermiteTargets.clear();
        if (!trackEndermites.get() || mc.level == null || mc.player == null) {
            notifiedEndermites.clear();
            return;
        }
        if (!mc.level.dimension().identifier().toString().equals("minecraft:overworld")) {
            notifiedEndermites.clear();
            return;
        }

        boolean isSpectral = renderMode.get() == RenderMode.SPECTRAL;
        int    blockRange = range.get() * 16;
        AABB    searchBox  = new AABB(mc.player.blockPosition()).inflate(blockRange);
        Set<Integer> currentIds = new HashSet<>();

        for (Endermite endermite : mc.level.getEntitiesOfClass(Endermite.class, searchBox, e -> true)) {
            endermiteTargets.add(endermite);
            currentIds.add(endermite.getId());

            if (isSpectral) {
                GlowingRegistry.add(endermite.getId(), toArgb(endermiteColor.get()));
            } else {
                GlowingRegistry.remove(endermite.getId());
            }

            if (notifiedEndermites.add(endermite.getId())) {
                info("Endermite Detected, Beam created");
                mc.player.playSound(SoundEvents.ENDERMITE_AMBIENT, 1.0f, 1.0f);
            }
        }
        notifiedEndermites.retainAll(currentIds);
    }

    private void scanXpOrbs() {
        xpOrbTargets.clear();
        if (!trackXpOrbs.get() || mc.level == null || mc.player == null) return;

        boolean isSpectral = renderMode.get() == RenderMode.SPECTRAL;
        int blockRange = range.get() * 16;
        AABB searchBox = new AABB(mc.player.blockPosition()).inflate(blockRange);

        for (ExperienceOrb orb : mc.level.getEntitiesOfClass(ExperienceOrb.class, searchBox, e -> true)) {
            xpOrbTargets.add(orb);
            if (isSpectral) {
                GlowingRegistry.add(orb.getId(), toArgb(xpOrbColor.get()));
            } else {
                GlowingRegistry.remove(orb.getId());
            }
        }
    }

    private void scanSpawnerTorches() {
        spawnerTorches.clear();
        if (!trackSpawners.get() || !highlightSpawnerTorches.get()) return;

        for (Map.Entry<BlockPos, TargetType> entry : targets.entrySet()) {
            if (entry.getValue() != TargetType.SPAWNER) continue;
            BlockPos spawnerPos = entry.getKey();
            if (!mc.level.getChunkSource().hasChunk(spawnerPos.getX() >> 4, spawnerPos.getZ() >> 4)) continue;

            for (int x = -5; x <= 5; x++) {
                for (int y = -5; y <= 5; y++) {
                    for (int z = -5; z <= 5; z++) {
                        BlockPos pos = spawnerPos.offset(x, y, z);
                        Block    b   = mc.level.getBlockState(pos).getBlock();
                        if (b == Blocks.TORCH || b == Blocks.WALL_TORCH
                                || b == Blocks.SOUL_TORCH || b == Blocks.SOUL_WALL_TORCH) {
                            spawnerTorches.add(pos);
                        }
                    }
                }
            }
        }
    }

    private void scanSpawnerMobs() {
        spawnerMobTargets.clear();
        if (!trackSpawnerMobs.get() || mc.level == null || mc.player == null) {
            notifiedSpawnerMobs.clear();
            activeSpawners.clear();
            for (Integer id : spawnerMobGlowingIds) {
                GlowingRegistry.remove(id);
            }
            spawnerMobGlowingIds.clear();
            return;
        }

        boolean isSpectral = renderMode.get() == RenderMode.SPECTRAL;
        Set<Integer> currentIds = new HashSet<>();
        Set<BlockPos> spawnerPositions = new HashSet<>();

        for (Map.Entry<BlockPos, TargetType> entry : targets.entrySet()) {
            if (entry.getValue() == TargetType.SPAWNER) {
                spawnerPositions.add(entry.getKey());
            }
        }

        Set<Integer> newGlowingIds = new HashSet<>();
        Set<BlockPos> newActiveSpawners = new HashSet<>();

        for (BlockPos spawnerPos : spawnerPositions) {
            AABB searchBox = new AABB(spawnerPos).inflate(5);
            boolean hasMobs = false;
            
            for (Mob mob : mc.level.getEntitiesOfClass(Mob.class, searchBox, e -> true)) {
                if (spawnerPos.distToCenterSqr(com.example.addon.utils.MinecraftCompat.position(mob)) <= 25) { // 5 blocks radius squared
                    spawnerMobTargets.add(mob);
                    currentIds.add(mob.getId());
                    hasMobs = true;

                    if (isSpectral) {
                        GlowingRegistry.add(mob.getId(), toArgb(spawnerMobColor.get()));
                        newGlowingIds.add(mob.getId());
                    }

                    if (notifiedSpawnerMobs.add(mob.getId())) {
                        info("§cMob activity detected near a spawner!");
                    }
                }
            }
            
            if (hasMobs) {
                // Check for nearby chests to prevent false flags
                boolean hasChestNearby = false;
                double chestRadiusSq = Math.pow(spawnerChestRadius.get(), 2);
                
                for (Map.Entry<BlockPos, TargetType> targetEntry : targets.entrySet()) {
                    if (targetEntry.getValue() == TargetType.CHEST) {
                        if (spawnerPos.distSqr(targetEntry.getKey()) <= chestRadiusSq) {
                            hasChestNearby = true;
                            break;
                        }
                    }
                }
                
                if (hasChestNearby) {
                    newActiveSpawners.add(spawnerPos);
                }
            }
        }

        // Action Bar Notification Logic
        if (!newActiveSpawners.isEmpty()) {
            if (spawnerActionBarCooldown <= 0) {
                Component message = Component.literal("⚠ Active Spawner Detected!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                mc.player.sendOverlayMessage(message); // true sends it to the action bar (above hotbar)
                
                float volume = spawnerAlertVolume.get().floatValue();
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), volume, 0.5f); // Low pitch pling
                
                spawnerActionBarCooldown = 60; // 3 seconds (20 ticks * 3)
            } else {
                spawnerActionBarCooldown--;
            }
        } else {
            spawnerActionBarCooldown = 0; // Reset instantly when clear
        }

        notifiedSpawnerMobs.retainAll(currentIds);

        activeSpawners.clear();
        activeSpawners.addAll(newActiveSpawners);

        // Cleanup old glowing IDs
        if (isSpectral) {
            for (Integer id : spawnerMobGlowingIds) {
                if (!newGlowingIds.contains(id)) {
                    GlowingRegistry.remove(id);
                }
            }
            spawnerMobGlowingIds.clear();
            spawnerMobGlowingIds.addAll(newGlowingIds);
        } else {
            for (Integer id : spawnerMobGlowingIds) {
                GlowingRegistry.remove(id);
            }
            spawnerMobGlowingIds.clear();
        }
    }

    private void scanNewChunks(int centerChunkX, int centerChunkZ) {
        int r   = range.get();
        int rSq = r * r;

        scannedChunks.removeIf(cp -> {
            int dx = cp.x() - centerChunkX;
            int dz = cp.z() - centerChunkZ;
            return dx * dx + dz * dz > rSq;
        });

        int chunksScanned = 0;
        int limit = 10;

        outer:
        for (int d = 0; d <= r; d++) {
            int minX = -d, maxX = d, minZ = -d, maxZ = d;

            for (int x = minX; x <= maxX; x++) {
                if (processChunk(centerChunkX + x, centerChunkZ + minZ, rSq, centerChunkX, centerChunkZ)) chunksScanned++;
                if (chunksScanned >= limit) break outer;
                if (minZ != maxZ) {
                    if (processChunk(centerChunkX + x, centerChunkZ + maxZ, rSq, centerChunkX, centerChunkZ)) chunksScanned++;
                    if (chunksScanned >= limit) break outer;
                }
            }

            for (int z = minZ + 1; z < maxZ; z++) {
                if (processChunk(centerChunkX + minX, centerChunkZ + z, rSq, centerChunkX, centerChunkZ)) chunksScanned++;
                if (chunksScanned >= limit) break outer;
                if (minX != maxX) {
                    if (processChunk(centerChunkX + maxX, centerChunkZ + z, rSq, centerChunkX, centerChunkZ)) chunksScanned++;
                    if (chunksScanned >= limit) break outer;
                }
            }
        }
    }

    private boolean processChunk(int cx, int cz, int rSq, int centerChunkX, int centerChunkZ) {
        int dx = cx - centerChunkX, dz = cz - centerChunkZ;
        if (dx * dx + dz * dz > rSq) return false;

        ChunkPos cp = new ChunkPos(cx, cz);
        if (scannedChunks.contains(cp)) return false;
        if (!mc.level.getChunkSource().hasChunk(cx, cz)) return false;

        scanChunk(mc.level.getChunk(cx, cz));
        scanBlockEntitiesInChunk(mc.level.getChunk(cx, cz));
        scannedChunks.add(cp);
        return true;
    }

    private void scanChunk(LevelChunk chunk) {
        if (mc.level == null) return;

        boolean isOverworld = "minecraft:overworld".equals(lastDimension);
        boolean doCustomBlocks = scanCustomBlocks.get() && !filterBlocks.get().isEmpty() && isOverworld;
        boolean doMisrotated   = trackMisrotatedDeepslate.get() && isOverworld;
        boolean doLowY         = trackLowYStoneDirt.get();
        if (!doCustomBlocks && !doMisrotated && !doLowY) return;

        int          maxY     = dungeonYLevel.get(); 
        List<Block>  filter   = doCustomBlocks ? filterBlocks.get() : List.of();
        LevelChunkSection[] sections = chunk.getSections();

        ChunkPos currentChunkPos = chunk.getPos();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;

            int sectionY    = chunk.getMinSectionY() + i;
            int sectionMinY = sectionY * 16;
            int sectionMaxY = sectionMinY + 16;
            if (sectionMinY > maxY) continue;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int worldY = sectionMinY + y;
                    if (worldY > maxY) continue;

                    for (int z = 0; z < 16; z++) {
                        BlockState state    = section.getBlockState(x, y, z);
                        Block      block    = state.getBlock();
                        BlockPos   blockPos = new BlockPos((currentChunkPos.x() << 4) + x, worldY, (currentChunkPos.z() << 4) + z);

                        if (doCustomBlocks && filter.contains(block)) targets.put(blockPos, TargetType.CUSTOM_BLOCK);
                        if (doMisrotated && block == Blocks.DEEPSLATE
                                && state.hasProperty(BlockStateProperties.AXIS)
                                && state.getValue(BlockStateProperties.AXIS) != Axis.Y) {
                            targets.put(blockPos, TargetType.MISROTATED_DEEPSLATE);
                        }

                        if (doLowY && worldY < lowYLevel.get()) {
                            if (block == Blocks.STONE || block == Blocks.DIRT) {
                                targets.put(blockPos, TargetType.LOW_Y_STONE_DIRT);
                            }
                        }
                    }
                }
            }
        }
    }

    private void scanBlockEntitiesInChunk(LevelChunk chunk) {
        int maxY = dungeonYLevel.get(); 

        for (BlockEntity be : chunk.getBlockEntities().values()) {
            BlockPos pos = be.getBlockPos();
            if (pos.getY() > maxY) continue;

            if ((trackSpawners.get() || autoBreakSpawners.get()) && be instanceof SpawnerBlockEntity) {
                targets.put(pos, TargetType.SPAWNER);
            } else if (trackChests.get()) {
                Block block = mc.level.getBlockState(pos).getBlock();
                if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || isCopperChestBlock(block)) targets.put(pos, TargetType.CHEST);
            }
        }
    }

    private TargetType getMinecartType(MinecartChest cart) {
        Vec3 exactPos = com.example.addon.utils.MinecraftCompat.position(cart);
        BlockPos blockPos = cart.blockPosition();

        // Check for flowing water within 1 block radius
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    FluidState fluidState = mc.level.getFluidState(blockPos.offset(x, y, z));
                    if (fluidState.is(Fluids.FLOWING_WATER)) {
                        return TargetType.CHEST_MINECART; // Ignore displacement if caused by water
                    }
                }
            }
        }

        boolean isDisplaced = false;
        BlockState stateAtPos = mc.level.getBlockState(blockPos);
        
        if (!stateAtPos.isAir() && !stateAtPos.getCollisionShape(mc.level, blockPos).isEmpty() && !(stateAtPos.getBlock() instanceof BaseRailBlock)) {
            isDisplaced = true;
        } else {
            double closestCenterX = blockPos.getX() + 0.5;
            double closestCenterZ = blockPos.getZ() + 0.5;
            double offsetX = Math.abs(exactPos.x - closestCenterX);
            double offsetZ = Math.abs(exactPos.z - closestCenterZ);
            
            if (offsetX > 0.1 || offsetZ > 0.1) {
                isDisplaced = true;
            }
            
            if (!isDisplaced) {
                boolean hasRail = false;
                for (int y = 0; y >= -1; y--) {
                    if (mc.level.getBlockState(blockPos.offset(0, y, 0)).getBlock() instanceof BaseRailBlock) {
                        hasRail = true;
                        break;
                    }
                }
                if (!hasRail) {
                    isDisplaced = true;
                }
            }
        }
        if (isDisplaced) return TargetType.DISPLACED_CHEST_MINECART;

        float yaw = ((cart.getYRot() % 360) + 360) % 360;
        float remainder = yaw % 90;
        boolean isMisrotated = remainder > 5.0f && remainder < 85.0f;
        if (isMisrotated) return TargetType.MISROTATED_CHEST_MINECART;

        return TargetType.CHEST_MINECART;
    }

    private void scanChestMinecarts() {
        if (!trackChestMinecarts.get() && !trackAnomalousMinecarts.get()) return;

        boolean isSpectral  = renderMode.get() == RenderMode.SPECTRAL;
        int     blockRange  = range.get() * 16;
        int     worldHeight = mc.level.getHeight();
        AABB     searchBox   = new AABB(mc.player.blockPosition()).inflate(blockRange, worldHeight, blockRange);

        Set<BlockPos> currentPositions = new HashSet<>();
        for (MinecartChest minecart : mc.level.getEntitiesOfClass(MinecartChest.class, searchBox, entity -> true)) {
            BlockPos pos = minecart.blockPosition();
            currentPositions.add(pos);

            TargetType type = getMinecartType(minecart);
            TargetType targetType = null;
            int color = 0;

            if (type == TargetType.DISPLACED_CHEST_MINECART && trackAnomalousMinecarts.get()) {
                targetType = TargetType.DISPLACED_CHEST_MINECART;
                color = toArgb(displacedMinecartColor.get());
            } else if (type == TargetType.MISROTATED_CHEST_MINECART && trackAnomalousMinecarts.get()) {
                targetType = TargetType.MISROTATED_CHEST_MINECART;
                color = toArgb(misrotatedMinecartColor.get());
            } else if (trackChestMinecarts.get()) {
                targetType = TargetType.CHEST_MINECART;
                color = toArgb(chestMinecartColor.get());
            }

            if (targetType != null) {
                targets.put(pos, targetType);
                
                if (isSpectral) GlowingRegistry.add(minecart.getId(), color);
                else GlowingRegistry.remove(minecart.getId());

                if (targetType == TargetType.DISPLACED_CHEST_MINECART || targetType == TargetType.MISROTATED_CHEST_MINECART) {
                    if (notifiedAnomalousMinecarts.add(minecart.getId())) {
                        if (targetType == TargetType.DISPLACED_CHEST_MINECART) {
                            info("§bDisplaced minecart detected!");
                        } else {
                            info("§5Misrotated minecart detected!");
                        }
                        mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
                    }
                }
            }
        }

        targets.entrySet().removeIf(entry ->
            (entry.getValue() == TargetType.CHEST_MINECART || entry.getValue() == TargetType.MISROTATED_CHEST_MINECART || entry.getValue() == TargetType.DISPLACED_CHEST_MINECART) 
            && !currentPositions.contains(entry.getKey())
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Pruning
    // ═══════════════════════════════════════════════════════════════════════════

    private void pruneBlockTargets() {
        if (mc.level == null || mc.player == null) return;

        Set<BlockPos> toRemove = new HashSet<>();
        Set<ChunkPos> chunksToRescan = new HashSet<>();

        for (Map.Entry<BlockPos, TargetType> entry : targets.entrySet()) {
            TargetType type = entry.getValue();
            if (type == TargetType.CHEST_MINECART || type == TargetType.MISROTATED_CHEST_MINECART || type == TargetType.DISPLACED_CHEST_MINECART) continue;

            BlockPos pos = entry.getKey();
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;

            if (mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                Block currentBlock = mc.level.getBlockState(pos).getBlock();
                if (mc.level.getBlockState(pos).isAir() || !validateBlockType(currentBlock, type)) {
                    toRemove.add(pos);
                }
            } else {
                chunksToRescan.add(new ChunkPos(chunkX, chunkZ));
            }
        }

        for (BlockPos pos : toRemove) {
            targets.remove(pos);
        }

        if (!chunksToRescan.isEmpty()) {
            scannedChunks.removeAll(chunksToRescan);
        }
    }

    private void pruneCheckedEntityIds() {
        if (checkedEntityIds.isEmpty() && notifiedAnomalousMinecarts.isEmpty()) return;
        Set<Integer> liveIds = new HashSet<>();
        for (MinecartChest e : mc.level.getEntitiesOfClass(
                MinecartChest.class, new AABB(mc.player.blockPosition()).inflate(range.get() * 16), Entity::isAlive)) {
            liveIds.add(e.getId());
        }
        checkedEntityIds.retainAll(liveIds);
        notifiedAnomalousMinecarts.retainAll(liveIds);
    }

    private void pruneCheckedContainers() {
        if (checkedContainers.isEmpty()) return;
        checkedContainers.removeIf(pos -> !targets.containsKey(pos));
    }

    private void cleanupDistantTargets(BlockPos playerPos) {
        int cleanupRangeSq = (int) Math.pow(range.get() * 16 + 32, 2);

        targets.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            double   dx  = pos.getX() - playerPos.getX();
            double   dz  = pos.getZ() - playerPos.getZ();

            if (dx * dx + dz * dz > cleanupRangeSq) {
                scannedChunks.remove(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
                return true;
            }
            return false;
        });
    }

    private void removeNeighborFromChecked(BlockPos pos) {
        if (pos == null || mc.level == null) return;
        Block block = mc.level.getBlockState(pos).getBlock();
        if (block != Blocks.CHEST && block != Blocks.TRAPPED_CHEST && !isCopperChestBlock(block)) return;
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos neighbor = pos.relative(dir);
            if (mc.level.getBlockState(neighbor).getBlock() == block) {
                checkedContainers.remove(neighbor);
                break;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Render Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private AABB getMinecartChestBox(MinecartChest minecart) {
        AABB    entityBox = minecart.getBoundingBox();
        double chestSize = 14.0 / 16.0;
        double xPadding  = (entityBox.getXsize() - chestSize) / 2.0;
        double zPadding  = (entityBox.getZsize() - chestSize) / 2.0;
        double chestHeight = 10.0 / 16.0;
        double minY      = entityBox.maxY - chestHeight;
        return new AABB(
            entityBox.minX + xPadding, minY,           entityBox.minZ + zPadding,
            entityBox.maxX - xPadding, entityBox.maxY, entityBox.maxZ - zPadding
        );
    }

    private AABB createPaddedBox(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Color Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }

    private int toArgb(SettingColor c) {
        return (c.a << 24) | (c.r << 16) | (c.g << 8) | c.b;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Validation & Color Lookup
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean validateBlockType(Block block, TargetType type) {
        return switch (type) {
            case SPAWNER              -> block == Blocks.SPAWNER;
            case CHEST                -> block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || isCopperChestBlock(block);
            case CHEST_MINECART, MISROTATED_CHEST_MINECART, DISPLACED_CHEST_MINECART -> true;
            case CUSTOM_BLOCK         -> filterBlocks.get().contains(block);
            case MISROTATED_DEEPSLATE -> block == Blocks.DEEPSLATE;
            case LOW_Y_STONE_DIRT     -> block == Blocks.STONE || block == Blocks.DIRT;
        };
    }

    private SettingColor getColor(TargetType type) {
        return switch (type) {
            case SPAWNER              -> trackSpawners.get() ? spawnerColor.get() : null;
            case CHEST                -> chestColor.get();
            case CHEST_MINECART       -> chestMinecartColor.get();
            case MISROTATED_CHEST_MINECART -> misrotatedMinecartColor.get();
            case DISPLACED_CHEST_MINECART -> displacedMinecartColor.get();
            case CUSTOM_BLOCK         -> customBlockColor.get();
            case MISROTATED_DEEPSLATE -> misrotatedDeepslateColor.get();
            case LOW_Y_STONE_DIRT     -> lowYStoneDirtColor.get();
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Reset Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private void resetSoftState() {
        wasAutoOpened                  = false;
        silentOpenPending              = false;
        silentFoundWhitelisted         = false;
        pendingBreakCheck              = false;
        silentSlotReadRetryTimer       = 0;
        interactTimeoutTimer           = 0;
        lastOpenedContainer            = null;
        lastOpenedEntity               = null;
        hasPlayedSoundForCurrentScreen = false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Utility Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private void restoreSlot() {
        if (silentMode.get() && previousSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
            previousSlot = -1;
        }
    }

    private int findAxe() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(ItemTags.AXES)) return i;
        return -1;
    }

    private int findPickaxe() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(ItemTags.PICKAXES)) return i;
        return -1;
    }

    private int findSword() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(ItemTags.SWORDS)) return i;
        return -1;
    }

    private int getRandomizedDelay(int baseDelay) {
        if (baseDelay <= 0) return 1;
        return (int) Math.max(1, Math.round(baseDelay * (1.0 + (Math.random() - 0.5) * 0.8)));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean shouldShowStealDumpButtons() {
        return isActive() && stealDumpButtons.get();
    }

    public int getTotalTargets() {
        if (mc.player == null || mc.level == null) return 0;

        double rangeSq = Math.pow(range.get() * 16.0, 2);
        int count = 0;

        for (Map.Entry<BlockPos, TargetType> entry : targets.entrySet()) {
            BlockPos pos = entry.getKey();
            TargetType type = entry.getValue();

            double dx = pos.getX() + 0.5 - mc.player.getX();
            double dz = pos.getZ() + 0.5 - mc.player.getZ();
            if (dx * dx + dz * dz > rangeSq) continue;

            if (!mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;

            if (type != TargetType.CHEST_MINECART && type != TargetType.MISROTATED_CHEST_MINECART && type != TargetType.DISPLACED_CHEST_MINECART) {
                Block currentBlock = mc.level.getBlockState(pos).getBlock();
                if (!validateBlockType(currentBlock, type)) continue;
            }

            count++;
        }

        return count;
    }

    public int getBrokenChestsCount() { return brokenChestsCount; }
    public int getLootFoundCount()    { return lootFoundCount; }

    public Map<TargetType, Integer> getTargetCounts() {
        Map<TargetType, Integer> counts = new EnumMap<>(TargetType.class);
        for (TargetType type : TargetType.values()) counts.put(type, 0);

        if (mc.player == null || mc.level == null) return counts;

        double rangeSq = Math.pow(range.get() * 16.0, 2);

        for (Map.Entry<BlockPos, TargetType> entry : targets.entrySet()) {
            BlockPos pos = entry.getKey();
            TargetType type = entry.getValue();

            double dx = pos.getX() + 0.5 - mc.player.getX();
            double dz = pos.getZ() + 0.5 - mc.player.getZ();
            if (dx * dx + dz * dz > rangeSq) continue;

            if (!mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;

            if (type != TargetType.CHEST_MINECART && type != TargetType.MISROTATED_CHEST_MINECART && type != TargetType.DISPLACED_CHEST_MINECART) {
                Block currentBlock = mc.level.getBlockState(pos).getBlock();
                if (!validateBlockType(currentBlock, type)) continue;
            }

            counts.put(type, counts.get(type) + 1);
        }

        return counts;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internal Data Classes
    // ═══════════════════════════════════════════════════════════════════════════

    private record BeamData(AABB box, SettingColor color) {}
}
