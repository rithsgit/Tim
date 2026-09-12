package com.example.addon.mixin;

import com.example.addon.modules.Handmold;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
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
    private void onBobView(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        Handmold mod = Modules.get().get(Handmold.class);
        if (mod != null && mod.shouldDisableHandBob()) ci.cancel();
    }
}