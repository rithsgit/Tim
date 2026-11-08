package com.example.addon.mixin;

import com.example.addon.utils.HandledScreenActions;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMouseMixin {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tim$onMouseClicked(MouseButtonEvent click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() == 2 && HandledScreenActions.handleMiddleClick(hoveredSlot)) cir.setReturnValue(true);
    }
}
