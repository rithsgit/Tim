package com.example.addon.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.Item;

public final class ItemLists {
    private ItemLists() {
    }

    public static List<Item> withoutMissingItems(Item... items) {
        return Arrays.stream(items).filter(Objects::nonNull).toList();
    }
}
