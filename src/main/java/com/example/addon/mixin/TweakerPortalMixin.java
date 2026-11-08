package com.example.addon.mixin;

import com.example.addon.modules.Tweaker;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerEntity.class)
public class TweakerPortalMixin {
    @WrapWithCondition(method = "tickNausea", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;closeHandledScreen()V"))
    private boolean tim$keepPortalHandlerOpen(ClientPlayerEntity player) {
        return !tim$shouldKeepScreenOpen();
    }

    @WrapWithCondition(method = "tickNausea", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    private boolean tim$keepPortalScreenOpen(MinecraftClient client, Screen screen) {
        return !tim$shouldKeepScreenOpen();
    }

    @Unique
    private boolean tim$shouldKeepScreenOpen() {
        Tweaker tweaker = Modules.get().get(Tweaker.class);
        return tweaker != null && tweaker.keepPortalScreenOpen();
    }
}
