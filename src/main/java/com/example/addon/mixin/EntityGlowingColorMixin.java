package com.example.addon.mixin;

import com.example.addon.utils.GlowingRegistry;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts WorldRenderer.renderEntity() to override the spectral outline
 * color for entities registered in GlowingRegistry.
 *
 * Minecraft resolves outline color from the entity's team before this method
 * runs, defaulting to white (255, 255, 255) for teamless entities. We replace
 * that with the ARGB color stored per-entity in GlowingRegistry.
 */
@Mixin(WorldRenderer.class)
public class EntityGlowingColorMixin {

    @Inject(
        method = "renderEntity",
        at = @At("HEAD")
    )
    private void illushine_overrideOutlineColor(
            Entity entity,
            double cameraX, double cameraY, double cameraZ,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            CallbackInfo ci) {

        if (!(vertexConsumers instanceof OutlineVertexConsumerProvider outline)) return;
        if (!GlowingRegistry.isGlowing(entity.getId())) return;

        int argb = GlowingRegistry.getColor(entity.getId());
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8)  & 0xFF;
        int b =  argb        & 0xFF;
        int a = (argb >> 24) & 0xFF;

        outline.setColor(r, g, b, a);
    }
}