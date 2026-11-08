package com.example.addon.utils;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

public final class SurfaceDestinationSearch {
    private SurfaceDestinationSearch() {
    }

    public static BlockPos nearestEnderChest(ClientWorld world, BlockPos origin, int radius) {
        double closest = (double) radius * radius;
        BlockPos nearest = null;
        for (int x = (origin.getX() - radius) >> 4; x <= (origin.getX() + radius) >> 4; x++) {
            for (int z = (origin.getZ() - radius) >> 4; z <= (origin.getZ() + radius) >> 4; z++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(x, z);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof EnderChestBlockEntity)) continue;
                    BlockPos pos = blockEntity.getPos();
                    double distance = pos.getSquaredDistance(origin);
                    if (distance <= closest && world.getBlockState(pos).isOf(Blocks.ENDER_CHEST)) {
                        nearest = pos.toImmutable();
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
        matches.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(origin)));
        List<BlockPos> selected = new ArrayList<>();
        for (BlockPos pos : matches) {
            if (selected.stream().anyMatch(other -> other.getSquaredDistance(pos) < 16)) continue;
            selected.add(pos);
            if (selected.size() == 16) break;
        }
        return selected;
    }
}
