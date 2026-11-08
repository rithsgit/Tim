package com.example.addon.mixin;

import com.example.addon.modules.Tweaker;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerEntity.class)
public class TweakerWaterSprintMixin {
    @ModifyExpressionValue(
        method = "tickMovement",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z")
    )
    private boolean tim$waterSprintKey(boolean pressed) {
        MinecraftClient client = MinecraftClient.getInstance();
        Tweaker tweaker = Modules.get().get(Tweaker.class);
        return pressed || (client.player == (Object) this && tweaker != null
            && tweaker.waterSprintInputActive() && client.player.input.playerInput.sprint());
    }
}
