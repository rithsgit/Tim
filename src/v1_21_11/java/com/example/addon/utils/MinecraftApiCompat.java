package com.example.addon.utils;

import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.math.MatrixStack;
import meteordevelopment.meteorclient.events.render.Render3DEvent;

import java.util.function.BooleanSupplier;

public final class MinecraftApiCompat {
    private MinecraftApiCompat() {
    }

    public static String gameVersionName() {
        return SharedConstants.getGameVersion().name();
    }

    public static void drawScaledItem(DrawContext context, ItemStack stack, float x, float y, float scale) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        context.drawItem(stack, 0, 0);
        context.getMatrices().popMatrix();
    }

    public static void disconnect(MinecraftClient client) {
        if (client.world != null) client.world.disconnect(Text.empty());
        client.disconnect(Text.empty());
    }

    public static ButtonWidget mouseOnlyButton(Text label, ButtonWidget.PressAction action,
                                               int x, int y, int width, int height,
                                               Tooltip tooltip, BooleanSupplier hasData) {
        ButtonWidget button = new MouseOnlyButton(label, action, x, y, width, height, hasData);
        if (tooltip != null) button.setTooltip(tooltip);
        return button;
    }

    private static final class MouseOnlyButton extends ButtonWidget {
        private final BooleanSupplier hasData;

        private MouseOnlyButton(net.minecraft.text.Text label, PressAction action, int x, int y, int width, int height,
                                BooleanSupplier hasData) {
            super(x, y, width, height, label, action, DEFAULT_NARRATION_SUPPLIER);
            this.hasData = hasData;
        }

        @Override
        public void onPress(AbstractInput input) {
            onPress.onPress(this);
        }

        @Override
        protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
            if (hasData != null) active = hasData.getAsBoolean();
        }

        @Override
        public boolean keyPressed(KeyInput input) {
            return false;
        }
    }

    public static void renderPearlBeam(MinecraftClient client, Render3DEvent event, Vec3d base,
                                       Vec3d cameraPos, Identifier texture, float tickDelta,
                                       long worldTime, int height, int packedColor,
                                       float innerRadius, float outerRadius) {
        MatrixStack matrices = event.matrices;
        matrices.push();
        try {
            matrices.translate(base.x - cameraPos.x - 0.5, base.y - cameraPos.y, base.z - cameraPos.z - 0.5);
            var commandQueue = client.gameRenderer.getEntityRenderCommandQueue();
            BeaconBlockEntityRenderer.renderBeam(
                matrices, commandQueue, texture, tickDelta, 1.0f, 0, height,
                packedColor, innerRadius, outerRadius
            );
        } finally {
            matrices.pop();
        }
    }
}
