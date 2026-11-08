package com.example.addon.mixin;

import com.example.addon.utils.OceanBreachMovementInput;
import net.minecraft.client.input.Input;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Input.class)
public class OceanBreachMovementMixin implements OceanBreachMovementInput {
    @Shadow protected Vec2f movementVector;

    @Override
    public void tim$setBreachMovement(float sideways, float forward) {
        movementVector = new Vec2f(sideways, forward);
    }
}
