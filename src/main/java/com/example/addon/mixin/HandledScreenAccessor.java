package com.example.addon.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int getGuiX();

    @Accessor("y")
    int getGuiY();

    @Accessor("focusedSlot")
    Slot getFocusedSlot();
}