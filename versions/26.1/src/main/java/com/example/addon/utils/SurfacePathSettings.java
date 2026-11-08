package com.example.addon.utils;

import baritone.api.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class SurfacePathSettings implements SurfacePathProcess.RouteSettings {
    public static final class TemporaryBooleanSetting {
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private boolean previous;

        public TemporaryBooleanSetting(BooleanSupplier get, Consumer<Boolean> set) {
            this.get = get;
            this.set = set;
        }

        public void apply() {
            previous = get.getAsBoolean();
            set.accept(false);
        }

        public boolean unchanged() {
            return !get.getAsBoolean();
        }

        public void restore() {
            if (!get.getAsBoolean()) set.accept(previous);
        }
    }

    private final Settings settings;
    private final boolean protectEnderChests;
    private final TemporaryBooleanSetting debugSettings;
    private final TemporaryBooleanSetting goalRenderSettings;
    private final TemporaryBooleanSetting blockRenderSettings;
    private boolean oldBreak;
    private boolean oldPlace;
    private boolean oldParkourPlace;
    private boolean appliedBreak;
    private boolean appliedPlace;
    private List<Block> oldBreakExceptions;
    private List<Block> disabledBreakExceptions;
    private List<Block> oldDisallowedBlocks;
    private List<Block> protectedBlocks;
    private List<Block> initialProtectedBlocks;

    public SurfacePathSettings(Settings settings) {
        this(settings, false);
    }

    public SurfacePathSettings(Settings settings, boolean protectEnderChests) {
        this.settings = settings;
        this.protectEnderChests = protectEnderChests;
        this.debugSettings = new TemporaryBooleanSetting(() -> settings.chatDebug.value, value -> settings.chatDebug.value = value);
        this.goalRenderSettings = new TemporaryBooleanSetting(() -> settings.renderGoal.value, value -> settings.renderGoal.value = value);
        this.blockRenderSettings = new TemporaryBooleanSetting(() -> settings.renderSelectionBoxes.value, value -> settings.renderSelectionBoxes.value = value);
    }

    @Override
    public void apply(boolean allowBreak, boolean allowPlace) {
        oldBreak = settings.allowBreak.value;
        oldPlace = settings.allowPlace.value;
        oldParkourPlace = settings.allowParkourPlace.value;
        oldBreakExceptions = settings.allowBreakAnyway.value;
        appliedBreak = allowBreak;
        appliedPlace = allowPlace;
        disabledBreakExceptions = allowBreak ? null : new ArrayList<>();
        settings.allowBreak.value = allowBreak;
        settings.allowPlace.value = allowPlace;
        debugSettings.apply();
        goalRenderSettings.apply();
        blockRenderSettings.apply();
        if (!allowPlace) settings.allowParkourPlace.value = false;
        if (!allowBreak) settings.allowBreakAnyway.value = disabledBreakExceptions;
        if (protectEnderChests) {
            oldDisallowedBlocks = settings.blocksToDisallowBreaking.value;
            protectedBlocks = new ArrayList<>(oldDisallowedBlocks);
            if (!protectedBlocks.contains(Blocks.ENDER_CHEST)) protectedBlocks.add(Blocks.ENDER_CHEST);
            initialProtectedBlocks = List.copyOf(protectedBlocks);
            settings.blocksToDisallowBreaking.value = protectedBlocks;
        }
    }

    @Override
    public boolean unchanged() {
        return settings.allowBreak.value == appliedBreak && settings.allowPlace.value == appliedPlace
            && debugSettings.unchanged()
            && goalRenderSettings.unchanged() && blockRenderSettings.unchanged()
            && (appliedPlace || !settings.allowParkourPlace.value)
            && (appliedBreak || (settings.allowBreakAnyway.value == disabledBreakExceptions && disabledBreakExceptions.isEmpty()))
            && (!protectEnderChests || (settings.blocksToDisallowBreaking.value == protectedBlocks
                && protectedBlocks.equals(initialProtectedBlocks)));
    }

    @Override
    public void restore() {
        if (settings.allowBreak.value == appliedBreak) settings.allowBreak.value = oldBreak;
        if (settings.allowPlace.value == appliedPlace) settings.allowPlace.value = oldPlace;
        debugSettings.restore();
        goalRenderSettings.restore();
        blockRenderSettings.restore();
        if (!appliedPlace && !settings.allowParkourPlace.value) settings.allowParkourPlace.value = oldParkourPlace;
        if (!appliedBreak && settings.allowBreakAnyway.value == disabledBreakExceptions && disabledBreakExceptions.isEmpty())
            settings.allowBreakAnyway.value = oldBreakExceptions;
        if (protectEnderChests && settings.blocksToDisallowBreaking.value == protectedBlocks) {
            if (protectedBlocks.equals(initialProtectedBlocks)) settings.blocksToDisallowBreaking.value = oldDisallowedBlocks;
        }
    }
}
