package com.example.addon.mixin;

import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BookEditScreen.class)
public interface BookEditScreenModernAccessor {
    @Invoker("updatePageContent")
    void tim$updatePage();
}
