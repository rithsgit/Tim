package com.example.addon.utils;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec2;

public final class OceanBreachSequence {
    public enum Phase { Centering, Mining, WaitingForWater, Swimming, Complete, Failed }

    private Phase phase = Phase.Centering;
    private int ticks;
    private int centeringTicks;
    private boolean wasSubmerged;

    public Phase phase() {
        return phase;
    }

    public Phase tick(boolean centered, boolean blockPresent, boolean touchingWater, boolean submerged, boolean exposedAir) {
        if (phase == Phase.Complete || phase == Phase.Failed) return phase;
        if (++ticks > 1200) return phase = Phase.Failed;
        if (phase == Phase.Centering) {
            if (centered) phase = Phase.Mining;
            else if (++centeringTicks > 100) return phase = Phase.Failed;
        }
        if (phase == Phase.Mining && !blockPresent) phase = Phase.WaitingForWater;
        if (phase == Phase.WaitingForWater && touchingWater) phase = Phase.Swimming;
        if (phase == Phase.Swimming) {
            if (submerged) wasSubmerged = true;
            else if (wasSubmerged && exposedAir) phase = Phase.Complete;
        }
        return phase;
    }

    public static boolean centered(double dx, double dz, double velocityX, double velocityZ) {
        return Math.abs(dx) <= 0.12 && Math.abs(dz) <= 0.12
            && Math.abs(velocityX) <= 0.025 && Math.abs(velocityZ) <= 0.025;
    }

    public static boolean withinBreachColumn(double dx, double dz) {
        return Math.abs(dx) <= 0.19 && Math.abs(dz) <= 0.19;
    }

    public static Vec2 movement(double dx, double dz, float yaw) {
        if (Math.abs(dx) <= 0.12 && Math.abs(dz) <= 0.12) return Vec2.ZERO;
        double radians = Math.toRadians(yaw);
        double sideways = dx * Math.cos(radians) + dz * Math.sin(radians);
        double forward = dz * Math.cos(radians) - dx * Math.sin(radians);
        double scale = Math.min(1.5, 0.35 / Math.hypot(sideways, forward));
        return new Vec2((float) (sideways * scale), (float) (forward * scale));
    }

    public static boolean canStage(BlockPos feet, Predicate<BlockPos> clear, Predicate<BlockPos> mineable,
                                   Predicate<BlockPos> solidFloor) {
        return solidFloor.test(feet.below())
            && (clear.test(feet) || mineable.test(feet))
            && (clear.test(feet.above()) || mineable.test(feet.above()));
    }

    public static boolean isClear(BlockPos feet, Predicate<BlockPos> clear) {
        return clear.test(feet) && clear.test(feet.above());
    }

    public static boolean exposedWaterColumn(BlockPos target, int top, Predicate<BlockPos> water,
                                              Predicate<BlockPos> air) {
        if (top - target.getY() < 3 || top - target.getY() > 128) return false;
        for (int y = target.getY() + 1; y < top; y++)
            if (!water.test(new BlockPos(target.getX(), y, target.getZ()))) return false;
        return air.test(new BlockPos(target.getX(), top, target.getZ()))
            && air.test(new BlockPos(target.getX(), top + 1, target.getZ()));
    }
}
