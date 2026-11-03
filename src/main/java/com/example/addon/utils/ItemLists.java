package com.example.addon.utils;

import net.minecraft.item.Item;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ItemLists {
    private ItemLists() {
    }

    public static List<Item> withoutMissingItems(Item... items) {
        return Arrays.stream(items).filter(Objects::nonNull).toList();
    }
}
