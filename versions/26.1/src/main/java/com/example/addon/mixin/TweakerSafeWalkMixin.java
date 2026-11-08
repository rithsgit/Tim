package com.example.addon.mixin;

import com.example.addon.modules.Tweaker;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class TweakerSafeWalkMixin {
    @ModifyExpressionValue(method = "maybeBackOffFromEdge", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isStayingOnGroundSurface()Z"))
    private boolean tim$safeWalk(boolean original) {
        if ((Object) this != Minecraft.getInstance().player) return original;
        Tweaker tweaker = Modules.get().get(Tweaker.class);
        return original || (tweaker != null && tweaker.shouldSafeWalk());
    }
}
