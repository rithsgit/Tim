package com.example.addon.modules;

import com.example.addon.mixin.BookEditScreenAccessor;
import com.example.addon.mixin.BookEditScreenSigningAccessor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.util.Hand;

import java.util.List;

public final class ManuscriptScreenCompat {
    private ManuscriptScreenCompat() {
    }

    public static boolean isBookWorkflowScreen(Screen screen) {
        return screen instanceof BookEditScreen;
    }

    public static void update(BookEditScreen screen, List<String> pages, boolean sign, String title, Hand hand) {
        BookEditScreenAccessor pageAccessor = (BookEditScreenAccessor) screen;
        BookEditScreenSigningAccessor signingAccessor = (BookEditScreenSigningAccessor) screen;

        pageAccessor.tim$getPages().clear();
        pageAccessor.tim$getPages().addAll(pages);
        pageAccessor.tim$setCurrentPage(pages.size() - 1);
        signingAccessor.tim$setSigning(sign);
        if (sign) signingAccessor.tim$setTitle(title);
        signingAccessor.tim$updateButtons();
    }
}
