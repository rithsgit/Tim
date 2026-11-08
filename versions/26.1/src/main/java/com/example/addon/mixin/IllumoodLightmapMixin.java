package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class IllumoodLightmapMixin {
    @Inject(method = "extract", at = @At("RETURN"))
    private void illumood$applyLight(LightmapRenderState state, float tickProgress, CallbackInfo ci) {
        Illumood module = Illumood.get();
        if (module == null || !state.needsUpdate || state.ambientColor == null) return;
        Vector3fc ambient = state.ambientColor;
        state.ambientColor = new Vector3f(
            module.applyLightFloor(ambient.x()),
            module.applyLightFloor(ambient.y()),
            module.applyLightFloor(ambient.z())
        );
        if (module.useNightVision()) state.nightVisionEffectIntensity = 1.0f;
    }
}
