package com.example.addon.render;

import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

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
        Vec3d cameraPosition = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        float red = color.r / 255f;
        float green = color.g / 255f;
        float blue = color.b / 255f;
        float strandAlpha = alpha / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        MatrixStack matrices = new MatrixStack();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(
            VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        org.joml.Matrix4f matrix = matrices.peek().getPositionMatrix();

        double relativeCenterX = centerX - cameraPosition.x;
        double relativeCenterZ = centerZ - cameraPosition.z;
        double relativeBottom = worldBottom - cameraPosition.y;
        double relativeTop = worldTop - cameraPosition.y;

        for (int i = 0; i < strands; i++) {
            double angle = rotation + Math.PI * 2.0 * i / strands;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            float leftX = (float) (relativeCenterX + cos * radius);
            float leftZ = (float) (relativeCenterZ + sin * radius);
            float rightX = (float) (relativeCenterX - cos * radius);
            float rightZ = (float) (relativeCenterZ - sin * radius);
            float bottom = (float) relativeBottom;
            float top = (float) relativeTop;

            buffer.vertex(matrix, leftX, bottom, leftZ).color(red, green, blue, strandAlpha);
            buffer.vertex(matrix, rightX, bottom, rightZ).color(red, green, blue, strandAlpha);
            buffer.vertex(matrix, leftX, top, leftZ).color(red, green, blue, strandAlpha);

            buffer.vertex(matrix, rightX, bottom, rightZ).color(red, green, blue, strandAlpha);
            buffer.vertex(matrix, rightX, top, rightZ).color(red, green, blue, strandAlpha);
            buffer.vertex(matrix, leftX, top, leftZ).color(red, green, blue, strandAlpha);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void beginSignOverlay() {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
    }

    public static void endSignOverlay() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    public static void renderColor() {
        Renderer2D.COLOR.render(null);
    }
}
