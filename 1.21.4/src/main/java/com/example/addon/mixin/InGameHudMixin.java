package com.example.addon.mixin;

import com.example.addon.modules.Illushine;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(
        method = "renderCrosshair",
        at = @At("HEAD"),
        cancellable = true
    )
    private void illushine$cancelCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Illushine mod = Modules.get().get(Illushine.class);
        if (mod != null && mod.isActive() && mod.getCrosshairMode() != Illushine.CrosshairMode.None) {
            mod.drawCrosshair(context);
            ci.cancel();
        }
    }
}