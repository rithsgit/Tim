package com.example.addon.utils;

import com.example.addon.modules.Inventory101;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class HandledScreenActions {
    private HandledScreenActions() {
    }

    public static boolean handleMiddleClick(Slot slot) {
        Inventory101 inventory101 = Modules.get().get(Inventory101.class);
        if (inventory101 == null || !inventory101.isActive() || slot == null || !slot.hasItem()) return false;

        ItemStack stack = slot.getItem();
        if (Inventory101.isShulker(stack)) {
            inventory101.openPreview(stack);
            return true;
        }
        if (Inventory101.isEnderChest(stack)) {
            inventory101.openEnderChestPreview(stack);
            return true;
        }
        return false;
    }
}
