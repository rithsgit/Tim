package com.example.addon.mixin;

import com.example.addon.modules.Timethrottle;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InactivityFpsLimiter.class)
public class InactivityFpsLimiterMixin {
    @Inject(method = "update", at = @At("RETURN"), cancellable = true)
    private void timethrottle$unfocusedLimit(CallbackInfoReturnable<Integer> cir) {
        Timethrottle module = Modules.get().get(Timethrottle.class);
        if (module == null || !module.isActive()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.isWindowFocused()) return;

        int limit = module.getUnfocusedFpsLimit();
        if (limit > 0) {
            cir.setReturnValue(Math.min(limit, cir.getReturnValue()));
        }
    }
}