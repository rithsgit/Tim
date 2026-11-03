package com.example.addon.mixin;

import com.example.addon.hud.SkipperHud;
import com.example.addon.modules.Skipper;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.HighlighterRegistry;

@Pseudo
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class HighlighterRegistryMixin {
    @Shadow(remap = false)
    public abstract void register(AbstractHighlighter highlighter);

    @Inject(method = "<init>", at = @At("TAIL"), require = 0, remap = false)
    private void onInit(CallbackInfo ci) {
        register(new SkipperHud((cx, cz) -> {
            Skipper skipper = Modules.get().get(Skipper.class);
            return (skipper != null && skipper.isActive()) ? skipper.getXaeroColor(cx, cz) : 0;
        }));
    }
}