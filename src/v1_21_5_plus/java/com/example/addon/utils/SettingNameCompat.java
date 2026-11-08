package com.example.addon.utils;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

public final class SettingNameCompat {
    private SettingNameCompat() {
    }

    static NbtList groups(NbtCompound module) {
        return module.getCompoundOrEmpty("settings").getList("groups").orElseGet(NbtList::new);
    }

    static NbtList entries(NbtCompound group) {
        return group.getList("settings").orElseGet(NbtList::new);
    }

    static String name(NbtCompound tag) {
        return tag.getString("name", "");
    }

    public static void migrate(NbtCompound module, String groupName, String oldName, String newName) {
        NbtList groups = module.getCompoundOrEmpty("settings").getList("groups").orElseGet(NbtList::new);
        for (NbtElement element : groups) {
            if (!(element instanceof NbtCompound group) || !groupName.equals(group.getString("name", ""))) continue;
            NbtList settings = group.getList("settings").orElseGet(NbtList::new);
            boolean hasNew = false;
            NbtCompound old = null;
            for (NbtElement entry : settings) {
                if (!(entry instanceof NbtCompound setting)) continue;
                String name = setting.getString("name", "");
                if (newName.equals(name)) hasNew = true;
                else if (oldName.equals(name)) old = setting;
            }
            if (!hasNew && old != null) old.putString("name", newName);
        }
    }
}
