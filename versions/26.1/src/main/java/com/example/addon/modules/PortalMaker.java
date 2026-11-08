package com.example.addon.modules;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.example.addon.Tim;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.IBaritoneProcess;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
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
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PortalMaker extends Module {

    // ── Enums ──────────────────────────────────────────────────────
    public enum EntryMode    { None, Walk, Pearl }
    private enum RecycleState { IDLE, STEPPING_OUT, WAITING, RE_ENTERING }
    
    public enum RenderMode {
        GLOW,
        SPECTRAL,
        PULSE
    }

    // ── Setting Groups ─────────────────────────────────────────────
    private final SettingGroup sgGeneral      = settings.getDefaultGroup();
    private final SettingGroup sgMovement     = settings.createGroup("Movement & Entry");
    private final SettingGroup sgRecycle      = settings.createGroup("Recycle");
    private final SettingGroup sgRender       = settings.createGroup("Render");
    private final SettingGroup sgGlow         = settings.createGroup("Glow");

    // ── Settings — Building ────────────────────────────────────────
    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks to wait between placement actions.")
        .defaultValue(2).min(1).sliderRange(1, 12)
        .build()
    );

    private final Setting<Integer> finishDelay = sgGeneral.add(new IntSetting.Builder()
        .name("finish-delay")
        .description("Ticks to wait after lighting the portal before turning off.")
        .defaultValue(20).min(0).sliderMax(200)
        .build()
    );

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Lets you place obsidian in mid-air without needing a solid block to click against.")
        .defaultValue(true)
        .build()
    );

    // ── Settings — Movement ────────────────────────────────────────
    private final Setting<Boolean> useBaritone = sgMovement.add(new BoolSetting.Builder()
        .name("use-baritone")
        .description("Uses Baritone to automatically path into the portal.")
        .defaultValue(true)
        .build()
    );

    private final Setting<EntryMode> entryMode = sgMovement.add(new EnumSetting.Builder<EntryMode>()
        .name("entry-mode")
        .description("How to enter the portal after it is created.")
        .defaultValue(EntryMode.Walk)
        .visible(useBaritone::get)
        .build()
    );

    // ── Settings — Recycle ─────────────────────────────────────────
    private final Setting<Boolean> autoRecycle = sgRecycle.add(new BoolSetting.Builder()
        .name("auto-recycle")
        .description("After changing dimension, automatically step out, wait, and go back in.")
        .defaultValue(false)
        .visible(useBaritone::get)
        .build()
    );

    private final Setting<Boolean> cancelOnMovement = sgRecycle.add(new BoolSetting.Builder()
        .name("cancel-on-movement")
        .description("Cancels the recycle process if you manually press a movement key.")
        .defaultValue(true)
        .visible(() -> useBaritone.get() && autoRecycle.get())
        .build()
    );

    private final Setting<Integer> recycleDelaySeconds = sgRecycle.add(new IntSetting.Builder()
        .name("recycle-wait-time")
        .description("How many seconds to wait before going back into the portal.")
        .defaultValue(5).min(1).sliderMax(60)
        .visible(() -> useBaritone.get() && autoRecycle.get())
        .build()
    );

    private final Setting<Keybind> recycleKey = sgRecycle.add(new KeybindSetting.Builder()
        .name("recycle-key")
        .description("Manual keybind to trigger the recycle cycle (step out -> wait -> in).")
        .defaultValue(Keybind.none())
        .visible(useBaritone::get)
        .build()
    );

    private final Setting<Integer> dimensionSwitchCooldownTicks = sgRecycle.add(new IntSetting.Builder()
        .name("dimension-switch-cooldown")
        .description("Ticks to wait after a dimension change before resuming operations (e.g., recycling).")
        .defaultValue(40)
        .min(0).sliderMax(200)
        .visible(useBaritone::get)
        .build()
    );

    // ── Settings — Render ──────────────────────────────────────────
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Show remaining portal frame positions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the preview boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(80, 160, 255, 35))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(100, 180, 255, 255))
        .build()
    );

    // ── Settings — Glow ────────────────────────────────────────────
    private final Setting<RenderMode> renderMode = sgGlow.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode")
        .description("GLOW = layered bloom boxes. SPECTRAL = subtle fill. PULSE = fading in/out highlight.")
        .defaultValue(RenderMode.GLOW)
        .build()
    );

    private final Setting<Integer> glowLayers = sgGlow.add(new IntSetting.Builder()
        .name("glow-layers")
        .description("Number of bloom layers rendered around each preview block.")
        .defaultValue(4).min(1).sliderMax(8)
        .visible(() -> renderMode.get() == RenderMode.GLOW || renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Setting<Double> glowSpread = sgGlow.add(new DoubleSetting.Builder()
        .name("glow-spread")
        .description("How far each bloom layer expands outward (in blocks).")
        .defaultValue(0.05).min(0.01).sliderMax(0.2)
        .visible(() -> renderMode.get() == RenderMode.GLOW || renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Setting<Integer> glowBaseAlpha = sgGlow.add(new IntSetting.Builder()
        .name("glow-base-alpha")
        .description("Alpha of the innermost glow layer (0-255).")
        .defaultValue(60).min(4).sliderMax(150)
        .visible(() -> renderMode.get() == RenderMode.GLOW)
        .build()
    );

    private final Setting<Integer> spectralFillAlpha = sgGlow.add(new IntSetting.Builder()
        .name("spectral-fill-alpha")
        .description("Fill alpha for preview blocks in SPECTRAL mode.")
        .defaultValue(40).min(0).max(200).sliderMax(120)
        .visible(() -> renderMode.get() == RenderMode.SPECTRAL)
        .build()
    );

    private final Setting<Double> pulseSpeed = sgGlow.add(new DoubleSetting.Builder()
        .name("pulse-speed")
        .description("Pulse cycle speed. 1.0 = one full fade in/out per second.")
        .defaultValue(1.0).min(0.1).max(5.0).sliderMax(3.0)
        .visible(() -> renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Setting<Integer> pulseMinAlpha = sgGlow.add(new IntSetting.Builder()
        .name("pulse-min-alpha")
        .description("Lowest alpha reached during the pulse (0 = invisible).")
        .defaultValue(15).min(0).max(255).sliderMax(100)
        .visible(() -> renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Setting<Integer> pulseMaxAlpha = sgGlow.add(new IntSetting.Builder()
        .name("pulse-max-alpha")
        .description("Peak alpha reached during the pulse.")
        .defaultValue(220).min(15).max(255).sliderMax(255)
        .visible(() -> renderMode.get() == RenderMode.PULSE)
        .build()
    );

    // ── State ──────────────────────────────────────────────────────
    public final List<BlockPos> portalFramePositions = new ArrayList<>();
    private int     placementIndex   = 0;
    private int     tickTimer        = 0;
    private int     finishTimer      = 0;
    private boolean pearlThrown      = false;
    private String  lastDimension    = "";
    private String  builtDimension   = "";
    private boolean portalLitDetected = false;
    private int     dimensionChangeCooldown = 0;
    private RecycleState recycleState = RecycleState.IDLE;
    private Vec3   recycleTarget    = null;
    private Vec3   stepOutTarget    = null;
    private int     recycleWaitTimer = 0;
    private boolean wasRecyclePressed = false;
    private boolean hasWarnedThrowaway = false;

    private boolean originalEnterPortal = true;
    private boolean originalAllowPlace = true;
    private boolean originalAllowBreak = true;
    private boolean originalAllowParkour = true;
    private boolean originalAllowParkourPlace = true;

    public PortalMaker() {
        super(Tim.CATEGORY, "portal-maker", "Builds and lights a minimal Nether portal (10 obsidian).");
    }

    private Vec3 playerPos() {
        return new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private BlockState getSafeBlockState(BlockPos pos) {
        if (mc.level == null) return Blocks.AIR.defaultBlockState();
        try {
            if (!mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                return Blocks.AIR.defaultBlockState();
            }
            return mc.level.getBlockState(pos);
        } catch (Exception e) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    private boolean isChunkSafe(BlockPos pos) {
        if (mc.level == null || mc.player == null) return false;
        try {
            return mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onActivate() {
        portalFramePositions.clear();
        placementIndex   = 0;
        tickTimer        = 0;
        finishTimer      = 0;
        pearlThrown      = false;
        recycleState     = RecycleState.IDLE;
        lastDimension    = "";
        wasRecyclePressed = false;
        hasWarnedThrowaway = false;
        builtDimension   = "";
        portalLitDetected = false;
        dimensionChangeCooldown = 0;

        if (useBaritone.get()) {
            try {
                originalEnterPortal = BaritoneAPI.getSettings().enterPortal.value;
                BaritoneAPI.getSettings().enterPortal.value = true;
                
                originalAllowPlace = BaritoneAPI.getSettings().allowPlace.value;
                BaritoneAPI.getSettings().allowPlace.value = true;

                originalAllowBreak = BaritoneAPI.getSettings().allowBreak.value;
                BaritoneAPI.getSettings().allowBreak.value = true;

                originalAllowParkour = BaritoneAPI.getSettings().allowParkour.value;
                BaritoneAPI.getSettings().allowParkour.value = true;

                originalAllowParkourPlace = BaritoneAPI.getSettings().allowParkourPlace.value;
                BaritoneAPI.getSettings().allowParkourPlace.value = true;
            } catch (Exception ignored) {}
        }

        if (mc.player == null || mc.level == null) { toggle(); return; }

        if (!hasItemInHotbar(Items.OBSIDIAN)) {
            int total = countItem(Items.OBSIDIAN);
            if (total > 0) warning("Obsidian is in inventory but not hotbar!");
        }
        int obsidianCount = getObsidianCount();
        if (obsidianCount < 10) {
            error("Need at least 10 obsidian (found " + obsidianCount + ")");
            toggle();
            return;
        }

        if (!hasItem(Items.FLINT_AND_STEEL)) warning("No flint & steel found — light manually.");

        if (useBaritone.get() && !hasThrowawayBlocks() && !hasWarnedThrowaway) {
            warning("No throwaway blocks (dirt, cobblestone, etc.) found! Baritone may fail to bridge to the portal.");
            hasWarnedThrowaway = true;
        }

        Direction facing = mc.player.getDirection();
        Direction right  = facing.getClockWise();

        BlockPos feet     = mc.player.blockPosition();
        boolean  adjusted = false;

        if (!mc.level.getBlockState(feet.below()).isCollisionShapeFullBlock(mc.level, feet.below())) {
            feet     = feet.above();
            adjusted = true;
        }

        BlockPos origin = feet.relative(facing, 2).relative(right, -1);

        portalFramePositions.add(origin.relative(right, 1));
        portalFramePositions.add(origin.relative(right, 2));
        portalFramePositions.add(origin.above(1));
        portalFramePositions.add(origin.above(2));
        portalFramePositions.add(origin.above(3));
        portalFramePositions.add(origin.relative(right, 3).above(1));
        portalFramePositions.add(origin.relative(right, 3).above(2));
        portalFramePositions.add(origin.relative(right, 3).above(3));
        portalFramePositions.add(origin.relative(right, 1).above(4));
        portalFramePositions.add(origin.relative(right, 2).above(4));

        if (adjusted) {
            BlockPos stepPos = feet.relative(facing, 1);
            if (mc.level.getBlockState(stepPos).canBeReplaced()) portalFramePositions.add(stepPos);
        }

        boolean blocked = portalFramePositions.stream()
            .anyMatch(p -> !mc.level.getBlockState(p).canBeReplaced());
        if (blocked) { error("Portal area is obstructed. Move slightly and try again."); toggle(); return; }

        long existing = portalFramePositions.stream()
            .filter(p -> mc.level.getBlockState(p).getBlock() == Blocks.OBSIDIAN)
            .count();
        if (existing >= 9) {
            info("Portal frame looks complete → attempting to light it.");
            placementIndex = portalFramePositions.size();
        }

        lastDimension = mc.level.dimension().identifier().toString();
        builtDimension = lastDimension;

        selectHotbarItem(Items.OBSIDIAN);
        info("Building minimal Nether portal...");
    }

    @Override
    public void onDeactivate() {
        portalFramePositions.clear();
        placementIndex   = 0;
        tickTimer        = 0;
        stopMovement();
        
        if (useBaritone.get()) {
            try {
                BaritoneAPI.getSettings().enterPortal.value = originalEnterPortal;
                BaritoneAPI.getSettings().allowPlace.value = originalAllowPlace;
                BaritoneAPI.getSettings().allowBreak.value = originalAllowBreak;
                BaritoneAPI.getSettings().allowParkour.value = originalAllowParkour;
                BaritoneAPI.getSettings().allowParkourPlace.value = originalAllowParkourPlace;
            } catch (Exception ignored) {}
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        if (mc.player.isDeadOrDying() || !mc.player.isAlive()) {
            stopMovement();
            toggle();
            return;
        }

        if (useBaritone.get() && autoRecycle.get() && cancelOnMovement.get() && isMovingManually()) {
            if (recycleState != RecycleState.IDLE || dimensionChangeCooldown > 0) {
                info("Recycle cancelled by manual movement.");
            }
            toggle();
            return;
        }

        try { 
            if (mc.level.dimension() == null) return; 
        } catch (Exception ignored) { 
            return; 
        }
        
        String currentDim;
        try {
            currentDim = mc.level.dimension().identifier().toString();
        } catch (Exception e) { 
            return; 
        }

        if (builtDimension.isEmpty()) builtDimension = currentDim;

        if (!currentDim.equals(lastDimension)) {
            lastDimension = currentDim;
            portalFramePositions.clear();
            stopMovement();
            if (useBaritone.get() && autoRecycle.get()) {
                dimensionChangeCooldown = dimensionSwitchCooldownTicks.get();
            } else {
                info("PortalMaker finished.");
                toggle();
            }
            return;
        }

        if (dimensionChangeCooldown > 0) {
            dimensionChangeCooldown--;
            if (dimensionChangeCooldown == 0) {
                startRecycle();
            }
            return;
        }

        boolean recyclePressed = recycleKey.get().isPressed();
        if (useBaritone.get() && recyclePressed && !wasRecyclePressed) {
            if (recycleState == RecycleState.IDLE) {
                if (dimensionChangeCooldown > 0) info("Cannot recycle yet, waiting for dimension change cooldown.");
                else startRecycle();
            } else {
                recycleState = RecycleState.IDLE;
                stopMovement();
                info("Recycle cancelled.");
            }
        }
        wasRecyclePressed = recyclePressed;

        if (recycleState != RecycleState.IDLE) {
            handleRecycle();
            return;
        }

        if (isPlayerSolidlyInPortal(getPortalOpeningCenter())) {
            stopMovement();
            if (!(useBaritone.get() && autoRecycle.get())) {
                info("Entered portal -> disabling PortalMaker.");
                toggle();
            }
            return;
        }

        if (isPortalLit()) portalLitDetected = true;

        if (portalLitDetected || !currentDim.equals(builtDimension)) {
            if (currentDim.equals(builtDimension)) handlePhase2();
            return;
        }

        placementIndex = portalFramePositions.size();
        for (int i = 0; i < portalFramePositions.size(); i++) {
            BlockPos bp = portalFramePositions.get(i);
            if (!isChunkSafe(bp)) {
                placementIndex = portalFramePositions.size();
                break;
            }
            if (mc.level.getBlockState(bp).getBlock() != Blocks.OBSIDIAN) {
                placementIndex = i;
                break;
            }
        }

        if (placementIndex < portalFramePositions.size()) {
            if (mc.player.getInventory().isEmpty()) return;

            if (!mc.player.getMainHandItem().is(Items.OBSIDIAN)) {
                FindItemResult obsidian = InvUtils.find(Items.OBSIDIAN);
                if (!obsidian.found()) { error("No obsidian found -> disabled."); toggle(); return; }
                if (obsidian.isHotbar()) {
                    InvUtils.swap(obsidian.slot(), false);
                } else {
                    InvUtils.move().from(obsidian.slot()).toHotbar(0);
                    InvUtils.swap(0, false);
                }
            }

            tickTimer++;
            if (tickTimer < placeDelay.get()) return;
            tickTimer = 0;

            BlockPos target = portalFramePositions.get(placementIndex);
            if (!isChunkSafe(target)) return;
            
            if (mc.level.getBlockState(target).getBlock() == Blocks.OBSIDIAN) { placementIndex++; return; }

            if (!mc.level.getBlockState(target).canBeReplaced()) {
                mc.gameMode.startDestroyBlock(target, mc.player.getDirection().getOpposite());
                mc.player.swing(InteractionHand.MAIN_HAND);
                return;
            }

            BlockHitResult hit = null;
            BlockPos lookTarget = target;

            if (airPlace.get()) {
                hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
            } else {
                Direction placeDir = null;
                BlockPos neighborPos = null;
                
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = target.relative(dir);
                    BlockState neighborState = getSafeBlockState(neighbor);
                    if (!neighborState.canBeReplaced() && neighborState.isCollisionShapeFullBlock(mc.level, neighbor)) {
                        placeDir = dir.getOpposite();
                        neighborPos = neighbor;
                        break;
                    }
                }
                
                if (neighborPos != null) {
                    Vec3 hitVec = Vec3.atCenterOf(neighborPos).add(Vec3.atLowerCornerOf(placeDir.getUnitVec3i()).scale(0.5));
                    hit = new BlockHitResult(hitVec, placeDir, neighborPos, false);
                    lookTarget = neighborPos;
                } else {
                    error("Cannot place block without air-place (no solid neighbors found).");
                    toggle();
                    return;
                }
            }

            final BlockHitResult finalHit = hit;
            Rotations.rotate(Rotations.getYaw(lookTarget), Rotations.getPitch(lookTarget), () -> {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, finalHit);
                mc.player.swing(InteractionHand.MAIN_HAND);
            });
            placementIndex++;
            return;
        }

        handlePhase2();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null) return;
        
        if (render.get() && !portalFramePositions.isEmpty()) {
            for (int i = placementIndex; i < portalFramePositions.size(); i++) {
                BlockPos pos = portalFramePositions.get(i);
                if (!isChunkSafe(pos)) continue;
                if (!mc.level.getBlockState(pos).canBeReplaced()) continue;

                AABB box = new AABB(pos);
                
                if (renderMode.get() == RenderMode.PULSE) {
                    renderPulseBox(event, box, lineColor.get());
                } else if (renderMode.get() == RenderMode.SPECTRAL) {
                    event.renderer.box(box, withAlpha(lineColor.get(), spectralFillAlpha.get()), lineColor.get(), ShapeMode.Both, 0);
                } else {
                    renderGlowLayers(event, box, lineColor.get());
                    event.renderer.box(box, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                }
            }
        }
    }

    private void handlePhase2() {
        if (!portalLitDetected) {
            finishTimer = 0;
            if (tickTimer++ >= 10) { lightPortal(); tickTimer = 0; }
            return;
        }

        if (useBaritone.get() && entryMode.get() != EntryMode.None) {
            moveToPortal();
        } else {
            if (finishTimer++ >= finishDelay.get()) {
                info("PortalMaker finished.");
                toggle();
            }
        }
    }

    private void handleRecycle() {
        if (!useBaritone.get()) {
            recycleState = RecycleState.IDLE;
            return;
        }

        if (mc.player == null || mc.level == null) {
            recycleState = RecycleState.IDLE;
            stopMovement();
            return;
        }

        if (mc.player.isDeadOrDying() || !mc.player.isAlive()) {
            recycleState = RecycleState.IDLE;
            stopMovement();
            toggle();
            return;
        }

        switch (recycleState) {
            case STEPPING_OUT -> {
                if (stepOutTarget == null) { 
                    recycleState = RecycleState.WAITING; 
                    return; 
                }

                if (isBaritoneIdle()) {
                    if (!isPlayerInPortal()) {
                        stopMovement();
                        recycleState = RecycleState.WAITING;
                        return;
                    } else {
                        moveTo(stepOutTarget);
                    }
                }
            }
            case WAITING -> {
                stopMovement(); 
                if (recycleWaitTimer-- <= 0) {
                    recycleState = RecycleState.RE_ENTERING;
                    info("Wait complete. Re-entering portal...");
                }
            }
            case RE_ENTERING -> {
                if (isPlayerSolidlyInPortal(recycleTarget)) {
                    stopMovement();
                    toggle();
                    return;
                }
                
                if (isBaritoneIdle()) {
                    moveTo(recycleTarget);
                }
            }
            default -> {}
        }
    }

    private void startRecycle() {
        if (!useBaritone.get()) return;
        
        if (mc.player == null || mc.level == null) {
            recycleState = RecycleState.IDLE;
            return;
        }
        
        BlockPos playerPos = mc.player.blockPosition();
        if (!isChunkSafe(playerPos)) {
            dimensionChangeCooldown = 10; 
            return;
        }
        
        setupRecycleTarget();
        recycleState = RecycleState.STEPPING_OUT;
        recycleWaitTimer = recycleDelaySeconds.get() * 20;
        info("Initiating portal recycle...");
    }

    private void setupRecycleTarget() {
        if (mc.player == null || mc.level == null) {
            recycleTarget = null;
            stepOutTarget = null;
            return;
        }

        BlockPos pos = mc.player.blockPosition();
        
        if (!getSafeBlockState(pos).is(Blocks.NETHER_PORTAL)) {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-5, -5, -5), pos.offset(5, 5, 5))) {
                if (!isChunkSafe(p)) continue; 
                if (getSafeBlockState(p).is(Blocks.NETHER_PORTAL)) {
                    pos = p;
                    break;
                }
            }
        }

        if (getSafeBlockState(pos).is(Blocks.NETHER_PORTAL)) {
            BlockState state = getSafeBlockState(pos);
            Direction.Axis axis = state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) 
                ? state.getValue(BlockStateProperties.HORIZONTAL_AXIS) 
                : Direction.Axis.X;

            int minC = axis == Direction.Axis.X ? pos.getX() : pos.getZ();
            int maxC = minC;

            int maxIterations = 20;
            while (maxIterations-- > 0) {
                BlockPos checkPos = axis == Direction.Axis.X 
                    ? new BlockPos(minC - 1, pos.getY(), pos.getZ()) 
                    : new BlockPos(pos.getX(), pos.getY(), minC - 1);
                if (!isChunkSafe(checkPos)) break;
                if (!getSafeBlockState(checkPos).is(Blocks.NETHER_PORTAL)) break;
                minC--;
            }

            maxIterations = 20;
            while (maxIterations-- > 0) {
                BlockPos checkPos = axis == Direction.Axis.X 
                    ? new BlockPos(maxC + 1, pos.getY(), pos.getZ()) 
                    : new BlockPos(pos.getX(), pos.getY(), maxC + 1);
                if (!isChunkSafe(checkPos)) break;
                if (!getSafeBlockState(checkPos).is(Blocks.NETHER_PORTAL)) break;
                maxC++;
            }

            double mid = (minC + maxC + 1) / 2.0;
            if (axis == Direction.Axis.X) {
                recycleTarget = new Vec3(mid, pos.getY(), pos.getZ() + 0.5);
                Vec3 o1 = recycleTarget.add(0, 0, 2.0);
                Vec3 o2 = recycleTarget.add(0, 0, -2.0);
                if (isAreaClear(o1)) stepOutTarget = o1;
                else if (isAreaClear(o2)) stepOutTarget = o2;
                else stepOutTarget = o1;
            } else {
                recycleTarget = new Vec3(pos.getX() + 0.5, pos.getY(), mid);
                Vec3 o1 = recycleTarget.add(2.0, 0, 0);
                Vec3 o2 = recycleTarget.add(-2.0, 0, 0);
                if (isAreaClear(o1)) stepOutTarget = o1;
                else if (isAreaClear(o2)) stepOutTarget = o2;
                else stepOutTarget = o1;
            }
        } else {
            recycleTarget = playerPos();
            stepOutTarget = playerPos().add(mc.player.getLookAngle().scale(-2.0));
        }
    }

    private boolean isAreaClear(Vec3 pos) {
        if (mc.level == null) return false;
        BlockPos bp = BlockPos.containing(pos);
        if (!isChunkSafe(bp)) return false;
        return getSafeBlockState(bp).canBeReplaced() && getSafeBlockState(bp.above()).canBeReplaced();
    }

    private boolean isPlayerInPortal() {
        if (mc.player == null || mc.level == null) return false;

        AABB playerBox = mc.player.getBoundingBox().deflate(0.1, 0.0, 0.1);
        int minX = Mth.floor(playerBox.minX);
        int minY = Mth.floor(playerBox.minY);
        int minZ = Mth.floor(playerBox.minZ);
        int maxX = Mth.floor(playerBox.maxX);
        int maxY = Mth.floor(playerBox.maxY);
        int maxZ = Mth.floor(playerBox.maxZ);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    if (!isChunkSafe(pos)) continue;

                    BlockState state = getSafeBlockState(pos);
                    if (state.is(Blocks.NETHER_PORTAL)) {
                        VoxelShape shape = state.getShape(mc.level, pos);
                        if (!shape.isEmpty()) {
                            AABB portalShapeBox = shape.bounds().move(pos);
                            if (playerBox.intersects(portalShapeBox)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isPlayerSolidlyInPortal(Vec3 targetCenter) {
        if (!isPlayerInPortal()) return false;
        if (mc.player == null || targetCenter == null) return true;

        double horizontalDistSq = Mth.square(mc.player.getX() - targetCenter.x)
                                + Mth.square(mc.player.getZ() - targetCenter.z);

        return horizontalDistSq <= 0.0625 || isBaritoneIdle();
    }

    private void lightPortal() {
        if (portalFramePositions.isEmpty()) return;
        if (!selectHotbarItem(Items.FLINT_AND_STEEL)) { warning("Cannot find flint & steel in hotbar."); return; }

        BlockPos bottom1 = portalFramePositions.get(0);
        BlockPos bottom2 = portalFramePositions.get(1);

        for (BlockPos pos : new BlockPos[]{bottom1, bottom2}) {
            if (!isChunkSafe(pos)) continue;
            if (getSafeBlockState(pos.above()).isAir()) {
                Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> {
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                });
                break;
            }
        }
    }

    private boolean isPortalLit() {
        if (portalFramePositions.size() < 2) return false;
        BlockPos p1 = portalFramePositions.get(0).above();
        BlockPos p2 = portalFramePositions.get(1).above();

        if (!isChunkSafe(p1) || !isChunkSafe(p2)) {
            return portalLitDetected;
        }

        return mc.level.getBlockState(p1).getBlock() == Blocks.NETHER_PORTAL ||
               mc.level.getBlockState(p2).getBlock() == Blocks.NETHER_PORTAL;
    }

    private void moveToPortal() {
        if (portalFramePositions.size() < 2) return;
        moveTo(getPortalOpeningCenter());
    }

    private void moveTo(Vec3 target) {
        if (mc.player == null || mc.level == null || target == null) return;
        if (mc.player.isDeadOrDying() || !mc.player.isAlive()) {
            stopMovement();
            toggle();
            return;
        }

        BlockPos targetPos = BlockPos.containing(target.x, target.y, target.z);
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        
        if (isBaritoneIdle()) {
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(targetPos));
        }
    }

    private Vec3 getPortalOpeningCenter() {
        if (portalFramePositions.size() < 2) return mc.player != null ? playerPos() : Vec3.ZERO;
        BlockPos p1 = portalFramePositions.get(0).above();
        BlockPos p2 = portalFramePositions.get(1).above();
        Vec3 c1 = Vec3.atBottomCenterOf(p1);
        Vec3 c2 = Vec3.atBottomCenterOf(p2);
        if (mc.player == null) return c1;
        Vec3 pPos = playerPos();
        return pPos.distanceToSqr(c1) <= pPos.distanceToSqr(c2) ? c1 : c2;
    }

    private void stopMovement() {
        if (mc.options == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyShift.setDown(false);
        
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            IPathingBehavior pathing = baritone.getPathingBehavior();
            if (pathing != null && (pathing.isPathing() || pathing.hasPath() || pathing.getInProgress().isPresent())) {
                pathing.cancelEverything();
            }
        } catch (Exception ignored) {}
    }

    private void renderGlowLayers(Render3DEvent event, AABB box, SettingColor color) {
        int    layers    = glowLayers.get();
        double spread    = glowSpread.get();
        int    baseAlpha = glowBaseAlpha.get();

        for (int i = layers; i >= 1; i--) {
            double expansion  = spread * i;
            int    layerAlpha = Math.max(4, (int) (baseAlpha * (1.0 - (double) (i - 1) / layers)));
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

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }

    private boolean isBaritoneIdle() {
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            IPathingBehavior pathing = baritone.getPathingBehavior();
            if (pathing == null) return true;

            boolean activeProcess = baritone.getPathingControlManager()
                    .mostRecentInControl()
                    .map(IBaritoneProcess::isActive)
                    .orElse(false);

            return !activeProcess && !pathing.isPathing() && !pathing.hasPath() && pathing.getInProgress().isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    private boolean selectHotbarItem(Item targetItem) {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == targetItem) {
                InvUtils.swap(i, false);
                return true;
            }
        }
        return false;
    }

    private boolean hasItemInHotbar(Item targetItem) {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == targetItem) return true;
        }
        return false;
    }

    public int getObsidianCount() {
        return countItem(Items.OBSIDIAN);
    }

    private int countItem(Item targetItem) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(targetItem)) count += stack.getCount();
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.is(targetItem)) count += offhand.getCount();
        return count;
    }

    private boolean hasItem(Item targetItem) {
        return countItem(targetItem) > 0;
    }

    private boolean hasThrowawayBlocks() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            Item item = stack.getItem();
            if (item == Items.DIRT || item == Items.COBBLESTONE || item == Items.NETHERRACK || 
                item == Items.STONE || item == Items.GRASS_BLOCK || item == Items.DEEPSLATE ||
                item == Items.COBBLED_DEEPSLATE || item == Items.SAND || item == Items.GRAVEL ||
                item == Items.GLASS || item == Items.OAK_PLANKS || item == Items.SPRUCE_PLANKS) {
                return true;
            }
        }
        return false;
    }

    private boolean isMovingManually() {
        if (mc.screen != null) return false;
        return Input.isKeyPressed(GLFW.GLFW_KEY_W) || 
               Input.isKeyPressed(GLFW.GLFW_KEY_A) ||
               Input.isKeyPressed(GLFW.GLFW_KEY_S) || 
               Input.isKeyPressed(GLFW.GLFW_KEY_D) ||
               Input.isKeyPressed(GLFW.GLFW_KEY_SPACE) || 
               Input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT) ||
               Input.isKeyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}