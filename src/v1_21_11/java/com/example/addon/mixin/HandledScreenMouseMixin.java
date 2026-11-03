package com.example.addon.mixin;

import com.example.addon.utils.HandledScreenActions;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMouseMixin {
    @Shadow protected Slot focusedSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tim$onMouseClicked(Click click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() == 2 && HandledScreenActions.handleMiddleClick(focusedSlot)) cir.setReturnValue(true);
    }
}
