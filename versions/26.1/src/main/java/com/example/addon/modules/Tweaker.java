package com.example.addon.modules;

import com.example.addon.Tim;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import com.example.addon.mixin.RecipeBookWidgetAccessor;
import com.example.addon.mixin.SettingTitleAccessor;
import com.example.addon.utils.AttackGuardPolicy;
import com.example.addon.utils.ElytraTakeoffSequence;
import com.example.addon.utils.MeteorCompat;
import com.example.addon.utils.EnderChestWaterRecovery;
import com.example.addon.utils.OceanBreachAscent;
import com.example.addon.utils.OceanBreachSequence;
import com.example.addon.utils.PathSettingsMigration;
import com.example.addon.utils.SafeWalkSupport;
import com.example.addon.utils.SurfaceDestinationSearch;
import com.example.addon.utils.SurfacePathProcess;
import com.example.addon.utils.SurfacePathSettings;
import com.example.addon.utils.TakeoffMode;
import com.example.addon.utils.WaterEscapeNavigator;
import com.mojang.blaze3d.platform.InputConstants;
import com.example.addon.utils.SettingNameCompat;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerTickMovementEvent;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.lwjgl.glfw.GLFW;

import java.util.Set;
import java.util.List;
import java.util.Random;

public class Tweaker extends MouseAwareModule {
    public enum AttackGuardMode {
        SelectedTypes,
        AllMobs
    }

    public enum TakeoffTrigger {
        Normal,
        Sneak
    }

    private final SettingGroup sgSafeWalk = settings.createGroup("Safe Walk");
    private final SettingGroup sgAttackGuard = settings.createGroup("Attack Guard");
    private final SettingGroup sgTakeoff = settings.createGroup("Elytra Takeoff");
    private final SettingGroup sgPathfinding = settings.createGroup("Pathfinding");

