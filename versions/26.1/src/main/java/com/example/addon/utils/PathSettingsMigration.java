package com.example.addon.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class PathSettingsMigration {
    private PathSettingsMigration() {
    }

    public static void migrate(CompoundTag module) {
        ListTag groups = SettingNameCompat.groups(module);
        CompoundTag destination = findGroup(groups, "Pathfinding");
        for (String[] mapping : new String[][] {
            { "General", "path-allow-breaking-and-placement", "path-allow-breaking-and-placement" },
            { "Surface Path", "surface-path-start-cancel-key", "surface-path-start-cancel-key" },
            { "Surface Path", "search-radius", "surface-path-search-radius" },
            { "Ender Chest Path", "ender-chest-path-start-cancel-key", "ender-chest-path-start-cancel-key" },
            { "Ender Chest Path", "search-radius", "ender-chest-path-search-radius" }
        }) {
            CompoundTag source = findGroup(groups, mapping[0]);
            if (source == null) continue;
            CompoundTag setting = findSetting(SettingNameCompat.entries(source), mapping[1]);
            if (setting == null) continue;
            if (destination == null) {
                destination = new CompoundTag();
                destination.putString("name", "Pathfinding");
                destination.putBoolean("sectionExpanded", true);
                destination.put("settings", new ListTag());
                groups.add(destination);
            }
            ListTag entries = SettingNameCompat.entries(destination);
            if (findSetting(entries, mapping[2]) != null) continue;
            CompoundTag copy = setting.copy();
            copy.putString("name", mapping[2]);
            entries.add(copy);
            destination.put("settings", entries);
        }
    }

    private static CompoundTag findGroup(ListTag groups, String name) {
        for (Tag element : groups)
            if (element instanceof CompoundTag group && name.equals(SettingNameCompat.name(group))) return group;
        return null;
    }

    private static CompoundTag findSetting(ListTag settings, String name) {
        for (Tag element : settings)
            if (element instanceof CompoundTag setting && name.equals(SettingNameCompat.name(setting))) return setting;
        return null;
    }
}
