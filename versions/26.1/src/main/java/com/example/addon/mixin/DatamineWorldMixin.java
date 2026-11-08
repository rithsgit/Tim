package com.example.addon.mixin;

import com.example.addon.modules.Datamine;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class DatamineWorldMixin {

    @Inject(
        method = "setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
        at = @At("RETURN")
    )
    private void datamine$onBlockStateChanged(
        BlockPos pos,
        BlockState state,
        CallbackInfoReturnable<Boolean> cir
    ) {
        Level world = (Level) (Object) this;
        if (!world.isClientSide() || !cir.getReturnValue()) return;

        Datamine datamine = Modules.get().get(Datamine.class);
        if (datamine != null && datamine.isActive()) {
            datamine.onServerBlockUpdate(pos, state);
        }
    }
}
