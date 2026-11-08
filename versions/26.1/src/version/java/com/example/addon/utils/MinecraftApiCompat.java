package com.example.addon.utils;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.BooleanSupplier;

public final class MinecraftApiCompat {
    private MinecraftApiCompat() {
    }

    public static String gameVersionName() {
        return SharedConstants.getCurrentVersion().name();
    }

    public static void drawScaledItem(GuiGraphicsExtractor context, ItemStack stack, float x, float y, float scale) {
        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);
        context.item(stack, 0, 0);
        context.pose().popMatrix();
    }

    public static void disconnect(Minecraft client) {
        if (client.level != null) client.level.disconnect(Component.empty());
        client.disconnectFromWorld(Component.empty());
    }

    public static Button mouseOnlyButton(Component label, Button.OnPress action,
                                               int x, int y, int width, int height,
                                               Tooltip tooltip, BooleanSupplier hasData) {
        Button button = new MouseOnlyButton(label, action, x, y, width, height, hasData);
        if (tooltip != null) button.setTooltip(tooltip);
        return button;
    }

    private static final class MouseOnlyButton extends Button {
        private final BooleanSupplier hasData;

        private MouseOnlyButton(net.minecraft.network.chat.Component label, OnPress action, int x, int y, int width, int height,
                                BooleanSupplier hasData) {
            super(x, y, width, height, label, action, DEFAULT_NARRATION);
            this.hasData = hasData;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            onPress.onPress(this);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            if (hasData != null) active = hasData.getAsBoolean();
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
            return false;
        }
    }

    public static void renderPearlBeam(Minecraft client, Render3DEvent event, Vec3 base,
                                       Vec3 cameraPos, Identifier texture, float tickDelta,
                                       long worldTime, int height, int packedColor,
                                       float innerRadius, float outerRadius) {
        PoseStack matrices = event.matrices;
        matrices.pushPose();
        try {
            matrices.translate(base.x - cameraPos.x - 0.5, base.y - cameraPos.y, base.z - cameraPos.z - 0.5);
            var commandQueue = client.gameRenderer.getSubmitNodeStorage();
            BeaconRenderer.submitBeaconBeam(
                matrices, commandQueue, texture, 1.0f, Math.floorMod(worldTime, 40L) + tickDelta, 0, height,
                packedColor, innerRadius, outerRadius
            );
        } finally {
            matrices.popPose();
        }
    }
}
