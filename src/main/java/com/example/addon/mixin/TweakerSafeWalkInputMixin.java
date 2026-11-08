package com.example.addon.mixin;

import com.example.addon.modules.Tweaker;
import com.example.addon.modules.RocketPilot;
import com.example.addon.utils.WaterEscapeNavigator;
import com.example.addon.utils.OceanBreachMovementInput;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class TweakerSafeWalkInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void tim$autoSneak(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.input != (Object) this) return;
        RocketPilot pilot = Modules.get().get(RocketPilot.class);
        if (pilot != null) {
            Boolean jump = pilot.takeoffJumpOverride();
            if (jump != null || pilot.suppressTakeoffSneak()) {
                playerInput = new PlayerInput(playerInput.forward(), playerInput.backward(), playerInput.left(),
                    playerInput.right(), jump != null ? jump : playerInput.jump(), false, playerInput.sprint());
                return;
            }
        }
        Tweaker tweaker = Modules.get().get(Tweaker.class);
        if (tweaker == null) return;
        WaterEscapeNavigator.SwimInput waterMovement = tweaker.connectedWaterSwimInput();
        if (waterMovement != null) {
            client.player.setYaw(waterMovement.yaw());
            client.player.setPitch(waterMovement.pitch());
            ((OceanBreachMovementInput) this).tim$setBreachMovement(0, waterMovement.forward() ? 1 : 0);
            playerInput = new PlayerInput(waterMovement.forward(), false, false, false,
                waterMovement.jump(), waterMovement.sneak(), waterMovement.sprint());
            return;
        }
        Vec2f escapeMovement = tweaker.underwaterEscapeMovement();
        if (escapeMovement != null) {
            ((OceanBreachMovementInput) this).tim$setBreachMovement(escapeMovement.x, escapeMovement.y);
            playerInput = new PlayerInput(escapeMovement.y > 0, escapeMovement.y < 0,
                escapeMovement.x > 0, escapeMovement.x < 0, true, false, false);
            return;
        }
        Vec2f breachMovement = tweaker.oceanBreachCenteringMovement();
        if (breachMovement != null) {
            ((OceanBreachMovementInput) this).tim$setBreachMovement(breachMovement.x, breachMovement.y);
            playerInput = new PlayerInput(breachMovement.y > 0, breachMovement.y < 0,
                breachMovement.x > 0, breachMovement.x < 0, false, false, false);
            return;
        }
        Vec2f swim = tweaker.waterAscentSwimMovement();
        if (swim != null) {
            ((OceanBreachMovementInput) this).tim$setBreachMovement(swim.x, swim.y);
            playerInput = new PlayerInput(swim.y > 0, swim.y < 0, swim.x > 0, swim.x < 0,
                true, false, tweaker.waterAscentSprintSwimming());
            return;
        }
        Boolean takeoffSneak = tweaker.takeoffSneakOverride();
        Boolean takeoffJump = tweaker.takeoffJumpOverride();
        boolean jump = takeoffJump != null ? takeoffJump : playerInput.jump();
        boolean sneak = takeoffSneak != null ? takeoffSneak
            : playerInput.sneak() || (!jump && tweaker.shouldAutoSneak());
        if (sneak == playerInput.sneak() && jump == playerInput.jump()) return;

        playerInput = new PlayerInput(playerInput.forward(), playerInput.backward(), playerInput.left(),
            playerInput.right(), jump, sneak, playerInput.sprint());
    }
}
