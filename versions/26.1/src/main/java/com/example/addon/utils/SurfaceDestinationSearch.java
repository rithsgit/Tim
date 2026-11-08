package com.example.addon.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public final class SurfaceDestinationSearch {
    private SurfaceDestinationSearch() {
    }

    public static BlockPos nearestEnderChest(ClientLevel world, BlockPos origin, int radius) {
        double closest = (double) radius * radius;
        BlockPos nearest = null;
        for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++) {
            for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
                LevelChunk chunk = world.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof EnderChestBlockEntity)) continue;
                    BlockPos pos = blockEntity.getBlockPos();
                    double distance = pos.distSqr(origin);
                    if (distance <= closest && world.getBlockState(pos).is(Blocks.ENDER_CHEST)) {
                        nearest = pos.immutable();
                        closest = distance;
                    }
                }
            }
        }
        return nearest;
    }

    public static int belowCanopy(int topY, IntPredicate passThrough) {
        int y = topY;
        while (y > topY - 16 && passThrough.test(y - 1)) y--;
        return y;
    }

    public static boolean exposedThroughCanopy(int y, int topY, IntPredicate passThrough) {
        if (topY < y || topY - y > 16) return false;
        for (int current = y; current < topY; current++) {
            if (!passThrough.test(current)) return false;
        }
        return true;
    }

    public static List<BlockPos> findLand(BlockPos origin, int radius, BiPredicate<Integer, Integer> loaded,
                                           IntBinaryOperator topY, IntBinaryOperator belowCanopy,
                                           Predicate<BlockPos> preferred, Predicate<BlockPos> canopy,
                                           Predicate<BlockPos> broad) {
        List<BlockPos> matches = find(origin, radius, false, loaded, topY, preferred);
        if (!matches.isEmpty()) return matches;
        matches = find(origin, radius, false, loaded, belowCanopy, canopy);
        if (!matches.isEmpty()) return matches;
        return find(origin, radius, false, loaded, topY, broad);
    }

    public static List<BlockPos> find(BlockPos origin, int radius, boolean waterSurface,
                                      BiPredicate<Integer, Integer> loaded, IntBinaryOperator topY,
                                      Predicate<BlockPos> valid) {
        List<BlockPos> matches = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                if (!loaded.test(x, z)) continue;
                // Baritone can ascend through water only while the block above its head is water.
                int y = topY.applyAsInt(x, z) - (waterSurface ? 2 : 0);
                BlockPos pos = new BlockPos(x, y, z);
                if ((!waterSurface || y >= origin.getY()) && valid.test(pos)) matches.add(pos);
            }
        }
        matches.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        List<BlockPos> selected = new ArrayList<>();
        for (BlockPos pos : matches) {
            if (selected.stream().anyMatch(other -> other.distSqr(pos) < 16)) continue;
            selected.add(pos);
            if (selected.size() == 16) break;
        }
        return selected;
    }
}
