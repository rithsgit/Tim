package com.example.addon.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class WaterEscapeNavigator {
    public enum Result { Moving, Arrived, NoRoute, Stalled, CurrentBlocked, TargetBlocked, Deviated }
    public enum Status { Valid, Correctable, Deviated, CurrentBlocked, TargetBlocked }
    public record SwimInput(float yaw, float pitch, boolean forward, boolean sprint, boolean jump, boolean sneak) {}
    public record Route(List<BlockPos> steps, boolean reachesGoal) {}
    private record Visit(BlockPos position, int depth, int priority, int order) {}
    private static final Direction[] DIRECTIONS = {
        Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN
    };
    private static final int MAX_ESCAPE_BLOCKS = 4;
    private static final int MAX_ESCAPE_HEIGHT = 128;

    private final Predicate<BlockPos> traversable;
    private final Predicate<BlockPos> goal;
    private List<BlockPos> path = List.of();
    private BlockPos origin;
    private int index;
    private int ticks;
    private int stalledTicks;
    private int correctionTicks;
    private int replans;
    private int progressSegments;
    private boolean finalRoute;
    private double bestDistance = Double.POSITIVE_INFINITY;

    public WaterEscapeNavigator(Predicate<BlockPos> traversable, Predicate<BlockPos> goal) {
        this(traversable, goal, null);
    }

    public WaterEscapeNavigator(Predicate<BlockPos> traversable, Predicate<BlockPos> goal, BlockPos excludedColumn) {
        this.traversable = traversable;
        this.goal = pos -> (excludedColumn == null || pos.getX() != excludedColumn.getX()
            || pos.getZ() != excludedColumn.getZ()) && goal.test(pos);
    }

    public Result start(BlockPos start) {
        if (!traversable.test(start)) return Result.CurrentBlocked;
        if (goal.test(start)) return Result.Arrived;
        return plan(start) ? Result.Moving : Result.NoRoute;
    }

    public BlockPos target() {
        if (path.isEmpty()) throw new IllegalStateException("Water escape has no active route");
        return path.get(index);
    }

    public Result tick(BlockPos current, double x, double y, double z) {
        if (++ticks > 1200 || ++stalledTicks > 120) return Result.Stalled;
        if (index + 1 < path.size() && current.equals(path.get(index + 1))) {
            BlockPos previous = index == 0 ? origin : path.get(index - 1);
            BlockPos target = path.get(index);
            BlockPos next = path.get(index + 1);
            if (target.subtract(previous).equals(next.subtract(target))
                && traversable.test(target) && traversable.test(current)) {
                index++;
                bestDistance = Double.POSITIVE_INFINITY;
                correctionTicks = 0;
            }
        }
        BlockPos target = target();
        BlockPos previous = index == 0 ? origin : path.get(index - 1);
        Status status = inspect(current, previous, target, traversable);
        if (status == Status.Correctable) {
            if (++correctionTicks <= 40) status = Status.Valid;
            else status = Status.Deviated;
        } else if (status == Status.Valid) correctionTicks = 0;
        if (status != Status.Valid) {
            if (status == Status.Deviated && goal.test(current)) return Result.Arrived;
            if (status != Status.CurrentBlocked && replans++ < 2 && plan(current))
                return Result.Moving;
            return switch (status) {
                case CurrentBlocked -> Result.CurrentBlocked;
                case TargetBlocked -> Result.TargetBlocked;
                case Deviated -> Result.Deviated;
                default -> throw new IllegalStateException("Unexpected water navigation status");
            };
        }
        double dx = target.getX() + 0.5 - x;
        double dy = target.getY() + 0.15 - y;
        double dz = target.getZ() + 0.5 - z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance + 0.15 < bestDistance) {
            bestDistance = distance;
            stalledTicks = 0;
        }
        if (Math.hypot(dx, dz) <= 0.35 && Math.abs(dy) <= 0.55 && current.equals(target)) {
            index++;
            stalledTicks = 0;
            bestDistance = Double.POSITIVE_INFINITY;
            if (index == path.size()) {
                if (goal.test(current)) return Result.Arrived;
                if (finalRoute || ++progressSegments >= 4 || !plan(current)) return Result.NoRoute;
            }
        }
        return Result.Moving;
    }

    public static Status inspect(BlockPos current, BlockPos previous, BlockPos target,
                                 Predicate<BlockPos> traversable) {
        if (!traversable.test(current)) return Status.CurrentBlocked;
        if (!traversable.test(target)) return Status.TargetBlocked;
        if (current.equals(previous) || current.equals(target)) return Status.Valid;
        return adjacent(current, previous) || adjacent(current, target) ? Status.Correctable : Status.Deviated;
    }

    private static boolean adjacent(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY())
            + Math.abs(first.getZ() - second.getZ()) == 1;
    }

    public static SwimInput swimInput(double dx, double dy, double dz, float currentYaw, boolean canSprint) {
        double horizontal = Math.hypot(dx, dz);
        float yaw = horizontal > 0.01 ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : currentYaw;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        boolean forward = Math.hypot(horizontal, dy) > 0.15;
        boolean sprint = forward && canSprint;
        return new SwimInput(yaw, pitch, forward, sprint, dy > 0.3, !sprint && dy < -0.3);
    }

    public static Route search(BlockPos origin, int radius, int vertical, int maxNodes,
                               Predicate<BlockPos> traversable, Predicate<BlockPos> goal,
                               boolean allowUpwardProgress) {
        if (radius < 0 || vertical < 0 || maxNodes < 1 || !traversable.test(origin))
            return new Route(List.of(), false);
        PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingInt(Visit::priority)
            .thenComparingInt(Visit::order));
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        BlockPos best = origin;
        int bestDepth = Integer.MAX_VALUE;
        queue.add(new Visit(origin, 0, 0, 0));
        previous.put(origin, origin);
        while (!queue.isEmpty()) {
            Visit visit = queue.remove();
            BlockPos current = visit.position();
            if (goal.test(current)) return new Route(path(origin, current, previous), true);
            if (current.getY() > best.getY()
                || (current.getY() == best.getY() && visit.depth() < bestDepth)) {
                best = current;
                bestDepth = visit.depth();
            }
            for (Direction direction : DIRECTIONS) {
                BlockPos next = current.relative(direction);
                if (Math.abs(next.getX() - origin.getX()) > radius
                    || Math.abs(next.getZ() - origin.getZ()) > radius
                    || Math.abs(next.getY() - origin.getY()) > vertical
                    || previous.containsKey(next) || previous.size() >= maxNodes
                    || !traversable.test(next)) continue;
                previous.put(next, current);
                int depth = visit.depth() + 1;
                int priority = depth * 4 - Math.max(0, next.getY() - origin.getY()) * 3;
                queue.add(new Visit(next, depth, priority, previous.size()));
            }
        }
        return new Route(allowUpwardProgress && best.getY() >= origin.getY() + 2
            ? path(origin, best, previous) : List.of(), false);
    }

    private static List<BlockPos> path(BlockPos origin, BlockPos destination, Map<BlockPos, BlockPos> previous) {
        List<BlockPos> result = new ArrayList<>();
        while (!destination.equals(origin)) {
            result.add(destination);
            destination = previous.get(destination);
        }
        Collections.reverse(result);
        return List.copyOf(result);
    }

    public static List<BlockPos> findEscapeBlocks(int x, int z, int eyeY, int top,
                                                   Predicate<BlockPos> water, Predicate<BlockPos> mineable,
                                                   Predicate<BlockPos> air) {
        if (top <= eyeY || top - eyeY > MAX_ESCAPE_HEIGHT) return List.of();
        int first = eyeY;
        while (first <= eyeY + 3 && first < top && water.test(new BlockPos(x, first, z))) first++;
        if (first > eyeY + 3 || first >= top) return List.of();
        List<BlockPos> blocks = new ArrayList<>();
        int y = first;
        while (y < top && mineable.test(new BlockPos(x, y, z))) {
            if (blocks.size() == MAX_ESCAPE_BLOCKS) return List.of();
            blocks.add(new BlockPos(x, y++, z));
        }
        if (blocks.isEmpty() || top - y < 2) return List.of();
        for (; y < top; y++) if (!water.test(new BlockPos(x, y, z))) return List.of();
        if (!air.test(new BlockPos(x, top, z)) || !air.test(new BlockPos(x, top + 1, z))) return List.of();
        return List.copyOf(blocks);
    }

    private boolean plan(BlockPos start) {
        Route found = search(start, 12, 24, 4096,
            traversable, pos -> !pos.equals(start) && goal.test(pos), true);
        if (found.steps().isEmpty()) return false;
        origin = start;
        path = found.steps();
        finalRoute = found.reachesGoal();
        index = 0;
        stalledTicks = 0;
        correctionTicks = 0;
        bestDistance = Double.POSITIVE_INFINITY;
        return true;
    }
}
