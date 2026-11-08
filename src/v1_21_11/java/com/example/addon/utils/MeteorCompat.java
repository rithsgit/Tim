package com.example.addon.utils;

import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import com.example.addon.mixin.KeyBindingAccessor;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public final class MeteorCompat {
    private MeteorCompat() {}

    public static int key(KeyEvent event) {
        return event.key();
    }

    public static int modifiers(KeyEvent event) {
        return event.modifiers();
    }

    public static InputUtil.Key boundKey(KeyBinding binding) {
        return ((KeyBindingAccessor) binding).tim$getBoundKey();
    }
}
