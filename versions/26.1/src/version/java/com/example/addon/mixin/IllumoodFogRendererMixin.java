package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class IllumoodFogRendererMixin {
    @Shadow @Final private GpuBuffer emptyBuffer;
    @Shadow @Final private static int FOG_UBO_SIZE;

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void illumood$disableFog(FogRenderer.FogMode fogType, CallbackInfoReturnable<GpuBufferSlice> cir) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldDisableFog()) {
            cir.setReturnValue(emptyBuffer.slice(0, FOG_UBO_SIZE));
        }
    }
}
