package com.example.addon.utils;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

public final class MinecraftCompatCamera {
    private MinecraftCompatCamera() {
    }

    public static Vec3 position(Camera camera) {
        return camera.position();
    }
}
