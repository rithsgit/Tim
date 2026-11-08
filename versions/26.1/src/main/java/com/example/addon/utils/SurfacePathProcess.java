package com.example.addon.utils;

import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import java.util.function.Function;

public final class SurfacePathProcess implements IBaritoneProcess {
    public enum Result { None, Arrived, Failed, Cancelled, Interrupted }
    public interface RouteSettings {
        void apply(boolean allowBreak, boolean allowPlace);
        boolean unchanged();
        void restore();
    }

    private final IBaritone baritone;
    private final RouteSettings settings;
    private final Predicate<BlockPos> destinationValid;
    private final Consumer<String> report;
    private final String name;
    private final Function<BlockPos, Goal> goalFactory;
    private List<BlockPos> candidates = List.of();
    private int candidateIndex;
    private int ticks;
    private Goal goal;
    private boolean active;
    private boolean stopping;
    private boolean retryPending;
    private boolean interrupting;
    private int invalidCandidates;
    private int failedCalculations;
    private int timedOutCandidates;
    private String stopMessage;
    private Result result = Result.None;
    private BlockPos arrivedAt;
    public SurfacePathProcess(IBaritone baritone, RouteSettings settings, Predicate<BlockPos> destinationValid, Consumer<String> report) {
        this(baritone, settings, destinationValid, report, "Surface Path", GoalBlock::new);
    }

    public SurfacePathProcess(IBaritone baritone, RouteSettings settings, Predicate<BlockPos> destinationValid,
                              Consumer<String> report, String name, Function<BlockPos, Goal> goalFactory) {
        this.baritone = baritone;
        this.settings = settings;
        this.destinationValid = destinationValid;
        this.report = report;
        this.name = name;
        this.goalFactory = goalFactory;
        baritone.getPathingControlManager().registerProcess(this);
    }

    public boolean isBaritoneIdle() {
        IPathingBehavior pathing = baritone.getPathingBehavior();
        return !baritone.getPathingControlManager().mostRecentInControl().map(IBaritoneProcess::isActive).orElse(false)
            && !baritone.getCustomGoalProcess().isActive() && !baritone.getElytraProcess().isActive()
            && !baritone.getFollowProcess().isActive() && !baritone.getMineProcess().isActive()
            && !baritone.getBuilderProcess().isActive() && !baritone.getExploreProcess().isActive()
            && !baritone.getFarmProcess().isActive() && !baritone.getGetToBlockProcess().isActive()
            && !pathing.isPathing() && !pathing.hasPath() && pathing.getInProgress().isEmpty();
    }

    public void start(List<BlockPos> destinations, boolean allowBreak, boolean allowPlace) {
        if (active || destinations.isEmpty() || !isBaritoneIdle()) throw new IllegalStateException("Baritone is not idle or no destinations were supplied");
        settings.apply(allowBreak, allowPlace);
        candidates = List.copyOf(destinations);
        candidateIndex = 0;
        goal = goalFactory.apply(candidates.get(0));
        ticks = 0;
        active = true;
        stopping = false;
        retryPending = false;
        interrupting = false;
        invalidCandidates = 0;
        failedCalculations = 0;
        timedOutCandidates = 0;
        result = Result.None;
        arrivedAt = null;
        report.accept(name + " started.");
    }

    public void stop(String message) {
        if (active && !stopping) {
            stopping = true;
            stopMessage = message;
        }
    }

    public void interrupt(String message) {
        if (active && !stopping) {
            interrupting = true;
            stop(message);
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public Result result() {
        return result;
    }

    public BlockPos arrivedAt() {
        return arrivedAt;
    }

    public static Goal adjacentChestGoal(BlockPos chest) {
        return new GoalComposite(new GoalBlock(chest.north()), new GoalBlock(chest.south()),
            new GoalBlock(chest.east()), new GoalBlock(chest.west()));
    }

    public String failureSummary() {
        return "invalid candidates: " + invalidCandidates + ", failed calculations: " + failedCalculations
            + ", timed out: " + timedOutCandidates;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (!active) return null;
        if (stopping) {
            if (isSafeToCancel) {
                if (interrupting) result = Result.Interrupted;
                finish(stopMessage);
                return null;
            }
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        if (!settings.unchanged()) {
            stop(name + " stopped because Baritone route settings changed.");
            return onTick(false, isSafeToCancel);
        }
        BlockPos target = candidates.get(candidateIndex);
        if (!retryPending) {
            if (!destinationValid.test(target)) {
                invalidCandidates++;
                retryPending = true;
            } else if (calcFailed) {
                failedCalculations++;
                retryPending = true;
            } else if (++ticks > 1200) {
                timedOutCandidates++;
                retryPending = true;
            }
        }
        if (retryPending) {
            if (!isSafeToCancel) return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            if (++candidateIndex >= candidates.size()) {
                result = Result.Failed;
                finish(name + " failed: no reachable candidate found.");
                return null;
            }
            target = candidates.get(candidateIndex);
            goal = goalFactory.apply(target);
            ticks = 0;
            retryPending = false;
            report.accept(name + " trying another destination.");
            return new PathingCommand(goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
        }
        if (goal.isInGoal(baritone.getPlayerContext().playerFeet())) {
            if (!isSafeToCancel) return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            result = Result.Arrived;
            arrivedAt = target;
            finish(name + " arrived at the destination.");
            return null;
        }
        return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
    }

    private void finish(String message) {
        if (result == Result.None) result = Result.Cancelled;
        active = false;
        stopping = false;
        settings.restore();
        goal = null;
        candidates = List.of();
        if (message != null) report.accept(message);
    }

    @Override
    public void onLostControl() {
        if (active) finish(name + " stopped: Baritone control was taken over or cancelled.");
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public String displayName0() {
        return "Tweaker " + name;
    }

}
