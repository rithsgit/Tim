package com.example.addon.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class RenderCompat {
    private RenderCompat() {
    }

    public static void renderGuardianBeam(
        Render3DEvent event,
        double centerX,
        double centerZ,
        int worldBottom,
        int worldTop,
        double radius,
        int strands,
        double rotation,
        SettingColor color,
        int alpha
    ) {
        SettingColor strandColor = new SettingColor(color.r, color.g, color.b, Math.max(0, Math.min(255, alpha)));

        for (int i = 0; i < strands; i++) {
            double angle = rotation + Math.PI * 2.0 * i / strands;
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            event.renderer.quadVertical(
                centerX + offsetX, worldBottom, centerZ + offsetZ,
                centerX - offsetX, worldTop, centerZ - offsetZ,
                strandColor);
        }
    }

    public static void beginSignOverlay() {
        // Meteor's UI pipelines already disable depth testing and writes.
    }

    public static void endSignOverlay() {
    }

    public static void renderColor() {
        Renderer2D.COLOR.render();
    }
}
