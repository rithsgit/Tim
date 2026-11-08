package com.example.addon.utils;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

public final class SettingNameCompat {
    private SettingNameCompat() {
    }

    static NbtList groups(NbtCompound module) {
        return module.getCompound("settings").getList("groups", NbtElement.COMPOUND_TYPE);
    }

    static NbtList entries(NbtCompound group) {
        return group.getList("settings", NbtElement.COMPOUND_TYPE);
    }

    static String name(NbtCompound tag) {
        return tag.getString("name");
    }

    public static void migrate(NbtCompound module, String groupName, String oldName, String newName) {
        NbtList groups = module.getCompound("settings").getList("groups", NbtElement.COMPOUND_TYPE);
        for (NbtElement element : groups) {
            if (!(element instanceof NbtCompound group) || !groupName.equals(group.getString("name"))) continue;
            NbtList settings = group.getList("settings", NbtElement.COMPOUND_TYPE);
            boolean hasNew = false;
            NbtCompound old = null;
            for (NbtElement entry : settings) {
                if (!(entry instanceof NbtCompound setting)) continue;
                String name = setting.getString("name");
                if (newName.equals(name)) hasNew = true;
                else if (oldName.equals(name)) old = setting;
            }
            if (!hasNew && old != null) old.putString("name", newName);
        }
    }
}
