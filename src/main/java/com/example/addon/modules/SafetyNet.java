package com.example.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.DimensionTypes;

import com.example.addon.Tim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SafetyNet extends Module {

    public enum DimensionMode {
        Overworld, End, Both
    }

    public enum WarnSound {
        Pling, Bell, Anvil, Basedrum, Chime, Hat;

        public SoundEvent getSoundEvent() {
            return switch (this) {
                case Pling    -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
                case Bell     -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
                case Anvil    -> SoundEvents.BLOCK_ANVIL_LAND;
                case Basedrum -> SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value();
                case Chime    -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();
                case Hat      -> SoundEvents.BLOCK_NOTE_BLOCK_HAT.value();
            };
        }
    }

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgWarning    = settings.createGroup("Warning Ping");
    private final SettingGroup sgDisconnect = settings.createGroup("Auto Disconnect");
    private final SettingGroup sgOverworld  = settings.createGroup("Overworld Thresholds");
    private final SettingGroup sgEnd        = settings.createGroup("End Thresholds");
    private final SettingGroup sgGrace      = settings.createGroup("Grace Period");
    private final SettingGroup sgChorus     = settings.createGroup("Chorus Escape");
    private final SettingGroup sgPlatform   = settings.createGroup("Air Place Platform");

    private final Setting<DimensionMode> dimension = sgGeneral.add(new EnumSetting.Builder<DimensionMode>()
        .name("dimension")
        .description("Which dimension(s) to protect you in.")
        .defaultValue(DimensionMode.End)
        .build()
    );

    private final Setting<Boolean> perDimensionThresholds = sgGeneral.add(new BoolSetting.Builder()
        .name("per-dimension-thresholds")
        .description("Use separate warn and disconnect Y levels for the Overworld and End instead of shared values.")
        .defaultValue(false)
        .visible(() -> dimension.get() == DimensionMode.Both)
        .build()
    );

    private final Setting<Boolean> warnEnabled = sgWarning.add(new BoolSetting.Builder()
        .name("warn-enabled")
        .description("Play a sound and show a title warning when below the warning Y level.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> warnY = sgWarning.add(new IntSetting.Builder()
        .name("warn-y-level")
        .description("Y level at which the warning ping triggers. Used when sharing thresholds for both dimensions.")
        .defaultValue(0)
        .range(-128, 320)
        .sliderRange(-128, 320)
        .visible(() -> dimension.get() == DimensionMode.Both && !perDimensionThresholds.get())
        .build()
    );

    private final Setting<Integer> warnInterval = sgWarning.add(new IntSetting.Builder()
        .name("warn-interval")
        .description("How often the warning repeats while below the Y level, in ticks (20 = 1 second). Set to 0 to warn only once.")
        .defaultValue(40)
        .range(0, 200)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<WarnSound> warnSound = sgWarning.add(new EnumSetting.Builder<WarnSound>()
        .name("warn-sound")
        .description("The sound played when the warning triggers.")
        .defaultValue(WarnSound.Pling)
        .build()
    );

    private final Setting<Double> warnVolume = sgWarning.add(new DoubleSetting.Builder()
        .name("warn-volume")
        .description("Volume of the warning ping sound (0.0 = silent, 1.0 = full volume).")
        .defaultValue(1.0)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .build()
    );

    private final Setting<Boolean> disconnectEnabled = sgDisconnect.add(new BoolSetting.Builder()
        .name("disconnect-enabled")
        .description("Automatically disconnect when below the disconnect Y level.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> disconnectY = sgDisconnect.add(new IntSetting.Builder()
        .name("disconnect-y-level")
        .description("Y level at which auto-disconnect triggers. Used when sharing thresholds for both dimensions.")
        .defaultValue(-10)
        .range(-128, 320)
        .sliderRange(-128, 320)
        .visible(() -> dimension.get() == DimensionMode.Both && !perDimensionThresholds.get())
        .build()
    );

    private final Setting<Integer> overworldWarnY = sgOverworld.add(new IntSetting.Builder()
        .name("warn-y-level")
        .description("Overworld Y level at which the warning triggers.")
        .defaultValue(-60)
        .range(-128, 320)
        .sliderRange(-128, 320)
        .visible(() -> dimension.get() == DimensionMode.Overworld || (dimension.get() == DimensionMode.Both && perDimensionThresholds.get()))
        .build()
    );

    private final Setting<Integer> overworldDisconnectY = sgOverworld.add(new IntSetting.Builder()
        .name("disconnect-y-level")
        .description("Overworld Y level at which auto-disconnect triggers.")
        .defaultValue(-70)
        .range(-128, 320)
        .sliderRange(-128, 320)
        .visible(() -> dimension.get() == DimensionMode.Overworld || (dimension.get() == DimensionMode.Both && perDimensionThresholds.get()))
        .build()
    );

    private final Setting<Integer> endWarnY = sgEnd.add(new IntSetting.Builder()
        .name("warn-y-level")
        .description("End Y level at which the warning triggers.")
        .defaultValue(0)
        .range(-128, 320)
        .sliderRange(-128, 320)
        .visible(() -> dimension.get() == DimensionMode.End || (dimension.get() == DimensionMode.Both && perDimensionThresholds.get()))
        .build()
    );

    private final Setting<Integer> endDisconnectY = sgEnd.add(new IntSetting.Builder()
        .name("disconnect-y-level")
        .description("End Y level at which auto-disconnect triggers.")
        .defaultValue(-10)
        .range(-128, 320)
        .sliderRange(-128, 320)
        .visible(() -> dimension.get() == DimensionMode.End || (dimension.get() == DimensionMode.Both && perDimensionThresholds.get()))
        .build()
    );

    private final Setting<Boolean> graceEnabled = sgGrace.add(new BoolSetting.Builder()
        .name("grace-enabled")
        .description("Wait a set number of ticks below the threshold before firing warnings or disconnect. Prevents false triggers from lag spikes or brief knockback.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> graceTicks = sgGrace.add(new IntSetting.Builder()
        .name("grace-ticks")
        .description("How many consecutive ticks the player must be below the threshold before actions fire (20 = 1 second).")
        .defaultValue(10)
        .range(1, 100)
        .sliderRange(1, 60)
        .visible(graceEnabled::get)
        .build()
    );

    private final Setting<Keybind> chorusEscapeKey = sgChorus.add(new KeybindSetting.Builder()
        .name("chorus-escape-key")
        .description("Eats a chorus fruit to escape the void. Ignores warnings/disconnects while active and disables module on landing.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Boolean> platformEnabled = sgPlatform.add(new BoolSetting.Builder()
        .name("platform-enabled")
        .description("Attempt a 5x5 air-place platform below you when the warning Y level is reached.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> platformSlot = sgPlatform.add(new IntSetting.Builder()
        .name("hotbar-slot")
        .description("Hotbar slot containing solid, non-falling blocks for the platform (1-9).")
        .defaultValue(1).range(1, 9).sliderRange(1, 9)
        .visible(platformEnabled::get)
        .build()
    );

    private final Setting<Boolean> disconnectOnDamage = sgPlatform.add(new BoolSetting.Builder()
        .name("disconnect-on-damage")
        .description("Disconnect on damage while the platform safeguard is engaged, in addition to the Y-level disconnect.")
        .defaultValue(true)
        .visible(platformEnabled::get)
        .build()
    );

    private boolean hasDisconnected;
    private int     warnTickCounter;
    private int     graceTickCounter;
    
    private boolean chorusEscapeActive;
    private boolean hasTriggeredEat;
    private boolean wasChorusPressed;
    private boolean platformTriggered;
    private boolean platformBuilding;
    private int platformIndex;
    private int platformDelay;
    private int platformVerifyTicks;
    private int platformRetries;
    private final List<BlockPos> platformTargets = new ArrayList<>();
    private ClientWorld platformWorld;
    private ClientPlayerEntity platformPlayer;
    private float lastHealth;
    private float lastAbsorption;
    private int lastHurtTime;
    private boolean hasDamageBaseline;

    public SafetyNet() {
        super(Tim.CATEGORY, "Safety Net", "Protects you from the void by warning, disconnecting, or chorus teleporting at low Y levels.");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null) {
            mc.options.useKey.setPressed(false);
        }
        resetState();
    }

    private void resetState() {
        hasDisconnected    = false;
        warnTickCounter    = 0;
        graceTickCounter   = 0;
        chorusEscapeActive = false;
        hasTriggeredEat    = false;
        wasChorusPressed   = false;
        resetPlatform();
        platformTriggered = false;
        hasDamageBaseline = false;
        platformWorld = null;
        platformPlayer = null;
    }

    private void resetCounters() {
        warnTickCounter  = 0;
        graceTickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            resetPlatform();
            platformTriggered = false;
            hasDamageBaseline = false;
            platformWorld = null;
            platformPlayer = null;
            return;
        }

        if (platformWorld != mc.world || platformPlayer != mc.player) {
            resetPlatform();
            platformTriggered = false;
            hasDamageBaseline = false;
            platformWorld = mc.world;
            platformPlayer = mc.player;
        }
        float health = mc.player.getHealth();
        float absorption = mc.player.getAbsorptionAmount();
        int hurtTime = mc.player.hurtTime;
        boolean damaged = hasDamageBaseline && (health < lastHealth || absorption < lastAbsorption || hurtTime > lastHurtTime);
        lastHealth = health;
        lastAbsorption = absorption;
        lastHurtTime = hurtTime;
        hasDamageBaseline = true;

        handleChorusEscapeKeybind();
        if (chorusEscapeActive) {
            resetPlatform();
            platformTriggered = false;
            return;
        }

        if (!isInValidDimension()) {
            resetCounters();
            resetPlatform();
            platformTriggered = false;
            return;
        }

        int effectiveWarnY = getEffectiveWarnY();
        int effectiveDisconnectY = getEffectiveDisconnectY();

        validateThresholds(effectiveWarnY, effectiveDisconnectY);

        double playerY = mc.player.getY();
        boolean belowDisconnect = disconnectEnabled.get() && playerY < effectiveDisconnectY;
        boolean belowWarn = warnEnabled.get() && playerY < effectiveWarnY;
        boolean belowPlatformThreshold = platformEnabled.get() && playerY < effectiveWarnY;
        if (!platformEnabled.get()) {
            resetPlatform();
            platformTriggered = false;
        }
        if ((belowPlatformThreshold || platformBuilding) && platformEnabled.get()
            && disconnectOnDamage.get() && damaged && !hasDisconnected) {
            hasDisconnected = true;
            resetPlatform();
            mc.inGameHud.setTitle(Text.literal("§c§lSAFETY NET DISCONNECT"));
            mc.inGameHud.setSubtitle(Text.literal("§eDamage detected below the warning Y level"));
            warning("Safety Net disconnected: damage detected below the platform warning Y level.");
            com.example.addon.utils.MinecraftApiCompat.disconnect(mc);
            return;
        }
        boolean inDanger = belowDisconnect || belowWarn || belowPlatformThreshold;

        if (!handleGracePeriod(inDanger)) return;

        if (belowDisconnect) {
            if (!hasDisconnected) {
                hasDisconnected = true;
                executeDisconnect(playerY, effectiveDisconnectY);
            }
            return;
        } else {
            hasDisconnected = false;
        }

        if (belowPlatformThreshold && !platformTriggered) {
            platformTriggered = true;
            if (!platformBuilding) startPlatform();
        } else if (!belowPlatformThreshold) {
            platformTriggered = false;
        }
        if (platformBuilding) tickPlatform();

        if (belowWarn) {
            warnTickCounter++;
            int interval = warnInterval.get();
            boolean shouldWarn = (interval == 0) ? (warnTickCounter == 1) : (warnTickCounter % interval == 1);

            if (shouldWarn) {
                executeWarning(playerY, effectiveWarnY);
            }
        } else {
            warnTickCounter = 0;
        }
    }

    private void startPlatform() {
        if (mc.interactionManager == null || !mc.player.isAlive()) {
            warning("Air Place Platform could not start: the player or interaction manager is unavailable.");
            return;
        }
        BlockPos center = mc.player.getBlockPos().down();
        if (center.getY() < mc.world.getBottomY() || center.getY() >= mc.world.getBottomY() + mc.world.getHeight()) {
            warning("Air Place Platform cannot place outside the world's build height. Raise the warning Y level.");
            return;
        }
        if (!isPlatformBlockValid(center)) {
            warning("Air Place Platform needs a solid, non-falling block in hotbar slot %d.", platformSlot.get());
            return;
        }
        platformTargets.clear();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) platformTargets.add(center.add(x, 0, z));
        }
        platformTargets.sort(Comparator.comparingInt(pos ->
            Math.abs(pos.getX() - center.getX()) + Math.abs(pos.getZ() - center.getZ())));
        platformIndex = 0;
        platformDelay = 0;
        platformVerifyTicks = -1;
        platformRetries = 0;
        platformBuilding = true;
        warning("Air Place Platform started; server placement and survival are not guaranteed.");
    }

    private boolean isPlatformBlockValid(BlockPos pos) {
        if (!(mc.player.getInventory().getStack(platformSlot.get() - 1).getItem() instanceof BlockItem item)
            || item.getBlock() instanceof FallingBlock) return false;
        return item.getBlock().getDefaultState().isFullCube(mc.world, pos);
    }

    private void tickPlatform() {
        if (mc.interactionManager == null || !mc.player.isAlive()) {
            warning("Air Place Platform stopped: the player or interaction manager is unavailable.");
            resetPlatform();
            return;
        }
        if (platformDelay > 0) {
            platformDelay--;
            return;
        }
        while (platformIndex < platformTargets.size()) {
            BlockPos pos = platformTargets.get(platformIndex++);
            if (!mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                warning("Air Place Platform stopped: a target chunk is unloaded.");
                resetPlatform();
                return;
            }
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isReplaceable()) continue;
            if (!isPlatformBlockValid(pos)) {
                warning("Air Place Platform stopped: hotbar slot %d has no suitable blocks.", platformSlot.get());
                resetPlatform();
                return;
            }
            double reach = mc.player.getBlockInteractionRange();
            if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > reach * reach) {
                warning("Air Place Platform stopped: the remaining blocks are out of reach.");
                resetPlatform();
                return;
            }
            if (!InvUtils.swap(platformSlot.get() - 1, true)) {
                warning("Air Place Platform stopped: could not select the configured hotbar slot.");
                resetPlatform();
                return;
            }
            try {
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
                if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted())
                    mc.player.swingHand(Hand.MAIN_HAND);
            } finally {
                InvUtils.swapBack();
            }
            platformDelay = 1;
            return;
        }
        if (platformVerifyTicks < 0) {
            platformVerifyTicks = 5;
            return;
        }
        if (platformVerifyTicks-- > 0) return;
        long missing = platformTargets.stream().filter(pos ->
            !mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)
                || mc.world.getBlockState(pos).isReplaceable()).count();
        if (missing == 0) {
            info("Air Place Platform target spaces are occupied.");
            resetPlatform();
        } else if (platformRetries++ >= 3) {
            warning("Air Place Platform stopped with %d missing block(s).", missing);
            resetPlatform();
        } else {
            platformIndex = 0;
            platformVerifyTicks = -1;
        }
    }

    private void resetPlatform() {
        platformBuilding = false;
        platformTargets.clear();
        platformIndex = 0;
        platformDelay = 0;
        platformVerifyTicks = -1;
        platformRetries = 0;
    }

    private void handleChorusEscapeKeybind() {
        boolean chorusPressed = chorusEscapeKey.get().isPressed();
        if (chorusPressed && !wasChorusPressed && !chorusEscapeActive) {
            chorusEscapeActive = true;
            hasTriggeredEat = false;
        }
        wasChorusPressed = chorusPressed;

        if (!chorusEscapeActive) return;

        if (!hasTriggeredEat) {
            if (!selectHotbarItem(Items.CHORUS_FRUIT)) {
                warning("No Chorus Fruit found in hotbar! Falling back to normal Safety Net logic.");
                chorusEscapeActive = false;
            } else {
                mc.options.useKey.setPressed(true);
                hasTriggeredEat = true;
            }
        } else {
            if (!mc.player.isUsingItem()) {
                mc.options.useKey.setPressed(false);
                info("Chorus escape successful. Disabling Safety Net.");
                toggle();
            }
        }
    }

    private boolean isInValidDimension() {
        boolean inEnd = mc.world.getDimensionEntry().matchesKey(DimensionTypes.THE_END);
        boolean inOverworld = mc.world.getDimensionEntry().matchesKey(DimensionTypes.OVERWORLD);
        DimensionMode mode = dimension.get();

        if (mode == DimensionMode.Overworld && !inOverworld) return false;
        if (mode == DimensionMode.End && !inEnd) return false;
        if (mode == DimensionMode.Both && !inEnd && !inOverworld) return false;
        
        return true;
    }

    private int getEffectiveWarnY() {
        DimensionMode mode = dimension.get();
        boolean inEnd = mc.world.getDimensionEntry().matchesKey(DimensionTypes.THE_END);

        if (mode == DimensionMode.Overworld) return overworldWarnY.get();
        if (mode == DimensionMode.End) return endWarnY.get();

        if (perDimensionThresholds.get()) {
            return inEnd ? endWarnY.get() : overworldWarnY.get();
        }
        return warnY.get();
    }

    private int getEffectiveDisconnectY() {
        DimensionMode mode = dimension.get();
        boolean inEnd = mc.world.getDimensionEntry().matchesKey(DimensionTypes.THE_END);

        if (mode == DimensionMode.Overworld) return overworldDisconnectY.get();
        if (mode == DimensionMode.End) return endDisconnectY.get();

        if (perDimensionThresholds.get()) {
            return inEnd ? endDisconnectY.get() : overworldDisconnectY.get();
        }
        return disconnectY.get();
    }

    private void validateThresholds(int warnY, int disconnectY) {
        if (warnEnabled.get() && disconnectEnabled.get() && warnY <= disconnectY) {
            warning("⚠ Safety Net config issue: warn-y-level (" + warnY
                + ") is not above disconnect-y-level (" + disconnectY
                + "). You may not receive warnings before being disconnected!");
        }
    }

    private boolean handleGracePeriod(boolean inDanger) {
        if (inDanger && graceEnabled.get()) {
            graceTickCounter++;
            if (graceTickCounter < graceTicks.get()) {
                return false;
            }
        } else if (!inDanger) {
            graceTickCounter = 0;
        }
        return true;
    }

    private void executeDisconnect(double playerY, int targetDisconnectY) {
        mc.inGameHud.setTitle(Text.literal("§c§lSAFETY NET DISCONNECT"));
        mc.inGameHud.setSubtitle(Text.literal(
            "§eY: " + String.format("%.1f", playerY) + " §7is below §c" + targetDisconnectY
        ));

        info("§cDisconnected — Y §e" + String.format("%.1f", playerY)
            + " §cis below safe threshold §e(" + targetDisconnectY + ")§c.");

        com.example.addon.utils.MinecraftApiCompat.disconnect(mc);
    }

    private void executeWarning(double playerY, int targetWarnY) {
        mc.inGameHud.setTitle(Text.literal("§e§l⚠ VOID WARNING"));
        mc.inGameHud.setSubtitle(Text.literal(
            "§fY: §c" + String.format("%.1f", playerY) + "  §f| Safe above: §a" + targetWarnY
        ));

        warning("⚠ Safety Net: Below Y §c" + targetWarnY
            + "§r! Current Y: §c" + String.format("%.1f", playerY));

        mc.player.playSound(
            warnSound.get().getSoundEvent(),
            warnVolume.get().floatValue(),
            2.0f
        );
    }

    private boolean selectHotbarItem(Item item) {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                mc.player.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }
}