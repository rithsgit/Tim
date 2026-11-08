package com.example.addon.mixin;

import com.example.addon.utils.OceanBreachMovementInput;
import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Input.class)
public class OceanBreachMovementMixin implements OceanBreachMovementInput {
    @Shadow public float movementSideways;
    @Shadow public float movementForward;

    @Override
    public void tim$setBreachMovement(float sideways, float forward) {
        movementSideways = sideways;
        movementForward = forward;
    }
}