    private final Setting<Boolean> inventoryMove = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("inventory-move")
        .description("Allows movement in inventory and container screens, except while typing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> portalGui = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("portal-gui")
        .description("Keeps inventory and container screens open while standing in a portal.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ghostHands = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("ghost-hands")
        .description("Attempt to open containers through blocks or entities within normal reach. Sneak to interact normally.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> airPlace = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("air-place")
        .description("Place one held block in mid-air per use-key press.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> safeWalk = sgSafeWalk.add(displayName(new BoolSetting.Builder()
        .name("enabled")
        .description("Automatically sneak near edges while Tweaker is active.")
        .defaultValue(false)
        .build(), "Safe Walker")
    );

    private final Setting<Keybind> safeWalkToggle = sgSafeWalk.add(new KeybindSetting.Builder()
        .name("safe-walk-toggle-key")
        .description("Toggle Safe Walk without disabling Tweaker.")
        .defaultValue(Keybind.none())
        .action(() -> {
            if (mc.screen != null || !mc.isWindowActive()) return;
            boolean enabled = !safeWalk.get();
            safeWalk.set(enabled);
            info("Safe Walk " + (enabled ? "enabled" : "disabled") + ".");
        })
        .build()
    );

    private final Setting<Double> safeWalkEdgeDistance = sgSafeWalk.add(new DoubleSetting.Builder()
        .name("sneak-edge-distance")
        .description("How far the player's support can recede before auto-sneaking.")
        .defaultValue(0.25)
        .range(0.0, 0.29)
        .sliderRange(0.0, 0.29)
        .build()
    );

    private final Setting<Boolean> attackGuard = sgAttackGuard.add(displayName(new BoolSetting.Builder()
        .name("enabled")
        .description("Block direct attacks against protected entities while Tweaker is active.")
        .defaultValue(false)
        .build(), "Attack Guard")
    );

    private final Setting<Keybind> attackGuardToggle = sgAttackGuard.add(new KeybindSetting.Builder()
        .name("attack-guard-toggle-key")
        .description("Toggle Attack Guard without disabling Tweaker.")
        .defaultValue(Keybind.none())
        .action(() -> {
            if (mc.screen != null || !mc.isWindowActive()) return;
            boolean enabled = !attackGuard.get();
            attackGuard.set(enabled);
            info("Attack Guard " + (enabled ? "enabled" : "disabled") + ".");
        })
        .build()
    );

    private final Setting<AttackGuardMode> attackGuardMode = sgAttackGuard.add(new EnumSetting.Builder<AttackGuardMode>()
        .name("mode")
        .description("Protect selected mob types or all mobs.")
        .defaultValue(AttackGuardMode.SelectedTypes)
        .build()
    );

    private final Setting<Set<EntityType<?>>> attackGuardEntities = sgAttackGuard.add(new EntityTypeListSetting.Builder()
        .name("protected-mob-types")
        .description("Mob types protected in Selected Types mode. The list starts empty.")
        .visible(() -> attackGuardMode.get() == AttackGuardMode.SelectedTypes)
        .build()
    );

    private final Setting<Boolean> protectNamedEntities = sgAttackGuard.add(new BoolSetting.Builder()
        .name("protect-named-entities")
        .description("Also protect entities with custom names, excluding players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> protectTamedPets = sgAttackGuard.add(new BoolSetting.Builder()
        .name("protect-tamed-pets")
        .description("Also protect tamed animals, including horses and other players' pets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> attackGuardFeedback = sgAttackGuard.add(new BoolSetting.Builder()
        .name("blocked-attack-message")
        .description("Show a rate-limited message when an attack is blocked.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> takeoffEnabled = sgTakeoff.add(displayName(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable the one-shot elytra takeoff assistant.")
        .defaultValue(false)
        .build(), "Elytra Takeoff")
    );

    private final Setting<TakeoffTrigger> takeoffTrigger = sgTakeoff.add(new EnumSetting.Builder<TakeoffTrigger>()
        .name("trigger")
        .description("Normal starts with the takeoff key; Sneak starts with a fresh sneak-key press.")
        .defaultValue(TakeoffTrigger.Normal)
        .build()
    );

    private final Setting<Keybind> takeoffKey = sgTakeoff.add(new KeybindSetting.Builder()
        .name("takeoff-start-cancel-key")
        .description("Bind this key to start takeoff in Normal mode or cancel takeoff in either mode.")
        .defaultValue(Keybind.none())
        .action(() -> {
            if (mc.screen != null || !mc.isWindowActive()) return;
            if (this.takeoffSequence != null) cancelTakeoff("Cancelled by user.");
            else if (takeoffEnabled.get() && takeoffTrigger.get() == TakeoffTrigger.Normal) startTakeoff();
        })
        .build()
    );

    private final Setting<TakeoffMode> takeoffMode = sgTakeoff.add(new EnumSetting.Builder<TakeoffMode>()
        .name("mode")
        .description("Climb to a target Y, or jump, glide, and use one rocket without changing pitch.")
        .defaultValue(TakeoffMode.TargetY)
        .build()
    );

    private final Setting<Integer> takeoffTargetY = sgTakeoff.add(new IntSetting.Builder()
        .name("target-y")
        .description("Absolute Y coordinate at which Tweaker stops controlling the climb.")
        .defaultValue(200)
        .range(-2048, 4096)
        .sliderRange(-64, 512)
        .visible(() -> takeoffMode.get() == TakeoffMode.TargetY)
        .build()
    );

    private final Setting<Double> takeoffPitch = sgTakeoff.add(new DoubleSetting.Builder()
        .name("climb-pitch")
        .description("Upward pitch during takeoff and climb; negative values look upwards.")
        .defaultValue(-35.0)
        .range(-80.0, -5.0)
        .sliderRange(-80.0, -5.0)
        .visible(() -> takeoffMode.get() == TakeoffMode.TargetY)
        .build()
    );

    private final Setting<Integer> takeoffRocketDelay = sgTakeoff.add(new IntSetting.Builder()
        .name("rocket-interval-ticks")
        .description("Minimum ticks between rocket use attempts while climbing.")
        .defaultValue(40)
        .range(20, 200)
        .sliderRange(20, 100)
        .visible(() -> takeoffMode.get() == TakeoffMode.TargetY)
        .build()
    );

    private final Setting<Integer> takeoffTimeout = sgTakeoff.add(new IntSetting.Builder()
        .name("timeout-seconds")
        .description("Maximum duration of a takeoff and climb before returning control.")
        .defaultValue(60)
        .range(5, 300)
        .sliderRange(5, 120)
        .build()
    );

    private final Setting<Keybind> surfacePathKey = sgPathfinding.add(displayName(new KeybindSetting.Builder()
        .name("surface-path-start-cancel-key")
        .description("Find and path to an Overworld surface, or stop Tweaker's current surface path.")
        .defaultValue(Keybind.none())
        .action(() -> {
            if (mc.screen != null || !mc.isWindowActive() || mc.level == null
                || !mc.level.dimension().equals(Level.OVERWORLD)) return;
            toggleSurfacePath();
        })
        .build(), "Surface (START/STOP)")
    );

    private final Setting<Integer> surfacePathRadius = sgPathfinding.add(displayName(new IntSetting.Builder()
        .name("surface-path-search-radius")
        .description("Horizontal search radius for loaded surface destinations.")
        .defaultValue(32)
        .range(4, 64)
        .sliderRange(4, 64)
        .build(), "Surface Search Radius")
    );

    private final Setting<Keybind> enderChestPathKey = sgPathfinding.add(displayName(new KeybindSetting.Builder()
        .name("ender-chest-path-start-cancel-key")
        .description("Walk to the nearest loaded ender chest, or cancel the current ender chest path.")
        .defaultValue(Keybind.none())
        .action(() -> {
            if (mc.screen == null && mc.isWindowActive()) toggleEnderChestPath();
        })
        .build(), "Ender Chest (START/STOP)")
    );

    private final Setting<Integer> enderChestPathRadius = sgPathfinding.add(displayName(new IntSetting.Builder()
        .name("ender-chest-path-search-radius")
        .description("Maximum distance in blocks to search loaded chunks for an ender chest.")
        .defaultValue(64)
        .range(4, 256)
        .sliderRange(4, 128)
        .build(), "E-Chest Search Radius")
    );

    private final Setting<Boolean> pathTerrainChanges = sgPathfinding.add(displayName(new BoolSetting.Builder()
        .name("path-allow-breaking-and-placement")
        .description("Allow both Surface Path and Ender Chest Path to break and place blocks. Applies when a route starts.")
        .defaultValue(false)
        .build(), "Allow Break/Placement")
    );

    private boolean controllingKeys;
    private boolean airPlaceConsumed;
    private boolean surfacePathWater;
    private boolean surfaceBreachRouting;
    private int surfaceBreachWait;
    private boolean surfaceFallbackPending;
    private boolean surfaceNormalFallbackPending;
    private OceanBreachSequence oceanBreach;
    private OceanBreachAscent oceanBreachAscent;
    private Vec2 oceanBreachSwivel = Vec2.ZERO;
    private double oceanBreachAnchorX;
    private double oceanBreachAnchorZ;
    private boolean oceanBreachSlowWarned;
    private boolean oceanBreachAirWarned;
    private BlockPos oceanBreachBlock;
    private BlockState oceanBreachOriginal;
    private boolean sneakDown;
    private boolean sprintDown;
    private long lastBlockedAttackMessage;
    private ElytraTakeoffSequence takeoffSequence;
    private TakeoffMode activeTakeoffMode;
    private boolean takeoffSneakDown;
    private int takeoffTicks;
    private int takeoffLastRocketTick;
    private int activeTakeoffTargetY;
    private boolean takeoffSneakReleasePending;
    private boolean takeoffManualJump;
    private net.minecraft.client.player.LocalPlayer takeoffPlayer;
    private net.minecraft.client.multiplayer.ClientLevel takeoffWorld;
    private net.minecraft.client.player.LocalPlayer takeoffSneakReleasePlayer;
    private net.minecraft.client.multiplayer.ClientLevel takeoffSneakReleaseWorld;
    private SurfacePathProcess surfacePath;
    private net.minecraft.client.multiplayer.ClientLevel surfacePathWorld;
    private net.minecraft.client.player.LocalPlayer surfacePathPlayer;
    private SurfacePathProcess enderChestPath;
    private EnderChestWaterRecovery enderChestRecovery;
    private BlockPos enderChestTarget;
    private boolean enderChestTerrainChanges;
    private boolean enderChestAirWarned;
    private net.minecraft.client.multiplayer.ClientLevel enderChestPathWorld;
    private net.minecraft.client.player.LocalPlayer enderChestPathPlayer;
    private List<BlockPos> underwaterEscapeBlocks = List.of();
    private List<BlockState> underwaterEscapeOriginals = List.of();
    private boolean underwaterEscapeChest;
    private boolean surfaceEscapeAttempted;
    private boolean chestEscapeAttempted;
    private boolean surfaceWaterFallbackPending;
    private boolean surfaceWaterSearchAttempted;
    private boolean surfaceWaterHandoff;
    private boolean surfaceHandoffTerrainChanges;
    private int surfaceHandoffTicks;
    private boolean chestWaterSearchAttempted;
    private boolean waterSearchChest;
    private boolean waterSearchTerrainChanges;
    private WaterEscapeNavigator waterNavigator;
    private OceanBreachAscent waterAscent;
    private Vec2 waterAscentDirection = Vec2.ZERO;
    private double waterAscentAnchorX;
    private double waterAscentAnchorZ;
    private BlockPos waterAscentOpening;
    private int waterAscentTicks;
    private int waterAscentReroutes;
    private boolean waterSearchAirWarned;
    private int underwaterEscapeIndex;
    private int underwaterEscapeTicks;
    private int underwaterEscapeBlockTicks;
    private int underwaterEscapeTop;
    private boolean underwaterEscapeAirWarned;

    public Tweaker() {
        super(Tim.CATEGORY, "tweaker", "Small gameplay and quality-of-life tweaks.");
    }

    private static <T> Setting<T> displayName(Setting<T> setting, String title) {
        ((SettingTitleAccessor) setting).tim$setTitle(title);
        return setting;
    }

    @Override
    public Module fromTag(CompoundTag tag) {
        SettingNameCompat.migrate(tag, "Elytra Takeoff", "takeoff-cancel-key", "takeoff-start-cancel-key");
        SettingNameCompat.migrate(tag, "Safe Walk", "toggle-key", "safe-walk-toggle-key");
        SettingNameCompat.migrate(tag, "Attack Guard", "toggle-key", "attack-guard-toggle-key");
        SettingNameCompat.migrate(tag, "Surface Path", "start-cancel-key", "surface-path-start-cancel-key");
        SettingNameCompat.migrate(tag, "Ender Chest Path", "start-cancel-key", "ender-chest-path-start-cancel-key");
        PathSettingsMigration.migrate(tag);
        return super.fromTag(tag);
    }

    @Override
    public void onActivate() {
        takeoffSneakDown = mc.player != null && mc.isWindowActive() && mc.screen == null
            && isPhysicallyPressed(mc.options.keyShift);
    }

    @EventHandler
    private void onMovement(PlayerTickMovementEvent event) {
        if (!shouldControlKeys()) return;

        if (!controllingKeys) {
            sneakDown = false;
            sprintDown = false;
            controllingKeys = true;
        }
        updateMovementKeys(true);
        sneakDown = updateToggleKey(mc.options.keyShift, mc.options.toggleCrouch().get(), sneakDown);
        sprintDown = updateToggleKey(mc.options.keySprint, mc.options.toggleSprint().get(), sprintDown);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (controllingKeys && !shouldControlKeys()) releaseKeys();
        if (!mc.options.keyUse.isDown()) airPlaceConsumed = false;
        tickTakeoff();
        if (waterNavigator != null) {
            tickConnectedWaterTravel();
            return;
        }
        if (waterAscent != null) {
            tickWaterAscent();
            return;
        }
        if (!underwaterEscapeBlocks.isEmpty()) {
            tickUnderwaterEscape();
            return;
        }
        tickSurfacePath();
        tickEnderChestPath();
    }

    public boolean handleAirPlace() {
        if (!isActive() || !airPlace.get() || mc.level == null || mc.player == null || mc.gameMode == null
            || mc.screen != null || !mc.isWindowActive() || mc.getCameraEntity() != mc.player) return false;
        if (airPlaceConsumed) return true;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.MISS
            || containerTarget(mc.hitResult).getType() != HitResult.Type.MISS) return false;
        InteractionHand hand;
        if (mc.player.getMainHandItem().getItem() instanceof BlockItem) hand = InteractionHand.MAIN_HAND;
        else if (mc.player.getMainHandItem().isEmpty() && mc.player.getOffhandItem().getItem() instanceof BlockItem)
            hand = InteractionHand.OFF_HAND;
        else return false;

        HitResult target = mc.player.pick(mc.player.blockInteractionRange(), 1.0f, false);
        if (!(target instanceof BlockHitResult hit) || target.getType() != HitResult.Type.MISS) return false;
        BlockPos pos = hit.getBlockPos();
        if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || !mc.level.getBlockState(pos).canBeReplaced()) return false;

        airPlaceConsumed = true;
        BlockHitResult placement = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        if (mc.gameMode.useItemOn(mc.player, hand, placement).consumesAction()) mc.player.swing(hand);
        return true;
    }

    @EventHandler
    private void onAirPlaceKeyRelease(KeyInputEvent event) {
        InputConstants.Key key = MeteorCompat.boundKey(mc.options.keyUse);
        if (event.action == KeyAction.Release && key.getType() == InputConstants.Type.KEYSYM && key.getValue() == MeteorCompat.key(event))
            airPlaceConsumed = false;
    }

    @Override
    protected boolean onMouseButton(int button, KeyAction action) {
        InputConstants.Key key = MeteorCompat.boundKey(mc.options.keyUse);
        if (action == KeyAction.Release && key.getType() == InputConstants.Type.MOUSE && key.getValue() == button)
            airPlaceConsumed = false;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onAttackEntity(AttackEntityEvent event) {
        if (event.isCancelled() || !attackGuard.get() || mc.player == null || mc.level == null) return;

        Entity entity = event.entity;
        boolean tamed = (entity instanceof TamableAnimal pet && pet.isTame())
            || (entity instanceof AbstractHorse horse && horse.isTamed());
        if (!AttackGuardPolicy.protects(entity instanceof Mob, entity instanceof Player,
            attackGuardEntities.get().contains(entity.getType()), attackGuardMode.get() == AttackGuardMode.AllMobs,
            entity.hasCustomName(), protectNamedEntities.get(), tamed, protectTamedPets.get())) return;

        event.cancel();
        long now = System.nanoTime();
        if (attackGuardFeedback.get() && (lastBlockedAttackMessage == 0 || now - lastBlockedAttackMessage >= 2_000_000_000L)) {
            info("Attack Guard blocked an attack on a protected entity.");
            lastBlockedAttackMessage = now;
        }
    }

    @Override
    public void onDeactivate() {
        if (controllingKeys) releaseKeys();
        airPlaceConsumed = false;
        lastBlockedAttackMessage = 0;
        resetTakeoff();
        takeoffSneakDown = false;
        clearTakeoffSneakRelease();
        stopOceanBreach();
        stopUnderwaterEscape();
        stopConnectedWaterTravel();
        surfaceFallbackPending = false;
        surfaceNormalFallbackPending = false;
        surfaceBreachRouting = false;
        surfaceWaterFallbackPending = false;
        surfaceWaterHandoff = false;
        if (surfacePath != null) surfacePath.stop("Surface Path stopped: Tweaker disabled.");
        if (enderChestPath != null) enderChestPath.stop("Ender Chest Path stopped: Tweaker disabled.");
        enderChestRecovery = null;
        enderChestTarget = null;
    }

    private void toggleEnderChestPath() {
        if (enderChestRecovery != null) {
            stopConnectedWaterTravel();
            stopUnderwaterEscape();
            stopEnderChestRecovery("Ender Chest Path cancelled.");
            return;
        }
        if (enderChestPath != null && enderChestPath.isActive()) {
            enderChestPath.stop("Ender Chest Path cancelled.");
            return;
        }
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) {
            warning("Ender Chest Path requires a living player in a world.");
            return;
        }
        if (!isEnderChestDimension()) {
            warning("Ender Chest Path is available only in the Overworld, Nether, and End.");
            return;
        }
        if (waterNavigator != null || waterAscent != null || !underwaterEscapeBlocks.isEmpty() || oceanBreach != null || surfaceWaterHandoff || surfaceBreachRouting || surfaceFallbackPending || takeoffSequence != null || mc.player.isFallFlying() || mc.player.getAbilities().flying || mc.player.isPassenger()) {
            warning("Ender Chest Path requires the player to be on foot, outside elytra takeoff.");
            return;
        }
        if (!BaritoneUtils.IS_AVAILABLE) {
            warning("Ender Chest Path requires Baritone to be installed and available.");
            return;
        }
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (enderChestPath == null) enderChestPath = new SurfacePathProcess(baritone,
            new SurfacePathSettings(BaritoneAPI.getSettings(), true), this::isEnderChestDestination,
            this::reportSurfacePath, "Ender Chest Path", SurfacePathProcess::adjacentChestGoal);
        if (!enderChestPath.isBaritoneIdle() || (surfacePath != null && surfacePath.isActive())) {
            warning("Ender Chest Path cannot start while Baritone is busy.");
            return;
        }
        BlockPos chest = SurfaceDestinationSearch.nearestEnderChest(mc.level, mc.player.blockPosition(),
            enderChestPathRadius.get());
        if (chest == null) {
            warning("Ender Chest Path found no ender chest in loaded chunks within the search radius.");
            return;
        }
        enderChestPathWorld = mc.level;
        enderChestPathPlayer = mc.player;
        boolean allowTerrainChanges = pathTerrainChanges.get();
        enderChestTarget = chest;
        enderChestTerrainChanges = allowTerrainChanges;
        enderChestAirWarned = false;
        chestEscapeAttempted = false;
        chestWaterSearchAttempted = false;
        waterAscentReroutes = 0;
        enderChestRecovery = new EnderChestWaterRecovery();
        enderChestPath.start(List.of(chest), allowTerrainChanges, allowTerrainChanges);
        if (safeWalk.get()) warning("Safe Walk is paused while Ender Chest Path is active.");
    }

    private boolean isEnderChestDestination(BlockPos pos) {
        return mc.level == enderChestPathWorld && mc.player == enderChestPathPlayer && isEnderChestDimension()
            && mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && mc.level.getBlockState(pos).is(Blocks.ENDER_CHEST);
    }

    private boolean isEnderChestDimension() {
        return mc.level != null && (mc.level.dimension().equals(Level.OVERWORLD)
            || mc.level.dimension().equals(Level.NETHER) || mc.level.dimension().equals(Level.END));
    }

    private void tickEnderChestPath() {
        if (enderChestRecovery == null) return;
        if (mc.level != enderChestPathWorld || mc.player != enderChestPathPlayer || mc.level == null || mc.player == null
            || !isEnderChestDimension() || !mc.player.isAlive() || mc.player.isFallFlying() || mc.player.getAbilities().flying || mc.player.isPassenger()
            || takeoffSequence != null) {
            stopEnderChestRecovery("Ender Chest Path stopped: player or world state changed.");
            return;
        }
        EnderChestWaterRecovery.Phase phase = enderChestRecovery.phase();
        if (phase == EnderChestWaterRecovery.Phase.Routing) {
            if (!enderChestPath.isActive()) {
                enderChestRecovery = null;
                enderChestTarget = null;
                return;
            }
            if (!mc.player.isUnderWater()) return;
            if (!mc.level.dimension().equals(Level.OVERWORLD)) {
                stopEnderChestRecovery("Ender Chest Path stopped: water recovery is only available in the Overworld.");
            } else if (!enderChestRecovery.interrupt()) {
                stopEnderChestRecovery("Ender Chest Path stopped: repeated underwater routes could not be resolved.");
            } else {
                chestEscapeAttempted = false;
                chestWaterSearchAttempted = false;
                waterAscentReroutes = 0;
                enderChestPath.interrupt("Ender Chest Path paused for water recovery.");
            }
            return;
        }
        if (!isEnderChestDestination(enderChestTarget)) {
            stopEnderChestRecovery("Ender Chest Path stopped: the target chest is missing or unloaded.");
            return;
        }
        if (pathTerrainChanges.get() != enderChestTerrainChanges) {
            stopEnderChestRecovery("Ender Chest Path stopped: terrain permissions changed during water recovery.");
            return;
        }
        if (mc.player.getAirSupply() <= 80 && !enderChestAirWarned) {
            enderChestAirWarned = true;
            warning("Ender Chest water recovery: air is running low. Drowning is possible.");
        }
        if (phase == EnderChestWaterRecovery.Phase.Stopping) {
            if (enderChestPath.isActive() || !enderChestPath.isBaritoneIdle()) {
                if (enderChestRecovery.timedOut(100))
                    stopEnderChestRecovery("Ender Chest Path stopped: Baritone did not release control for water recovery.");
                return;
            }
            if (enderChestPath.result() != SurfacePathProcess.Result.Interrupted) {
                stopEnderChestRecovery("Ender Chest Path stopped: Baritone control changed during water recovery.");
                return;
            }
            if (!mc.player.isUnderWater()) {
                enderChestRecovery.resuming();
                return;
            }
            enderChestRecovery.recovering();
            if (startConnectedWaterTravel(true)) return;
            stopEnderChestRecovery("Ender Chest Path stopped: no connected water escape is available.");
            return;
        }
        if (phase == EnderChestWaterRecovery.Phase.Resuming) {
            if (!enderChestPath.isBaritoneIdle()) {
                if (enderChestRecovery.timedOut(100))
                    stopEnderChestRecovery("Ender Chest Path stopped: Baritone remained busy after water recovery.");
                return;
            }
            if (mc.player.isUnderWater()) {
                stopEnderChestRecovery("Ender Chest Path stopped: the player submerged again before the route resumed.");
                return;
            }
            enderChestPath.start(List.of(enderChestTarget), enderChestTerrainChanges, enderChestTerrainChanges);
            enderChestAirWarned = false;
            enderChestRecovery.resumed();
            info("Ender Chest Path resumed after water recovery.");
        }
    }

    private void stopEnderChestRecovery(String message) {
        if (waterSearchChest) stopConnectedWaterTravel();
        if (underwaterEscapeChest) stopUnderwaterEscape();
        if (enderChestPath != null && enderChestPath.isActive()) enderChestPath.stop(null);
        enderChestRecovery = null;
        enderChestTarget = null;
        warning("%s", message);
    }

    private void toggleSurfacePath() {
        if (enderChestRecovery != null) {
            warning("Surface Path cannot start while Ender Chest Path is active.");
            return;
        }
        if (surfaceWaterHandoff) {
            surfaceWaterHandoff = false;
            if (surfacePath != null && surfacePath.isActive()) surfacePath.stop("Surface Path cancelled.");
            else info("Surface Path cancelled.");
            return;
        }
        if (waterNavigator != null || waterAscent != null) {
            stopConnectedWaterTravel();
            info("Surface Path cancelled.");
            return;
        }
        if (!underwaterEscapeBlocks.isEmpty()) {
            stopUnderwaterEscape();
            info("Surface Path cancelled.");
            return;
        }
        if (oceanBreach != null) {
            stopOceanBreach();
            info("Surface Path cancelled.");
            return;
        }
        if (surfacePath != null && surfacePath.isActive()) {
            surfaceFallbackPending = false;
            surfaceNormalFallbackPending = false;
            surfaceWaterFallbackPending = false;
            surfacePath.stop("Surface Path cancelled.");
            return;
        }
        if (surfaceBreachRouting) {
            surfaceBreachRouting = false;
            surfaceNormalFallbackPending = false;
            surfaceWaterFallbackPending = false;
            info("Surface Path cancelled.");
            return;
        }
        if ((surfaceFallbackPending || surfaceWaterFallbackPending) && surfacePath != null
            && surfacePath.result() == SurfacePathProcess.Result.Failed) {
            surfaceFallbackPending = false;
            surfaceWaterFallbackPending = false;
            info("Surface Path cancelled.");
            return;
        }
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) {
            warning("Surface Path requires a living player in a world.");
            return;
        }
        if (!mc.level.dimension().equals(Level.OVERWORLD)) {
            warning("Surface Path is available only in the Overworld.");
            return;
        }
        if (takeoffSequence != null || mc.player.isFallFlying() || mc.player.getAbilities().flying || mc.player.isPassenger()) {
            warning("Surface Path requires the player to be on foot, outside elytra takeoff.");
            return;
        }
        if (!BaritoneUtils.IS_AVAILABLE) {
            warning("Surface Path requires Baritone to be installed and available.");
            return;
        }
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (surfacePath == null) surfacePath = new SurfacePathProcess(baritone,
            new SurfacePathSettings(BaritoneAPI.getSettings()), this::isCurrentSurfaceDestination, this::reportSurfacePath);
        if (!surfacePath.isBaritoneIdle() || (enderChestPath != null && enderChestPath.isActive())) {
            warning("Surface Path cannot start while Baritone is busy.");
            return;
        }
        surfacePathWorld = mc.level;
        surfacePathPlayer = mc.player;
        surfaceFallbackPending = false;
        surfaceNormalFallbackPending = false;
        surfaceBreachRouting = false;
        surfaceEscapeAttempted = false;
        surfaceWaterFallbackPending = false;
        surfaceWaterSearchAttempted = false;
        surfaceWaterHandoff = false;
        waterAscentReroutes = 0;
        info("Surface Path diagnostics: %s; terrain changes %s.",
            mc.player.isUnderWater() ? "submerged" : "not submerged",
            pathTerrainChanges.get() ? "enabled" : "disabled");
        if (mc.player.isUnderWater() && !pathTerrainChanges.get())
            warning("Surface Path diagnostics: direct underwater breaching is disabled by terrain permissions.");
        if (mc.player.isUnderWater()) {
            if (!startConnectedWaterTravel(false))
                warning("Surface Path stopped: no connected water escape is available.");
            return;
        }
        if (pathTerrainChanges.get() && isBelowOceanFloor() && startOceanBreachRoute(false)) {
            surfaceNormalFallbackPending = true;
            return;
        }
        startNormalSurfaceRoute(true);
    }

