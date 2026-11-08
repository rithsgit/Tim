package com.example.addon.utils;

import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

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

    public Vec2f direction(boolean aboveBreach, BiPredicate<Double, Double> clearWater) {
        if (!aboveBreach || stagnantTicks >= SLOW_TICKS) {
            direction = -1;
            return Vec2f.ZERO;
        }
        if (directionTicks > 0 && direction >= 0) {
            Vec2f current = directionVector(direction);
            if (clearWater.test((double) current.x, (double) current.y)) {
                directionTicks--;
                return current;
            }
        }
        int first = random.nextInt(8);
        for (int i = 0; i < 8; i++) {
            int candidate = (first + i) % 8;
            if (candidate == direction) continue;
            Vec2f movement = directionVector(candidate);
            if (clearWater.test((double) movement.x, (double) movement.y)) {
                direction = candidate;
                directionTicks = DIRECTION_TICKS - 1;
                return movement;
            }
        }
        if (direction >= 0) {
            Vec2f previous = directionVector(direction);
            if (clearWater.test((double) previous.x, (double) previous.y)) {
                directionTicks = DIRECTION_TICKS - 1;
                return previous;
            }
        }
        direction = -1;
        directionTicks = 0;
        return Vec2f.ZERO;
    }

    private static Vec2f directionVector(int direction) {
        double angle = direction * Math.PI / 4;
        return new Vec2f((float) (0.25 * Math.cos(angle)), (float) (0.25 * Math.sin(angle)));
    }

    public static Vec2f sprintMovement(Vec2f worldDirection, boolean submerged) {
        return submerged && !worldDirection.equals(Vec2f.ZERO) ? new Vec2f(0, 1) : Vec2f.ZERO;
    }

    public static boolean aboveOpening(BlockPos opening, double minY) {
        return opening == null || minY >= opening.getY() + 1;
    }

    public static Vec2f movement(BlockPos opening, double x, double minY, double z, float yaw,
                                 Vec2f direction, boolean submerged) {
        if (!aboveOpening(opening, minY))
            return OceanBreachSequence.movement(opening.getX() + 0.5 - x, opening.getZ() + 0.5 - z, yaw);
        return sprintMovement(direction, submerged);
    }

    public static boolean sprintRequested(BlockPos opening, double minY, Vec2f direction, boolean submerged) {
        return submerged && aboveOpening(opening, minY) && !direction.equals(Vec2f.ZERO);
    }

    public static boolean withinDriftLimit(double anchorX, double anchorZ, double x, double z,
                                           double directionX, double directionZ) {
        return Math.hypot(x + directionX * 3 - anchorX, z + directionZ * 3 - anchorZ) <= 1.5;
    }

    public static boolean clearAscentSweep(Box playerBox, double x, double z, Predicate<BlockPos> clear) {
        Box area = playerBox.expand(0.05).stretch(x * 3, 3, z * 3);
        for (int bx = MathHelper.floor(area.minX); bx <= MathHelper.floor(area.maxX); bx++) {
            for (int bz = MathHelper.floor(area.minZ); bz <= MathHelper.floor(area.maxZ); bz++) {
                for (int by = MathHelper.floor(area.minY); by <= MathHelper.floor(area.maxY); by++) {
                    if (!clear.test(new BlockPos(bx, by, bz))) return false;
                }
            }
        }
        return true;
    }
}
