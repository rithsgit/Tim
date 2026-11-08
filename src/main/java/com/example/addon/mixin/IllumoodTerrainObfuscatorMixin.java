package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class IllumoodTerrainObfuscatorMixin {

    @Inject(method = "getRenderingSeed", at = @At("HEAD"), cancellable = true)
    private void illumood$obfuscateTextureRotation(BlockPos pos, CallbackInfoReturnable<Long> cir) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldObfuscateTerrain()) {
            // Fixed constant seed so block texture rotations cannot be reversed to coordinates
            cir.setReturnValue(42069L);
        }
    }

    @Inject(method = "getModelOffset", at = @At("HEAD"), cancellable = true)
    private void illumood$obfuscateModelOffset(BlockPos pos, CallbackInfoReturnable<Vec3d> cir) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldObfuscateTerrain()) {
            // Centers grass, flowers, and bamboo so sub-block offsets leak zero coordinate data
            cir.setReturnValue(Vec3d.ZERO);
        }
    }
}