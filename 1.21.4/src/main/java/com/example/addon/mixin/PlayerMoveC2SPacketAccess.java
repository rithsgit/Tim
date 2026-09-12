package com.example.addon.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public interface PlayerMoveC2SPacketAccess {
    void setOnGround(boolean onGround);

    void setPitch(float pitch);

    void setYaw(float yaw);

    void setCause(Cause cause);

    Cause getCause();

    static PlayerMoveC2SPacket setCause(PlayerMoveC2SPacket packet, Cause cause) {
        ((PlayerMoveC2SPacketAccess) packet).setCause(cause);
        return packet;
    }

    static PlayerMoveC2SPacket setCauseFrom(PlayerMoveC2SPacket packet, PlayerMoveC2SPacket packet2) {
        return setCause(packet, ((PlayerMoveC2SPacketAccess) packet2).getCause());
    }

    static PlayerMoveC2SPacketAccess of(PlayerMoveC2SPacket packet) {
        return (PlayerMoveC2SPacketAccess) packet;
    }

    enum Cause {
        SET_BACK,
        PLAYER_MOVEMENT,
        HACKING_PACKETS,
        LEGACY_SNAP,
        TRIGGER_SIMULATION
    }
}