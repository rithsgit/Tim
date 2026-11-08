package com.example.addon.mixin;

import com.example.addon.modules.Tweaker;
import com.example.addon.modules.RocketPilot;
import com.example.addon.utils.WaterEscapeNavigator;
import com.example.addon.utils.OceanBreachMovementInput;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class TweakerSafeWalkInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void tim$autoSneak(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.input != (Object) this) return;
        RocketPilot pilot = Modules.get().get(RocketPilot.class);
        if (pilot != null) {
            Boolean jump = pilot.takeoffJumpOverride();
            if (jump != null || pilot.suppressTakeoffSneak()) {
                keyPresses = new Input(keyPresses.forward(), keyPresses.backward(), keyPresses.left(),
                    keyPresses.right(), jump != null ? jump : keyPresses.jump(), false, keyPresses.sprint());
                return;
            }
        }
        Tweaker tweaker = Modules.get().get(Tweaker.class);
        if (tweaker == null) return;
        WaterEscapeNavigator.SwimInput waterMovement = tweaker.connectedWaterSwimInput();
        if (waterMovement != null) {
            client.player.setYRot(waterMovement.yaw());
            client.player.setXRot(waterMovement.pitch());
            ((OceanBreachMovementInput) this).tim$setBreachMovement(0, waterMovement.forward() ? 1 : 0);
            keyPresses = new Input(waterMovement.forward(), false, false, false,
                waterMovement.jump(), waterMovement.sneak(), waterMovement.sprint());
            return;
        }
        Vec2 escapeMovement = tweaker.underwaterEscapeMovement();
        if (escapeMovement != null) {
            ((OceanBreachMovementInput) this).tim$setBreachMovement(escapeMovement.x, escapeMovement.y);
            keyPresses = new Input(escapeMovement.y > 0, escapeMovement.y < 0,
                escapeMovement.x > 0, escapeMovement.x < 0, true, false, false);
            return;
        }
        Vec2 breachMovement = tweaker.oceanBreachCenteringMovement();
        if (breachMovement != null) {
            ((OceanBreachMovementInput) this).tim$setBreachMovement(breachMovement.x, breachMovement.y);
            keyPresses = new Input(breachMovement.y > 0, breachMovement.y < 0,
                breachMovement.x > 0, breachMovement.x < 0, false, false, false);
            return;
        }
        Vec2 swim = tweaker.waterAscentSwimMovement();
        if (swim != null) {
            ((OceanBreachMovementInput) this).tim$setBreachMovement(swim.x, swim.y);
            keyPresses = new Input(swim.y > 0, swim.y < 0, swim.x > 0, swim.x < 0,
                true, false, tweaker.waterAscentSprintSwimming());
            return;
        }
        Boolean takeoffSneak = tweaker.takeoffSneakOverride();
        Boolean takeoffJump = tweaker.takeoffJumpOverride();
        boolean jump = takeoffJump != null ? takeoffJump : keyPresses.jump();
        boolean sneak = takeoffSneak != null ? takeoffSneak
            : keyPresses.shift() || (!jump && tweaker.shouldAutoSneak());
        if (sneak == keyPresses.shift() && jump == keyPresses.jump()) return;

        keyPresses = new Input(keyPresses.forward(), keyPresses.backward(), keyPresses.left(),
            keyPresses.right(), jump, sneak, keyPresses.sprint());
    }
}
