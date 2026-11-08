package com.example.addon.mixin;

import com.example.addon.modules.EightToOne;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ClientPlayerEntityMixin {

    @Shadow public float portalEffectIntensity;
    @Shadow public float oPortalEffectIntensity;

    @Inject(method = "handlePortalTransitionEffect", at = @At("HEAD"), cancellable = true)
    private void onTickNausea(boolean fromPortalEffect, CallbackInfo ci) {
        if (!fromPortalEffect) return;
        EightToOne eto = Modules.get().get(EightToOne.class);
        if (eto != null && eto.isPortalGuiEnabled()) {
            this.oPortalEffectIntensity = this.portalEffectIntensity;
            this.portalEffectIntensity = 0.0f;
            ci.cancel();
        }
    }
}