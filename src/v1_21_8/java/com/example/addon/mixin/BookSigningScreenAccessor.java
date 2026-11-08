package com.example.addon.mixin;

import net.minecraft.client.gui.screen.ingame.BookSigningScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookSigningScreen.class)
public interface BookSigningScreenAccessor {
    @Accessor("bookTitle")
    void tim$setBookTitle(String title);
}
