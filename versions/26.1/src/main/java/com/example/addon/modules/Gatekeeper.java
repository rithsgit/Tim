package com.example.addon.modules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.addon.Tim;
import com.example.addon.hud.EndAssistantHud;
import com.example.addon.mixin.EndGatewayBlockEntityAccessor;
import com.example.addon.utils.GlowingRegistry;

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
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class Gatekeeper extends Module {

    private static final int    CHUNK_SCAN_LIMIT_PER_TICK        = 64;
    private static final int    CLEANUP_INTERVAL_TICKS           = 60;

    // End Assistant Constants
    private static final int DIMENSION_CHANGE_COOLDOWN_TICKS = 40;
    private static final int INTERACT_TIMEOUT_TICKS = 20;

    public enum RenderMode { GLOW, SPECTRAL, PULSE }
    public enum BeamStyle  { BOX, GUARDIAN }

    // End Assistant Enums
    public enum TargetType {
        CONTAINER
    }

    public enum AlertSound {
        ENDER_DRAGON_GROWL("Dragon Growl"),
        SHULKER_TELEPORT("Shulker Teleport"),
        LEVEL_UP("Level Up"),
        EXPERIENCE_ORB("Experience Orb"),
        BELL("Bell");

        private final String displayName;
        AlertSound(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    // ── State ──────────────────────────────────────────────────────
    private final Map<BlockPos, PortalType>      portals            = new ConcurrentHashMap<>();
    private final Map<BlockPos, PortalStructure> portalStructureMap = new ConcurrentHashMap<>();
    private final Set<ChunkPos>                  scannedChunks      = new HashSet<>();
    private final Set<ChunkPos>                  dirtyChunks        = new HashSet<>();
    private final Set<String>                    notifiedStructures = new HashSet<>();
    private boolean portalsDirty = false;
    private int cleanupTimer = 0;

    // End Assistant State
    private final Map<BlockPos, TargetType> targets = new ConcurrentHashMap<>();
    private final Set<ChunkPos> eaScannedChunks = new HashSet<>();
    private final Set<BlockPos> checkedContainers = new HashSet<>();
    private final Set<Integer> notifiedShulkers = new HashSet<>();
    private final Set<Integer> notifiedElytras = new HashSet<>();
    private int levitationWarnTimer = 0;
    private int totalElytrasFound = 0;
    private boolean wasAutoOpened = false;
    private int interactTimeoutTimer = 0;
    private int drinkTimer = 0;
    private int previousDrinkSlot = -1;
    private boolean hasAlertedForCurrentScreen = false;
    private String lastDimension = "";
    private int dimensionChangeCooldown = 0;
    private final List<ItemFrame> elytraFrameTargets = new ArrayList<>();
    private final List<Shulker> shulkerTargets = new ArrayList<>();
    private final List<ShulkerBullet> bulletTargets = new ArrayList<>();

    // ── Setting Groups ─────────────────────────────────────────────
    private final SettingGroup sgGeneral      = settings.getDefaultGroup();
    private final SettingGroup sgEndDimension = settings.createGroup("End Dimension");
    private final SettingGroup sgRender       = settings.createGroup("Render");
    private final SettingGroup sgBeam         = settings.createGroup("Beam");

    // End Assistant Setting Groups
    private final SettingGroup sgEAGeneral    = settings.createGroup("End Assistant");
    private final SettingGroup sgEABlocks     = settings.createGroup("EA - Targets (City)");
    private final SettingGroup sgEAEntities   = settings.createGroup("EA - Entities");
    private final SettingGroup sgEAAutomation = settings.createGroup("EA - Automation");
    private final SettingGroup sgEASafety     = settings.createGroup("EA - Safety");

    // --- Multi-Version Registry Helpers (1.21.4 - 1.21.11) ---
    private static Block optBlock(String path) {
        Identifier id = Identifier.withDefaultNamespace(path);
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id) : null;
    }

    // ── Toggles ────────────────────────────────────────────────────
    private final Setting<Boolean> scanEndPortals = sgEndDimension.add(new BoolSetting.Builder()
        .name("end-portals").description("Scan End portal blocks.").defaultValue(true)
        .onChanged(v -> portalsDirty = true).build());

    private final Setting<Boolean> scanEndGateways = sgEndDimension.add(new BoolSetting.Builder()
        .name("end-gateways").description("Scan End gateways.").defaultValue(true)
        .onChanged(v -> portalsDirty = true).build());

    // ── General ────────────────────────────────────────────────────
    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range").description("Detection range in chunks for all features.").defaultValue(16).min(1).max(128).sliderMin(1).sliderMax(64).build());

    // EA Master Toggle
    private final Setting<Boolean> enableEndAssistant = sgEAGeneral.add(new BoolSetting.Builder()
        .name("end-assistant")
        .description("Master toggle for all End Assistant features.")
        .defaultValue(true)
        .build()
    );

    // ── End Dimension ──────────────────────────────────────────────
    private final Setting<SettingColor> endPortalColor = sgEndDimension.add(new ColorSetting.Builder()
        .name("end-portal-color").defaultValue(new SettingColor(0, 255, 128, 255)).visible(scanEndPortals::get).build());

    private final Setting<SettingColor> endGatewayColor = sgEndDimension.add(new ColorSetting.Builder()
        .name("end-gateway-color").defaultValue(new SettingColor(255, 0, 255, 255)).visible(scanEndGateways::get).build());

    // ── Render ─────────────────────────────────────────────────────
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());
        
    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode").description("GLOW = layered bloom boxes. SPECTRAL = outline shader. PULSE = fading highlight.").defaultValue(RenderMode.GLOW).build());
        
    private final Setting<Boolean> dynamicColors = sgRender.add(new BoolSetting.Builder()
        .name("dynamic-colors").defaultValue(false).build());

    private final Setting<Integer> glowLayers = sgRender.add(new IntSetting.Builder()
        .name("glow-layers").description("Number of bloom layers rendered around each target.")
        .defaultValue(4).min(1).sliderMax(8)
        .visible(() -> renderMode.get() == RenderMode.GLOW || renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Setting<Double> glowSpread = sgRender.add(new DoubleSetting.Builder()
        .name("glow-spread").description("How far each bloom layer expands outward (in blocks).")
        .defaultValue(0.04).min(0.01).sliderMax(0.15)
        .visible(() -> renderMode.get() == RenderMode.GLOW || renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Setting<Integer> glowBaseAlpha = sgRender.add(new IntSetting.Builder()
        .name("glow-base-alpha").description("Alpha of the innermost glow layer (0-255).")
        .defaultValue(60).min(10).sliderMax(150)
        .visible(() -> renderMode.get() == RenderMode.GLOW)
        .build()
    );

    private final Setting<Integer> spectralFillAlpha = sgRender.add(new IntSetting.Builder()
        .name("spectral-fill-alpha")
        .description("Fill alpha for block targets in SPECTRAL mode (0 = invisible, 30 = subtle).")
        .defaultValue(30).min(0).max(120).sliderMax(80)
        .visible(() -> renderMode.get() == RenderMode.SPECTRAL)
        .build()
    );

    private final Setting<Double> pulseSpeed = sgRender.add(new DoubleSetting.Builder()
        .name("pulse-speed")
        .description("Pulse cycle speed. 1.0 = one full fade in/out per second.")
        .defaultValue(1.0).min(0.1).max(5.0).sliderMax(3.0)
        .visible(() -> renderMode.get() == RenderMode.PULSE).build()
    );

    private final Setting<Integer> pulseMinAlpha = sgRender.add(new IntSetting.Builder()
        .name("pulse-min-alpha")
        .description("Lowest alpha reached during the pulse (0 = invisible).")
        .defaultValue(15).min(0).max(255).sliderMax(100)
        .visible(() -> renderMode.get() == RenderMode.PULSE).build()
    );

    private final Setting<Integer> pulseMaxAlpha = sgRender.add(new IntSetting.Builder()
        .name("pulse-max-alpha")
        .description("Peak alpha reached during the pulse.")
        .defaultValue(220).min(15).max(255).sliderMax(255)
        .visible(() -> renderMode.get() == RenderMode.PULSE).build()
    );

    // ── Beam ───────────────────────────────────────────────────────
    private final Setting<Boolean> showBeam = sgBeam.add(new BoolSetting.Builder()
        .name("show-beam").defaultValue(true).build());

    private final Setting<Integer> beamRange = sgBeam.add(new IntSetting.Builder()
        .name("beam-range")
        .description("Maximum horizontal distance (in chunks) to render the vertical beam.")
        .defaultValue(16)
        .min(1)
        .sliderMax(64)
        .visible(showBeam::get)
        .build());

    private final Setting<Boolean> onlyNearestBeam = sgBeam.add(new BoolSetting.Builder()
        .name("nearest-beam")
        .description("Only render the beam for the portal closest to the player.")
        .defaultValue(false)
        .visible(showBeam::get)
        .build());

    private final Setting<BeamStyle> beamStyle = sgBeam.add(new EnumSetting.Builder<BeamStyle>()
        .name("beam-style").defaultValue(BeamStyle.GUARDIAN).visible(showBeam::get).build());

    private final Setting<Integer> beamWidth = sgBeam.add(new IntSetting.Builder()
        .name("beam-width").description("Width of the box-style beam.").defaultValue(15)
        .visible(() -> showBeam.get() && beamStyle.get() == BeamStyle.BOX).build());

    private final Setting<Double> guardianRadius = sgBeam.add(new DoubleSetting.Builder()
        .name("guardian-radius").description("Radius of guardian-style beam strands.").defaultValue(0.08).visible(() -> showBeam.get() && beamStyle.get() == BeamStyle.GUARDIAN).build());

    private final Setting<Integer> guardianStrands = sgBeam.add(new IntSetting.Builder()
        .name("guardian-strands").description("Number of rotating strands.").defaultValue(4).visible(() -> showBeam.get() && beamStyle.get() == BeamStyle.GUARDIAN).build());

    private final Setting<Double> guardianSpinSpeed = sgBeam.add(new DoubleSetting.Builder()
        .name("guardian-spin-speed").description("Rotation speed of strands.").defaultValue(1.0).visible(() -> showBeam.get() && beamStyle.get() == BeamStyle.GUARDIAN).build());

    private final Setting<Integer> guardianCoreAlpha = sgBeam.add(new IntSetting.Builder()
        .name("guardian-core-alpha").description("Alpha of the beam center.").defaultValue(90).visible(() -> showBeam.get() && beamStyle.get() == BeamStyle.GUARDIAN).build());

    private final Setting<Integer> guardianStrandAlpha = sgBeam.add(new IntSetting.Builder()
        .name("guardian-strand-alpha").description("Alpha of the rotating strands.").defaultValue(160).visible(() -> showBeam.get() && beamStyle.get() == BeamStyle.GUARDIAN).build());

    // ═══════════════════════════════════════════════════════════════
    // End Assistant Settings
    // ═══════════════════════════════════════════════════════════════

    private final Setting<Integer> cityYLevel = sgEAGeneral.add(new IntSetting.Builder()
        .name("city-y-level")
        .description("Minimum Y level to scan. End Cities generate above Y = 0.")
        .defaultValue(0).min(-64).max(320).sliderMin(-64).sliderMax(320)
        .visible(() -> enableEndAssistant.get())
        .onChanged(v -> {
            eaScannedChunks.clear();
            targets.entrySet().removeIf(entry -> entry.getKey().getY() < v);
        })
        .build()
    );

    // EA Blocks
    private final Setting<Boolean> trackContainers = sgEABlocks.add(new BoolSetting.Builder()
        .name("track-chests").description("Highlight standard chests and copper chests.").defaultValue(true)
        .visible(() -> enableEndAssistant.get())
        .build()
    );

    private final Setting<SettingColor> containerColor = sgEABlocks.add(new ColorSetting.Builder()
        .name("chest-color").description("Color for standard chests.")
        .defaultValue(new SettingColor(255, 215, 0, 255))
        .visible(() -> enableEndAssistant.get() && trackContainers.get()).build()
    );

    private final Setting<List<Item>> containerWhitelist = sgEABlocks.add(new ItemListSetting.Builder()
        .name("chest-whitelist")
        .description("Items to alert you about when opening Chests.")
        .defaultValue(List.of(
            Items.NETHERITE_BLOCK, Items.NETHERITE_INGOT, Items.DIAMOND, 
            Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            Items.ENDER_CHEST, Items.ENCHANTED_GOLDEN_APPLE, Items.ELYTRA,
            Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE,
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
            Items.SHULKER_BOX, Items.WHITE_SHULKER_BOX, Items.ORANGE_SHULKER_BOX, Items.MAGENTA_SHULKER_BOX,
            Items.LIGHT_BLUE_SHULKER_BOX, Items.YELLOW_SHULKER_BOX, Items.LIME_SHULKER_BOX, Items.PINK_SHULKER_BOX,
            Items.GRAY_SHULKER_BOX, Items.LIGHT_GRAY_SHULKER_BOX, Items.CYAN_SHULKER_BOX, Items.PURPLE_SHULKER_BOX,
            Items.BLUE_SHULKER_BOX, Items.BROWN_SHULKER_BOX, Items.GREEN_SHULKER_BOX, Items.RED_SHULKER_BOX,
            Items.BLACK_SHULKER_BOX,
            Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
            Items.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SHULKER_SHELL, Items.DRAGON_BREATH, Items.END_CRYSTAL, Items.CHORUS_FRUIT
        ))
        .visible(() -> enableEndAssistant.get())
        .build()
    );

    // EA Entities
    private final Setting<Boolean> trackElytras = sgEAEntities.add(new BoolSetting.Builder()
        .name("elytra-frames").description("Highlights Item Frames holding an Elytra.").defaultValue(true)
        .visible(() -> enableEndAssistant.get())
        .build()
    );

    private final Setting<SettingColor> elytraColor = sgEAEntities.add(new ColorSetting.Builder()
        .name("elytra-color").description("Color for Elytra Item Frames.")
        .defaultValue(new SettingColor(255, 255, 0, 255))
        .visible(() -> enableEndAssistant.get() && trackElytras.get()).build()
    );

    private final Setting<Boolean> trackShulkers = sgEAEntities.add(new BoolSetting.Builder()
        .name("shulkers").description("Highlights Shulkers and Shulker Bullets.").defaultValue(true)
        .visible(() -> enableEndAssistant.get())
        .build()
    );

    private final Setting<SettingColor> shulkerColor = sgEAEntities.add(new ColorSetting.Builder()
        .name("shulker-color").description("Color for Shulkers and Shulker Bullets.")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .visible(() -> enableEndAssistant.get() && trackShulkers.get()).build()
    );

    // EA Automation & Safety
    private final Setting<Boolean> enableAlerts = sgEAAutomation.add(new BoolSetting.Builder()
        .name("alerts")
        .description("Master toggle for audio cues and loot announcements.")
        .defaultValue(true)
        .visible(() -> enableEndAssistant.get())
        .build()
    );

    private final Setting<AlertSound> alertSound = sgEAAutomation.add(new EnumSetting.Builder<AlertSound>()
        .name("alert-sound")
        .description("Which sound to play for module alerts.")
        .defaultValue(AlertSound.ENDER_DRAGON_GROWL)
        .visible(() -> enableEndAssistant.get() && enableAlerts.get())
        .build()
    );

    private final Setting<Double> alertVolume = sgEAAutomation.add(new DoubleSetting.Builder()
        .name("alert-volume")
        .description("Volume of the alert sound. Goes up to 5.0 for extra loud alerts.")
        .defaultValue(1.0).min(0).max(5.0).sliderMax(5.0)
        .visible(() -> enableEndAssistant.get() && enableAlerts.get())
        .build()
    );

    private final Setting<Boolean> autoOpenChests = sgEAAutomation.add(new BoolSetting.Builder()
        .name("auto-open-chests")
        .description("Automatically opens nearby chests.")
        .defaultValue(true)
        .visible(() -> enableEndAssistant.get())
        .build()
    );

    private final Setting<Boolean> autoMilkLevitation = sgEAAutomation.add(new BoolSetting.Builder()
        .name("auto-milk-levitation")
        .description("Automatically drinks milk to clear the Levitation effect.")
        .defaultValue(false)
        .visible(() -> enableEndAssistant.get())
        .build()
    );

    private final Setting<Boolean> disconnectOnPlayer = sgEASafety.add(new BoolSetting.Builder()
        .name("disconnect-on-player")
        .description("Instantly disconnects from the server if another player enters render distance.")
        .defaultValue(false)
        .visible(() -> enableEndAssistant.get())
        .build()
    );

    private final Setting<Boolean> autoDisableOnLowHealth = sgEASafety.add(new BoolSetting.Builder()
        .name("auto-disable-on-low-health")
        .description("Disables the module if health is critical.")
        .defaultValue(true)
        .visible(() -> enableEndAssistant.get())
        .build()
    );

    public Gatekeeper() {
        super(Tim.CATEGORY, "gatekeeper", "Advanced End gateway and End portal tracking with integrated End Assistant.");
    }

    // ── Lifecycle ───────────────────────────────────────────────────────
    @Override
    public void onActivate() {
        clearAllState();
        // End Assistant State Clear
        targets.clear();
        eaScannedChunks.clear();
        checkedContainers.clear();
        notifiedShulkers.clear();
        notifiedElytras.clear();
        levitationWarnTimer = 0;
        totalElytrasFound = 0;
        drinkTimer = 0;
        previousDrinkSlot = -1;
        hasAlertedForCurrentScreen = false;
        GlowingRegistry.clear();
    }

    @Override
    public void onDeactivate() {
        clearAllState();
        // End Assistant State Clear
        if (drinkTimer > 0) {
            mc.options.keyUse.setDown(false);
            if (previousDrinkSlot != -1 && mc.player != null) {
                mc.player.getInventory().setSelectedSlot(previousDrinkSlot);
            }
        }
        GlowingRegistry.clear();
        targets.clear();
    }

    private void clearAllState() {
        portals.clear(); portalStructureMap.clear(); scannedChunks.clear(); dirtyChunks.clear();
        notifiedStructures.clear(); portalsDirty = false;
    }

    // ── Event Handlers ─────────────────────────────────────────────
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (!dirtyChunks.isEmpty()) { scannedChunks.removeAll(dirtyChunks); dirtyChunks.clear(); }
        BlockPos p = mc.player.blockPosition();
        scanNewChunks(p.getX() >> 4, p.getZ() >> 4);
        if (portalsDirty) { portalsDirty = false; groupPortals(); }

        if (++cleanupTimer >= CLEANUP_INTERVAL_TICKS) {
            cleanupTimer = 0;
            cleanupDistantPortals();
        }

        if (enableEndAssistant.get()) {
            if (eaPerformSafetyChecks()) return;
            eaCheckForPlayers();
            eaCheckLevitationEffect();
            eaUpdateContainerLogic();
            eaCheckOpenedContainerLoot(); 
            eaUpdateMilkDrink();
            eaUpdateScanningLogic();
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.level == null) return;
        PortalType type = (event.newState.is(Blocks.END_GATEWAY)) ? PortalType.END_GATEWAY : (event.newState.is(Blocks.END_PORTAL)) ? PortalType.END_PORTAL : null;
        if (type != null) { portals.put(event.pos, type); portalsDirty = true; }
        else if (portals.remove(event.pos) != null) portalsDirty = true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        double beamDistSq = Math.pow(beamRange.get() * 16.0, 2);

        PortalStructure nearest = null;
        if (showBeam.get() && onlyNearestBeam.get()) {
            double minSq = Double.MAX_VALUE;
            for (PortalStructure structure : portalStructureMap.values()) {
                double sq = com.example.addon.utils.MinecraftCompat.position(mc.player).distanceToSqr(structure.boundingBox.getCenter());
                if (sq < minSq) { minSq = sq; nearest = structure; }
            }
        }

        for (PortalStructure structure : portalStructureMap.values()) {
            SettingColor color = getStructureColor(structure);
            if (color == null) continue;
            if (renderMode.get() == RenderMode.SPECTRAL) renderSpectral(event, structure, color);
            else if (renderMode.get() == RenderMode.PULSE) {
                renderPulseBox(event, structure.boundingBox, color);
            } else {
                renderGlowLayers(event, structure.boundingBox, color);
                event.renderer.box(structure.boundingBox, withAlpha(color, 0), color, shapeMode.get(), 0);
            }
            if (showBeam.get() && (nearest == null || structure == nearest) && com.example.addon.utils.MinecraftCompat.position(mc.player).distanceToSqr(structure.boundingBox.getCenter()) <= beamDistSq) {
                SettingColor beamColor = (renderMode.get() == RenderMode.PULSE) ? pulseColor(color) : color;
                renderBeams(event, List.of(new BeamData(structure.boundingBox, beamColor)));
            }
        }

        if (enableEndAssistant.get()) {
            boolean isSpectral = renderMode.get() == RenderMode.SPECTRAL;
            boolean isPulse = renderMode.get() == RenderMode.PULSE;
            Set<BlockPos> toRemove = new HashSet<>();

            for (Map.Entry<BlockPos, TargetType> entry : targets.entrySet()) {
                BlockPos pos = entry.getKey();
                TargetType type = entry.getValue();

                if (!mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                if (mc.level.getBlockState(pos).isAir()) { toRemove.add(pos); continue; }

                Block currentBlock = mc.level.getBlockState(pos).getBlock();
                if (!eaValidateBlockType(currentBlock, type)) { toRemove.add(pos); continue; }

                AABB renderBox = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
                SettingColor color = eaGetColor(type);
                if (color == null) continue;

                if (isSpectral) {
                    event.renderer.box(renderBox, withAlpha(color, spectralFillAlpha.get()), withAlpha(color, 0), ShapeMode.Sides, 0);
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

            // Beams disabled for all End Assistant entities (set to false)
            eaRenderEntity(event, isSpectral, isPulse, trackElytras.get(), elytraFrameTargets, elytraColor.get());
            eaRenderEntity(event, isSpectral, isPulse, trackShulkers.get(), shulkerTargets, shulkerColor.get());
            eaRenderEntity(event, isSpectral, isPulse, trackShulkers.get(), bulletTargets, shulkerColor.get());
        }
    }

    // ── Gatekeeper Core Logic ──────────────────────────────────────
    private void scanNewChunks(int centerChunkX, int centerChunkZ) {
        int r = range.get(), rSq = r * r, scanned = 0;
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

        LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx, cz);
        if (chunk != null) {
            scanChunk(chunk);
            scannedChunks.add(cp);
            return true;
        }
        return false;
    }

    private void scanChunk(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int chunkX = chunk.getPos().x() << 4;
        int chunkZ = chunk.getPos().z() << 4;

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;

            // High-performance check: Skip entire section if no target blocks exist in the palette
            boolean hasPortal = scanEndPortals.get() && section.maybeHas(state -> state.is(Blocks.END_PORTAL));
            boolean hasGateway = scanEndGateways.get() && section.maybeHas(state -> state.is(Blocks.END_GATEWAY));
            if (!hasPortal && !hasGateway) continue;

            int sectionMinY = (chunk.getMinSectionY() + i) * 16;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        var state = section.getBlockState(x, y, z);
                        if (hasPortal && state.is(Blocks.END_PORTAL)) {
                            BlockPos pos = new BlockPos(chunkX + x, sectionMinY + y, chunkZ + z);
                            if (!portals.containsKey(pos)) {
                                portals.put(pos, PortalType.END_PORTAL);
                                portalsDirty = true;
                            }
                        } else if (hasGateway && state.is(Blocks.END_GATEWAY)) {
                            BlockPos pos = new BlockPos(chunkX + x, sectionMinY + y, chunkZ + z);
                            if (!portals.containsKey(pos)) {
                                portals.put(pos, PortalType.END_GATEWAY);
                                portalsDirty = true;
                            }
                        }
                    }
                }
            }
        }
    }

    private void groupPortals() {
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> active = new HashSet<>();

        for (BlockPos startPos : portals.keySet()) {
            if (visited.contains(startPos)) continue;
            PortalType type = portals.get(startPos);
            Set<BlockPos> component = new HashSet<>();
            Queue<BlockPos> queue = new LinkedList<>();
            AABB structureBox = new AABB(startPos);
            queue.add(startPos); visited.add(startPos);
            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                component.add(current);
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.relative(dir);
                    if (portals.get(neighbor) == type && visited.add(neighbor)) {
                        queue.add(neighbor); structureBox = structureBox.minmax(new AABB(neighbor));
                    }
                }
            }
            BlockPos anchor = componentAnchor(component);
            active.add(anchor);
            BlockPos dest = null;
            if (type == PortalType.END_GATEWAY) {
                BlockEntity be = mc.level.getBlockEntity(anchor);
                if (be instanceof TheEndGatewayBlockEntity gateway) {
                    dest = ((EndGatewayBlockEntityAccessor) gateway).getExitPortalPos();
                }
            }
            portalStructureMap.put(anchor, new PortalStructure(structureBox.inflate(0.02), component, type, dest));
            if (type == PortalType.END_GATEWAY) notifyGateway(anchor, dest);
        }
        portalStructureMap.keySet().retainAll(active);
    }

    private void cleanupDistantPortals() {
        if (mc.player == null) return;
        double distSq = Math.pow(range.get() * 16 + 64, 2);
        if (portals.entrySet().removeIf(e -> e.getKey().distToCenterSqr(com.example.addon.utils.MinecraftCompat.position(mc.player)) > distSq)) portalsDirty = true;

        int px = mc.player.blockPosition().getX() >> 4, pz = mc.player.blockPosition().getZ() >> 4;
        int rSq = range.get() * range.get();
        scannedChunks.removeIf(cp -> (cp.x() - px) * (cp.x() - px) + (cp.z() - pz) * (cp.z() - pz) > rSq);
    }

    private void notifyGateway(BlockPos pos, BlockPos dest) {
        String id = "GW_" + pos.toShortString();
        if (!notifiedStructures.add(id)) return;
        info("§dEnd Gateway §7detected");
    }

    private BlockPos componentAnchor(Set<BlockPos> comp) {
        BlockPos anchor = null;
        for (BlockPos p : comp) if (anchor == null || p.getY() < anchor.getY() || (p.getY() == anchor.getY() && p.getX() < anchor.getX())) anchor = p;
        return anchor;
    }

    // ── Unified Render Helpers ─────────────────────────────────────
    private void renderSpectral(Render3DEvent event, PortalStructure structure, SettingColor color) {
        event.renderer.box(structure.boundingBox.inflate(0.05), withAlpha(color, spectralFillAlpha.get()), withAlpha(color, 255), ShapeMode.Both, 0);
    }

    private void renderGlowLayers(Render3DEvent event, AABB box, SettingColor color) {
        int layers = glowLayers.get();
        double spread = glowSpread.get();
        int baseAlpha = glowBaseAlpha.get();

        for (int i = layers; i >= 1; i--) {
            double expansion = spread * i;
            int layerAlpha = Math.max(4, (int) (baseAlpha * (1.0 - (double)(i - 1) / layers)));
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

    private void renderBeams(Render3DEvent event, List<BeamData> beams) {
        for (BeamData beam : beams) {
            if (beamStyle.get() == BeamStyle.GUARDIAN) renderGuardianBeam(event, beam.box, beam.color);
            else renderBoxBeam(event, beam.box, beam.color);
        }
    }

    private void renderBoxBeam(Render3DEvent event, AABB anchorBox, SettingColor color) {
        double beamSize = beamWidth.get() / 100.0, centerX = (anchorBox.minX + anchorBox.maxX) / 2.0, centerZ = (anchorBox.minZ + anchorBox.maxZ) / 2.0;
        int worldBot = mc.level.getMinY(), worldTop = worldBot + mc.level.getHeight();
        AABB beamBox = new AABB(centerX - beamSize, worldBot, centerZ - beamSize, centerX + beamSize, worldTop, centerZ + beamSize);
        renderGlowLayers(event, beamBox, color);
        event.renderer.box(beamBox, withAlpha(color, 60), color, ShapeMode.Both, 0);
    }

    private void renderGuardianBeam(Render3DEvent event, AABB anchorBox, SettingColor color) {
        double cx = (anchorBox.minX + anchorBox.maxX) / 2.0, cz = (anchorBox.minZ + anchorBox.maxZ) / 2.0;
        int worldBot = mc.level.getMinY(), worldTop = worldBot + mc.level.getHeight();
        double radius = guardianRadius.get(), rotationRad = (System.currentTimeMillis() % 6000L) / 6000.0 * Math.PI * 2.0;
        for (int i = 0; i < guardianStrands.get(); i++) {
            double angle = rotationRad + (Math.PI * 2.0 / guardianStrands.get()) * i;
            AABB strandBox = new AABB(cx + Math.cos(angle) * radius - 0.01, worldBot, cz + Math.sin(angle) * radius - 0.01, cx + Math.cos(angle) * radius + 0.01, worldTop, cz + Math.sin(angle) * radius + 0.01);
            event.renderer.box(strandBox, withAlpha(color, guardianStrandAlpha.get() / 2), withAlpha(color, guardianStrandAlpha.get()), ShapeMode.Both, 0);
        }
    }

    // ── Utility Helpers ────────────────────────────────────────────
    private SettingColor getStructureColor(PortalStructure structure) {
        if (dynamicColors.get()) {
            float hue = ( (structure.type == PortalType.END_PORTAL ? 0.333f : 0.667f) + (System.currentTimeMillis() % 3000) / 3000f) % 1f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
            return new SettingColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
        }
        return structure.type == PortalType.END_PORTAL ? endPortalColor.get() : endGatewayColor.get();
    }

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }

    // ═══════════════════════════════════════════════════════════════
    // End Assistant Logic
    // ═══════════════════════════════════════════════════════════════

    private void eaUpdateScanningLogic() {
        if (mc.level.dimension() == null) return;
        if (dimensionChangeCooldown > 0) { dimensionChangeCooldown--; return; }

        String currDim = mc.level.dimension().identifier().toString();
        if (!currDim.equals(lastDimension)) {
            dimensionChangeCooldown = DIMENSION_CHANGE_COOLDOWN_TICKS;
            lastDimension = currDim;
            targets.clear();
            eaScannedChunks.clear();
            GlowingRegistry.clear();
            return;
        }

        BlockPos playerPos = mc.player.blockPosition();
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;

        eaCleanupDistantTargets(playerPos);
        eaScanElytraFrames();
        eaScanShulkers();
        eaScanShulkerBullets();
        eaPruneBlockTargets();
        eaScanNewChunks(centerChunkX, centerChunkZ);
    }

    private void eaScanElytraFrames() {
        elytraFrameTargets.clear();
        if (!trackElytras.get()) return;

        int blockRange = range.get() * 16;
        AABB searchBox = new AABB(mc.player.blockPosition()).inflate(blockRange);
        Set<Integer> currentIds = new HashSet<>();

        for (ItemFrame frame : mc.level.getEntitiesOfClass(ItemFrame.class, searchBox, e -> true)) {
            if (frame.getItem().is(Items.ELYTRA)) {
                elytraFrameTargets.add(frame);
                currentIds.add(frame.getId());

                if (renderMode.get() == RenderMode.SPECTRAL) {
                    GlowingRegistry.add(frame.getId(), eaToArgb(elytraColor.get()));
                } else {
                    GlowingRegistry.remove(frame.getId());
                }

                if (notifiedElytras.add(frame.getId())) {
                    totalElytrasFound++;
                    if (enableAlerts.get()) {
                        info("§e§lELYTRA FOUND! §aItem frame detected.");
                        eaPlayAlert();
                    }
                }
            }
        }
        notifiedElytras.retainAll(currentIds);
    }

    private void eaScanShulkers() {
        shulkerTargets.clear();
        if (!trackShulkers.get()) return;

        int blockRange = range.get() * 16;
        AABB searchBox = new AABB(mc.player.blockPosition()).inflate(blockRange);
        Set<Integer> currentIds = new HashSet<>();

        for (Shulker shulker : mc.level.getEntitiesOfClass(Shulker.class, searchBox, e -> true)) {
            shulkerTargets.add(shulker);
            currentIds.add(shulker.getId());

            if (renderMode.get() == RenderMode.SPECTRAL) {
                GlowingRegistry.add(shulker.getId(), eaToArgb(shulkerColor.get()));
            } else {
                GlowingRegistry.remove(shulker.getId());
            }

            if (notifiedShulkers.add(shulker.getId())) {
                if (enableAlerts.get()) {
                    info("§dShulker Detected!");
                    eaPlayAlert();
                }
            }
        }
        notifiedShulkers.retainAll(currentIds);
    }

    private void eaScanShulkerBullets() {
        bulletTargets.clear();
        if (!trackShulkers.get()) return;

        int blockRange = range.get() * 16;
        AABB searchBox = new AABB(mc.player.blockPosition()).inflate(blockRange);

        for (ShulkerBullet bullet : mc.level.getEntitiesOfClass(ShulkerBullet.class, searchBox, e -> true)) {
            bulletTargets.add(bullet);
            if (renderMode.get() == RenderMode.SPECTRAL) {
                GlowingRegistry.add(bullet.getId(), eaToArgb(shulkerColor.get()));
            }
        }
    }

    private void eaScanNewChunks(int centerChunkX, int centerChunkZ) {
        int r = range.get();
        int rSq = r * r;

        eaScannedChunks.removeIf(cp -> {
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
                if (eaProcessChunk(centerChunkX + x, centerChunkZ + minZ, rSq, centerChunkX, centerChunkZ)) chunksScanned++;
                if (chunksScanned >= limit) break outer;
                if (minZ != maxZ) {
                    if (eaProcessChunk(centerChunkX + x, centerChunkZ + maxZ, rSq, centerChunkX, centerChunkZ)) chunksScanned++;
                    if (chunksScanned >= limit) break outer;
                }
            }

            for (int z = minZ + 1; z < maxZ; z++) {
                if (eaProcessChunk(centerChunkX + minX, centerChunkZ + z, rSq, centerChunkX, centerChunkZ)) chunksScanned++;
                if (chunksScanned >= limit) break outer;
                if (minX != maxX) {
                    if (eaProcessChunk(centerChunkX + maxX, centerChunkZ + z, rSq, centerChunkX, centerChunkZ)) chunksScanned++;
                    if (chunksScanned >= limit) break outer;
                }
            }
        }
    }

    private boolean eaProcessChunk(int cx, int cz, int rSq, int centerChunkX, int centerChunkZ) {
        int dx = cx - centerChunkX, dz = cz - centerChunkZ;
        if (dx * dx + dz * dz > rSq) return false;

        ChunkPos cp = new ChunkPos(cx, cz);
        if (eaScannedChunks.contains(cp)) return false;
        if (!mc.level.getChunkSource().hasChunk(cx, cz)) return false;

        LevelChunk chunk = mc.level.getChunk(cx, cz);
        eaScanBlockEntitiesInChunk(chunk);
        eaScannedChunks.add(cp);
        return true;
    }

    private void eaScanBlockEntitiesInChunk(LevelChunk chunk) {
        int minY = cityYLevel.get(); 

        for (BlockEntity be : chunk.getBlockEntities().values()) {
            BlockPos pos = be.getBlockPos();
            if (pos.getY() < minY) continue;

            if (be instanceof ChestBlockEntity || isCopperChestBlock(mc.level.getBlockState(pos).getBlock())) {
                targets.put(pos, TargetType.CONTAINER);
            }
        }
    }

    private boolean isCopperChestBlock(Block block) {
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return name.contains("copper_chest");
    }

    private void eaUpdateContainerLogic() {
        if (!autoOpenChests.get()) return;
        
        if (interactTimeoutTimer > 0) interactTimeoutTimer--;

        if (mc.screen == null && !wasAutoOpened) {
            List<BlockPos> nearbyChests = targets.entrySet().stream()
                .filter(e -> e.getValue() == TargetType.CONTAINER)
                .map(Map.Entry::getKey)
                .filter(pos -> !checkedContainers.contains(pos))
                .filter(pos -> Math.sqrt(pos.distToCenterSqr(com.example.addon.utils.MinecraftCompat.position(mc.player))) <= 4.5)
                .sorted(Comparator.comparingDouble(pos -> pos.distToCenterSqr(com.example.addon.utils.MinecraftCompat.position(mc.player))))
                .toList();

            if (!nearbyChests.isEmpty()) {
                BlockPos pos = nearbyChests.get(0);
                checkedContainers.add(pos);
                wasAutoOpened = true;
                interactTimeoutTimer = INTERACT_TIMEOUT_TICKS;

                Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> {
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                });
            }
        } else if (mc.screen == null && wasAutoOpened && interactTimeoutTimer == 0) {
            wasAutoOpened = false;
        }
    }

    private void eaCheckOpenedContainerLoot() {
        if (mc.screen instanceof AbstractContainerScreen<?> screen && !(mc.screen instanceof InventoryScreen)) {
            if (mc.screen instanceof ShulkerBoxScreen || screen.getTitle().getString().equals(Component.translatable("container.enderchest").getString())) {
                hasAlertedForCurrentScreen = true;
                return;
            }
            
            if (!hasAlertedForCurrentScreen) {
                for (int i = 0; i < screen.getMenu().slots.size(); i++) {
                    Slot slot = screen.getMenu().slots.get(i);
                    if (slot.container instanceof Inventory) continue;
                    
                    ItemStack stack = slot.getItem();
                    if (!stack.isEmpty() && containerWhitelist.get().contains(stack.getItem())) {
                        info("§cRare loot found in chest: §e" + stack.getHoverName().getString() + "§c!");
                        eaPlayAlert();
                        hasAlertedForCurrentScreen = true;
                        break;
                    }
                }
            }
        } else {
            hasAlertedForCurrentScreen = false;
        }
    }

    private void eaUpdateMilkDrink() {
        if (!autoMilkLevitation.get()) {
            if (drinkTimer > 0) {
                mc.options.keyUse.setDown(false);
                if (previousDrinkSlot != -1 && mc.player != null) {
                    mc.player.getInventory().setSelectedSlot(previousDrinkSlot);
                    previousDrinkSlot = -1;
                }
                drinkTimer = 0;
            }
            return;
        }

        boolean hasLevitation = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION);
        
        if (drinkTimer == 0 && hasLevitation && mc.screen == null) {
            int milkSlot = eaFindMilkBucket();
            if (milkSlot != -1) {
                previousDrinkSlot = com.example.addon.utils.MinecraftCompat.selectedSlot(mc.player.getInventory());
                mc.player.getInventory().setSelectedSlot(milkSlot);
                mc.options.keyUse.setDown(true);
                drinkTimer = 32;
            }
        } else if (drinkTimer > 0) {
            drinkTimer--;
            if (!hasLevitation || drinkTimer == 0 || mc.player.getInventory().getItem(com.example.addon.utils.MinecraftCompat.selectedSlot(mc.player.getInventory())).getItem() != Items.MILK_BUCKET) {
                mc.options.keyUse.setDown(false);
                if (previousDrinkSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(previousDrinkSlot);
                    previousDrinkSlot = -1;
                }
                drinkTimer = 0;
            }
        }
    }

    private int eaFindMilkBucket() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.MILK_BUCKET)) return i;
        }
        return -1;
    }

    private void eaCheckLevitationEffect() {
        if (!enableAlerts.get()) return;

        if (levitationWarnTimer > 0) {
            levitationWarnTimer--;
            return;
        }

        boolean hasLevitation = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION);

        if (hasLevitation) {
            warning("Levitation effect applied! Watch your altitude.");
            eaPlayAlert();
            levitationWarnTimer = 200;
        }
    }

    private void eaCheckForPlayers() {
        if (!disconnectOnPlayer.get()) return;
        for (Player player : mc.level.players()) {
            if (player == mc.player || player.isSpectator()) continue;
            if (player.distanceTo(mc.player) < 128) {
                info("§cPlayer detected in render distance! Disconnecting...");
                mc.getConnection().getConnection().disconnect(new DisconnectionDetails(Component.literal("Player detected in render distance")));
                return;
            }
        }
    }

    private void eaPlayAlert() {
        if (mc.player == null) return;
        SoundEvent sound = switch (alertSound.get()) {
            case LEVEL_UP -> SoundEvents.PLAYER_LEVELUP;
            case SHULKER_TELEPORT -> SoundEvents.SHULKER_TELEPORT;
            case EXPERIENCE_ORB -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case BELL -> SoundEvents.BELL_BLOCK;
            case ENDER_DRAGON_GROWL -> SoundEvents.ENDER_DRAGON_GROWL;
        };
        mc.player.playSound(sound, alertVolume.get().floatValue(), 1.0f);
    }

    private void eaRenderEntity(Render3DEvent event, boolean isSpectral, boolean isPulse, boolean isEnabled, List<? extends net.minecraft.world.entity.Entity> entities, SettingColor color) {
        if (!isEnabled || entities.isEmpty()) return;

        for (net.minecraft.world.entity.Entity entity : entities) {
            if (!entity.isAlive()) continue;
            AABB box = entity.getBoundingBox();

            if (isSpectral) {
                event.renderer.box(box, withAlpha(color, 0), withAlpha(color, 200), ShapeMode.Lines, 0);
            } else if (isPulse) {
                renderPulseBox(event, box, color);
            } else {
                renderGlowLayers(event, box, color);
                event.renderer.box(box, withAlpha(color, 0), color, ShapeMode.Lines, 0);
            }
        }
    }

    private boolean eaValidateBlockType(Block block, TargetType type) {
        return switch (type) {
            case CONTAINER -> block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL || isCopperChestBlock(block);
        };
    }

    private SettingColor eaGetColor(TargetType type) {
        return switch (type) {
            case CONTAINER -> trackContainers.get() ? containerColor.get() : null;
        };
    }

    private void eaPruneBlockTargets() {
        if (mc.level == null || mc.player == null) return;
        Set<BlockPos> toRemove = new HashSet<>();
        for (Map.Entry<BlockPos, TargetType> entry : targets.entrySet()) {
            BlockPos pos = entry.getKey();
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;

            if (mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                Block currentBlock = mc.level.getBlockState(pos).getBlock();
                if (mc.level.getBlockState(pos).isAir() || !eaValidateBlockType(currentBlock, entry.getValue())) {
                    toRemove.add(pos);
                }
            } else {
                toRemove.add(pos);
                eaScannedChunks.remove(new ChunkPos(chunkX, chunkZ));
            }
        }
        for (BlockPos pos : toRemove) {
            targets.remove(pos);
        }
    }

    private void eaCleanupDistantTargets(BlockPos playerPos) {
        int r = range.get();
        int pChunkX = playerPos.getX() >> 4;
        int pChunkZ = playerPos.getZ() >> 4;

        targets.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            int dx = (pos.getX() >> 4) - pChunkX;
            int dz = (pos.getZ() >> 4) - pChunkZ;
            if (dx * dx + dz * dz > r * r) {
                eaScannedChunks.remove(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
                return true;
            }
            return false;
        });
    }

    private boolean eaPerformSafetyChecks() {
        if (!autoDisableOnLowHealth.get()) return false;
        boolean hasTotem = mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
            || mc.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
        if (hasTotem && mc.player.getHealth() <= 6) { 
            error("Health is critical, disabling to prevent totem pop.");
            toggle();
            return true;
        }
        return false;
    }

    private int eaToArgb(SettingColor c) {
        return (c.a << 24) | (c.r << 16) | (c.g << 8) | c.b;
    }

    public List<EndAssistantHud.EndStat> getEndAssistantStats() {
        List<EndAssistantHud.EndStat> stats = new ArrayList<>();
        int chestsNearby = 0;
        
        for (TargetType type : targets.values()) {
            if (type == TargetType.CONTAINER) chestsNearby++;
        }
        
        int elytrasNearby = elytraFrameTargets.size();
        int shulkersNearby = shulkerTargets.size();
        
        stats.add(new EndAssistantHud.EndStat("Elytras Found", totalElytrasFound, new ItemStack(Items.ELYTRA), totalElytrasFound > 0 ? EndAssistantHud.StatSeverity.Warning : EndAssistantHud.StatSeverity.Normal));
        stats.add(new EndAssistantHud.EndStat("Elytras Nearby", elytrasNearby, new ItemStack(Items.ELYTRA), elytrasNearby > 0 ? EndAssistantHud.StatSeverity.Critical : EndAssistantHud.StatSeverity.Normal));
        stats.add(new EndAssistantHud.EndStat("Chests Nearby", chestsNearby, new ItemStack(Items.CHEST), EndAssistantHud.StatSeverity.Normal));
        stats.add(new EndAssistantHud.EndStat("Shulkers", shulkersNearby, new ItemStack(Items.SHULKER_SHELL), shulkersNearby > 0 ? EndAssistantHud.StatSeverity.Warning : EndAssistantHud.StatSeverity.Normal));
        
        return stats;
    }

    // ── Public API ─────────────────────────────────────────────────
    public void markChunkDirty(ChunkPos cp) { scannedChunks.remove(cp); dirtyChunks.add(cp); portalsDirty = true; }

    public int getTotalEndPortals() { return (int) portalStructureMap.values().stream().filter(s -> s.type == PortalType.END_PORTAL).count(); }
    public int getTotalGateways()   { return (int) portalStructureMap.values().stream().filter(s -> s.type == PortalType.END_GATEWAY).count(); }

    // ── Inner Types ────────────────────────────────────────────────
    private enum PortalType { END_PORTAL, END_GATEWAY }

    private static class PortalStructure {
        final AABB boundingBox;
        final Set<BlockPos> portalBlocks;
        final PortalType type;
        final BlockPos destination;

        PortalStructure(AABB bb, Set<BlockPos> pb, PortalType t, BlockPos dest) {
            this.boundingBox = bb; this.portalBlocks = pb; this.type = t; this.destination = dest;
        }
    }

    private record BeamData(AABB box, SettingColor color) {}
}
