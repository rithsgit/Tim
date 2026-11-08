package com.example.addon.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.addon.Tim;
import com.example.addon.render.RenderCompat;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
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
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class LootLens extends Module {

    public enum RenderMode { GLOW, SPECTRAL, PULSE }
    public enum BeamStyle  { BOX, GUARDIAN }

    private final Map<BlockPos, StorageType>      containers                 = new HashMap<>();
    private final Set<BlockPos>                   inventoryCheckedContainers = new HashSet<>();
    private final Set<BlockPos>                   scannedByScanner           = new HashSet<>();
    private final Set<BlockPos>                   shulkerContainers          = new HashSet<>();
    private final Map<BlockPos, LootCounts>       checkedLootCounts           = new HashMap<>();

    private BlockPos lastOpenedContainer    = null;
    private boolean shulkerSummarySent = false;

    private String lastDimension = "";
    private static final int DIMENSION_CHANGE_COOLDOWN_TICKS = 40;
    private int dimensionChangeCooldown = 0;

    private static final int CLEANUP_INTERVAL = 40;
    private int cleanupTimer = 0;

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgStorage    = settings.createGroup("Storage");
    private final SettingGroup sgUtility    = settings.createGroup("Utility");
    private final SettingGroup sgDecorative = settings.createGroup("Decorative");
    private final SettingGroup sgBeam       = settings.createGroup("Beam");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range").description("Container detection range in blocks.")
        .defaultValue(128).min(16).max(512).sliderMin(32).sliderMax(256).build()
    );

    private final Setting<Boolean> notification = sgGeneral.add(new BoolSetting.Builder()
        .name("notification").description("Send chat messages and play sound when shulkers are found.")
        .defaultValue(true).build()
    );

    private final Setting<List<Item>> customItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("custom-items").description("Additional items to highlight in containers.")
        .defaultValue(List.of(Items.ENCHANTED_GOLDEN_APPLE, Items.ELYTRA)).build()
    );

    private final Setting<Boolean> ignoreEmptyShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-empty-shulkers")
        .description("Ignore empty shulker items found inside containers.")
        .defaultValue(true)
        .onChanged(ignored -> refreshCheckedLoot())
        .build()
    );

    private final Setting<Boolean> stealDumpButtons = sgGeneral.add(new BoolSetting.Builder()
        .name("steal-dump-buttons")
        .description("Show steal and dump buttons on container screens.")
        .defaultValue(true)
        .build()
    );

    private final Setting<RenderMode> renderMode = sgGeneral.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode")
        .description("GLOW = layered bloom boxes. SPECTRAL = subtle fill. PULSE = fading in/out highlight.")
        .defaultValue(RenderMode.GLOW).build()
    );

    private final Setting<Integer> glowLayers = sgGeneral.add(new IntSetting.Builder()
        .name("glow-layers").description("Number of bloom layers rendered around each container.")
        .defaultValue(4).min(1).sliderMax(8)
        .visible(() -> renderMode.get() == RenderMode.GLOW || renderMode.get() == RenderMode.PULSE).build()
    );

    private final Setting<Double> glowSpread = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-spread").description("How far each bloom layer expands outward (in blocks).")
        .defaultValue(0.04).min(0.01).sliderMax(0.15)
        .visible(() -> renderMode.get() == RenderMode.GLOW || renderMode.get() == RenderMode.PULSE).build()
    );

    private final Setting<Integer> glowBaseAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("glow-base-alpha").description("Alpha of the innermost glow layer (0-255).")
        .defaultValue(60).min(10).sliderMax(150)
        .visible(() -> renderMode.get() == RenderMode.GLOW).build()
    );

    private final Setting<Integer> spectralFillAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("spectral-fill-alpha")
        .description("Fill alpha for block containers in SPECTRAL mode (0 = invisible, 40 = subtle).")
        .defaultValue(40).min(0).max(200).sliderMax(120)
        .visible(() -> renderMode.get() == RenderMode.SPECTRAL).build()
    );

    private final Setting<Boolean> spectralOutline = sgGeneral.add(new BoolSetting.Builder()
        .name("spectral-outline").description("Draw a crisp outline around block containers in SPECTRAL mode.")
        .defaultValue(true).visible(() -> renderMode.get() == RenderMode.SPECTRAL).build()
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
        .defaultValue(220).min(0).max(255).sliderMax(255)
        .visible(() -> renderMode.get() == RenderMode.PULSE).build()
    );

    private final Setting<Boolean> pulseBeams = sgGeneral.add(new BoolSetting.Builder()
        .name("pulse-beams")
        .description("Also pulse the beam opacity in sync with the highlights.")
        .defaultValue(true)
        .visible(() -> renderMode.get() == RenderMode.PULSE).build()
    );

    private final Setting<BeamStyle> beamStyle = sgBeam.add(new EnumSetting.Builder<BeamStyle>()
        .name("beam-style")
        .description("BOX = simple axis-aligned box beam. GUARDIAN = spinning guardian-style beam.")
        .defaultValue(BeamStyle.GUARDIAN).build()
    );

    private final Setting<Integer> beamWidth = sgBeam.add(new IntSetting.Builder()
        .name("beam-width").description("Box beam width (in hundredths of a block).")
        .defaultValue(15).min(5).max(50).sliderMin(5).sliderMax(50)
        .visible(() -> beamStyle.get() == BeamStyle.BOX).build()
    );

    private final Setting<Boolean> mergeBeams = sgBeam.add(new BoolSetting.Builder()
        .name("merge-beams").description("Merge beams for nearby shulker containers to reduce clutter.")
        .defaultValue(true).build()
    );

    private final Setting<Double> mergeDistance = sgBeam.add(new DoubleSetting.Builder()
        .name("merge-distance").description("Distance within which beams are merged.")
        .defaultValue(2.0).min(0).sliderMax(10).visible(mergeBeams::get).build()
    );

    private final Setting<Double> guardianBeamRadius = sgBeam.add(new DoubleSetting.Builder()
        .name("guardian-radius")
        .description("Radius of the guardian beam strands from centre (blocks).")
        .defaultValue(0.08).min(0.01).max(0.6).sliderMax(0.3)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN).build()
    );

    private final Setting<Integer> guardianStrands = sgBeam.add(new IntSetting.Builder()
        .name("guardian-strands")
        .description("Number of spinning flat quads that make up the beam (2-8).")
        .defaultValue(4).min(2).max(8).sliderMax(8)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN).build()
    );

    private final Setting<Double> guardianSpinSpeed = sgBeam.add(new DoubleSetting.Builder()
        .name("guardian-spin-speed")
        .description("How fast the beam rotates. 1.0 = one full revolution every ~6 seconds.")
        .defaultValue(1.0).min(0.1).max(5.0).sliderMax(3.0)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN).build()
    );

    private final Setting<Integer> guardianCoreAlpha = sgBeam.add(new IntSetting.Builder()
        .name("guardian-core-alpha")
        .description("Alpha of the solid centre core of the guardian beam (0 = no core).")
        .defaultValue(90).min(0).max(255).sliderMax(200)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN).build()
    );

    private final Setting<Integer> guardianStrandAlpha = sgBeam.add(new IntSetting.Builder()
        .name("guardian-strand-alpha")
        .description("Alpha of the outer spinning strands.")
        .defaultValue(160).min(10).max(255).sliderMax(255)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN).build()
    );

    private final Setting<Boolean> guardianGlow = sgBeam.add(new BoolSetting.Builder()
        .name("guardian-glow")
        .description("Add a soft bloom halo around the guardian beam.")
        .defaultValue(true)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN).build()
    );

    private final Setting<Double> guardianGlowRadius = sgBeam.add(new DoubleSetting.Builder()
        .name("guardian-glow-radius")
        .description("Radius of the bloom halo around the guardian beam.")
        .defaultValue(0.18).min(0.02).max(1.0).sliderMax(0.5)
        .visible(() -> beamStyle.get() == BeamStyle.GUARDIAN && guardianGlow.get()).build()
    );

    private final Setting<Boolean> scanChests = sgStorage.add(new BoolSetting.Builder()
        .name("chests").description("Detect chests and trapped chests.")
        .defaultValue(true)
        .onChanged(v -> {
            if (!v) {
                removeContainersOfType(StorageType.CHEST);
                removeContainersOfType(StorageType.TRAPPED_CHEST);
            }
        }).build()
    );
    private final Setting<SettingColor> chestColor = sgStorage.add(new ColorSetting.Builder()
        .name("chest-color").defaultValue(new SettingColor(255, 215, 0, 200))
        .visible(scanChests::get).build()
    );

    private final Setting<Boolean> scanBarrels = sgStorage.add(new BoolSetting.Builder()
        .name("barrels").description("Detect barrels.").defaultValue(true)
        .onChanged(v -> { if (!v) removeContainersOfType(StorageType.BARREL); }).build()
    );
    private final Setting<SettingColor> barrelColor = sgStorage.add(new ColorSetting.Builder()
        .name("barrel-color").defaultValue(new SettingColor(139, 69, 19, 200))
        .visible(scanBarrels::get).build()
    );

    private final Setting<Boolean> scanShulkerBoxes = sgStorage.add(new BoolSetting.Builder()
        .name("shulker-boxes").description("Detect shulker boxes placed in the world.").defaultValue(true)
        .onChanged(v -> { if (!v) removeContainersOfType(StorageType.SHULKER_BOX); }).build()
    );
    private final Setting<SettingColor> shulkerBoxColor = sgStorage.add(new ColorSetting.Builder()
        .name("shulker-box-color").defaultValue(new SettingColor(160, 32, 240, 200))
        .visible(scanShulkerBoxes::get).build()
    );

    private final Setting<Boolean> scanEnderChests = sgStorage.add(new BoolSetting.Builder()
        .name("ender-chests").description("Detect ender chests.").defaultValue(true)
        .onChanged(v -> { if (!v) removeContainersOfType(StorageType.ENDER_CHEST); }).build()
    );
    private final Setting<SettingColor> enderChestColor = sgStorage.add(new ColorSetting.Builder()
        .name("ender-chest-color").defaultValue(new SettingColor(75, 0, 130, 200))
        .visible(scanEnderChests::get).build()
    );

    private final Setting<SettingColor> shulkerFoundColor = sgStorage.add(new ColorSetting.Builder()
        .name("shulker-found-color").description("Bright color for chests/barrels confirmed to hold shulkers or custom items.")
        .defaultValue(new SettingColor(0, 255, 80, 255)).build()
    );

    private final Setting<Boolean> scanUtility = sgUtility.add(new BoolSetting.Builder()
        .name("utility-blocks")
        .description("Detect utility containers: furnaces, blast furnaces, smokers, hoppers, dispensers, and droppers.")
        .defaultValue(true)
        .onChanged(v -> { if (!v) removeContainersOfType(StorageType.UTILITY); }).build()
    );
    private final Setting<SettingColor> utilityColor = sgUtility.add(new ColorSetting.Builder()
        .name("utility-color")
        .defaultValue(new SettingColor(150, 150, 150, 200))
        .visible(scanUtility::get).build()
    );

    private final Setting<Boolean> scanDecorative = sgDecorative.add(new BoolSetting.Builder()
        .name("decorative-blocks")
        .description("Detect decorative containers: brewing stands, crafters, chiseled bookshelves, and decorated pots.")
        .defaultValue(true)
        .onChanged(v -> { if (!v) removeContainersOfType(StorageType.DECORATIVE); }).build()
    );
    private final Setting<SettingColor> decorativeColor = sgDecorative.add(new ColorSetting.Builder()
        .name("decorative-color")
        .defaultValue(new SettingColor(180, 100, 220, 200))
        .visible(scanDecorative::get).build()
    );

    public LootLens() {
        super(Tim.CATEGORY, "loot-lens", "Highlights storage containers confirmed to hold shulkers or custom items.");
    }

    @Override
    public void onActivate() {
        clearAllState();
        if (mc.player != null && mc.level != null && mc.level.dimension() != null)
            lastDimension = mc.level.dimension().identifier().toString();
    }

    @Override
    public void onDeactivate() { clearAllState(); }

    private void clearAllState() {
        containers.clear(); inventoryCheckedContainers.clear(); scannedByScanner.clear();
        shulkerContainers.clear(); checkedLootCounts.clear();
        lastOpenedContainer = null; shulkerSummarySent = false; cleanupTimer = 0;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        try { if (mc.level.dimension() == null) return; } catch (Exception e) { return; }
        if (dimensionChangeCooldown > 0) { dimensionChangeCooldown--; return; }
        try {
            String currDim = mc.level.dimension().identifier().toString();
            if (!currDim.equals(lastDimension)) {
                dimensionChangeCooldown = DIMENSION_CHANGE_COOLDOWN_TICKS;
                lastDimension = currDim; clearAllState(); return;
            }
        } catch (Exception ignored) { return; }
        if (++cleanupTimer >= CLEANUP_INTERVAL) { cleanupTimer = 0; cleanupDistantContainers(); }
        BlockPos currentPos = mc.player.blockPosition();
        scanBlockEntities(currentPos.getX() >> 4, currentPos.getZ() >> 4);
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof AbstractContainerScreen<?>
                && !(mc.screen instanceof InventoryScreen)
                && lastOpenedContainer != null) {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
            if (containers.containsKey(lastOpenedContainer) || shulkerContainers.contains(lastOpenedContainer)
                    || inventoryCheckedContainers.contains(lastOpenedContainer)) {
                checkScreenInventoryForShulkers(screen);
            }
        }
        if (mc.screen == null && lastOpenedContainer != null) {
            lastOpenedContainer = null;
            shulkerSummarySent = false;
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (event.screen instanceof InventoryScreen) return;
        shulkerSummarySent = false;
        HitResult hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK)
            lastOpenedContainer = ((BlockHitResult) hitResult).getBlockPos();
    }

    public void setLastInteractedPos(BlockPos pos) { lastOpenedContainer = pos; }

    private boolean isImmediateHighlight(StorageType type) {
        return switch (type) {
            case SHULKER_BOX, ENDER_CHEST, UTILITY, DECORATIVE -> true;
            case CHEST, TRAPPED_CHEST, BARREL -> false;
        };
    }

    private void refreshCheckedLoot() {
        for (Map.Entry<BlockPos, LootCounts> entry : checkedLootCounts.entrySet()) {
            int count = ignoreEmptyShulkers.get() ? entry.getValue().nonEmpty() : entry.getValue().all();
            if (count > 0) shulkerContainers.add(entry.getKey());
            else shulkerContainers.remove(entry.getKey());
        }
    }

    private void checkScreenInventoryForShulkers(AbstractContainerScreen<?> screen) {
        if (lastOpenedContainer == null) return;
        if (mc.level != null && mc.level.getBlockState(lastOpenedContainer).getBlock() == Blocks.ENDER_CHEST) return;
        AbstractContainerMenu handler    = screen.getMenu();
        int playerInventoryStart = handler.slots.size() - 36;
        int shulkerCount         = 0;
        int nonEmptyCount        = 0;
        Map<String, Integer> contentsSummary = new LinkedHashMap<>();
        boolean previouslyHad    = shulkerContainers.contains(lastOpenedContainer);
        for (int i = 0; i < playerInventoryStart; i++) {
            Slot slot = handler.slots.get(i); ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            boolean isShulker = stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
            if (isShulker) {
                shulkerCount++;
                ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
                if (contents != null) {
                    boolean hasContents = false;
                    for (ItemStack item : contents.nonEmptyItemCopyStream().toList()) {
                        hasContents = true;
                        contentsSummary.merge(item.getHoverName().getString(), item.getCount(), Integer::sum);
                    }
                    if (hasContents) nonEmptyCount++;
                }
            } else if (customItems.get().contains(stack.getItem())) {
                shulkerCount++;
                nonEmptyCount++;
            }
        }
        StorageType type = containers.get(lastOpenedContainer);
        if (type != null && isImmediateHighlight(type)) return;

        inventoryCheckedContainers.add(lastOpenedContainer);

        BlockPos adjacentChest = findAdjacentChest(lastOpenedContainer, false);
        if (adjacentChest != null) inventoryCheckedContainers.add(adjacentChest);

        LootCounts counts = new LootCounts(shulkerCount, nonEmptyCount);
        checkedLootCounts.put(lastOpenedContainer, counts);
        if (adjacentChest != null) checkedLootCounts.put(adjacentChest, counts);
        shulkerCount = ignoreEmptyShulkers.get() ? nonEmptyCount : shulkerCount;
        if (shulkerCount > 0) {
            shulkerContainers.add(lastOpenedContainer);
            if (adjacentChest != null) shulkerContainers.add(adjacentChest);
            if (!previouslyHad && notification.get()) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                info("%d %s found!", shulkerCount, shulkerCount == 1 ? "item" : "items");
            }
        } else {
            containers.remove(lastOpenedContainer);
            shulkerContainers.remove(lastOpenedContainer);
            if (adjacentChest != null) { containers.remove(adjacentChest); shulkerContainers.remove(adjacentChest); }
            if (previouslyHad && notification.get()) info("0 items found, removing highlight.");
        }
        if (!shulkerSummarySent && notification.get() && !contentsSummary.isEmpty()) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : contentsSummary.entrySet()) {
                if (entries.size() == 5) break;
                entries.add(entry.getValue() + "x " + entry.getKey());
            }
            int remaining = contentsSummary.size() - entries.size();
            info("Shulker contents: %s%s", String.join(", ", entries),
                remaining > 0 ? " (+" + remaining + " more)" : "");
            shulkerSummarySent = true;
        }
    }

    private void scanBlockEntities(int centerChunkX, int centerChunkZ) {
        int rangeBlocks  = range.get();
        int chunkRange   = (rangeBlocks >> 4) + 1;
        int chunkRangeSq = chunkRange * chunkRange;
        int maxDistSq    = rangeBlocks * rangeBlocks;
        BlockPos playerPos = mc.player.blockPosition();
        for (int cx = centerChunkX - chunkRange; cx <= centerChunkX + chunkRange; cx++) {
            for (int cz = centerChunkZ - chunkRange; cz <= centerChunkZ + chunkRange; cz++) {
                int dx = cx - centerChunkX, dz = cz - centerChunkZ;
                if (dx * dx + dz * dz > chunkRangeSq) continue;
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (pos.distSqr(playerPos) > maxDistSq) continue;
                    if (scannedByScanner.contains(pos)
                            && !shulkerContainers.contains(pos)
                            && !inventoryCheckedContainers.contains(pos)) continue;
                    Block block = mc.level.getBlockState(pos).getBlock();
                    StorageType type = classifyBlock(block);
                    if (type != null) { containers.put(pos, type); scannedByScanner.add(pos); }
                }
            }
        }
    }

    private StorageType classifyBlock(Block block) {
        if (block == Blocks.CHEST         && scanChests.get())        return StorageType.CHEST;
        if (block == Blocks.TRAPPED_CHEST && scanChests.get())        return StorageType.TRAPPED_CHEST;
        if (block == Blocks.BARREL        && scanBarrels.get())       return StorageType.BARREL;
        if (block == Blocks.ENDER_CHEST   && scanEnderChests.get())   return StorageType.ENDER_CHEST;
        if (block instanceof ShulkerBoxBlock && scanShulkerBoxes.get()) return StorageType.SHULKER_BOX;
        if (scanUtility.get() && (block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER
                || block == Blocks.HOPPER
                || block == Blocks.DISPENSER
                || block == Blocks.DROPPER))                          return StorageType.UTILITY;
        if (scanDecorative.get() && (block == Blocks.BREWING_STAND
                || block == Blocks.CRAFTER
                || block == Blocks.CHISELED_BOOKSHELF
                || block == Blocks.DECORATED_POT))                    return StorageType.DECORATIVE;
        return null;
    }

    private BlockPos findAdjacentChest(BlockPos pos, boolean checkContainers) {
        if (mc.level == null) return null;
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return null;
        try {
            ChestType chestType = state.getValue(ChestBlock.TYPE);
            if (chestType == ChestType.SINGLE) return null;
            Direction facing      = state.getValue(ChestBlock.FACING);
            Direction neighborDir = chestType == ChestType.LEFT
                ? facing.getClockWise() : facing.getCounterClockWise();
            BlockPos   neighborPos   = pos.relative(neighborDir);
            BlockState neighborState = mc.level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof ChestBlock)) return null;
            ChestType  neighborType   = neighborState.getValue(ChestBlock.TYPE);
            Direction  neighborFacing = neighborState.getValue(ChestBlock.FACING);
            if (neighborFacing != facing || neighborType == ChestType.SINGLE || neighborType == chestType) return null;
            if (checkContainers && !containers.containsKey(neighborPos)) return null;
            return neighborPos;
        } catch (Exception ignored) { return null; }
    }

    private void removeContainersOfType(StorageType type) {
        containers.entrySet().removeIf(entry -> {
            if (entry.getValue() != type) return false;
            BlockPos pos = entry.getKey();
            inventoryCheckedContainers.remove(pos); scannedByScanner.remove(pos);
            shulkerContainers.remove(pos); checkedLootCounts.remove(pos);
            return true;
        });
    }

    private void cleanupDistantContainers() {
        if (mc.player == null) return;
        BlockPos playerPos = mc.player.blockPosition();
        int cleanupRange   = range.get() + (range.get() >> 1);
        int cleanupRangeSq = cleanupRange * cleanupRange;

        containers.entrySet().removeIf(entry -> {
            if (entry.getKey().distSqr(playerPos) <= cleanupRangeSq) return false;
            BlockPos pos = entry.getKey();
            inventoryCheckedContainers.remove(pos); scannedByScanner.remove(pos);
            shulkerContainers.remove(pos); checkedLootCounts.remove(pos);
            return true;
        });
        checkedLootCounts.keySet().removeIf(pos -> pos.distSqr(playerPos) > cleanupRangeSq);
        inventoryCheckedContainers.removeIf(pos -> pos.distSqr(playerPos) > cleanupRangeSq);
        scannedByScanner.removeIf(pos -> pos.distSqr(playerPos) > cleanupRangeSq);
        shulkerContainers.removeIf(pos -> pos.distSqr(playerPos) > cleanupRangeSq);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        boolean isSpectral = renderMode.get() == RenderMode.SPECTRAL;
        boolean isPulse    = renderMode.get() == RenderMode.PULSE;
        Set<BlockPos>  toRemove             = new HashSet<>();
        Set<BlockPos>  renderedDoubleChests = new HashSet<>();
        List<BeamData> beamsToRender        = new ArrayList<>();

        for (Map.Entry<BlockPos, StorageType> entry : containers.entrySet()) {
            BlockPos    pos  = entry.getKey();
            StorageType type = entry.getValue();

            boolean shouldRender = isImmediateHighlight(type) || shulkerContainers.contains(pos);
            if (!shouldRender) continue;

            if (renderedDoubleChests.contains(pos)) continue;

            AABB renderBox;
            SettingColor baseColor;

            BlockState currentState = mc.level.getBlockState(pos);
            if (!validateBlockType(currentState.getBlock(), type)) { toRemove.add(pos); continue; }
            BlockPos adjacentPos = findAdjacentChest(pos, true);
            if (adjacentPos != null) {
                renderBox = createPaddedDoubleChestBox(pos, adjacentPos);
                renderedDoubleChests.add(adjacentPos);
            } else if (type == StorageType.SHULKER_BOX) {
                renderBox = createShulkerBox(pos, currentState);
            } else {
                renderBox = createPaddedBox(pos);
            }
            baseColor = isImmediateHighlight(type) ? getColor(type) : shulkerFoundColor.get();

            if (isSpectral) {
                int fillAlpha = spectralFillAlpha.get();
                int lineAlpha = (!spectralOutline.get()) ? 0 : baseColor.a;
                event.renderer.box(renderBox, withAlpha(baseColor, fillAlpha), withAlpha(baseColor, lineAlpha),
                    spectralOutline.get() ? ShapeMode.Both : ShapeMode.Sides, 0);
            } else if (isPulse) {
                renderPulseBox(event, renderBox, baseColor);
            } else {
                renderGlowLayers(event, renderBox, baseColor);
                event.renderer.box(renderBox, withAlpha(baseColor, 0), baseColor, ShapeMode.Lines, 0);
            }

            boolean shouldBeam = type != StorageType.UTILITY && type != StorageType.DECORATIVE && type != StorageType.ENDER_CHEST;

            if (shouldBeam) {
                SettingColor beamColor = (isPulse && pulseBeams.get()) ? pulseColor(baseColor) : baseColor;
                beamsToRender.add(new BeamData(renderBox, beamColor));
            }
        }

        renderBeams(event, beamsToRender);

        if (!toRemove.isEmpty()) {
            for (BlockPos removePos : toRemove) {
                containers.remove(removePos); inventoryCheckedContainers.remove(removePos);
                scannedByScanner.remove(removePos); shulkerContainers.remove(removePos);
                checkedLootCounts.remove(removePos);
            }
        }
    }

    private void renderGlowLayers(Render3DEvent event, AABB box, SettingColor color) {
        int layers = glowLayers.get(); double spread = glowSpread.get(); int baseAlpha = glowBaseAlpha.get();
        for (int i = layers; i >= 1; i--) {
            double expansion = spread * i; double t = (double)(i - 1) / layers;
            int layerAlpha = Math.max(4, (int)(baseAlpha * (1.0 - t * t)));
            event.renderer.box(box.inflate(expansion), withAlpha(color, layerAlpha),
                withAlpha(color, 0), ShapeMode.Sides, 0);
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

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }

    private AABB createPaddedBox(BlockPos pos) {
        double p = 0.0625;
        return new AABB(pos.getX()+p, pos.getY()+p, pos.getZ()+p,
                       pos.getX()+1-p, pos.getY()+1-p, pos.getZ()+1-p);
    }

    private AABB createShulkerBox(BlockPos pos, BlockState state) {
        try {
            AABB shape = state.getShape(mc.level, pos).bounds(); double p = 0.5 / 16.0;
            return new AABB(pos.getX()+shape.minX-p, pos.getY()+shape.minY-p, pos.getZ()+shape.minZ-p,
                           pos.getX()+shape.maxX+p, pos.getY()+shape.maxY+p, pos.getZ()+shape.maxZ+p);
        } catch (Exception ignored) { return createPaddedBox(pos); }
    }

    private AABB createPaddedDoubleChestBox(BlockPos pos1, BlockPos pos2) {
        double p = 0.0625;
        double minX = Math.min(pos1.getX(), pos2.getX()), minY = Math.min(pos1.getY(), pos2.getY()),
               minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxX = Math.max(pos1.getX(), pos2.getX())+1, maxY = Math.max(pos1.getY(), pos2.getY())+1,
               maxZ = Math.max(pos1.getZ(), pos2.getZ())+1;
        return new AABB(minX+p, minY+p, minZ+p, maxX-p, maxY-p, maxZ-p);
    }

    private boolean validateBlockType(Block block, StorageType type) {
        return switch (type) {
            case CHEST          -> block == Blocks.CHEST;
            case TRAPPED_CHEST  -> block == Blocks.TRAPPED_CHEST;
            case BARREL         -> block == Blocks.BARREL;
            case SHULKER_BOX    -> block instanceof ShulkerBoxBlock;
            case ENDER_CHEST    -> block == Blocks.ENDER_CHEST;
            case UTILITY        -> block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE
                                || block == Blocks.SMOKER  || block == Blocks.HOPPER
                                || block == Blocks.DISPENSER || block == Blocks.DROPPER;
            case DECORATIVE     -> block == Blocks.BREWING_STAND || block == Blocks.CRAFTER
                                || block == Blocks.DECORATED_POT || block == Blocks.CHISELED_BOOKSHELF;
        };
    }

    private SettingColor getColor(StorageType type) {
        return switch (type) {
            case CHEST, TRAPPED_CHEST -> chestColor.get();
            case BARREL         -> barrelColor.get();
            case SHULKER_BOX    -> shulkerBoxColor.get();
            case ENDER_CHEST    -> enderChestColor.get();
            case UTILITY        -> utilityColor.get();
            case DECORATIVE     -> decorativeColor.get();
        };
    }

    public int getTotalContainers() { return containers.size(); }

    public boolean shouldShowStealDumpButtons() {
        return isActive() && stealDumpButtons.get();
    }

    public int getDoubleChestCount() {
        if (mc.level == null) return 0;
        Set<BlockPos> counted = new HashSet<>();
        int count = 0;
        for (Map.Entry<BlockPos, StorageType> entry : containers.entrySet()) {
            BlockPos    pos  = entry.getKey();
            StorageType type = entry.getValue();
            if (type != StorageType.CHEST && type != StorageType.TRAPPED_CHEST) continue;
            if (counted.contains(pos)) continue;
            BlockState state = mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof ChestBlock)) continue;
            try {
                ChestType chestType = state.getValue(ChestBlock.TYPE);
                if (chestType == ChestType.SINGLE) continue;
                BlockPos adjacent = findAdjacentChest(pos, false);
                if (adjacent == null) continue;
                counted.add(pos); counted.add(adjacent);
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    public int getShulkerBoxCount() {
        int count = 0;
        for (StorageType type : containers.values())
            if (type == StorageType.SHULKER_BOX) count++;
        return count;
    }

    public int getEnderChestCount() {
        int count = 0;
        for (StorageType type : containers.values())
            if (type == StorageType.ENDER_CHEST) count++;
        return count;
    }

    private record LootCounts(int all, int nonEmpty) {}

    private enum StorageType {
        CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX, ENDER_CHEST,
        UTILITY, DECORATIVE
    }

    private record BeamData(AABB box, SettingColor color) {}
}