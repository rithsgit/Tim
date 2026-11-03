package com.example.addon.mixin;

import com.example.addon.utils.GlowingRegistry;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityGlowingMixin {

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void mobanom_forceGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (GlowingRegistry.isGlowing(self.getId())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void mobanom_overrideGlowColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (GlowingRegistry.isGlowing(self.getId())) {
            int argb = GlowingRegistry.getColor(self.getId());
            cir.setReturnValue(argb & 0x00FFFFFF);
        }
    }
}