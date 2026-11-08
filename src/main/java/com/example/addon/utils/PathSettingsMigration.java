package com.example.addon.utils;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

public final class PathSettingsMigration {
    private PathSettingsMigration() {
    }

    public static void migrate(NbtCompound module) {
        NbtList groups = SettingNameCompat.groups(module);
        NbtCompound destination = findGroup(groups, "Pathfinding");
        for (String[] mapping : new String[][] {
            { "General", "path-allow-breaking-and-placement", "path-allow-breaking-and-placement" },
            { "Surface Path", "surface-path-start-cancel-key", "surface-path-start-cancel-key" },
            { "Surface Path", "search-radius", "surface-path-search-radius" },
            { "Ender Chest Path", "ender-chest-path-start-cancel-key", "ender-chest-path-start-cancel-key" },
            { "Ender Chest Path", "search-radius", "ender-chest-path-search-radius" }
        }) {
            NbtCompound source = findGroup(groups, mapping[0]);
            if (source == null) continue;
            NbtCompound setting = findSetting(SettingNameCompat.entries(source), mapping[1]);
            if (setting == null) continue;
            if (destination == null) {
                destination = new NbtCompound();
                destination.putString("name", "Pathfinding");
                destination.putBoolean("sectionExpanded", true);
                destination.put("settings", new NbtList());
                groups.add(destination);
            }
            NbtList entries = SettingNameCompat.entries(destination);
            if (findSetting(entries, mapping[2]) != null) continue;
            NbtCompound copy = setting.copy();
            copy.putString("name", mapping[2]);
            entries.add(copy);
            destination.put("settings", entries);
        }
    }

    private static NbtCompound findGroup(NbtList groups, String name) {
        for (NbtElement element : groups)
            if (element instanceof NbtCompound group && name.equals(SettingNameCompat.name(group))) return group;
        return null;
    }

    private static NbtCompound findSetting(NbtList settings, String name) {
        for (NbtElement element : settings)
            if (element instanceof NbtCompound setting && name.equals(SettingNameCompat.name(setting))) return setting;
        return null;
    }
}