    private void startNormalSurfaceRoute(boolean allowBreachFallback) {
        boolean waterSurface = mc.player.isUnderWater();
        List<BlockPos> destinations = findSurfaceDestinations(waterSurface);
        if (destinations.isEmpty() && !waterSurface) {
            destinations = findSurfaceDestinations(true);
            waterSurface = !destinations.isEmpty();
        }
        if (destinations.isEmpty()) {
            if (startUnderwaterEscape(false)) return;
            if (waterSurface && startConnectedWaterTravel(false)) return;
            warning(waterSurface
                ? "Surface Path found no exposed water surface in loaded chunks within the search radius."
                : "Surface Path found no suitable surface in loaded chunks within the search radius.");
            if (allowBreachFallback && pathTerrainChanges.get()) startOceanBreachRoute(true);
            return;
        }
        surfacePathWater = waterSurface;
        surfaceWaterFallbackPending = waterSurface;
        surfaceBreachRouting = false;
        surfaceFallbackPending = allowBreachFallback && pathTerrainChanges.get();
        surfaceBreachWait = 0;
        boolean allowTerrainChanges = pathTerrainChanges.get();
        surfacePath.start(destinations, allowTerrainChanges, allowTerrainChanges);
        if (safeWalk.get()) warning("Safe Walk is paused while Surface Path is active.");
    }

    private void reportSurfacePath(String message) {
        info("%s", message);
        if (message.equals("Surface Path failed: no reachable candidate found.") && surfacePath != null) {
            String route = surfaceBreachRouting ? "breach staging" : surfacePathWater ? "water surface" : "land surface";
            warning("Surface Path diagnostics: %s route exhausted; %s.", route, surfacePath.failureSummary());
        }
    }

