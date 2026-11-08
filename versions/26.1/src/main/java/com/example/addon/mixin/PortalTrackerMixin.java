package com.example.addon.mixin;

import com.example.addon.modules.EightToOne;
import com.example.addon.modules.Gatekeeper;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * This mixin helps portal modules detect block changes more efficiently
 * by marking chunks as dirty when portal-related blocks are modified.
 */
@Mixin(Level.class)
public abstract class PortalTrackerMixin {

    /**
     * Monitor portal block state changes and mark chunks for re-scanning.
     */
    @Inject(
        method = "setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
        at = @At("RETURN")
    )
    private void onSetBlockState(
        BlockPos pos,
        BlockState newState,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue()) return;

        boolean isPortalRelated = newState.is(Blocks.NETHER_PORTAL)
            || newState.is(Blocks.END_PORTAL)
            || newState.is(Blocks.END_GATEWAY)
            || newState.is(Blocks.END_PORTAL_FRAME);

        if (isPortalRelated) {
            EightToOne eto = Modules.get().get(EightToOne.class);
            if (eto != null && eto.isActive()) {
                eto.markChunkDirty(ChunkPos.containing(pos));
            }

            Gatekeeper gk = Modules.get().get(Gatekeeper.class);
            if (gk != null && gk.isActive()) {
                gk.markChunkDirty(ChunkPos.containing(pos));
            }
        }
    }
}