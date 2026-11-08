package com.example.addon.mixin;

import com.example.addon.modules.RocketPilot;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class RocketPilotMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow public abstract net.minecraft.world.phys.Vec3 position();
    @Shadow public abstract Entity entity();

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void onUpdate(float tickDelta, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;
        RocketPilot rocketPilot = modules.get(RocketPilot.class);
        if (rocketPilot != null && rocketPilot.isActive() && rocketPilot.useFreeLookY.get()) {
            if (entity() instanceof LivingEntity living && living.isFallFlying()) {
                setPosition(position().x, rocketPilot.freeLookY.get(), position().z);
            }
        }
    }
}