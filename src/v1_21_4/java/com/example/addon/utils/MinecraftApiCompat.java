package com.example.addon.utils;

import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import meteordevelopment.meteorclient.events.render.Render3DEvent;

import java.util.function.BooleanSupplier;

public final class MinecraftApiCompat {
    private MinecraftApiCompat() {
    }

    public static String gameVersionName() {
        return SharedConstants.getGameVersion().getName();
    }

    public static void drawScaledItem(DrawContext context, ItemStack stack, float x, float y, float scale) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 230.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        context.drawItem(stack, 0, 0);
        context.getMatrices().pop();
    }

    public static void disconnect(MinecraftClient client) {
        if (client.world != null) client.world.disconnect();
        client.disconnect();
    }

    public static ButtonWidget mouseOnlyButton(Text label, ButtonWidget.PressAction action,
                                               int x, int y, int width, int height,
                                               Tooltip tooltip, BooleanSupplier hasData) {
        ButtonWidget button = new ButtonWidget(x, y, width, height, label, action,
            textSupplier -> textSupplier.get().copy()) {
            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                return false;
            }

            @Override
            public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
                return false;
            }

            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                if (hasData != null) {
                    boolean previousActive = active;
                    active = hasData.getAsBoolean();
                    super.renderWidget(context, mouseX, mouseY, delta);
                    active = previousActive;
                } else {
                    super.renderWidget(context, mouseX, mouseY, delta);
                }
            }
        };
        if (tooltip != null) button.setTooltip(tooltip);
        return button;
    }

    public static void renderPearlBeam(MinecraftClient client, Render3DEvent event, Vec3d base,
                                       Vec3d cameraPos, Identifier texture, float tickDelta,
                                       long worldTime, int height, int color,
                                       float innerRadius, float outerRadius) {
        MatrixStack matrices = event.matrices;
        matrices.push();
        matrices.translate(base.x - cameraPos.x - 0.5, base.y - cameraPos.y, base.z - cameraPos.z - 0.5);
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        BeaconBlockEntityRenderer.renderBeam(
            matrices, immediate, texture, tickDelta, 1.0f, worldTime, 0, height, color, innerRadius, outerRadius
        );
        immediate.draw();
        matrices.pop();
    }
}
