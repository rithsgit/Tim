package com.example.addon.utils;

import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

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
        int red = packedColor >> 16 & 0xFF;
        int green = packedColor >> 8 & 0xFF;
        int blue = packedColor & 0xFF;
        int alpha = packedColor >>> 24 & 0xFF;
        SettingColor beamColor = new SettingColor(red, green, blue, alpha);
        event.renderer.box(
            new Box(base.x - innerRadius, base.y, base.z - innerRadius,
                    base.x + innerRadius, base.y + height, base.z + innerRadius),
            beamColor, beamColor, ShapeMode.Both, 0
        );
    }
}
