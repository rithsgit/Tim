package com.example.addon.utils;

public final class ElytraTakeoffSequence {
    public enum Phase {
        Preparing,
        JumpPressed,
        JumpReleased,
        GlidePressed,
        Climbing
    }

    public enum Action {
        Wait,
        Jump,
        Deploy,
        Climb,
        Failed
    }

    private Phase phase = Phase.Preparing;
    private int stageTicks;

    public ElytraTakeoffSequence() {
    }

    public ElytraTakeoffSequence(boolean onGround) {
        if (!onGround) phase = Phase.JumpPressed;
    }

    public Phase phase() {
        return phase;
    }

    public boolean jumpPressed() {
        return phase == Phase.JumpPressed || phase == Phase.GlidePressed;
    }

    public Action tick(boolean onGround, boolean gliding) {
        if (phase == Phase.Preparing) {
            if (!onGround) return Action.Failed;
            if (++stageTicks < 2) return Action.Wait;
            phase = Phase.JumpPressed;
            stageTicks = 0;
            return Action.Jump;
        }
        if (phase != Phase.Climbing) {
            if (gliding) {
                phase = Phase.Climbing;
                return Action.Climb;
            }
            if (++stageTicks > 40 || (stageTicks > 3 && onGround)) return Action.Failed;
            if (phase == Phase.JumpPressed || phase == Phase.GlidePressed) {
                phase = Phase.JumpReleased;
                return Action.Wait;
            }
            if (!onGround) {
                phase = Phase.GlidePressed;
                return Action.Deploy;
            }
            return Action.Wait;
        }
        return gliding ? Action.Climb : Action.Failed;
    }
}
