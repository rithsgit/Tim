package com.example.addon.mixin;

import com.example.addon.modules.Illumood;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightmapTextureManager.class)
public class IllumoodLightmapMixin {
    @Redirect(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/dimension/DimensionType;ambientLight()F"
        )
    )
    private float illumood$applyLightFloor(DimensionType dimensionType) {
        float ambientLight = dimensionType.ambientLight();
        Illumood module = Illumood.get();
        return module == null ? ambientLight : module.applyLightFloor(ambientLight);
    }

    @ModifyExpressionValue(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
            ordinal = 0
        )
    )
    private boolean illumood$enableNightVision(boolean original) {
        Illumood module = Illumood.get();
        return original || (module != null && module.useNightVision());
    }

    @WrapOperation(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F"
        )
    )
    private float illumood$nightVisionStrength(LivingEntity entity, float tickProgress, Operation<Float> original) {
        Illumood module = Illumood.get();
        return module != null && module.useNightVision() ? 1.0f : original.call(entity, tickProgress);
    }
}
