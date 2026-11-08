package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.SpatialAttributeInterpolator;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnvironmentAttributeSystem.class)
public class IllumoodEnvironmentAttributeMixin {
    @Inject(
        method = "getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/attribute/SpatialAttributeInterpolator;)Ljava/lang/Object;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void illumood$environmentColor(
        EnvironmentAttribute<?> attribute,
        Vec3 position,
        SpatialAttributeInterpolator modifiers,
        CallbackInfoReturnable<Object> cir
    ) {
        Illumood module = Illumood.get();
        if (module == null || !module.shouldOverrideBiomeColors()) return;

        if (attribute == EnvironmentAttributes.SKY_COLOR) {
            cir.setReturnValue(module.getSkyColor((Integer) cir.getReturnValue()));
        } else if (attribute == EnvironmentAttributes.FOG_COLOR) {
            cir.setReturnValue(module.getFogColor((Integer) cir.getReturnValue()));
        }
    }
}
