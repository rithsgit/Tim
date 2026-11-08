package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class IllumoodBiomeColorMixin {
    @Inject(method = "getGrassColor(DD)I", at = @At("RETURN"), cancellable = true)
    private void illumood$grassColor(double x, double z, CallbackInfoReturnable<Integer> cir) {
        Illumood module = Illumood.get();
        if (module != null) cir.setReturnValue(module.getGrassColor(cir.getReturnValue()));
    }

    @Inject(method = "getFoliageColor()I", at = @At("RETURN"), cancellable = true)
    private void illumood$foliageColor(CallbackInfoReturnable<Integer> cir) {
        Illumood module = Illumood.get();
        if (module != null) cir.setReturnValue(module.getFoliageColor(cir.getReturnValue()));
    }

    @Inject(method = "getWaterColor()I", at = @At("RETURN"), cancellable = true)
    private void illumood$waterColor(CallbackInfoReturnable<Integer> cir) {
        Illumood module = Illumood.get();
        if (module != null) cir.setReturnValue(module.getWaterColor(cir.getReturnValue()));
    }

}