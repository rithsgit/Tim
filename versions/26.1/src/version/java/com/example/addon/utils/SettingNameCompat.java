package com.example.addon.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class SettingNameCompat {
    private SettingNameCompat() {
    }

    static ListTag groups(CompoundTag module) {
        return module.getCompoundOrEmpty("settings").getList("groups").orElseGet(ListTag::new);
    }

    static ListTag entries(CompoundTag group) {
        return group.getList("settings").orElseGet(ListTag::new);
    }

    static String name(CompoundTag tag) {
        return tag.getStringOr("name", "");
    }

    public static void migrate(CompoundTag module, String groupName, String oldName, String newName) {
        ListTag groups = module.getCompoundOrEmpty("settings").getList("groups").orElseGet(ListTag::new);
        for (Tag element : groups) {
            if (!(element instanceof CompoundTag group) || !groupName.equals(group.getStringOr("name", ""))) continue;
            ListTag settings = group.getList("settings").orElseGet(ListTag::new);
            boolean hasNew = false;
            CompoundTag old = null;
            for (Tag entry : settings) {
                if (!(entry instanceof CompoundTag setting)) continue;
                String name = setting.getStringOr("name", "");
                if (newName.equals(name)) hasNew = true;
                else if (oldName.equals(name)) old = setting;
            }
            if (!hasNew && old != null) old.putString("name", newName);
        }
    }
}
