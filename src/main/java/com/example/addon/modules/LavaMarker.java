package com.example.addon.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.addon.Tim;

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
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * LavaMarker — standalone module highlighting fully-flowed lava falls in the Nether,
 * integrated with optimized column-merging and chunk batch tracking from Skipper.
 */
public class LavaMarker extends Module {

    public enum RenderMode {
        GLOW,
        SPECTRAL,
        PULSE
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender  = settings.createGroup("Render");

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("Horizontal scan radius in chunks.")
        .defaultValue(4).min(1).sliderMax(128)
        .build()
    );

    private final Setting<Integer> verticalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("vertical-radius")
        .description("Vertical scan radius in blocks.")
        .defaultValue(64).min(0).sliderMax(128)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("flowing-lava")
        .description("Color for fully-flowed lava falls.")
        .defaultValue(new SettingColor(255, 100, 0, 200))
        .build()
    );

    private final Setting<Integer> minFallHeight = sgGeneral.add(new IntSetting.Builder()
        .name("min-fall-height")
        .description("Lava falls shorter than this will be ignored.")
        .defaultValue(5).min(0).sliderMax(32)
        .build()
    );

    private final Setting<Integer> maxRenderBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("max-render-blocks")
        .description("Maximum number of blocks to render per frame to prevent performance drops.")
        .defaultValue(5000).min(100).sliderMax(20000)
        .build()
    );

    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode")
        .description("GLOW = layered bloom boxes. SPECTRAL = subtle fill box. PULSE = fading in/out highlight.")
        .defaultValue(RenderMode.GLOW)
        .build()
    );

    private final Setting<Integer> glowLayers = sgRender.add(new IntSetting.Builder()
        .name("glow-layers")
        .description("Number of bloom layers rendered around each lava block.")
        .defaultValue(3).min(1).sliderMax(6)
        .visible(() -> renderMode.get() == RenderMode.GLOW || renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Setting<Double> glowSpread = sgRender.add(new DoubleSetting.Builder()
        .name("glow-spread")
        .description("How far each bloom layer expands outward (in blocks).")
        .defaultValue(0.04).min(0.01).sliderMax(0.15)
        .visible(() -> renderMode.get() == RenderMode.GLOW || renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Setting<Integer> spectralFillAlpha = sgRender.add(new IntSetting.Builder()
        .name("spectral-fill-alpha")
        .description("Opacity of the fill box in SPECTRAL mode (0 = invisible, 80 = subtle).")
        .defaultValue(40).min(0).max(200).sliderMax(120)
        .visible(() -> renderMode.get() == RenderMode.SPECTRAL)
        .build()
    );

    private final Setting<Boolean> spectralOutline = sgRender.add(new BoolSetting.Builder()
        .name("spectral-outline")
        .description("Draw a solid outline around lava blocks in SPECTRAL mode.")
        .defaultValue(true)
        .visible(() -> renderMode.get() == RenderMode.SPECTRAL)
        .build()
    );

    private final Setting<Double> pulseSpeed = sgRender.add(new DoubleSetting.Builder()
        .name("pulse-speed")
        .description("Pulse cycle speed. 1.0 = one full fade in/out per second.")
        .defaultValue(1.0).min(0.1).max(5.0).sliderMax(3.0)
        .visible(() -> renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Setting<Integer> pulseMinAlpha = sgRender.add(new IntSetting.Builder()
        .name("pulse-min-alpha")
        .description("Lowest opacity reached during the pulse (0 = invisible).")
        .defaultValue(15).min(0).max(255).sliderMax(100)
        .visible(() -> renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Setting<Integer> pulseMaxAlpha = sgRender.add(new IntSetting.Builder()
        .name("pulse-max-alpha")
        .description("Peak opacity reached during the pulse.")
        .defaultValue(220).min(15).max(255).sliderMax(255)
        .visible(() -> renderMode.get() == RenderMode.PULSE)
        .build()
    );

    private final Map<ChunkPos, List<Box>> lavaColumnBoxesByChunk = new ConcurrentHashMap<>();
    private final Set<ChunkPos>                scannedChunks         = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos>                dirtyChunks           = ConcurrentHashMap.newKeySet();

    private String lastDimension = "";

    public LavaMarker() {
        super(Tim.CATEGORY, "lava-marker", "Highlights fully-flowed lava falls in the Nether.");
    }

    @Override
    public void onActivate() {
        clearData();
        if (mc.world != null) lastDimension = mc.world.getRegistryKey().getValue().toString();
    }

    @Override
    public void onDeactivate() {
        clearData();
    }

    private void clearData() {
        lavaColumnBoxesByChunk.clear();
        scannedChunks.clear();
        dirtyChunks.clear();
    }

    private boolean isNether() {
        return mc.world != null && mc.world.getRegistryKey().equals(World.NETHER);
    }

    private void updateDimension() {
        if (mc.world == null) return;
        String dim = mc.world.getRegistryKey().getValue().toString();
        if (!dim.equals(lastDimension)) {
            clearData();
            lastDimension = dim;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        updateDimension();
        if (!isNether()) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int radius = chunkRadius.get();
        int pX = playerPos.getX() >> 4;
        int pZ = playerPos.getZ() >> 4;

        scannedChunks.removeIf(cp -> isOutOfRange(cp, pX, pZ, radius));
        lavaColumnBoxesByChunk.keySet().removeIf(cp -> isOutOfRange(cp, pX, pZ, radius));
        dirtyChunks.removeIf(cp -> isOutOfRange(cp, pX, pZ, radius));

        List<ChunkPos> todo = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ChunkPos cp = new ChunkPos(pX + x, pZ + z);
                if (!scannedChunks.contains(cp) && mc.world.getChunkManager().isChunkLoaded(cp.x, cp.z)) {
                    todo.add(cp);
                }
            }
        }
        todo.sort(Comparator.comparingDouble(cp -> {
            double dx = cp.x - pX, dz = cp.z - pZ;
            return dx * dx + dz * dz;
        }));

        int processed = 0;
        while (!dirtyChunks.isEmpty() && processed < 4) {
            ChunkPos cp = dirtyChunks.iterator().next();
            dirtyChunks.remove(cp);
            scannedChunks.remove(cp);
            if (mc.world.getChunkManager().isChunkLoaded(cp.x, cp.z)) {
                scanLavaChunk(mc.world.getChunk(cp.x, cp.z));
                scannedChunks.add(cp);
                processed++;
            }
        }
        for (ChunkPos cp : todo) {
            if (processed >= 4) break;
            scanLavaChunk(mc.world.getChunk(cp.x, cp.z));
            scannedChunks.add(cp);
            processed++;
        }
    }

    private boolean isOutOfRange(ChunkPos cp, int pX, int pZ, int radius) {
        return Math.abs(cp.x - pX) > radius || Math.abs(cp.z - pZ) > radius;
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null || !isNether()) return;
        BlockState ns = event.newState;
        if (ns.isOf(Blocks.LAVA) || ns.isAir()) {
            ChunkPos cp = new ChunkPos(event.pos);
            scannedChunks.remove(cp);
            dirtyChunks.add(cp);
            dirtyChunks.add(new ChunkPos(cp.x - 1, cp.z));
            dirtyChunks.add(new ChunkPos(cp.x + 1, cp.z));
            dirtyChunks.add(new ChunkPos(cp.x, cp.z - 1));
            dirtyChunks.add(new ChunkPos(cp.x, cp.z + 1));
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.world == null || !isNether()) return;
        ChunkPos cp = event.chunk().getPos();
        scannedChunks.remove(cp);
        dirtyChunks.add(cp);
    }

    private void scanLavaChunk(Chunk chunk) {
        if (chunk == null || mc.player == null || mc.world == null) return;
        ChunkPos cp = chunk.getPos();
        if (isNaturalBasaltSpring(chunk)) {
            lavaColumnBoxesByChunk.remove(cp);
            return;
        }

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

        if (allValidFallBlocks.isEmpty()) {
            lavaColumnBoxesByChunk.remove(cp);
            return;
        }

        Map<Long, List<Integer>> columns = new HashMap<>();
        for (BlockPos pos : allValidFallBlocks) {
            long key = (((long) pos.getX()) & 0xFFFFFFFFL) | (((long) pos.getZ()) << 32);
            columns.computeIfAbsent(key, k -> new ArrayList<>()).add(pos.getY());
        }

        List<Box> mergedBoxes = new ArrayList<>();
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
                    mergedBoxes.add(new Box(x, startY, z, x + 1.0, prevY + 1.0, z + 1.0));
                    startY = y;
                    prevY = y;
                }
            }
            mergedBoxes.add(new Box(x, startY, z, x + 1.0, prevY + 1.0, z + 1.0));
        }

        lavaColumnBoxesByChunk.put(cp, mergedBoxes);
    }

    private boolean isNaturalBasaltSpring(Chunk chunk) {
        int basaltCount = 0;
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section != null && !section.isEmpty() && section.hasAny(s -> s.isOf(Blocks.BASALT))) basaltCount++;
        }
        return basaltCount >= 6;
    }

    private boolean isFalling(BlockPos pos) {
        if (!mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        FluidState fs = mc.world.getFluidState(pos);
        return fs.isIn(FluidTags.LAVA) && fs.contains(Properties.FALLING) && fs.get(Properties.FALLING);
    }

    private void bfsLavaFall(BlockPos start, Set<BlockPos> result, Set<BlockPos> visited) {
        if (visited.contains(start)) return;
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && result.size() < 256) {
            BlockPos cur = queue.poll();
            result.add(cur);

            for (BlockPos nb : new BlockPos[]{cur.north(), cur.south(), cur.east(), cur.west(), cur.down()}) {
                if (!visited.contains(nb) && mc.world.getChunkManager().isChunkLoaded(nb.getX() >> 4, nb.getZ() >> 4)) {
                    FluidState ns = mc.world.getFluidState(nb);
                    if (ns.isIn(FluidTags.LAVA) && !ns.isStill()) {
                        visited.add(nb);
                        queue.add(nb);
                    }
                }
            }
            BlockPos up = cur.up();
            if (!visited.contains(up) && mc.world.getChunkManager().isChunkLoaded(up.getX() >> 4, up.getZ() >> 4) && isFalling(up)) {
                visited.add(up);
                queue.add(up);
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || !isNether()) return;

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return;
        Vec3d camPos = com.example.addon.utils.MinecraftCompatCamera.position(camera);
        Box renderZone = new Box(
            camPos.x - 256, camPos.y - 256, camPos.z - 256,
            camPos.x + 256, camPos.y + 256, camPos.z + 256
        );

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double maxLavaDistSq = 96.0 * 96.0;
        int count = 0;
        int max = maxRenderBlocks.get();

        SettingColor markerColor = color.get();

        for (Map.Entry<ChunkPos, List<Box>> entry : lavaColumnBoxesByChunk.entrySet()) {
            ChunkPos cp = entry.getKey();
            double chunkCenterDx = (cp.getStartX() + 8.0) - px;
            double chunkCenterDz = (cp.getStartZ() + 8.0) - pz;
            if (chunkCenterDx * chunkCenterDx + chunkCenterDz * chunkCenterDz > (96.0 + 16.0) * (96.0 + 16.0)) continue;

            for (Box columnBox : entry.getValue()) {
                if (count >= max) return;
                double dx = columnBox.minX + 0.5 - px;
                double dz = columnBox.minZ + 0.5 - pz;
                if (dx * dx + dz * dz <= maxLavaDistSq && renderZone.intersects(columnBox)) {
                    renderStyledBox(event, columnBox, markerColor);
                    count++;
                }
            }
        }
    }

    private void renderStyledBox(Render3DEvent event, Box box, SettingColor color) {
        if (renderMode.get() == RenderMode.SPECTRAL) {
            int fillAlpha = spectralFillAlpha.get();
            ShapeMode mode = spectralOutline.get() ? ShapeMode.Both : ShapeMode.Sides;
            SettingColor outlineColor = spectralOutline.get() ? color : withAlpha(color, 0);
            event.renderer.box(box, withAlpha(color, fillAlpha), outlineColor, mode, 0);
        } else if (renderMode.get() == RenderMode.PULSE) {
            renderPulseBox(event, box, color);
        } else {
            renderGlowLayers(event, box, color);
            event.renderer.box(box, withAlpha(color, color.a), color, ShapeMode.Both, 0);
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