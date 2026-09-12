package com.example.addon.mixin;

import com.example.addon.modules.EightToOne;
import com.example.addon.modules.Gatekeeper;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * This mixin helps portal modules detect block changes more efficiently
 * by marking chunks as dirty when portal-related blocks are modified.
 */
@Mixin(World.class)
public abstract class PortalTrackerMixin {

    /**
     * Monitor portal block state changes and mark chunks for re-scanning.
     */
    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("RETURN")
    )
    private void onSetBlockState(
        BlockPos pos,
        BlockState newState,
        int flags,
        int maxUpdateDepth,
        CallbackInfoReturnable<Boolean> cir
    ) {
        // Only process if the block state actually changed
        if (!cir.getReturnValue()) return;

        // We trigger if the NEW block is a portal (placement) 
        // Note: To detect removal, you'd ideally check the state before replacement,
        // but checking the new state is the most common use case for "marking dirty".
        boolean isPortalRelated = newState.isOf(Blocks.NETHER_PORTAL) ||
                                  newState.isOf(Blocks.END_PORTAL) ||
                                  newState.isOf(Blocks.END_GATEWAY) ||
                                  newState.isOf(Blocks.END_PORTAL_FRAME);

        if (isPortalRelated) {
            EightToOne eto = Modules.get().get(EightToOne.class);
            if (eto != null && eto.isActive()) {
                eto.markChunkDirty(new ChunkPos(pos));
            }

            Gatekeeper gk = Modules.get().get(Gatekeeper.class);
            if (gk != null && gk.isActive()) {
                gk.markChunkDirty(new ChunkPos(pos));
            }
        }
    }
}
