package com.example.addon.mixin;

import com.example.addon.modules.ThirdSight;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class ThirdSightMouseMixin {
    @Shadow private Minecraft minecraft;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onUpdateMouse(CallbackInfo ci) {
        ThirdSight module = Modules.get().get(ThirdSight.class);
        if (module == null || !module.isFreeLookActive()) return;
        if (minecraft.player == null || minecraft.screen != null) return;

        ci.cancel();

        double vanillaSens = minecraft.options.sensitivity().get() * 0.6 + 0.2;
        double scale = vanillaSens * vanillaSens * vanillaSens * module.sensitivity.get();

        double dx = accumulatedDX * scale;
        double dy = accumulatedDY * scale;

        if (minecraft.options.invertMouseY().get()) dy = -dy;

        module.cameraYaw += (float) dx;
        module.cameraPitch = Math.max(-90.0f, Math.min(90.0f, module.cameraPitch + (float) dy));
    }
}