    private void tickSurfacePath() {
        if (surfaceWaterHandoff) {
            if (mc.level == null || mc.player == null || mc.level != surfacePathWorld
                || mc.player != surfacePathPlayer || !mc.player.isAlive()
                || mc.player.isFallFlying() || mc.player.getAbilities().flying || mc.player.isPassenger()
                || pathTerrainChanges.get() != surfaceHandoffTerrainChanges) {
                surfaceWaterHandoff = false;
                surfacePath.stop(null);
                warning("Surface Path stopped: conditions changed during water recovery.");
                return;
            }
            if (surfacePath.isActive() || !surfacePath.isBaritoneIdle()) {
                if (++surfaceHandoffTicks > 100) {
                    surfaceWaterHandoff = false;
                    surfacePath.stop(null);
                    warning("Surface Path stopped: Baritone did not release control for water recovery.");
                }
                return;
            }
            surfaceWaterHandoff = false;
            if (surfacePath.result() != SurfacePathProcess.Result.Interrupted) {
                warning("Surface Path stopped: Baritone control changed during water recovery.");
            } else if (mc.player.isUnderWater()) {
                if (!startConnectedWaterTravel(false))
                    warning("Surface Path stopped: no connected water escape is available.");
            } else {
                startNormalSurfaceRoute(true);
            }
            return;
        }
        if (oceanBreach != null) {
            tickOceanBreach();
            return;
        }
        if (surfacePath == null) return;
        if (!surfacePath.isActive()) {
            if (surfaceBreachRouting) {
                if ((surfacePath.result() == SurfacePathProcess.Result.Arrived
                    || (surfaceNormalFallbackPending && surfacePath.result() == SurfacePathProcess.Result.Failed))
                    && !surfacePath.isBaritoneIdle()) {
                    if (++surfaceBreachWait <= 100) return;
                    surfaceBreachRouting = false;
                    surfaceNormalFallbackPending = false;
                    warning("Surface Path ocean breach stopped: Baritone did not release control.");
                    return;
                }
                surfaceBreachRouting = false;
                if (surfacePath.result() == SurfacePathProcess.Result.Arrived && surfacePath.arrivedAt() != null
                    && pathTerrainChanges.get() && mc.player == surfacePathPlayer && mc.level == surfacePathWorld
                    && mc.player != null && mc.player.isAlive() && validOceanBreachSite(surfacePath.arrivedAt())
                    && clearOceanBreachStagingSpace(surfacePath.arrivedAt())) {
                    oceanBreachBlock = surfacePath.arrivedAt().above(2);
                    oceanBreachOriginal = mc.level.getBlockState(oceanBreachBlock);
                    oceanBreach = new OceanBreachSequence();
                    warning("Surface Path is breaching the ocean floor. Flooding and drowning are possible.");
                } else if (surfacePath.result() == SurfacePathProcess.Result.Failed && surfaceNormalFallbackPending
                    && mc.player == surfacePathPlayer && mc.level == surfacePathWorld
                    && mc.player != null && mc.player.isAlive()) {
                    surfaceNormalFallbackPending = false;
                    warning("Surface Path could not reach an ocean breach site; trying a normal surface route.");
                    startNormalSurfaceRoute(false);
                    return;
                } else if (surfacePath.result() == SurfacePathProcess.Result.Arrived)
                    warning("Surface Path ocean breach stopped: the site or player state changed.");
                surfaceNormalFallbackPending = false;
                return;
            }
            if (surfaceFallbackPending && surfacePath.result() == SurfacePathProcess.Result.Failed) {
                if (!surfacePath.isBaritoneIdle()) {
                    if (++surfaceBreachWait <= 100) return;
                    surfaceFallbackPending = false;
                    warning("Surface Path ocean breach stopped: Baritone did not release control.");
                    return;
                }
                surfaceFallbackPending = false;
                if (startUnderwaterEscape(false)) return;
                if (surfaceWaterFallbackPending && startConnectedWaterTravel(false)) return;
                surfaceWaterFallbackPending = false;
                startOceanBreachRoute(true);
            } else if (surfaceWaterFallbackPending && surfacePath.result() == SurfacePathProcess.Result.Failed) {
                if (!surfacePath.isBaritoneIdle()) {
                    if (++surfaceBreachWait <= 100) return;
                    surfaceWaterFallbackPending = false;
                    warning("Surface Path stopped: Baritone did not release control for water recovery.");
                    return;
                }
                surfaceWaterFallbackPending = false;
                if (startUnderwaterEscape(false) || startConnectedWaterTravel(false)) return;
            } else if (surfacePath.result() != SurfacePathProcess.Result.None) {
                surfaceFallbackPending = false;
                surfaceWaterFallbackPending = false;
            }
            return;
        }
        if (mc.level != surfacePathWorld || mc.player != surfacePathPlayer || mc.level == null || mc.player == null
            || !mc.player.isAlive() || !mc.level.dimension().equals(Level.OVERWORLD)
            || mc.player.isFallFlying() || mc.player.getAbilities().flying || mc.player.isPassenger()
            || takeoffSequence != null) {
            surfaceFallbackPending = false;
            surfaceNormalFallbackPending = false;
            surfacePath.stop("Surface Path stopped: player or world state changed.");
            return;
        }
        if (mc.player.isUnderWater()) {
            surfaceWaterHandoff = true;
            surfaceHandoffTerrainChanges = pathTerrainChanges.get();
            surfaceHandoffTicks = 0;
            surfaceFallbackPending = false;
            surfaceWaterFallbackPending = false;
            surfaceNormalFallbackPending = false;
            surfaceBreachRouting = false;
            surfaceWaterSearchAttempted = false;
            waterAscentReroutes = 0;
            surfacePath.interrupt("Surface Path paused for water recovery.");
        }
    }

    private boolean startOceanBreachRoute(boolean reportMissing) {
        if (!pathTerrainChanges.get() || mc.level == null || mc.player == null || mc.player != surfacePathPlayer
            || mc.level != surfacePathWorld || !mc.player.isAlive()) return false;
        List<BlockPos> candidates = SurfaceDestinationSearch.find(mc.player.blockPosition(), surfacePathRadius.get(), false,
            (x, z) -> mc.level.hasChunk(x >> 4, z >> 4),
            this::oceanBreachStandingY, this::validOceanBreachSite);
        if (candidates.isEmpty()) {
            if (reportMissing) warning("Surface Path found no suitable ocean breach site.");
            return false;
        }
        if (!surfacePath.isBaritoneIdle()) {
            warning("Surface Path could not start the ocean breach because Baritone is busy.");
            return false;
        }
        surfaceBreachRouting = true;
        surfaceBreachWait = 0;
        surfacePath.start(candidates, true, true);
        warning("Surface Path is seeking an ocean breach site; flooding and drowning are possible.");
        return true;
    }

