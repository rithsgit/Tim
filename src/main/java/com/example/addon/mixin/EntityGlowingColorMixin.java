package com.example.addon.mixin;

import com.example.addon.utils.GlowingRegistry;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public class EntityGlowingColorMixin {

    @Inject(method = "canDrawEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void forceEntityOutlines(CallbackInfoReturnable<Boolean> cir) {
        if (!GlowingRegistry.isEmpty()) {
            cir.setReturnValue(true);
        }
    }
}