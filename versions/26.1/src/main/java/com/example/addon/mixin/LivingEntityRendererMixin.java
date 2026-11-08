package com.example.addon.mixin;

import com.example.addon.modules.Illushine;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Unique
    private static final ThreadLocal<Mob> illushine$currentMob = new ThreadLocal<>();

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void illushine$captureEntity(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        illushine$currentMob.set(entity instanceof Mob mob ? mob : null);
    }

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void illushine$onScale(LivingEntityRenderState state, PoseStack matrices, CallbackInfo ci) {
        Mob mob = illushine$currentMob.get();
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