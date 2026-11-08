package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class IllumoodOverlayMixin {

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true, require = 0)
    private static void illumood$cleanFire(PoseStack matrices, MultiBufferSource vertexConsumers, CallbackInfo ci) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldCleanOverlays()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true, require = 0)
    private static void illumood$cleanWater(Minecraft client, PoseStack matrices, MultiBufferSource vertexConsumers, CallbackInfo ci) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldCleanOverlays()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true, require = 0)
    private static void illumood$cleanInWall(TextureAtlasSprite sprite, PoseStack matrices, MultiBufferSource vertexConsumers, CallbackInfo ci) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldCleanOverlays()) {
            ci.cancel();
        }
    }
}