package com.example.addon.mixin;

import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BookEditScreen.class)
public interface BookEditScreenModernAccessor {
    @Invoker("updatePage")
    void tim$updatePage();
}
