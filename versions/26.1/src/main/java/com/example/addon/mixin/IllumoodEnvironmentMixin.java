package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class IllumoodEnvironmentMixin {

    @Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true, require = 0)
    private void illumood$disableClouds(CallbackInfo ci) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldDisableClouds()) {
            ci.cancel();
        }
    }

    @Inject(method = "addWeatherPass", at = @At("HEAD"), cancellable = true, require = 0)
    private void illumood$disableWeather(CallbackInfo ci) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldDisableWeather()) {
            ci.cancel();
        }
    }
}