package com.example.addon.hud;

import java.util.List;
import java.util.function.BiFunction;

import com.example.addon.modules.Skipper;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import xaero.map.highlight.ChunkHighlighter;

public class SkipperHud extends ChunkHighlighter {
    private final BiFunction<Integer, Integer, Integer> colorProvider;

    public SkipperHud(BiFunction<Integer, Integer, Integer> colorProvider) {
        super(true);
        this.colorProvider = colorProvider;
    }

    public SkipperHud() {
        super(true);
        this.colorProvider = (chunkX, chunkZ) -> {
            Skipper skipper = Modules.get().get(Skipper.class);
            return skipper != null && skipper.isActive() ? skipper.getXaeroColor(chunkX, chunkZ) : 0;
        };
    }

    @Override
    protected int[] getColors(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        if (!World.NETHER.equals(dimension)) return null;
        int color = colorProvider.apply(chunkX, chunkZ);
        if (color == 0) return null;

        this.resultStore[0] = color;
        this.resultStore[1] = color;
        this.resultStore[2] = color;
        this.resultStore[3] = color;
        return this.resultStore;
    }

    @Override
    public int calculateRegionHash(RegistryKey<World> dimension, int regionX, int regionZ) {
        if (!World.NETHER.equals(dimension)) return 0;
        int hash = 1;
        int startX = regionX * 32;
        int startZ = regionZ * 32;

        for (int chunkX = startX; chunkX < startX + 32; chunkX++) {
            for (int chunkZ = startZ; chunkZ < startZ + 32; chunkZ++) {
                hash = 31 * hash + colorProvider.apply(chunkX, chunkZ);
            }
        }

        return hash;
    }

    @Override
    public boolean regionHasHighlights(RegistryKey<World> dimension, int regionX, int regionZ) {
        if (!World.NETHER.equals(dimension)) return false;
        int startX = regionX * 32;
        int startZ = regionZ * 32;
        for (int chunkX = startX; chunkX < startX + 32; chunkX++) {
            for (int chunkZ = startZ; chunkZ < startZ + 32; chunkZ++) {
                if (colorProvider.apply(chunkX, chunkZ) != 0) return true;
            }
        }
        return false;
    }

    @Override
    public boolean chunkIsHighlit(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        if (!World.NETHER.equals(dimension)) return false;
        return colorProvider.apply(chunkX, chunkZ) != 0;
    }

    @Override
    public Text getChunkHighlightSubtleTooltip(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        // Returning null (instead of Text.empty() or custom text) eliminates the
        // "Skipper" message and allows XaeroPlus's native tooltips to display!
        return null;
    }

    @Override
    public Text getChunkHighlightBluntTooltip(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public Text getBlockHighlightSubtleTooltip(RegistryKey<World> dimension, int blockX, int blockZ) {
        return null;
    }

    @Override
    public Text getBlockHighlightBluntTooltip(RegistryKey<World> dimension, int blockX, int blockZ) {
        return null;
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Text> list, RegistryKey<World> dimension, int blockX, int blockZ, int width) {
        // Intentionally empty so no Skipper text is injected into the minimap/worldmap UI
    }
}