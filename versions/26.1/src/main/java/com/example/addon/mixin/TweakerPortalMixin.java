package com.example.addon.mixin;

import com.example.addon.modules.Tweaker;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public class TweakerPortalMixin {
    @WrapWithCondition(method = "handlePortalTransitionEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;closeContainer()V"))
    private boolean tim$keepPortalHandlerOpen(LocalPlayer player) {
        return !tim$shouldKeepScreenOpen();
    }

    @WrapWithCondition(method = "handlePortalTransitionEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private boolean tim$keepPortalScreenOpen(Minecraft client, Screen screen) {
        return !tim$shouldKeepScreenOpen();
    }

    @Unique
    private boolean tim$shouldKeepScreenOpen() {
        Tweaker tweaker = Modules.get().get(Tweaker.class);
        return tweaker != null && tweaker.keepPortalScreenOpen();
    }
}
