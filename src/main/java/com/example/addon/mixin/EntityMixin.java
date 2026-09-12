package com.example.addon.mixin;

import com.example.addon.modules.Illushine;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "getEyeY()D", at = @At("RETURN"), cancellable = true)
    private void onGetEyeY(CallbackInfoReturnable<Double> cir) {
        // SAFETY: Prevent crash when disconnecting
        if (MinecraftClient.getInstance().player == null) return;

        Entity self = (Entity) (Object) this;
        
        if (self instanceof PlayerEntity player && player.equals(MinecraftClient.getInstance().player)) {
            Illushine illushine = Modules.get().get(Illushine.class);
            if (illushine != null && illushine.isActive()) {
                double scale = illushine.getPlayerScale();
                if (scale != 1.0) {
                    double difference = 1.62 * (scale - 1.0);
                    cir.setReturnValue(cir.getReturnValue() + difference);
                }
            }
        }
    }
}