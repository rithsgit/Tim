package com.example.addon.mixin;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightmapRenderStateExtractor.class)
public interface LightmapTextureManagerAccessor {
    @Accessor("needsUpdate")
    void tim$setDirty(boolean dirty);
}
