package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class IllumoodTerrainObfuscatorMixin {

    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void illumood$obfuscateTextureRotation(BlockPos pos, CallbackInfoReturnable<Long> cir) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldObfuscateTerrain()) {
            // Fixed constant seed so block texture rotations cannot be reversed to coordinates
            cir.setReturnValue(42069L);
        }
    }

    @Inject(method = "getOffset", at = @At("HEAD"), cancellable = true)
    private void illumood$obfuscateModelOffset(BlockPos pos, CallbackInfoReturnable<Vec3> cir) {
        Illumood module = Illumood.get();
        if (module != null && module.shouldObfuscateTerrain()) {
            // Centers grass, flowers, and bamboo so sub-block offsets leak zero coordinate data
            cir.setReturnValue(Vec3.ZERO);
        }
    }
}