package com.example.addon.mixin;

import com.example.addon.modules.Datamine;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class DatamineMixin {

    @Shadow
    private int destroyDelay;

    @Inject(method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void onAttack(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> info) {
        this.datamine$mine(pos, side, info);
    }

    @Inject(method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void onUpdate(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> info) {
        this.datamine$mine(pos, side, info);
    }

    @Unique
    private void datamine$mine(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> info) {
        Minecraft client = Minecraft.getInstance();
        Datamine module = Modules.get().get(Datamine.class);

        if (module == null || !module.isActive() ||
            client.player == null || client.player.isCreative()) {
            return;
        }

        if (module.bypass(pos)) {
            this.destroyDelay = 0; // Mandatory reset to avoid server transaction desync
            return;
        }

        if (module.mine(pos, side)) info.setReturnValue(true);
    }
}