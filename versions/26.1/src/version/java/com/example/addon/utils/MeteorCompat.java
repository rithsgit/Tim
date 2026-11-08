package com.example.addon.utils;

import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import net.minecraft.client.KeyMapping;
import com.example.addon.mixin.KeyBindingAccessor;
import com.mojang.blaze3d.platform.InputConstants;

public final class MeteorCompat {
    private MeteorCompat() {}

    public static int key(KeyInputEvent event) {
        return event.key();
    }

    public static int modifiers(KeyInputEvent event) {
        return event.modifiers();
    }

    public static InputConstants.Key boundKey(KeyMapping binding) {
        return ((KeyBindingAccessor) binding).tim$getBoundKey();
    }
}
