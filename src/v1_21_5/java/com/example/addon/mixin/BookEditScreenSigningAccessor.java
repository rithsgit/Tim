package com.example.addon.mixin;

import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BookEditScreen.class)
public interface BookEditScreenSigningAccessor {
    @Accessor("signing")
    void tim$setSigning(boolean signing);

    @Accessor("title")
    void tim$setTitle(String title);

    @Invoker("updateButtons")
    void tim$updateButtons();
}
