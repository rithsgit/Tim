package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.WeightedAttributeList;
import net.minecraft.world.attribute.WorldEnvironmentAttributeAccess;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldEnvironmentAttributeAccess.class)
public class IllumoodEnvironmentAttributeMixin {
    @Inject(
        method = "getAttributeValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/world/attribute/WeightedAttributeList;)Ljava/lang/Object;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void illumood$environmentColor(
        EnvironmentAttribute<?> attribute,
        Vec3d position,
        WeightedAttributeList modifiers,
        CallbackInfoReturnable<Object> cir
    ) {
        Illumood module = Illumood.get();
        if (module == null || !module.shouldOverrideBiomeColors()) return;

        if (attribute == EnvironmentAttributes.SKY_COLOR_VISUAL) {
            cir.setReturnValue(module.getSkyColor((Integer) cir.getReturnValue()));
        } else if (attribute == EnvironmentAttributes.FOG_COLOR_VISUAL) {
            cir.setReturnValue(module.getFogColor((Integer) cir.getReturnValue()));
        }
    }
}
