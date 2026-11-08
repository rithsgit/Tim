package com.example.addon.mixin;

import com.example.addon.modules.Tweaker;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class TweakerGhostHandsMixin {
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void tim$airPlaceOnce(CallbackInfo info) {
        Tweaker tweaker = Modules.get().get(Tweaker.class);
        if (tweaker != null && tweaker.handleAirPlace()) info.cancel();
    }

    @ModifyExpressionValue(method = "doItemUse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;crosshairTarget:Lnet/minecraft/util/hit/HitResult;", ordinal = 2))
    private HitResult tim$containerInteractionType(HitResult original) {
        return tim$containerTarget(original);
    }

    @ModifyExpressionValue(method = "doItemUse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;crosshairTarget:Lnet/minecraft/util/hit/HitResult;", ordinal = 4))
    private HitResult tim$containerInteractionTarget(HitResult original) {
        return tim$containerTarget(original);
    }

    @Unique
    private HitResult tim$containerTarget(HitResult original) {
        Tweaker tweaker = Modules.get().get(Tweaker.class);
        return tweaker == null ? original : tweaker.containerTarget(original);
    }
}
