package com.example.addon.modules;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.example.addon.Tim;
import com.example.addon.mixin.InteractionAccessor;
import com.example.addon.utils.Hotbar;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Datamine extends Module {
    private static final double BREAK_THRESHOLD = 0.7;
    private static final double REACH = 6.0;
    private static final long RESTART_DELAY = 300;
    private static final long BURST_PAUSE = 275;
    private static final int BURST_COUNT = 22;
    private static final int FAKE_BLOCK_HEIGHT = 1024;

    public enum MiningMode { Packet, Normal }
    public enum SwapMode { Normal, Silent }
    public enum HighlightStyle { GLOW, SPECTRAL, PULSE }
    public enum NukerMode { Disabled, Tunnel, Hole, Excavator }
    public enum TunnelShape { x1_2, x2_2, x3_3, x4_4, x5_5 }
    public enum ExcavatorShape { Box, Sphere, Flat }
    public enum ListMode { Whitelist, Blacklist }

    private final SettingGroup sgMining = this.settings.getDefaultGroup();
    private final SettingGroup sgNuker = this.settings.createGroup("Nuker");
    private final SettingGroup sgTools = this.settings.createGroup("Tools");
    private final SettingGroup sgVanilla = this.settings.createGroup("Vanilla Bypass");
    private final SettingGroup sgCollect = this.settings.createGroup("Auto-Collect");
    private final SettingGroup sgVisuals = this.settings.createGroup("Visuals");

    private static Item optItem(String path) {
        Identifier id = Identifier.withDefaultNamespace(path);
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
    }

    private final Setting<MiningMode> miningMode = sgMining.add(new EnumSetting.Builder<MiningMode>()
        .name("mining-mode").defaultValue(MiningMode.Packet).build());
    private final Setting<Boolean> instantRemine = sgMining.add(new BoolSetting.Builder()
        .name("instant-remine").defaultValue(false).build());
    private final Setting<Integer> validationTicks = sgMining.add(new IntSetting.Builder()
        .name("validation-wait").defaultValue(5).min(1).sliderMax(10).build());
    private final Setting<Integer> maxRetries = sgMining.add(new IntSetting.Builder()
        .name("maximum-retries").defaultValue(1).min(0).sliderMax(2).build());
    private final Setting<Integer> retryCooldown = sgMining.add(new IntSetting.Builder()
        .name("retry-cooldown").defaultValue(6).min(1).sliderMax(12).build());

    private final Setting<NukerMode> nukerMode = sgNuker.add(new EnumSetting.Builder<NukerMode>()
        .name("nuker-mode").defaultValue(NukerMode.Disabled).build());
    private final Setting<TunnelShape> tunnelShape = sgNuker.add(new EnumSetting.Builder<TunnelShape>()
        .name("tunnel-shape").defaultValue(TunnelShape.x3_3).visible(() -> nukerMode.get() == NukerMode.Tunnel).build());
    private final Setting<ExcavatorShape> excavatorShape = sgNuker.add(new EnumSetting.Builder<ExcavatorShape>()
        .name("excavator-shape").defaultValue(ExcavatorShape.Box).visible(() -> nukerMode.get() == NukerMode.Excavator).build());
    private final Setting<Integer> excavatorRange = sgNuker.add(new IntSetting.Builder()
        .name("excavator-range").defaultValue(16).min(4).sliderMax(64).visible(() -> nukerMode.get() == NukerMode.Excavator).build());
    private final Setting<Boolean> excavatorIgnoreFloor = sgNuker.add(new BoolSetting.Builder()
        .name("ignore-floor").defaultValue(true).visible(() -> nukerMode.get() == NukerMode.Excavator).build());
    private final Setting<ListMode> listMode = sgNuker.add(new EnumSetting.Builder<ListMode>()
        .name("list-mode").defaultValue(ListMode.Whitelist).visible(() -> nukerMode.get() == NukerMode.Tunnel || nukerMode.get() == NukerMode.Hole).build());
    private final Setting<List<Block>> blockList = sgNuker.add(new BlockListSetting.Builder()
        .name("block-list").visible(() -> nukerMode.get() != NukerMode.Disabled).build());

    private final Setting<SwapMode> swapMode = sgTools.add(new EnumSetting.Builder<SwapMode>()
        .name("auto-swap").defaultValue(SwapMode.Normal).build());
    private final Setting<Boolean> silentSwing = sgTools.add(new BoolSetting.Builder()
        .name("silent-swing").defaultValue(false).build());
    private final Setting<Integer> toolSyncDelay = sgTools.add(new IntSetting.Builder()
        .name("tool-sync-delay").defaultValue(3).min(1).sliderMax(5).build());
    private final Setting<Boolean> durabilityProtection = sgTools.add(new BoolSetting.Builder()
        .name("durability-protection").defaultValue(true).build());
    private final Setting<Integer> durabilityThreshold = sgTools.add(new IntSetting.Builder()
        .name("durability-threshold").defaultValue(5).min(1).sliderMax(50).visible(durabilityProtection::get).build());

    private final Setting<Integer> vanilla = sgVanilla.add(new IntSetting.Builder()
        .name("vanilla-cutoff").defaultValue(1).min(0).sliderMax(5).build());

    private final Setting<Boolean> autoCollect = sgCollect.add(new BoolSetting.Builder()
        .name("auto-collect").defaultValue(false).build());
    private final Setting<Integer> collectRange = sgCollect.add(new IntSetting.Builder()
        .name("collect-range").defaultValue(16).min(4).sliderMax(64).visible(autoCollect::get).build());
    private final Setting<List<Item>> collectWhitelist = sgCollect.add(new ItemListSetting.Builder()
        .name("collect-whitelist").defaultValue(com.example.addon.utils.ItemLists.withoutMissingItems(
            optItem("netherite_spear"), optItem("diamond_spear"))).visible(autoCollect::get).build());
    private final Setting<Integer> collectDelay = sgCollect.add(new IntSetting.Builder()
        .name("collect-delay").defaultValue(10).min(0).sliderMax(40).visible(autoCollect::get).build());
    private final Setting<Integer> gracePeriod = sgCollect.add(new IntSetting.Builder()
        .name("collect-grace-period").defaultValue(5).min(1).sliderMax(15).visible(autoCollect::get).build());

    private final Setting<Boolean> render = sgVisuals.add(new BoolSetting.Builder().name("render").defaultValue(true).build());
    private final Setting<HighlightStyle> highlightStyle = sgVisuals.add(new EnumSetting.Builder<HighlightStyle>()
        .name("highlight-style").defaultValue(HighlightStyle.GLOW).visible(render::get).build());
    private final Setting<ShapeMode> shapeMode = sgVisuals.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).visible(() -> render.get() && highlightStyle.get() == HighlightStyle.GLOW).build());
    private final Setting<Integer> glowLayers = sgVisuals.add(new IntSetting.Builder().name("glow-layers").defaultValue(4).min(1).sliderMax(8).visible(() -> render.get() && (highlightStyle.get() == HighlightStyle.GLOW || highlightStyle.get() == HighlightStyle.PULSE)).build());
    private final Setting<Double> glowSpread = sgVisuals.add(new DoubleSetting.Builder().name("glow-spread").defaultValue(0.05).min(0.01).sliderMax(0.2).visible(() -> render.get() && (highlightStyle.get() == HighlightStyle.GLOW || highlightStyle.get() == HighlightStyle.PULSE)).build());
    private final Setting<Integer> glowBaseAlpha = sgVisuals.add(new IntSetting.Builder().name("glow-base-alpha").defaultValue(50).min(4).sliderMax(150).visible(() -> render.get() && highlightStyle.get() == HighlightStyle.GLOW).build());
    private final Setting<Double> pulseSpeed = sgVisuals.add(new DoubleSetting.Builder().name("pulse-speed").defaultValue(1.0).min(0.1).max(5.0).sliderMax(3.0).visible(() -> render.get() && highlightStyle.get() == HighlightStyle.PULSE).build());
    private final Setting<Integer> pulseMinAlpha = sgVisuals.add(new IntSetting.Builder().name("pulse-min-alpha").defaultValue(15).min(0).max(255).sliderMax(100).visible(() -> render.get() && highlightStyle.get() == HighlightStyle.PULSE).build());
    private final Setting<Integer> pulseMaxAlpha = sgVisuals.add(new IntSetting.Builder().name("pulse-max-alpha").defaultValue(220).min(15).max(255).sliderMax(255).visible(() -> render.get() && highlightStyle.get() == HighlightStyle.PULSE).build());
    private final Setting<Integer> spectralLineAlpha = sgVisuals.add(new IntSetting.Builder().name("spectral-line-alpha").defaultValue(255).min(0).sliderMax(255).visible(() -> render.get() && highlightStyle.get() == HighlightStyle.SPECTRAL).build());
    private final Setting<Integer> spectralFillAlpha = sgVisuals.add(new IntSetting.Builder().name("spectral-fill-alpha").defaultValue(15).min(0).sliderMax(255).visible(() -> render.get() && highlightStyle.get() == HighlightStyle.SPECTRAL).build());
    private final Setting<Double> spectralExpand = sgVisuals.add(new DoubleSetting.Builder().name("spectral-expand").defaultValue(0.05).min(0).sliderMax(0.5).visible(() -> render.get() && highlightStyle.get() == HighlightStyle.SPECTRAL).build());
    private final Setting<SettingColor> queueColor = sgVisuals.add(new ColorSetting.Builder().name("queue-color").defaultValue(new SettingColor(0, 200, 255, 200)).visible(render::get).build());
    private final Setting<SettingColor> primaryColor = sgVisuals.add(new ColorSetting.Builder().name("primary-color").defaultValue(new SettingColor(0, 255, 100, 255)).visible(render::get).build());
    private final Setting<SettingColor> secondaryColor = sgVisuals.add(new ColorSetting.Builder().name("secondary-color").defaultValue(new SettingColor(180, 0, 255, 200)).visible(render::get).build());

    private final Deque<Request> queue = new ArrayDeque<>();
    private final Deque<Retry> waiting = new ArrayDeque<>();
    private Target primary;
    private Target secondary;
    private Request last;
    private int tick = 0;
    private long ready = 0;
    private long stopped = 0;
    private boolean fast = false;
    private long lastMineTime = 0;
    private final Set<Integer> seenItems = new HashSet<>();
    private boolean sendingCustomPacket = false;
    private boolean swapped = false;
    private BlockPos excavatorTarget = null;
    private long lastReminePacketTime = 0;

    public Datamine() {
        super(Tim.CATEGORY, "datamine", "Queues blocks for fast packet mining with double break.");
    }

    @Override
    public void onActivate() {
        this.reset();
    }

    @Override
    public void onDeactivate() {
        if (this.primary != null && !this.primary.finished) {
            this.action(this.primary, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, this.primary.pos, this.primary.side);
        }
        if (this.secondary != null && !this.secondary.finished) {
            this.action(this.secondary, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, this.secondary.pos, this.secondary.side);
        }
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().forceCancel();
        }
        this.reset();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (this.miningMode.get() == MiningMode.Normal || sendingCustomPacket) return;
        if (event.packet instanceof ServerboundPlayerActionPacket packet) {
            ServerboundPlayerActionPacket.Action action = packet.getAction();
            if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK ||
                action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK ||
                action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
                if (this.isTracked(packet.getPos())) event.cancel();
                if (this.instantRemine.get() && this.last != null && packet.getPos().equals(this.last.pos) &&
                    this.mc.player != null && this.mc.level != null &&
                    this.breakable(this.last.pos, this.mc.level.getBlockState(this.last.pos))) {
                    event.cancel();
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null) return;
        if (this.mc.player.tickCount < 20) return; // World-load login safety guard

        this.tick++;
        if (this.nukerMode.get() != NukerMode.Disabled) this.doNuking();
        this.promote();
        this.clean();
        this.update(this.secondary);
        this.update(this.primary);
        this.remine();
        if (!this.remineReady()) this.fill();
        this.checkForNewItems();
        this.doAutoCollect();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!this.render.get()) return;
        for (Request request : this.queue) {
            this.renderBox(event, new AABB(request.pos), this.queueColor.get());
        }
        if (this.secondary != null) this.renderTarget(event, this.secondary, this.secondaryColor.get());
        if (this.primary != null) this.renderTarget(event, this.primary, this.primaryColor.get());
    }

    private void doNuking() {
        if (this.mc.player == null || this.mc.level == null) return;
        BlockPos basePos = this.mc.player.blockPosition();
        Direction facing = this.mc.player.getDirection();
        if (this.nukerMode.get() == NukerMode.Excavator) {
            this.doExcavating();
            return;
        }
        int queuedCount = 0;
        int maxQueuePerTick = 2;
        if (this.nukerMode.get() == NukerMode.Tunnel) {
            int width = 1, height = 2;
            TunnelShape shape = this.tunnelShape.get();
            if (shape == TunnelShape.x2_2) { width = 2; height = 2; }
            else if (shape == TunnelShape.x3_3) { width = 3; height = 3; }
            else if (shape == TunnelShape.x4_4) { width = 4; height = 4; }
            else if (shape == TunnelShape.x5_5) { width = 5; height = 5; }
            int halfW = width / 2;
            for (int y = 0; y < height && queuedCount < maxQueuePerTick; y++) {
                for (int x = 0; x < width && queuedCount < maxQueuePerTick; x++) {
                    BlockPos pos = this.getTunnelPos(basePos, facing, x - halfW, y);
                    if (this.tryQueueMine(pos)) queuedCount++;
                }
            }
        } else if (this.nukerMode.get() == NukerMode.Hole) {
            for (int x = -1; x <= 1 && queuedCount < maxQueuePerTick; x++) {
                for (int z = -1; z <= 1 && queuedCount < maxQueuePerTick; z++) {
                    BlockPos pos = basePos.offset(x, -1, z);
                    if (this.tryQueueMine(pos)) queuedCount++;
                }
            }
        }
    }

    private boolean tryQueueMine(BlockPos pos) {
        if (pos == null || this.isTracked(pos)) return false;
        BlockState state = this.mc.level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        if (!this.isBlockAllowed(state.getBlock()) || !this.breakable(pos, state)) return false;
        if (this.queue.size() >= 2) return false;
        this.mine(pos, this.face(pos, Direction.UP));
        return true;
    }

    private BlockPos getTunnelPos(BlockPos base, Direction facing, int offsetX, int offsetY) {
        if (facing == Direction.NORTH) return base.offset(offsetX, offsetY, -1);
        if (facing == Direction.SOUTH) return base.offset(offsetX, offsetY, 1);
        if (facing == Direction.WEST) return base.offset(-1, offsetY, offsetX);
        if (facing == Direction.EAST) return base.offset(1, offsetY, offsetX);
        return base;
    }

    private boolean isBlockAllowed(Block block) {
        if (blockList.get().isEmpty()) return listMode.get() == ListMode.Blacklist;
        return listMode.get() == ListMode.Whitelist ? blockList.get().contains(block) : !blockList.get().contains(block);
    }

    private void doExcavating() {
        if (this.primary != null || this.secondary != null || !this.queue.isEmpty()) {
            if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().forceCancel();
            }
            return;
        }
        if (this.excavatorTarget == null || !this.isExcavatable(this.excavatorTarget)) {
            this.excavatorTarget = this.findExcavatorTarget();
        }
        if (this.excavatorTarget == null) {
            if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().forceCancel();
            }
            return;
        }
        if (this.reachable(this.excavatorTarget)) {
            if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().forceCancel();
            }
            this.tryMineExcavator(this.excavatorTarget);
            this.excavatorTarget = null;
        } else {
            if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(this.excavatorTarget, 1));
            }
        }
    }

    private void tryMineExcavator(BlockPos pos) {
        if (pos == null || this.isTracked(pos)) return;
        BlockState state = this.mc.level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return;
        if (!this.isBlockAllowed(state.getBlock()) || !this.breakable(pos, state)) return;
        this.mine(pos, this.face(pos, Direction.UP));
    }

    private boolean isExcavatable(BlockPos pos) {
        if (pos == null) return false;
        if (this.excavatorIgnoreFloor.get() && this.mc.player.onGround() && pos.getY() < this.mc.player.blockPosition().getY()) return false;
        BlockState state = this.mc.level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(this.mc.level, pos) < 0.0F) return false;
        return !this.blockList.get().isEmpty() && this.blockList.get().contains(state.getBlock());
    }

    private BlockPos findExcavatorTarget() {
        BlockPos playerPos = this.mc.player.blockPosition();
        int range = this.excavatorRange.get();
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;
        int rangeSq = range * range;
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    if (this.excavatorShape.get() == ExcavatorShape.Sphere && x*x + y*y + z*z > rangeSq) continue;
                    if (this.excavatorShape.get() == ExcavatorShape.Flat && y != 0) continue;
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (this.isTracked(pos) || !this.isExcavatable(pos)) continue;
                    double dist = this.mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (dist < closestDist) { closestDist = dist; closest = pos.immutable(); }
                }
            }
        }
        return closest;
    }

    public boolean mine(BlockPos pos, Direction side) {
        if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null || pos == null || side == null) return false;
        pos = pos.immutable();
        if (this.instantRemine.get() && this.last != null && pos.equals(this.last.pos)) {
            if (this.remine() || this.remineReady()) return true;
        }
        if (this.isTracked(pos)) return true;
        BlockState state = this.mc.level.getBlockState(pos);
        if (!this.breakable(pos, state)) return false;
        this.queue.addLast(new Request(pos, side, 0));
        if (!this.remineReady()) this.fill();
        return true;
    }

    private void reset() {
        this.queue.clear();
        this.waiting.clear();
        this.primary = null;
        this.secondary = null;
        this.last = null;
        this.tick = 0;
        this.ready = 0;
        this.stopped = 0;
        this.fast = false;
        this.lastMineTime = 0;
        this.seenItems.clear();
        this.sendingCustomPacket = false;
        this.swapped = false;
        this.excavatorTarget = null;
        this.lastReminePacketTime = 0;
    }

    private void promote() {
        if (this.waiting.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Retry> iterator = this.waiting.iterator();
        while (iterator.hasNext()) {
            Retry retry = iterator.next();
            if (now < retry.ready) continue;
            iterator.remove();
            if (!this.breakable(retry.request.pos, this.mc.level.getBlockState(retry.request.pos))) continue;
            this.queue.addFirst(retry.request);
        }
    }

    private void clean() {
        this.queue.removeIf(r -> !this.breakable(r.pos, this.mc.level.getBlockState(r.pos)));
        this.waiting.removeIf(r -> !this.breakable(r.request.pos, this.mc.level.getBlockState(r.request.pos)));
    }

    private void fill() {
        if (this.queue.isEmpty()) return;
        if (this.primary == null) {
            if (!this.startable()) return;
            Target target = this.next();
            if (target != null) this.begin(target);
        }
        if (this.primary == null || this.secondary != null || this.queue.isEmpty() || !this.parkable()) return;
        Target target = this.next();
        if (target == null) return;
        this.park();
        this.begin(target);
    }

    private Target next() {
        while (!this.queue.isEmpty()) {
            Request request = this.queue.removeFirst();
            BlockState state = this.mc.level.getBlockState(request.pos);
            if (!this.breakable(request.pos, state)) continue;
            return new Target(request, state, this.face(request.pos, request.side));
        }
        return null;
    }

    private boolean startable() { return this.fast || System.currentTimeMillis() - this.stopped > RESTART_DELAY; }
    private boolean parkable() { return this.secondary == null && System.currentTimeMillis() >= this.ready && this.primary != null && !this.primary.arming && !this.primary.finished && !this.primary.instant && this.progress(this.primary) < 1.0; }

    private void park() {
        Target target = this.primary;
        this.advance(target);
        this.action(target, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, target.pos, target.side);
        this.secondary = target;
        this.primary = null;
    }

    private void remove(Target target, boolean confirmed) {
        if (target == this.primary) this.primary = null;
        if (target == this.secondary) {
            this.secondary = null;
            this.ready = System.currentTimeMillis() + (confirmed ? 50L : BURST_PAUSE);
        }
        if (target.arming && this.swapped) this.revertSlot();
    }

    public boolean isTracked(BlockPos pos) {
        if (this.primary != null && this.primary.pos.equals(pos)) return true;
        if (this.secondary != null && this.secondary.pos.equals(pos)) return true;
        for (Request r : this.queue) if (r.pos.equals(pos)) return true;
        for (Retry r : this.waiting) if (r.request.pos.equals(pos)) return true;
        return false;
    }

    public void onServerBlockUpdate(BlockPos pos, BlockState state) {
        if (pos == null || state == null) return;
        BlockPos immutablePos = pos.immutable();
        if (this.primary != null && this.primary.pos.equals(immutablePos)) {
            if (state.isAir()) this.confirm(this.primary);
            else if (!this.breakable(immutablePos, state)) this.remove(this.primary, false);
        }
        if (this.secondary != null && this.secondary.pos.equals(immutablePos)) {
            if (state.isAir()) this.confirm(this.secondary);
            else if (!this.breakable(immutablePos, state)) this.remove(this.secondary, false);
        }
        this.queue.removeIf(r -> r.pos.equals(immutablePos) && !this.breakable(immutablePos, state));
        this.waiting.removeIf(r -> r.request.pos.equals(immutablePos) && !this.breakable(immutablePos, state));
        if (this.last != null && this.last.pos.equals(immutablePos)) {
            if (this.instantRemine.get() && this.breakable(immutablePos, state)) this.remine();
        }
    }

    public boolean bypass(BlockPos pos) {
        if (this.mc.player == null || this.mc.level == null || pos == null || this.vanilla.get() <= 0 || this.isTracked(pos)) return false;
        BlockState state = this.mc.level.getBlockState(pos);
        if (!this.breakable(pos, state)) return false;
        float delta = state.getDestroyProgress(this.mc.player, this.mc.level, pos);
        return delta >= 1.0F / this.vanilla.get();
    }

    private void begin(Target target) {
        target.side = this.face(target.pos, target.side);
        target.slot = this.best(target.state, target.pos);
        this.primary = target;
        this.select(target.slot);
        if (this.swapped) {
            target.arming = true;
            target.arm = this.tick + this.toolSyncDelay.get();
            return;
        }
        this.start(target);
    }

    private void start(Target target) {
        long now = System.currentTimeMillis();
        target.arming = false;
        target.started = now;
        target.updated = now;
        target.delta = this.delta(target);
        target.work = Math.max(0.0, target.delta);
        target.instant = target.delta >= 1.0F;

        this.packet(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, target.pos, target.side);
        if (this.miningMode.get() == MiningMode.Packet && !target.instant) {
            this.packet(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, this.fake(target.pos), target.side);
        }

        if (this.silentSwing.get()) this.mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        else this.mc.player.swing(InteractionHand.MAIN_HAND);

        if (target.instant) this.finish(target);
        this.revertSlot();
    }

    private void update(Target target) {
        if (target == null) return;
        BlockState state = this.mc.level.getBlockState(target.pos);
        if (state.isAir()) { this.confirm(target); return; }
        if (!this.reachable(target.pos) || !state.equals(target.state)) { this.fail(target); return; }

        if (target.arming) {
            int slot = this.best(target.state, target.pos);
            if (slot != target.slot) {
                target.slot = slot;
                this.select(slot);
                if (!this.swapped) { this.start(target); return; }
                target.arm = this.tick + this.toolSyncDelay.get();
                return;
            }
            if (this.tick < target.arm) return;
            this.start(target);
            return;
        }

        if (target.finished) {
            int delay = target == this.primary ? this.validationTicks.get() : this.validationTicks.get() * 2;
            if (this.tick - target.finish >= delay) this.verify(target);
            return;
        }

        int slot = this.best(target.state, target.pos);
        if (slot != target.slot) target.slot = slot;
        this.advance(target);
        double progress = this.progress(target);
        long elapsed = System.currentTimeMillis() - target.started;

        if (this.miningMode.get() == MiningMode.Packet) {
            if (!target.burst && elapsed >= BURST_PAUSE && this.expected(target) >= BURST_PAUSE && progress < 1.0) {
                this.burst(target);
            }
        }
        if (progress >= 1.0) this.finish(target);
    }

    private void advance(Target target) {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - target.updated);
        target.delta = this.delta(target);
        if (elapsed > 0 && target.delta > 0.0F) target.work += target.delta * elapsed / 50.0;
        target.updated = now;
    }

    private void burst(Target target) {
        this.advance(target);
        target.side = this.face(target.pos, target.side);
        target.slot = this.best(target.state, target.pos);
        this.select(target.slot);
        BlockPos pos = this.fake(target.pos);
        for (int idx = 0; idx < BURST_COUNT; idx++) {
            this.packet(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, target.side);
        }
        target.burst = true;
        this.revertSlot();
    }

    private void finish(Target target) {
        if (target.finished) return;
        this.advance(target);
        target.finished = true;
        target.finish = this.tick;
        this.fast = target.burst;
        if (target == this.primary && !target.instant) {
            this.action(target, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, target.pos, target.side);
        }
        this.stopped = System.currentTimeMillis();
    }

    private void verify(Target target) {
        if (this.mc.level.getBlockState(target.pos).isAir()) this.confirm(target);
        else this.fail(target);
    }

    private void fail(Target target) {
        BlockState state = this.mc.level.getBlockState(target.pos);
        boolean reachable = this.reachable(target.pos);
        boolean identical = state.equals(target.state);
        target.side = this.face(target.pos, target.side);
        this.action(target, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, target.pos, target.side);
        this.remove(target, false);
        if (!reachable || !identical || target.retry >= this.maxRetries.get()) return;
        this.waiting.addLast(new Retry(new Request(target.pos, target.side, target.retry + 1), System.currentTimeMillis() + this.retryCooldown.get() * 50L));
    }

    private void confirm(Target target) {
        this.last = new Request(target.pos, this.face(target.pos, target.side), 0);
        this.remove(target, true);
    }

    private boolean remine() {
        if (!this.instantRemine.get() || this.last == null || this.primary != null || this.secondary != null) return false;
        BlockState state = this.mc.level.getBlockState(this.last.pos);
        if (!this.breakable(this.last.pos, state)) return false;

        long now = System.currentTimeMillis();
        if (now - this.lastReminePacketTime < 50L) return false;

        Direction side = this.face(this.last.pos, this.last.side);
        int slot = this.best(state, this.last.pos);
        this.select(slot);
        this.packet(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, this.last.pos, side);
        this.revertSlot();

        this.lastReminePacketTime = now;
        this.stopped = now;
        return true;
    }

    private boolean remineReady() {
        if (!this.instantRemine.get() || this.last == null || this.primary != null || this.secondary != null ||
            this.mc.player == null || this.mc.level == null) {
            return false;
        }
        return this.breakable(this.last.pos, this.mc.level.getBlockState(this.last.pos));
    }

    private double progress(Target target) {
        double limit = this.limit(target);
        return limit <= 0.0 ? 1.0 : Math.min(1.0, target.work / limit);
    }

    private long expected(Target target) {
        if (target.delta <= 0.0F) return Long.MAX_VALUE;
        return (long) Math.max(0.0, (this.limit(target) / target.delta - 1.0) * 50.0);
    }

    private double limit(Target target) { return target == this.primary ? BREAK_THRESHOLD : 1.0; }

    private float delta(Target target) {
        int selected = Hotbar.selected();
        Hotbar.set(target.slot);
        try {
            return target.state.getDestroyProgress(this.mc.player, this.mc.level, target.pos);
        } finally {
            Hotbar.set(selected);
        }
    }

    private double visual(Target target) {
        if (target.finished) return 1.0;
        double limit = this.limit(target);
        if (limit <= 0.0) return 1.0;
        double work = target.work;
        if (!target.arming && target.updated > 0 && target.delta > 0.0F) {
            work += target.delta * Math.max(0, System.currentTimeMillis() - target.updated) / 50.0;
        }
        return Math.min(1.0, work / limit);
    }

    private int best(BlockState state, BlockPos pos) {
        int selected = Hotbar.selected();
        int best = selected;
        float speed = -1.0F;
        boolean suitable = false;
        boolean required = state.requiresCorrectToolForDrops();
        try {
            for (int idx = 0; idx < 9; idx++) {
                ItemStack stack = Hotbar.stack(idx);
                if (this.durabilityProtection.get() && stack.isDamageableItem() && (stack.getMaxDamage() - stack.getDamageValue() <= this.durabilityThreshold.get())) continue;
                boolean good = stack.isCorrectToolForDrops(state);
                Hotbar.set(idx);
                float value = state.getDestroyProgress(this.mc.player, this.mc.level, pos);
                if (required && good != suitable) {
                    if (!good) continue;
                    best = idx; speed = value; suitable = true; continue;
                }
                if (value <= speed) continue;
                best = idx; speed = value; suitable = good;
            }
        } finally {
            Hotbar.set(selected);
        }
        return best;
    }

    private void action(Target target, ServerboundPlayerActionPacket.Action action, BlockPos pos, Direction side) {
        target.side = this.face(target.pos, target.side);
        target.slot = this.best(target.state, target.pos);
        this.select(target.slot);
        this.packet(action, pos, target.side);
        this.revertSlot();
    }

    private void select(int slot) {
        if (Hotbar.selected() == slot) { this.swapped = false; return; }
        this.swapped = true;
        if (this.swapMode.get() == SwapMode.Normal) Hotbar.set(slot);
        Hotbar.sync(slot);
    }

    private void revertSlot() {
        if (!this.swapped || this.swapMode.get() != SwapMode.Silent) return;
        Hotbar.sync(Hotbar.selected());
        this.swapped = false;
    }

    private void packet(ServerboundPlayerActionPacket.Action action, BlockPos pos, Direction side) {
        if (this.mc.level == null || this.mc.gameMode == null || this.mc.player == null) return;
        sendingCustomPacket = true;
        try {
            ((InteractionAccessor) this.mc.gameMode).Tim$sendSequencedPacket(
                this.mc.level,
                sequence -> new ServerboundPlayerActionPacket(action, pos, side, sequence)
            );
        } catch (Exception e) {
            Tim.LOG.error("[Datamine] Packet dispatch exception: " + e.getMessage());
        } finally {
            sendingCustomPacket = false;
        }
    }

    private BlockPos fake(BlockPos pos) { return new BlockPos(pos.getX(), FAKE_BLOCK_HEIGHT, pos.getZ()); }

    private Direction face(BlockPos pos, Direction fallback) {
        Vec3 eye = this.mc.player.getEyePosition();
        Direction best = fallback == null ? Direction.UP : fallback;
        double distance = Double.POSITIVE_INFINITY;
        for (Direction side : Direction.values()) {
            Vec3 point = this.point(pos, side);
            BlockHitResult hit = this.mc.level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mc.player));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) continue;
            double value = eye.distanceToSqr(point);
            if (value >= distance) continue;
            distance = value; best = hit.getDirection();
        }
        if (distance < Double.POSITIVE_INFINITY) return best;
        for (Direction side : Direction.values()) {
            Vec3 point = this.point(pos, side);
            double value = eye.distanceToSqr(point);
            if (value >= distance) continue;
            distance = value; best = side;
        }
        return best;
    }

    private Vec3 point(BlockPos pos, Direction side) {
        return new Vec3(pos.getX() + 0.5 + side.getStepX() * 0.49, pos.getY() + 0.5 + side.getStepY() * 0.49, pos.getZ() + 0.5 + side.getStepZ() * 0.49);
    }

    private boolean breakable(BlockPos pos, BlockState state) {
        return this.reachable(pos) && !state.isAir() && !(state.getBlock() instanceof LiquidBlock) && state.getDestroySpeed(this.mc.level, pos) >= 0.0F;
    }

    private boolean reachable(BlockPos pos) {
        Vec3 eye = this.mc.player.getEyePosition();
        double px = Math.max(pos.getX(), Math.min(eye.x, pos.getX() + 1.0));
        double py = Math.max(pos.getY(), Math.min(eye.y, pos.getY() + 1.0));
        double pz = Math.max(pos.getZ(), Math.min(eye.z, pos.getZ() + 1.0));
        double dx = px - eye.x, dy = py - eye.y, dz = pz - eye.z;
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }

    private void checkForNewItems() {
        if (!this.autoCollect.get() || this.mc.player == null || this.mc.level == null) return;
        boolean foundNew = false;
        List<ItemEntity> items = this.mc.level.getEntitiesOfClass(ItemEntity.class, this.mc.player.getBoundingBox().inflate(this.collectRange.get()), e -> this.collectWhitelist.get().isEmpty() || this.collectWhitelist.get().contains(e.getItem().getItem()));
        for (ItemEntity item : items) {
            if (this.seenItems.add(item.getId())) foundNew = true;
        }
        if (foundNew) this.lastMineTime = System.currentTimeMillis();
        this.seenItems.removeIf(id -> this.mc.level.getEntity(id) == null);
    }

    private void doAutoCollect() {
        if (!this.autoCollect.get() || this.mc.player == null || this.mc.level == null || Modules.get().isActive(PortalMaker.class)) return;
        long currentTime = System.currentTimeMillis();
        long elapsedMs = currentTime - this.lastMineTime;
        if (this.primary != null || this.secondary != null || !this.queue.isEmpty() || elapsedMs < this.collectDelay.get() * 50L || elapsedMs > this.gracePeriod.get() * 1000L) {
            if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().forceCancel();
            return;
        }
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) return;
        ItemEntity closestItem = null;
        double closestDist = this.collectRange.get() * this.collectRange.get();
        List<ItemEntity> items = this.mc.level.getEntitiesOfClass(ItemEntity.class, this.mc.player.getBoundingBox().inflate(this.collectRange.get()), e -> this.collectWhitelist.get().isEmpty() || this.collectWhitelist.get().contains(e.getItem().getItem()));
        for (ItemEntity item : items) {
            double dist = item.distanceToSqr(this.mc.player);
            if (dist < closestDist) { closestDist = dist; closestItem = item; }
        }
        if (closestItem != null) BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(closestItem.blockPosition()));
    }

    private void renderTarget(Render3DEvent event, Target target, SettingColor color) {
        double offset = (1.0 - this.visual(target)) / 2.0;
        this.renderBox(event, new AABB(target.pos.getX() + offset, target.pos.getY() + offset, target.pos.getZ() + offset, target.pos.getX() + 1.0 - offset, target.pos.getY() + 1.0 - offset, target.pos.getZ() + 1.0 - offset), color);
    }

    private void renderBox(Render3DEvent event, AABB box, SettingColor color) {
        if (this.highlightStyle.get() == HighlightStyle.SPECTRAL) {
            event.renderer.box(box.inflate(this.spectralExpand.get()), this.withAlpha(color, Math.max(4, color.a / 4)), this.withAlpha(color, this.spectralLineAlpha.get()), ShapeMode.Both, 0);
        } else if (this.highlightStyle.get() == HighlightStyle.GLOW) {
            this.renderGlowLayers(event, box, color);
            event.renderer.box(box, this.withAlpha(color, Math.max(4, color.a / 4)), color, this.shapeMode.get(), 0);
        } else if (this.highlightStyle.get() == HighlightStyle.PULSE) {
            this.renderPulseBox(event, box, color);
        }
    }

    private void renderGlowLayers(Render3DEvent event, AABB box, SettingColor color) {
        int layers = this.glowLayers.get();
        double spread = this.glowSpread.get();
        int baseAlpha = this.glowBaseAlpha.get();
        for (int i = layers; i >= 1; i--) {
            event.renderer.box(box.inflate(spread * i), this.withAlpha(color, Math.max(4, (int)(baseAlpha * (1.0 - (double)(i-1) / layers)))), this.withAlpha(color, 0), ShapeMode.Sides, 0);
        }
    }

    private float getPulseFactor() {
        return (float)((Math.sin(System.currentTimeMillis() / 1000.0 * this.pulseSpeed.get() * Math.PI * 2.0) + 1.0) * 0.5);
    }

    private void renderPulseBox(Render3DEvent event, AABB box, SettingColor color) {
        int pa = Math.min(255, Math.max(0, (int)(this.pulseMinAlpha.get() + (this.pulseMaxAlpha.get() - this.pulseMinAlpha.get()) * getPulseFactor())));
        SettingColor pColor = this.withAlpha(color, pa);
        int layers = this.glowLayers.get();
        double spread = this.glowSpread.get();
        for (int i = layers; i >= 1; i--) {
            event.renderer.box(box.inflate(spread * i), this.withAlpha(pColor, Math.max(4, (int)(pa * (1.0 - ((double)(i - 1) / layers) * 0.6)))), this.withAlpha(pColor, 0), ShapeMode.Sides, 0);
        }
        event.renderer.box(box, this.withAlpha(pColor, pa / 3), pColor, ShapeMode.Both, 0);
    }

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }

    private record Request(BlockPos pos, Direction side, int retry) {
        private Request { pos = pos.immutable(); }
    }
    private record Retry(Request request, long ready) {}
    private static class Target {
        private final BlockPos pos;
        private final BlockState state;
        private final int retry;
        private Direction side;
        private long started, updated;
        private float delta;
        private double work;
        private int slot, arm, finish;
        private boolean arming, burst, instant, finished;
        private Target(Request request, BlockState state, Direction side) {
            this.pos = request.pos; this.state = state; this.side = side; this.retry = request.retry;
        }
    }
}