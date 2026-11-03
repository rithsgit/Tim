package com.example.addon.utils;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

public final class MinecraftCompatCamera {
    private MinecraftCompatCamera() {
    }

    public static Vec3d position(Camera camera) {
        return camera.getPos();
    }
}
