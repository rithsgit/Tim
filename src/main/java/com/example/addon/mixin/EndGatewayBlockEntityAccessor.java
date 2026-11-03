package com.example.addon.mixin;

import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EndGatewayBlockEntity.class)
public interface EndGatewayBlockEntityAccessor {

    @Accessor("exitPortalPos")
    @Nullable
    BlockPos getExitPortalPos();
}