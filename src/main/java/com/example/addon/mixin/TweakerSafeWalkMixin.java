package com.example.addon.mixin;

import com.example.addon.modules.Tweaker;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
public class TweakerSafeWalkMixin {
    @ModifyExpressionValue(method = "adjustMovementForSneaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;clipAtLedge()Z"))
    private boolean tim$safeWalk(boolean original) {
        if ((Object) this != MinecraftClient.getInstance().player) return original;
        Tweaker tweaker = Modules.get().get(Tweaker.class);
        return original || (tweaker != null && tweaker.shouldSafeWalk());
    }
}
