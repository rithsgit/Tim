package com.example.addon.utils;

import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;

public final class OceanBreachAscent {
    public enum Progress { Moving, Slow, Stalled }

    private static final int DIRECTION_TICKS = 25;
    private static final int SLOW_TICKS = 60;
    private static final int STALL_TICKS = 140;
    private final Random random;
    private double progressHeight;
    private int stagnantTicks;
    private int directionTicks;
    private int direction = -1;

    public OceanBreachAscent(double initialHeight, Random random) {
        this.progressHeight = initialHeight;
        this.random = random;
    }

    public Progress tick(double height) {
        if (height >= progressHeight + 0.35) {
            progressHeight = height;
            stagnantTicks = 0;
        } else {
            stagnantTicks++;
        }
        if (stagnantTicks >= STALL_TICKS) return Progress.Stalled;
        return stagnantTicks >= SLOW_TICKS ? Progress.Slow : Progress.Moving;
    }

    public Vec2 direction(boolean aboveBreach, BiPredicate<Double, Double> clearWater) {
        if (!aboveBreach || stagnantTicks >= SLOW_TICKS) {
            direction = -1;
            return Vec2.ZERO;
        }
        if (directionTicks > 0 && direction >= 0) {
            Vec2 current = directionVector(direction);
            if (clearWater.test((double) current.x, (double) current.y)) {
                directionTicks--;
                return current;
            }
        }
        int first = random.nextInt(8);
        for (int i = 0; i < 8; i++) {
            int candidate = (first + i) % 8;
            if (candidate == direction) continue;
            Vec2 movement = directionVector(candidate);
            if (clearWater.test((double) movement.x, (double) movement.y)) {
                direction = candidate;
                directionTicks = DIRECTION_TICKS - 1;
                return movement;
            }
        }
        if (direction >= 0) {
            Vec2 previous = directionVector(direction);
            if (clearWater.test((double) previous.x, (double) previous.y)) {
                directionTicks = DIRECTION_TICKS - 1;
                return previous;
            }
        }
        direction = -1;
        directionTicks = 0;
        return Vec2.ZERO;
    }

    private static Vec2 directionVector(int direction) {
        double angle = direction * Math.PI / 4;
        return new Vec2((float) (0.25 * Math.cos(angle)), (float) (0.25 * Math.sin(angle)));
    }

    public static Vec2 sprintMovement(Vec2 worldDirection, boolean submerged) {
        return submerged && !worldDirection.equals(Vec2.ZERO) ? new Vec2(0, 1) : Vec2.ZERO;
    }

    public static boolean aboveOpening(BlockPos opening, double minY) {
        return opening == null || minY >= opening.getY() + 1;
    }

    public static Vec2 movement(BlockPos opening, double x, double minY, double z, float yaw,
                                 Vec2 direction, boolean submerged) {
        if (!aboveOpening(opening, minY))
            return OceanBreachSequence.movement(opening.getX() + 0.5 - x, opening.getZ() + 0.5 - z, yaw);
        return sprintMovement(direction, submerged);
    }

    public static boolean sprintRequested(BlockPos opening, double minY, Vec2 direction, boolean submerged) {
        return submerged && aboveOpening(opening, minY) && !direction.equals(Vec2.ZERO);
    }

    public static boolean withinDriftLimit(double anchorX, double anchorZ, double x, double z,
                                           double directionX, double directionZ) {
        return Math.hypot(x + directionX * 3 - anchorX, z + directionZ * 3 - anchorZ) <= 1.5;
    }

    public static boolean clearAscentSweep(AABB playerBox, double x, double z, Predicate<BlockPos> clear) {
        AABB area = playerBox.inflate(0.05).expandTowards(x * 3, 3, z * 3);
        for (int bx = Mth.floor(area.minX); bx <= Mth.floor(area.maxX); bx++) {
            for (int bz = Mth.floor(area.minZ); bz <= Mth.floor(area.maxZ); bz++) {
                for (int by = Mth.floor(area.minY); by <= Mth.floor(area.maxY); by++) {
                    if (!clear.test(new BlockPos(bx, by, bz))) return false;
                }
            }
        }
        return true;
    }
}
