package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class IllumoodOverlayMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void illumood$cleanFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldCleanOverlays()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void illumood$cleanWater(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldCleanOverlays()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void illumood$cleanInWall(Sprite sprite, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldCleanOverlays()) {
            ci.cancel();
        }
    }
}