package com.example.addon.modules;

import com.example.addon.mixin.BookEditScreenAccessor;
import com.example.addon.mixin.BookEditScreenModernAccessor;
import com.example.addon.mixin.BookSigningScreenAccessor;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.world.InteractionHand;

public final class ManuscriptScreenCompat {
    private ManuscriptScreenCompat() {
    }

    public static boolean isBookWorkflowScreen(Screen screen) {
        return screen instanceof BookEditScreen || screen instanceof BookSignScreen;
    }

    public static void update(BookEditScreen screen, List<String> pages, boolean sign, String title, InteractionHand hand) {
        BookEditScreenAccessor pageAccessor = (BookEditScreenAccessor) screen;
        pageAccessor.tim$getPages().clear();
        pageAccessor.tim$getPages().addAll(pages);
        pageAccessor.tim$setCurrentPage(pages.size() - 1);
        ((BookEditScreenModernAccessor) screen).tim$updatePage();

        if (sign) {
            BookSignScreen signingScreen = new BookSignScreen(screen, Minecraft.getInstance().player, hand, pages);
            ((BookSigningScreenAccessor) signingScreen).tim$setBookTitle(title);
            Minecraft.getInstance().setScreen(signingScreen);
        }
    }
}
