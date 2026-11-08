package com.example.addon.mixin;

import com.example.addon.modules.ElytraAssistant;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class ElytraAssistantMixin {
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void onInteractItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.FIREWORK_ROCKET)) {
            ElytraAssistant module = Modules.get().get(ElytraAssistant.class);
            if (module != null && module.shouldPreventRocketUse()) {
                cir.setReturnValue(InteractionResult.FAIL);
            }
        }
    }
}