package com.example.addon.mixin;

import com.example.addon.modules.Timethrottle;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ChunkBatchSizeCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkBatchSizeCalculator.class)
public class ChunkBatchSizeCalculatorMixin {
    @Inject(method = "getDesiredChunksPerTick()F", at = @At("RETURN"), cancellable = true)
    private void timethrottle$boost(CallbackInfoReturnable<Float> cir) {
        Timethrottle module = Modules.get().get(Timethrottle.class);
        if (module != null && module.isActive()) {
            cir.setReturnValue(module.modifyChunkRate(cir.getReturnValueF()));
        }
    }
}