package com.example.addon.mixin;

import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookSignScreen.class)
public interface BookSigningScreenAccessor {
    @Accessor("titleValue")
    void tim$setBookTitle(String title);
}
