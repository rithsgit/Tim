package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Fog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BackgroundRenderer.class)
public class IllumoodFogMixin {
    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true, require = 0)
    private static void illumood$disableFog(CallbackInfoReturnable<Fog> cir) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldDisableFog()) cir.setReturnValue(Fog.DUMMY);
    }
}
