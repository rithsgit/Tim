package com.example.addon.mixin;

import com.example.addon.utils.OceanBreachMovementInput;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientInput.class)
public class OceanBreachMovementMixin implements OceanBreachMovementInput {
    @Shadow protected Vec2 moveVector;

    @Override
    public void tim$setBreachMovement(float sideways, float forward) {
        moveVector = new Vec2(sideways, forward);
    }
}
