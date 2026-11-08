package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class IllumoodLegacyBiomeColorMixin {
    @Inject(method = "getSkyColor()I", at = @At("RETURN"), cancellable = true)
    private void illumood$skyColor(CallbackInfoReturnable<Integer> cir) {
        Illumood module = Illumood.get();
        if (module != null) cir.setReturnValue(module.getSkyColor(cir.getReturnValue()));
    }

    @Inject(method = "getFogColor()I", at = @At("RETURN"), cancellable = true)
    private void illumood$fogColor(CallbackInfoReturnable<Integer> cir) {
        Illumood module = Illumood.get();
        if (module != null) cir.setReturnValue(module.getFogColor(cir.getReturnValue()));
    }
}
