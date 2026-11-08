package com.example.addon.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererLightmapAccessor {
    @Accessor("lightmapRenderStateExtractor")
    LightmapRenderStateExtractor tim$getLightmapExtractor();
}
