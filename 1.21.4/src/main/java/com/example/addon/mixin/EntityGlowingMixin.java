package com.example.addon.mixin;

import com.example.addon.utils.GlowingRegistry;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces isGlowing() to return true for any entity registered in GlowingRegistry.
 * This is the only reliable way to trigger Minecraft's outline shader on the client.
 */
@Mixin(Entity.class)
public class EntityGlowingMixin {

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void mobanom_forceGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (GlowingRegistry.isGlowing(self.getId())) {
            cir.setReturnValue(true);
        }
    }
}