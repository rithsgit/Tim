package com.example.addon.mixin;

import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(BookEditScreen.class)
public interface BookEditScreenAccessor {
    @Accessor("pages")
    List<String> tim$getPages();

    @Accessor("currentPage")
    void tim$setCurrentPage(int currentPage);
}