    private boolean isBelowOceanFloor() {
        BlockPos feet = mc.player.blockPosition();
        int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ());
        return mc.level.getFluidState(new BlockPos(feet.getX(), top - 1, feet.getZ())).is(FluidTags.WATER)
            && feet.getY() < oceanBreachStandingY(feet.getX(), feet.getZ()) + 2;
    }

    private int oceanBreachStandingY(int x, int z) {
        int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int y = top - 1;
        int bottom = Math.max(mc.level.getMinY(), top - 128);
        while (y >= bottom && mc.level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER)) y--;
        return y - 2;
    }

    private boolean startConnectedWaterTravel(boolean chest) {
        return startConnectedWaterTravel(chest, null);
    }

    private boolean startConnectedWaterTravel(boolean chest, BlockPos excludedColumn) {
        if (mc.level == null || mc.player == null || !mc.player.isUnderWater()
            || (chest ? chestWaterSearchAttempted : surfaceWaterSearchAttempted)
            || !(chest ? enderChestPath.isBaritoneIdle() : surfacePath.isBaritoneIdle())) return false;
        if (chest) chestWaterSearchAttempted = true;
        else surfaceWaterSearchAttempted = true;
        boolean allowBreak = pathTerrainChanges.get() && (!chest || enderChestTerrainChanges);
        WaterEscapeNavigator navigator = new WaterEscapeNavigator(this::isConnectedWaterCell,
            pos -> isConnectedWaterGoal(pos, allowBreak), excludedColumn);
        WaterEscapeNavigator.Result result = navigator.start(mc.player.blockPosition());
        if (result == WaterEscapeNavigator.Result.NoRoute || result == WaterEscapeNavigator.Result.CurrentBlocked) {
            warning("%s water recovery: no connected escape was found within the search limits.",
                chest ? "Ender Chest Path" : "Surface Path");
            return false;
        }
        waterSearchChest = chest;
        waterSearchTerrainChanges = pathTerrainChanges.get();
        waterSearchAirWarned = false;
        if (!chest) surfaceWaterFallbackPending = false;
        if (result == WaterEscapeNavigator.Result.Arrived) {
            finishConnectedWaterTravel(chest);
            return true;
        }
        waterNavigator = navigator;
        warning("%s is navigating connected water and checking for an escape. Drowning is possible.",
            chest ? "Ender Chest Path" : "Surface Path");
        return true;
    }

    private boolean isConnectedWaterCell(BlockPos pos) {
        return pos.getY() >= mc.level.getMinY()
            && mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && isClearEscapeWater(pos) && isClearEscapeWater(pos.above());
    }

    private boolean isConnectedWaterGoal(BlockPos pos, boolean allowBreak) {
        int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        if (!isWaterSurfaceDestination(new BlockPos(pos.getX(), top - 2, pos.getZ()))) return false;
        boolean clear = true;
        for (int y = pos.getY(); y < top; y++) {
            if (!isClearEscapeWater(new BlockPos(pos.getX(), y, pos.getZ()))) {
                clear = false;
                break;
            }
        }
        if (clear) return true;
        return allowBreak && !WaterEscapeNavigator.findEscapeBlocks(pos.getX(), pos.getZ(), pos.getY() + 1, top,
            this::isClearEscapeWater, this::isSafeEscapeBlock,
            point -> mc.level.getBlockState(point).isAir()).isEmpty();
    }

    private void tickConnectedWaterTravel() {
        boolean chest = waterSearchChest;
        String label = chest ? "Ender Chest Path" : "Surface Path";
        if (!waterRecoveryConditionsValid(chest)) {
            failConnectedWaterTravel(label + " stopped: water recovery conditions changed.");
            return;
        }
        if (!mc.player.isUnderWater()) {
            finishWaterRecoveryInAir(chest);
            return;
        }
        warnWaterRecoveryAir(label);
        WaterEscapeNavigator.Result result = waterNavigator.tick(mc.player.blockPosition(),
            mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (result == WaterEscapeNavigator.Result.Arrived) finishConnectedWaterTravel(chest);
        else if (result != WaterEscapeNavigator.Result.Moving) {
            String reason = switch (result) {
                case CurrentBlocked -> "the player's current water space is no longer clear";
                case TargetBlocked -> "the next water space is blocked or unloaded";
                case Deviated -> "the player moved outside the planned water route";
                case Stalled -> "connected-water movement stalled";
                case NoRoute -> "no further connected-water escape was found";
                default -> throw new IllegalStateException("Unexpected water navigation result");
            };
            failConnectedWaterTravel(label + " stopped: " + reason + ". Take control immediately.");
        }
    }

    private void finishConnectedWaterTravel(boolean chest) {
        stopConnectedWaterTravel();
        if (isConnectedWaterGoal(mc.player.blockPosition(), false)) {
            startWaterAscent(chest, null);
        } else if (!startUnderwaterEscape(chest)) {
            failConnectedWaterTravelForOwner(chest, (chest ? "Ender Chest Path" : "Surface Path")
                + " stopped: the planned escape is no longer available.");
        }
    }

    private void startWaterAscent(boolean chest, BlockPos opening) {
        waterSearchChest = chest;
        waterSearchTerrainChanges = pathTerrainChanges.get();
        waterSearchAirWarned = false;
        waterAscent = new OceanBreachAscent(mc.player.getY(), new Random());
        waterAscentAnchorX = mc.player.getX();
        waterAscentAnchorZ = mc.player.getZ();
        waterAscentDirection = Vec2.ZERO;
        waterAscentOpening = opening;
        waterAscentTicks = 0;
        info("%s is swimming toward exposed air.", chest ? "Ender Chest Path" : "Surface Path");
    }

    private void tickWaterAscent() {
        boolean chest = waterSearchChest;
        String label = chest ? "Ender Chest Path" : "Surface Path";
        if (!waterRecoveryConditionsValid(chest)) {
            failConnectedWaterTravel(label + " stopped: water recovery conditions changed.");
            return;
        }
        if (!mc.player.isUnderWater()) {
            finishWaterRecoveryInAir(chest);
            return;
        }
        warnWaterRecoveryAir(label);
        OceanBreachAscent.Progress progress = waterAscent.tick(mc.player.getY());
        if (++waterAscentTicks > 1200 || progress == OceanBreachAscent.Progress.Stalled) {
            failConnectedWaterTravel(label + " stopped: water ascent stalled. Take control immediately.");
            return;
        }
        boolean aboveOpening = waterAscentOpening == null
            || mc.player.getBoundingBox().minY >= waterAscentOpening.getY() + 1;
        if (progress == OceanBreachAscent.Progress.Slow && aboveOpening) {
            if (waterAscentReroutes++ >= 2) {
                failConnectedWaterTravel(label + " stopped: water ascent remains blocked. Take control immediately.");
                return;
            }
            BlockPos excludedColumn = mc.player.blockPosition();
            stopConnectedWaterTravel();
            if (chest) chestWaterSearchAttempted = false;
            else surfaceWaterSearchAttempted = false;
            warning("%s is searching for another connected-water route.", label);
            if (!startConnectedWaterTravel(chest, excludedColumn))
                failConnectedWaterTravelForOwner(chest, label + " stopped: no alternative water route is available.");
            return;
        }
        waterAscentDirection = waterAscent.direction(aboveOpening,
            (x, z) -> ascentDirectionClear(waterAscentAnchorX, waterAscentAnchorZ, x, z));
        if (aboveOpening) aimForFastAscent(waterAscentDirection);
    }

    private boolean waterRecoveryConditionsValid(boolean chest) {
        return mc.level != null && mc.player != null
            && mc.level == (chest ? enderChestPathWorld : surfacePathWorld)
            && mc.player == (chest ? enderChestPathPlayer : surfacePathPlayer)
            && mc.player.isAlive() && !mc.player.isInLava() && !mc.player.isFallFlying()
            && !mc.player.getAbilities().flying && !mc.player.isPassenger()
            && mc.isWindowActive() && mc.screen == null && !oceanBreachManualMovement()
            && pathTerrainChanges.get() == waterSearchTerrainChanges
            && (!chest || (enderChestRecovery != null && enderChestTarget != null
                && isEnderChestDestination(enderChestTarget) && enderChestTerrainChanges == waterSearchTerrainChanges))
            && (chest ? enderChestPath.isBaritoneIdle() : surfacePath.isBaritoneIdle());
    }

    private void finishWaterRecoveryInAir(boolean chest) {
        stopConnectedWaterTravel();
        if (chest) enderChestRecovery.resuming();
        else if (mc.level.canSeeSky(mc.player.blockPosition().above())) info("Surface Path reached breathable air.");
        else warning("Surface Path stopped in an air pocket; the surface has not been reached.");
    }

    private void warnWaterRecoveryAir(String label) {
        if (mc.player.getAirSupply() <= 80 && !waterSearchAirWarned) {
            waterSearchAirWarned = true;
            warning("%s water recovery: air is running low. Drowning is possible.", label);
        }
    }

    private void failConnectedWaterTravel(String message) {
        boolean chest = waterSearchChest;
        stopConnectedWaterTravel();
        failConnectedWaterTravelForOwner(chest, message);
    }

    private void failConnectedWaterTravelForOwner(boolean chest, String message) {
        if (chest) stopEnderChestRecovery(message);
        else warning("%s", message);
    }

    private void stopConnectedWaterTravel() {
        waterNavigator = null;
        waterAscent = null;
        waterAscentDirection = Vec2.ZERO;
        waterAscentOpening = null;
        waterSearchChest = false;
    }

    public WaterEscapeNavigator.SwimInput connectedWaterSwimInput() {
        if (!isActive() || waterNavigator == null || mc.player == null) return null;
        BlockPos target = waterNavigator.target();
        return WaterEscapeNavigator.swimInput(target.getX() + 0.5 - mc.player.getX(),
            target.getY() + 0.15 - mc.player.getY(), target.getZ() + 0.5 - mc.player.getZ(),
            mc.player.getYRot(), mc.player.isUnderWater() && mc.player.getFoodData().getFoodLevel() > 6);
    }

    private boolean startUnderwaterEscape(boolean chest) {
        if (mc.level == null || mc.player == null || mc.gameMode == null
            || !mc.player.isUnderWater() || !pathTerrainChanges.get()
            || (chest ? chestEscapeAttempted || !enderChestTerrainChanges : surfaceEscapeAttempted)
            || !(chest ? enderChestPath.isBaritoneIdle() : surfacePath.isBaritoneIdle())) return false;
        BlockPos feet = mc.player.blockPosition();
        if (!mc.level.hasChunk(feet.getX() >> 4, feet.getZ() >> 4)) return false;
        int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ());
        if (!mc.level.canSeeSky(new BlockPos(feet.getX(), top, feet.getZ()))) return false;
        List<BlockPos> blocks = WaterEscapeNavigator.findEscapeBlocks(feet.getX(), feet.getZ(),
            Mth.floor(mc.player.getEyeY()), top, this::isClearEscapeWater,
            this::isSafeEscapeBlock, pos -> mc.level.getBlockState(pos).isAir());
        if (blocks.isEmpty()) return false;
        underwaterEscapeBlocks = blocks;
        underwaterEscapeOriginals = blocks.stream().map(mc.level::getBlockState).toList();
        underwaterEscapeChest = chest;
        underwaterEscapeIndex = 0;
        underwaterEscapeTicks = 0;
        underwaterEscapeBlockTicks = 0;
        underwaterEscapeTop = top;
        underwaterEscapeAirWarned = false;
        if (chest) chestEscapeAttempted = true;
        else surfaceEscapeAttempted = true;
        warning("%s is clearing a short underwater escape. Drowning and flooding are possible.",
            chest ? "Ender Chest Path" : "Surface Path");
        return true;
    }

    private boolean isClearEscapeWater(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.getFluidState().is(FluidTags.WATER)
            && state.getCollisionShape(mc.level, pos).isEmpty();
    }

    private boolean isSafeEscapeBlock(BlockPos pos) {
        if (!isOceanBreachMineable(pos)) return false;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!mc.level.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)
                || mc.level.getFluidState(neighbor).is(FluidTags.LAVA)) return false;
        }
        return true;
    }

    private void tickUnderwaterEscape() {
        boolean chest = underwaterEscapeChest;
        String label = chest ? "Ender Chest Path" : "Surface Path";
        if (mc.level == null || mc.player == null || mc.gameMode == null
            || mc.level != (chest ? enderChestPathWorld : surfacePathWorld)
            || mc.player != (chest ? enderChestPathPlayer : surfacePathPlayer)
            || !mc.player.isAlive() || !mc.player.isUnderWater() || mc.player.isInLava()
            || mc.player.isFallFlying() || mc.player.getAbilities().flying || mc.player.isPassenger()
            || !mc.isWindowActive() || mc.screen != null || !pathTerrainChanges.get()
            || (chest && (!enderChestTerrainChanges || !isEnderChestDestination(enderChestTarget)))
            || !(chest ? enderChestPath.isBaritoneIdle() : surfacePath.isBaritoneIdle())
            || oceanBreachManualMovement()) {
            failUnderwaterEscape(label + " stopped: underwater escape conditions changed.");
            return;
        }
        if (++underwaterEscapeTicks > 1200 || ++underwaterEscapeBlockTicks > 400) {
            failUnderwaterEscape(label + " stopped: underwater escape timed out. Take control immediately.");
            return;
        }
        if (mc.player.getAirSupply() <= 80 && !underwaterEscapeAirWarned) {
            underwaterEscapeAirWarned = true;
            warning("%s underwater escape: air is running low. Drowning is possible.", label);
        }
        BlockPos target = underwaterEscapeBlocks.get(underwaterEscapeIndex);
        if (mc.player.getBlockX() != target.getX() || mc.player.getBlockZ() != target.getZ()
            || !mc.level.hasChunk(target.getX() >> 4, target.getZ() >> 4)
            || mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, target.getX(), target.getZ()) != underwaterEscapeTop
            || !mc.level.canSeeSky(new BlockPos(target.getX(), underwaterEscapeTop, target.getZ()))) {
            failUnderwaterEscape(label + " stopped: the escape column changed.");
            return;
        }
        BlockPos last = underwaterEscapeBlocks.get(underwaterEscapeBlocks.size() - 1);
        for (int y = last.getY() + 1; y < underwaterEscapeTop; y++) {
            if (!isClearEscapeWater(new BlockPos(target.getX(), y, target.getZ()))) {
                failUnderwaterEscape(label + " stopped: the water above the escape changed.");
                return;
            }
        }
        if (!mc.level.getBlockState(new BlockPos(target.getX(), underwaterEscapeTop, target.getZ())).isAir()
            || !mc.level.getBlockState(new BlockPos(target.getX(), underwaterEscapeTop + 1, target.getZ())).isAir()) {
            failUnderwaterEscape(label + " stopped: exposed air is no longer available.");
            return;
        }
        for (int i = underwaterEscapeIndex + 1; i < underwaterEscapeBlocks.size(); i++) {
            BlockPos remaining = underwaterEscapeBlocks.get(i);
            if (!mc.level.getBlockState(remaining).equals(underwaterEscapeOriginals.get(i))
                || !isSafeEscapeBlock(remaining)) {
                failUnderwaterEscape(label + " stopped: the escape blocks changed.");
                return;
            }
        }
        BlockState state = mc.level.getBlockState(target);
        if (!state.equals(underwaterEscapeOriginals.get(underwaterEscapeIndex))) {
            if (!state.isAir() && !isClearEscapeWater(target)) {
                failUnderwaterEscape(label + " stopped: the escape block changed.");
                return;
            }
            mc.gameMode.stopDestroyBlock();
            underwaterEscapeIndex++;
            underwaterEscapeBlockTicks = 0;
            if (underwaterEscapeIndex == underwaterEscapeBlocks.size()) {
                stopUnderwaterEscape();
                startWaterAscent(chest, target);
                info("%s cleared the escape and is swimming upward.", label);
            }
            return;
        }
        if (!isSafeEscapeBlock(target)) {
            failUnderwaterEscape(label + " stopped: the escape block is no longer safe to mine.");
            return;
        }
        if (state.requiresCorrectToolForDrops() && !mc.player.getMainHandItem().isCorrectToolForDrops(state)) {
            failUnderwaterEscape(label + " stopped: hold a suitable tool for the escape block.");
            return;
        }
        double dx = target.getX() + 0.5 - mc.player.getX();
        double dz = target.getZ() + 0.5 - mc.player.getZ();
        if (!OceanBreachSequence.centered(dx, dz, mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().z)
            || mc.player.getEyePosition().distanceToSqr(target.getCenter())
                > mc.player.blockInteractionRange() * mc.player.blockInteractionRange()) return;
        mc.player.setXRot(-90);
        if (!mc.gameMode.continueDestroyBlock(target, Direction.DOWN)) {
            failUnderwaterEscape(label + " stopped: the escape block could not be mined.");
            return;
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void failUnderwaterEscape(String message) {
        boolean chest = underwaterEscapeChest;
        stopUnderwaterEscape();
        if (chest) stopEnderChestRecovery(message);
        else warning("%s", message);
    }

    private void stopUnderwaterEscape() {
        if (!underwaterEscapeBlocks.isEmpty() && mc.gameMode != null)
            mc.gameMode.stopDestroyBlock();
        underwaterEscapeBlocks = List.of();
        underwaterEscapeOriginals = List.of();
        underwaterEscapeChest = false;
    }

    public Vec2 underwaterEscapeMovement() {
        if (!isActive() || underwaterEscapeBlocks.isEmpty() || mc.player == null) return null;
        BlockPos target = underwaterEscapeBlocks.get(underwaterEscapeIndex);
        return OceanBreachSequence.movement(target.getX() + 0.5 - mc.player.getX(),
            target.getZ() + 0.5 - mc.player.getZ(), mc.player.getYRot());
    }

    private boolean validOceanBreachSite(BlockPos feet) {
        if (mc.level == null || mc.player == null
            || !mc.level.hasChunk(feet.getX() >> 4, feet.getZ() >> 4)) return false;
        BlockPos target = feet.above(2);
        if (!isOceanBreachMineable(target)
            || !OceanBreachSequence.canStage(feet, pos -> mc.level.getBlockState(pos).isAir(),
                this::isOceanBreachMineable,
                pos -> mc.level.getBlockState(pos).isFaceSturdy(mc.level, pos, Direction.UP))) return false;
        int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ());
        return OceanBreachSequence.exposedWaterColumn(target, top,
            pos -> mc.level.getFluidState(pos).is(FluidTags.WATER),
            pos -> mc.level.getBlockState(pos).isAir());
    }

    private boolean isOceanBreachMineable(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        Block type = state.getBlock();
        return (type == Blocks.STONE || type == Blocks.DEEPSLATE || type == Blocks.DIRT
            || type == Blocks.CLAY || type == Blocks.GRANITE || type == Blocks.DIORITE
            || type == Blocks.ANDESITE || type == Blocks.TUFF)
            && state.getFluidState().isEmpty() && state.getDestroySpeed(mc.level, pos) >= 0
            && !BaritoneAPI.getSettings().blocksToDisallowBreaking.value.contains(type);
    }

    private boolean clearOceanBreachStagingSpace(BlockPos feet) {
        return OceanBreachSequence.isClear(feet, pos -> mc.level.getBlockState(pos).isAir());
    }

    private void tickOceanBreach() {
        if (!pathTerrainChanges.get()
            || mc.player == null || mc.level == null || mc.gameMode == null
            || mc.player != surfacePathPlayer || mc.level != surfacePathWorld || !mc.player.isAlive()
            || !mc.isWindowActive() || mc.screen != null || mc.player.isPassenger()
            || mc.player.isInLava() || mc.player.getAbilities().flying
            || !surfacePath.isBaritoneIdle()) {
            stopOceanBreach();
            warning("Surface Path ocean breach stopped: player, world, focus or permissions changed.");
            return;
        }
        if (!mc.level.hasChunk(oceanBreachBlock.getX() >> 4, oceanBreachBlock.getZ() >> 4)) {
            stopOceanBreach();
            warning("Surface Path ocean breach stopped: the area unloaded.");
            return;
        }
        if (mc.player.isUnderWater() && mc.player.getAirSupply() <= 80 && !oceanBreachAirWarned) {
            oceanBreachAirWarned = true;
            warning("Surface Path ocean breach: air is running low. Drowning is possible.");
        }
        BlockState state = mc.level.getBlockState(oceanBreachBlock);
        boolean present = state.equals(oceanBreachOriginal);
        OceanBreachSequence.Phase previousPhase = oceanBreach.phase();
        if (present && previousPhase != OceanBreachSequence.Phase.Centering
            && previousPhase != OceanBreachSequence.Phase.Mining) {
            stopOceanBreach();
            warning("Surface Path ocean breach stopped: the block was restored.");
            return;
        }
        if (!present && previousPhase == OceanBreachSequence.Phase.Centering) {
            stopOceanBreach();
            warning("Surface Path ocean breach stopped: the target changed before mining.");
            return;
        }
        if (!present && !state.isAir() && !state.getFluidState().is(FluidTags.WATER)) {
            stopOceanBreach();
            warning("Surface Path ocean breach stopped: the target changed.");
            return;
        }
        if (previousPhase == OceanBreachSequence.Phase.Swimming && oceanBreachManualMovement()) {
            stopOceanBreach();
            warning("Surface Path ocean breach stopped: manual movement was detected.");
            return;
        }
        BlockPos feet = oceanBreachBlock.below(2);
        double dx = feet.getX() + 0.5 - mc.player.getX();
        double dz = feet.getZ() + 0.5 - mc.player.getZ();
        if (present && (previousPhase == OceanBreachSequence.Phase.Centering
            || previousPhase == OceanBreachSequence.Phase.Mining)) {
            if (!validOceanBreachSite(feet) || !clearOceanBreachStagingSpace(feet)) {
                stopOceanBreach();
                warning("Surface Path ocean breach stopped: the staging site is no longer safe.");
                return;
            }
            if (!mc.player.blockPosition().equals(feet) || !mc.player.onGround()) {
                stopOceanBreach();
                warning("Surface Path ocean breach stopped: the player left the staging position.");
                return;
            }
            if (oceanBreachManualMovement()) {
                stopOceanBreach();
                warning("Surface Path ocean breach stopped: manual movement was detected.");
                return;
            }
            if (state.requiresCorrectToolForDrops() && !mc.player.getMainHandItem().isCorrectToolForDrops(state)) {
                stopOceanBreach();
                warning("Surface Path ocean breach stopped: hold a suitable tool for the seabed block.");
                return;
            }
            if (mc.player.getEyePosition().distanceToSqr(oceanBreachBlock.getCenter())
                > mc.player.blockInteractionRange() * mc.player.blockInteractionRange()) {
                stopOceanBreach();
                warning("Surface Path ocean breach stopped: the seabed block is out of reach.");
                return;
            }
            if (previousPhase == OceanBreachSequence.Phase.Mining
                && !OceanBreachSequence.withinBreachColumn(dx, dz)) {
                stopOceanBreach();
                warning("Surface Path ocean breach stopped: the player moved out of alignment.");
                return;
            }
        }
        boolean centered = OceanBreachSequence.centered(dx, dz, mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().z);
        boolean exposedAir = mc.level.canSeeSky(mc.player.blockPosition().above());
        OceanBreachSequence.Phase phase = oceanBreach.tick(centered, present, mc.player.isInWater(),
            mc.player.isUnderWater(), exposedAir);
        if (phase == OceanBreachSequence.Phase.Complete || phase == OceanBreachSequence.Phase.Failed) {
            stopOceanBreach();
            if (phase == OceanBreachSequence.Phase.Complete) info("Surface Path reached breathable air.");
            else if (previousPhase == OceanBreachSequence.Phase.Centering)
                warning("Surface Path ocean breach stopped: could not center beneath the seabed block.");
            else warning("Surface Path ocean breach timed out.");
            return;
        }
        if (phase == OceanBreachSequence.Phase.Swimming) {
            tickOceanBreachAscent();
            return;
        }
        if (phase != OceanBreachSequence.Phase.Mining) return;
        mc.player.setXRot(-90);
        if (!mc.gameMode.continueDestroyBlock(oceanBreachBlock, Direction.DOWN)) {
            stopOceanBreach();
            warning("Surface Path ocean breach could not mine the target.");
            return;
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void stopOceanBreach() {
        if (oceanBreach != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        oceanBreach = null;
        oceanBreachAscent = null;
        oceanBreachSwivel = Vec2.ZERO;
        oceanBreachSlowWarned = false;
        oceanBreachAirWarned = false;
        oceanBreachBlock = null;
        oceanBreachOriginal = null;
    }

    private boolean oceanBreachManualMovement() {
        return isPhysicallyPressed(mc.options.keyUp) || isPhysicallyPressed(mc.options.keyDown)
            || isPhysicallyPressed(mc.options.keyLeft) || isPhysicallyPressed(mc.options.keyRight)
            || isPhysicallyPressed(mc.options.keyJump) || isPhysicallyPressed(mc.options.keyShift);
    }

    private void tickOceanBreachAscent() {
        if (oceanBreachAscent == null) {
            oceanBreachAscent = new OceanBreachAscent(mc.player.getY(), new Random());
            oceanBreachAnchorX = mc.player.getX();
            oceanBreachAnchorZ = mc.player.getZ();
        }
        OceanBreachAscent.Progress progress = oceanBreachAscent.tick(mc.player.getY());
        if (progress == OceanBreachAscent.Progress.Stalled) {
            stopOceanBreach();
            warning("Surface Path ocean breach stopped: upward movement stalled. Take control immediately.");
            return;
        }
        if (progress == OceanBreachAscent.Progress.Slow && !oceanBreachSlowWarned) {
            oceanBreachSlowWarned = true;
            warning("Surface Path ocean breach is not gaining height; horizontal movement is paused.");
        } else if (progress == OceanBreachAscent.Progress.Moving) oceanBreachSlowWarned = false;
        if (mc.player.getAirSupply() <= 80 && !oceanBreachAirWarned) {
            oceanBreachAirWarned = true;
            warning("Surface Path ocean breach: air is running low. Drowning is possible.");
        }
        boolean aboveBreach = mc.player.getBoundingBox().minY >= oceanBreachBlock.getY() + 1;
        if (aboveBreach && mc.player.isUnderWater() && progress == OceanBreachAscent.Progress.Slow) {
            BlockPos blockedColumn = mc.player.blockPosition();
            stopOceanBreach();
            surfaceWaterSearchAttempted = false;
            waterAscentReroutes = 1;
            warning("Surface Path is searching connected water around the obstruction.");
            if (!startConnectedWaterTravel(false, blockedColumn))
                warning("Surface Path stopped: no alternative connected-water route is available. Take control immediately.");
            return;
        }
        oceanBreachSwivel = oceanBreachAscent.direction(aboveBreach,
            (x, z) -> ascentDirectionClear(oceanBreachAnchorX, oceanBreachAnchorZ, x, z));
        if (aboveBreach) aimForFastAscent(oceanBreachSwivel);
    }

    private void aimForFastAscent(Vec2 direction) {
        if (direction.equals(Vec2.ZERO) || !mc.player.isUnderWater()) return;
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.y)));
        mc.player.setXRot(-70);
    }

    private boolean oceanBreachClearWater(double x, double z) {
        return OceanBreachAscent.clearAscentSweep(mc.player.getBoundingBox(), x, z, pos -> {
            if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
            BlockState state = mc.level.getBlockState(pos);
            return (state.getFluidState().is(FluidTags.WATER)
                && state.getCollisionShape(mc.level, pos).isEmpty())
                || (state.isAir() && mc.level.canSeeSky(pos));
        });
    }

    private boolean ascentDirectionClear(double anchorX, double anchorZ, double x, double z) {
        return OceanBreachAscent.withinDriftLimit(anchorX, anchorZ, mc.player.getX(), mc.player.getZ(), x, z)
            && oceanBreachClearWater(x, z);
    }

    private boolean oceanBreachSwimming() {
        return isActive() && oceanBreach != null
            && oceanBreach.phase() == OceanBreachSequence.Phase.Swimming;
    }

    public Vec2 waterAscentSwimMovement() {
        if (!isActive() || (waterAscent == null && !oceanBreachSwimming())) return null;
        if (mc.player == null) return Vec2.ZERO;
        BlockPos opening = waterAscent != null ? waterAscentOpening : oceanBreachBlock;
        Vec2 direction = waterAscent != null ? waterAscentDirection : oceanBreachSwivel;
        return OceanBreachAscent.movement(opening, mc.player.getX(), mc.player.getBoundingBox().minY,
            mc.player.getZ(), mc.player.getYRot(), direction, mc.player.isUnderWater());
    }

    public boolean waterAscentSprintSwimming() {
        if (!isActive() || mc.player == null || !mc.player.isUnderWater()
            || (waterAscent == null && !oceanBreachSwimming())) return false;
        BlockPos opening = waterAscent != null ? waterAscentOpening : oceanBreachBlock;
        Vec2 direction = waterAscent != null ? waterAscentDirection : oceanBreachSwivel;
        return OceanBreachAscent.sprintRequested(opening, mc.player.getBoundingBox().minY, direction, true);
    }

    public boolean waterSprintInputActive() {
        return isActive() && mc.player != null && mc.player.isUnderWater()
            && mc.isWindowActive() && mc.screen == null
            && (waterNavigator != null || waterAscent != null || oceanBreachSwimming());
    }

    public Vec2 oceanBreachCenteringMovement() {
        if (!isActive() || oceanBreach == null || oceanBreach.phase() != OceanBreachSequence.Phase.Centering
            || mc.player == null || mc.player != surfacePathPlayer || oceanBreachBlock == null) return null;
        return OceanBreachSequence.movement(oceanBreachBlock.getX() + 0.5 - mc.player.getX(),
            oceanBreachBlock.getZ() + 0.5 - mc.player.getZ(), mc.player.getYRot());
    }

    private List<BlockPos> findSurfaceDestinations(boolean waterSurface) {
        if (waterSurface) return SurfaceDestinationSearch.find(mc.player.blockPosition(), surfacePathRadius.get(), true,
            (x, z) -> mc.level.hasChunk(x >> 4, z >> 4),
            (x, z) -> mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z),
            this::isWaterSurfaceDestination);
        return SurfaceDestinationSearch.findLand(mc.player.blockPosition(), surfacePathRadius.get(),
            (x, z) -> mc.level.hasChunk(x >> 4, z >> 4),
            (x, z) -> mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z),
            this::surfaceHeightBelowCanopy, this::isSurfaceDestination,
            this::isFallbackSurfaceDestination, this::isBroadSurfaceDestination);
    }

    private boolean isCurrentSurfaceDestination(BlockPos pos) {
        if (surfaceBreachRouting) return validOceanBreachSite(pos);
        return surfacePathWater ? isWaterSurfaceDestination(pos)
            : isSurfaceDestination(pos) || isFallbackSurfaceDestination(pos) || isBroadSurfaceDestination(pos);
    }

    private boolean isBroadSurfaceDestination(BlockPos pos) {
        if (mc.level == null || mc.player == null || !mc.level.dimension().equals(Level.OVERWORLD)
            || !mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) != pos.getY()
            || !mc.level.getFluidState(pos).isEmpty() || !mc.level.getFluidState(pos.above()).isEmpty()) return false;
        BlockPos floorPos = pos.below();
        BlockState floor = mc.level.getBlockState(floorPos);
        if (!floor.getFluidState().isEmpty() || floor.is(Blocks.MAGMA_BLOCK)
            || !floor.isFaceSturdy(mc.level, floorPos, Direction.UP)) return false;
        for (BlockPos body : List.of(pos, pos.above())) {
            BlockState state = mc.level.getBlockState(body);
            if (state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)) return false;
        }
        return true;
    }

    private int surfaceHeightBelowCanopy(int x, int z) {
        int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return SurfaceDestinationSearch.belowCanopy(top, y -> isCanopyPassThrough(new BlockPos(x, y, z)));
    }

    private boolean isCanopyPassThrough(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.getFluidState().isEmpty()
            && (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS));
    }

    private boolean isFallbackSurfaceDestination(BlockPos pos) {
        if (mc.level == null || mc.player == null || !mc.level.dimension().equals(Level.OVERWORLD)
            || !mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || !isNaturalSurfaceFloor(pos.below()) || !mc.level.getBlockState(pos).isAir()
            || !mc.level.getBlockState(pos.above()).isAir()
            || !mc.level.getFluidState(pos).isEmpty() || !mc.level.getFluidState(pos.above()).isEmpty()) return false;
        int top = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        if (!mc.level.canSeeSky(new BlockPos(pos.getX(), top, pos.getZ()))
            || !SurfaceDestinationSearch.exposedThroughCanopy(pos.getY(), top,
                y -> isCanopyPassThrough(new BlockPos(pos.getX(), y, pos.getZ())))) return false;
        return mc.level.noCollision(mc.player, new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
            pos.getX() + 0.8, pos.getY() + 1.8, pos.getZ() + 0.8));
    }

    private boolean isWaterSurfaceDestination(BlockPos pos) {
        return mc.level != null && mc.player != null && mc.level.dimension().equals(Level.OVERWORLD)
            && mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && mc.level.getBlockState(pos).is(Blocks.WATER)
            && mc.level.getBlockState(pos.above()).is(Blocks.WATER)
            && mc.level.getBlockState(pos.above(2)).isAir()
            && mc.level.getBlockState(pos.above(3)).isAir()
            && mc.level.canSeeSky(pos.above(2));
    }

    private boolean isSurfaceDestination(BlockPos pos) {
        if (mc.level == null || mc.player == null || !mc.level.dimension().equals(Level.OVERWORLD)
            || !mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || !mc.level.canSeeSky(pos) || !mc.level.getFluidState(pos).isEmpty()
            || !mc.level.getFluidState(pos.above()).isEmpty()) return false;
        if (!isNaturalSurfaceFloor(pos.below())) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos adjacent = pos.offset(dx, 0, dz);
                if (!mc.level.hasChunk(adjacent.getX() >> 4, adjacent.getZ() >> 4)
                    || !isNaturalSurfaceFloor(adjacent.below())
                    || !mc.level.getFluidState(adjacent).isEmpty()
                    || !mc.level.getBlockState(adjacent).isAir()
                    || !mc.level.getBlockState(adjacent.above()).isAir()) return false;
            }
        }
        return mc.level.noCollision(mc.player, new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
            pos.getX() + 0.8, pos.getY() + 1.8, pos.getZ() + 0.8));
    }

    private boolean isNaturalSurfaceFloor(BlockPos floorPos) {
        BlockState floor = mc.level.getBlockState(floorPos);
        Block block = floor.getBlock();
        return (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.COARSE_DIRT
            || block == Blocks.PODZOL || block == Blocks.MYCELIUM || block == Blocks.STONE
            || block == Blocks.GRAVEL || block == Blocks.SAND || block == Blocks.RED_SAND
            || block == Blocks.CLAY || block == Blocks.SNOW_BLOCK || block == Blocks.TERRACOTTA)
            && floor.isCollisionShapeFullBlock(mc.level, floorPos);
    }

    private void startTakeoff() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            warning("Elytra takeoff requires an active world.");
            return;
        }
        if (waterNavigator != null || waterAscent != null || !underwaterEscapeBlocks.isEmpty() || oceanBreach != null || surfaceWaterHandoff || enderChestRecovery != null || (surfacePath != null && surfacePath.isActive()) || (enderChestPath != null && enderChestPath.isActive())) {
            warning("Elytra takeoff unavailable while a Tweaker path is active.");
            return;
        }
        RocketPilot pilot = Modules.get().get(RocketPilot.class);
        if (pilot != null && pilot.isActive()) {
            warning("Elytra takeoff unavailable while Rocket Pilot is active.");
            return;
        }
        if (!mc.player.isAlive() || !mc.player.onGround() || mc.player.isFallFlying() || mc.player.getAbilities().flying
            || mc.player.isPassenger() || mc.player.isInWater() || mc.player.isInLava()) {
            warning("Elytra takeoff requires a grounded player outside water, lava, and vehicles.");
            return;
        }
        if (takeoffMode.get() == TakeoffMode.TargetY && mc.player.getY() >= takeoffTargetY.get()) {
            warning("Elytra takeoff target Y must be above your current position.");
            return;
        }
        if (!hasUsableTakeoffElytra()) {
            warning("Elytra takeoff requires a usable equipped elytra.");
            return;
        }
        if (!hasTakeoffRocket()) {
            warning("Elytra takeoff requires a rocket in the hotbar or offhand.");
            return;
        }
        AABB playerBox = mc.player.getBoundingBox();
        AABB clearance = new AABB(playerBox.minX, playerBox.maxY, playerBox.minZ,
            playerBox.maxX, playerBox.maxY + 2.0, playerBox.maxZ);
        if (mc.level.getBlockCollisions(mc.player, clearance).iterator().hasNext()) {
            warning("Elytra takeoff blocked: insufficient overhead clearance.");
            return;
        }
        takeoffPlayer = mc.player;
        takeoffWorld = mc.level;
        activeTakeoffTargetY = takeoffTargetY.get();
        activeTakeoffMode = takeoffMode.get();
        takeoffSequence = new ElytraTakeoffSequence();
        takeoffManualJump = mc.player.getAbilities().mayfly;
        takeoffTicks = 0;
        takeoffLastRocketTick = -takeoffRocketDelay.get();
        if (activeTakeoffMode == TakeoffMode.TargetY)
            info("Elytra takeoff started toward target Y. Use the takeoff key to cancel.");
        else info("Elytra jump/glide started. Use the takeoff key to cancel.");
    }

    private void tickTakeoff() {
        boolean sneakPressed = mc.player != null && mc.isWindowActive() && mc.screen == null
            && isPhysicallyPressed(mc.options.keyShift);
        boolean sneakJustPressed = sneakPressed && !takeoffSneakDown;
        takeoffSneakDown = sneakPressed;
        if (takeoffSneakReleasePending
            && (mc.player != takeoffSneakReleasePlayer || mc.level != takeoffSneakReleaseWorld
                || !takeoffEnabled.get() || !mc.isWindowActive() || mc.screen != null
                || (!sneakPressed && !mc.options.keyShift.isDown()))) clearTakeoffSneakRelease();
        if (takeoffSequence == null) {
            if (takeoffEnabled.get() && takeoffTrigger.get() == TakeoffTrigger.Sneak && sneakJustPressed) startTakeoff();
            return;
        }
        if (!takeoffEnabled.get()) {
            cancelTakeoff("Feature disabled.");
            return;
        }
        if (mc.player != takeoffPlayer || mc.level != takeoffWorld || mc.player == null || mc.level == null) {
            resetTakeoff();
            return;
        }
        RocketPilot pilot = Modules.get().get(RocketPilot.class);
        if (mc.screen != null || !mc.isWindowActive() || mc.gameMode == null
            || (pilot != null && pilot.isActive())) {
            cancelTakeoff("Screen opened, focus lost, or another flight controller became active.");
            return;
        }
        if (!mc.player.isAlive() || !hasUsableTakeoffElytra() || mc.player.isPassenger() || mc.player.getAbilities().flying
            || mc.player.isInWater() || mc.player.isInLava()) {
            cancelTakeoff("Elytra or player state no longer permits flight.");
            return;
        }
        if (++takeoffTicks > takeoffTimeout.get() * 20) {
            cancelTakeoff("Takeoff timed out.");
            return;
        }
        if (activeTakeoffMode == TakeoffMode.TargetY && takeoffSequence.phase() == ElytraTakeoffSequence.Phase.Climbing
            && mc.player.isFallFlying() && mc.player.getY() >= activeTakeoffTargetY) {
            finishTakeoff();
            info("Elytra takeoff reached target Y. Flight control returned.");
            return;
        }
        if (activeTakeoffMode.controlsPitch()) mc.player.setXRot(takeoffPitch.get().floatValue());
        ElytraTakeoffSequence.Action action = takeoffSequence.tick(mc.player.onGround(), mc.player.isFallFlying());
        switch (action) {
            case Failed -> {
                cancelTakeoff("Launch or elytra deployment failed, or flight stopped.");
                return;
            }
            case Jump -> {
                if (takeoffManualJump) mc.player.jumpFromGround();
            }
            case Deploy -> { }
            case Climb -> tickTakeoffClimb();
            case Wait -> { }
        }
    }

    private void tickTakeoffClimb() {
        if (activeTakeoffMode == TakeoffMode.TargetY && mc.player.getY() >= activeTakeoffTargetY) {
            finishTakeoff();
            info("Elytra takeoff reached target Y. Flight control returned.");
        } else if (mc.player.horizontalCollision) {
            cancelTakeoff("An obstacle was hit.");
        } else if (takeoffTicks - takeoffLastRocketTick >= takeoffRocketDelay.get()) {
            if (!fireTakeoffRocket()) {
                cancelTakeoff("No usable rocket, or rocket use was rejected.");
            } else if (activeTakeoffMode.completesAfterRocket()) {
                finishTakeoff();
                info("Elytra jump/glide rocket used. Flight control returned.");
            } else {
                takeoffLastRocketTick = takeoffTicks;
            }
        }
    }

    private boolean hasUsableTakeoffElytra() {
        ItemStack elytra = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        return elytra.is(Items.ELYTRA) && elytra.getDamageValue() < elytra.getMaxDamage() - 1;
    }

    private boolean hasTakeoffRocket() {
        if (mc.player.getOffhandItem().is(Items.FIREWORK_ROCKET)) return true;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.FIREWORK_ROCKET)) return true;
        }
        return false;
    }

    private boolean fireTakeoffRocket() {
        if (mc.player.getOffhandItem().is(Items.FIREWORK_ROCKET))
            return mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND).consumesAction();
        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getItem(i).is(Items.FIREWORK_ROCKET)) continue;
            int previous = com.example.addon.utils.MinecraftCompat.selectedSlot(mc.player.getInventory());
            if (!InvUtils.swap(i, false)) return false;
            try {
                return mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND).consumesAction();
            } finally {
                InvUtils.swap(previous, false);
            }
        }
        return false;
    }

    private void cancelTakeoff(String reason) {
        resetTakeoff();
        warning("Elytra takeoff stopped: %s", reason);
    }

    private void resetTakeoff() {
        takeoffSequence = null;
        takeoffManualJump = false;
        activeTakeoffMode = null;
        takeoffPlayer = null;
        takeoffWorld = null;
    }

    private void finishTakeoff() {
        if (isPhysicallyPressed(mc.options.keyShift) || mc.options.keyShift.isDown()) {
            takeoffSneakReleasePending = true;
            takeoffSneakReleasePlayer = takeoffPlayer;
            takeoffSneakReleaseWorld = takeoffWorld;
        }
        resetTakeoff();
    }

    private void clearTakeoffSneakRelease() {
        takeoffSneakReleasePending = false;
        takeoffSneakReleasePlayer = null;
        takeoffSneakReleaseWorld = null;
    }

    public Boolean takeoffSneakOverride() {
        if (!isActive() || !takeoffEnabled.get() || !mc.isWindowActive() || mc.screen != null
            || mc.player == null || mc.level == null) return null;
        if (takeoffSequence != null && mc.player == takeoffPlayer && mc.level == takeoffWorld) return false;
        if (takeoffSneakReleasePending && mc.player == takeoffSneakReleasePlayer
            && mc.level == takeoffSneakReleaseWorld) return false;
        return null;
    }

    public Boolean takeoffJumpOverride() {
        if (!isActive() || !takeoffEnabled.get() || !mc.isWindowActive() || mc.screen != null
            || takeoffSequence == null || mc.player != takeoffPlayer || mc.level != takeoffWorld) return null;
        if (takeoffManualJump && takeoffSequence.phase() == ElytraTakeoffSequence.Phase.JumpPressed) return false;
        return takeoffSequence.jumpPressed();
    }

    public boolean hasActivePathOrTakeoff() {
        return isActive() && (takeoffSequence != null || waterNavigator != null || waterAscent != null
            || !underwaterEscapeBlocks.isEmpty() || oceanBreach != null || surfaceWaterHandoff
            || surfaceBreachRouting || surfaceFallbackPending || surfaceNormalFallbackPending
            || surfaceWaterFallbackPending || enderChestRecovery != null
            || (surfacePath != null && surfacePath.isActive())
            || (enderChestPath != null && enderChestPath.isActive()));
    }

    public boolean keepPortalScreenOpen() {
        return isActive() && portalGui.get() && mc.screen instanceof AbstractContainerScreen<?>;
    }

    public boolean shouldSafeWalk() {
        if (!isActive() || !safeWalk.get() || mc.player == null || mc.level == null
            || !mc.player.onGround() || mc.player.getAbilities().flying || mc.player.isPassenger()
            || mc.player.isInWater() || mc.player.isInLava()
            || SafeWalkSupport.pausedForTweakerPath(waterNavigator != null || waterAscent != null || !underwaterEscapeBlocks.isEmpty() || oceanBreach != null || surfaceWaterHandoff || (surfacePath != null && surfacePath.isActive()),
                enderChestRecovery != null || (enderChestPath != null && enderChestPath.isActive()))) return false;

        return true;
    }

    public boolean shouldAutoSneak() {
        if (!shouldSafeWalk()) return false;
        AABB box = mc.player.getBoundingBox().inflate(-safeWalkEdgeDistance.get(), 0, -safeWalkEdgeDistance.get());
        AABB support = new AABB(box.minX, box.minY - mc.player.maxUpStep(), box.minZ, box.maxX, box.minY, box.maxZ);
        return mc.level.noCollision(mc.player, support);
    }

    public HitResult containerTarget(HitResult original) {
        if (!isActive() || !ghostHands.get() || mc.player == null || mc.level == null
            || mc.getCameraEntity() != mc.player || mc.player.isShiftKeyDown()) return original;

        Vec3 start = mc.player.getEyePosition(1.0f);
        double reach = mc.player.blockInteractionRange();
        Vec3 end = start.add(mc.player.getViewVector(1.0f).scale(reach));
        CollisionContext shapeContext = CollisionContext.of(mc.player);

        BlockHitResult hit = BlockGetter.traverseBlocks(start, end, mc.level, (world, mutablePos) -> {
            BlockEntity blockEntity = world.getBlockEntity(mutablePos);
            if (!(blockEntity instanceof Container && blockEntity instanceof MenuProvider)
                && !(blockEntity instanceof EnderChestBlockEntity)) return null;

            BlockPos pos = mutablePos.immutable();
            BlockHitResult result = world.getBlockState(pos).getShape(world, pos, shapeContext).clip(start, end, pos);
            return result != null && start.distanceToSqr(result.getLocation()) <= reach * reach ? result : null;
        }, world -> null);

        return hit == null ? original : hit;
    }

    private boolean shouldControlKeys() {
        if (!inventoryMove.get() || !mc.isWindowActive()
            || !(mc.screen instanceof AbstractContainerScreen<?> screen)
            || screen instanceof CreativeModeInventoryScreen) return false;

        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox textField && textField.isVisible() && textField.isFocused()) return false;
            if (child instanceof RecipeBookComponent recipeBook && recipeBook.isVisible()) {
                EditBox search = ((RecipeBookWidgetAccessor) recipeBook).tim$getSearchField();
                if (search != null && search.isFocused()) return false;
            }
        }
        return true;
    }

    private void releaseKeys() {
        boolean restorePhysicalState = mc.screen == null && mc.isWindowActive();
        updateMovementKeys(restorePhysicalState);
        releaseToggleKey(mc.options.keyShift, mc.options.toggleCrouch().get(), restorePhysicalState);
        releaseToggleKey(mc.options.keySprint, mc.options.toggleSprint().get(), restorePhysicalState);
        controllingKeys = false;
    }

    private void updateMovementKeys(boolean usePhysicalState) {
        updateKey(mc.options.keyUp, usePhysicalState);
        updateKey(mc.options.keyDown, usePhysicalState);
        updateKey(mc.options.keyLeft, usePhysicalState);
        updateKey(mc.options.keyRight, usePhysicalState);
        updateKey(mc.options.keyJump, usePhysicalState);
    }

    private void updateKey(KeyMapping binding, boolean usePhysicalState) {
        binding.setDown(usePhysicalState && isPhysicallyPressed(binding));
    }

    private boolean updateToggleKey(KeyMapping binding, boolean toggled, boolean wasDown) {
        boolean down = isPhysicallyPressed(binding);
        if (!toggled) binding.setDown(down);
        else if (down && !wasDown) binding.setDown(true);
        return down;
    }

    private void releaseToggleKey(KeyMapping binding, boolean toggled, boolean restorePhysicalState) {
        if (!toggled) updateKey(binding, restorePhysicalState);
        else if (!restorePhysicalState && binding.isDown()) binding.setDown(true);
    }

    private boolean isPhysicallyPressed(KeyMapping binding) {
        InputConstants.Key key = MeteorCompat.boundKey(binding);
        long window = mc.getWindow().handle();
        if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() != GLFW.GLFW_KEY_UNKNOWN)
            return GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.MOUSE)
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return false;
    }
}
