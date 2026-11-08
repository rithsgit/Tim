package com.example.addon.mixin;

import com.example.addon.modules.ThirdSight;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class ThirdSightCameraMixin {

    @org.spongepowered.asm.mixin.Shadow
    protected abstract void move(float forward, float up, float right);

    /**
     * Intercept the distance passed to clipToSpace so we always get
     * our configured distance and blocks never pull the camera closer.
     */
    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void onClipToSpace(float desiredDistance, CallbackInfoReturnable<Float> cir) {
        Modules modules = Modules.get();
        if (modules == null) return;
        ThirdSight module = modules.get(ThirdSight.class);
        if (module == null || !module.isActive()) return;

        if (module.isNoDistanceActive() && !module.isZooming()) return;

        cir.setReturnValue((float) module.getDistance());
    }

    /**
     * When free-look is active, replace the yaw passed to
     * Camera#setRotation with our independent cameraYaw so vanilla's
     * camera positioning logic uses our angle from the start.
     */
    @ModifyArg(
        method = "alignWithEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setRotation(FF)V"
        ),
        index = 0
    )
    private float modifyCameraYaw(float yaw) {
        Modules modules = Modules.get();
        if (modules == null) return yaw;
        ThirdSight module = modules.get(ThirdSight.class);
        if (module == null || !module.isFreeLookActive()) return yaw;
        return module.cameraYaw;
    }

    @ModifyArg(
        method = "alignWithEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setRotation(FF)V"
        ),
        index = 1
    )
    private float modifyCameraPitch(float pitch) {
        Modules modules = Modules.get();
        if (modules == null) return pitch;
        ThirdSight module = modules.get(ThirdSight.class);
        if (module == null || !module.isFreeLookActive()) return pitch;
        return module.cameraPitch;
    }
}