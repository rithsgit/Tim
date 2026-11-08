package com.example.addon.modules;

import com.example.addon.mixin.BookEditScreenAccessor;
import com.example.addon.mixin.BookEditScreenModernAccessor;
import com.example.addon.mixin.BookSigningScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.BookSigningScreen;
import net.minecraft.util.Hand;

import java.util.List;

public final class ManuscriptScreenCompat {
    private ManuscriptScreenCompat() {
    }

    public static boolean isBookWorkflowScreen(Screen screen) {
        return screen instanceof BookEditScreen || screen instanceof BookSigningScreen;
    }

    public static void update(BookEditScreen screen, List<String> pages, boolean sign, String title, Hand hand) {
        BookEditScreenAccessor pageAccessor = (BookEditScreenAccessor) screen;
        pageAccessor.tim$getPages().clear();
        pageAccessor.tim$getPages().addAll(pages);
        pageAccessor.tim$setCurrentPage(pages.size() - 1);
        ((BookEditScreenModernAccessor) screen).tim$updatePage();

        if (sign) {
            BookSigningScreen signingScreen = new BookSigningScreen(screen, MinecraftClient.getInstance().player, hand, pages);
            ((BookSigningScreenAccessor) signingScreen).tim$setBookTitle(title);
            MinecraftClient.getInstance().setScreen(signingScreen);
        }
    }
}
