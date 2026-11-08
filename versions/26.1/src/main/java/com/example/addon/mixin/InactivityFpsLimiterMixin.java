package com.example.addon.mixin;

import com.example.addon.modules.Timethrottle;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public class InactivityFpsLimiterMixin {
    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void timethrottle$unfocusedLimit(CallbackInfoReturnable<Integer> cir) {
        Timethrottle module = Modules.get().get(Timethrottle.class);
        if (module == null || !module.isActive()) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.isWindowActive()) return;

        int limit = module.getUnfocusedFpsLimit();
        if (limit > 0) {
            cir.setReturnValue(Math.min(limit, cir.getReturnValue()));
        }
    }
}