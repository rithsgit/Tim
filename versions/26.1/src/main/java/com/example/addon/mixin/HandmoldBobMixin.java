package com.example.addon.mixin;

import com.example.addon.modules.Handmold;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class HandmoldBobMixin {

    @Inject(
        method = "bobView",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onBobView(CameraRenderState camera, PoseStack matrices, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;
        Handmold mod = modules.get(Handmold.class);
        if (mod != null && mod.shouldDisableHandBob()) ci.cancel();
    }
}