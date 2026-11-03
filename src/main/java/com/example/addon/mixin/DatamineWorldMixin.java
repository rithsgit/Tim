package com.example.addon.mixin;

import com.example.addon.modules.Datamine;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class DatamineWorldMixin {

    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z",
        at = @At("RETURN")
    )
    private void datamine$onBlockStateChanged(
        BlockPos pos,
        BlockState state,
        CallbackInfoReturnable<Boolean> cir
    ) {
        World world = (World) (Object) this;
        if (!world.isClient() || !cir.getReturnValue()) return;

        Datamine datamine = Modules.get().get(Datamine.class);
        if (datamine != null && datamine.isActive()) {
            datamine.onServerBlockUpdate(pos, state);
        }
    }
}
