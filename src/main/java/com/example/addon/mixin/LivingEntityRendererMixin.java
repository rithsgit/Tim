package com.example.addon.mixin;

import com.example.addon.modules.Illushine;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Unique
    private static final ThreadLocal<MobEntity> illushine$currentMob = new ThreadLocal<>();

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void illushine$captureEntity(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        illushine$currentMob.set(entity instanceof MobEntity mob ? mob : null);
    }

    @Inject(method = "scale(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V", at = @At("TAIL"))
    private void illushine$onScale(LivingEntityRenderState state, MatrixStack matrices, CallbackInfo ci) {
        MobEntity mob = illushine$currentMob.get();
        if (mob != null) {
            // SAFETY: Prevent freeze if the mob dies or the world unloads while rendering
            if (!mob.isAlive()) {
                illushine$currentMob.set(null);
                return;
            }

            Illushine illushine = Modules.get().get(Illushine.class);
            if (illushine == null || !illushine.isActive()) return;

            double scale = illushine.getMobScale(mob);
            
            if (scale != 1.0) {
                matrices.scale((float) scale, (float) scale, (float) scale);
            }
            
            illushine$currentMob.set(null); // Clear to prevent memory leaks
        }
    }
}