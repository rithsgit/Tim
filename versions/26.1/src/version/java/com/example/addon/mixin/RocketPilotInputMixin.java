package com.example.addon.mixin;

import com.example.addon.modules.RocketPilot;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class RocketPilotInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        RocketPilot rocketPilot = Modules.get().get(RocketPilot.class);
        if (rocketPilot != null && rocketPilot.isActive() && rocketPilot.useFreeLookY.get()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.isFallFlying()) {
                ClientInput input = (ClientInput) (Object) this;
                Input current = input.keyPresses;
                input.keyPresses = new Input(false, false, false, false, current.jump(), current.shift(), current.sprint());
            }
        }
    }
}
