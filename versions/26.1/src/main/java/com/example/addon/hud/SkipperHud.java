package com.example.addon.hud;

import java.util.List;
import java.util.function.BiFunction;

import com.example.addon.modules.Skipper;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
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

    private Skipper getActiveHighlighterSkipper() {
        Skipper skipper = Modules.get().get(Skipper.class);
        if (skipper == null || !skipper.isActive() || skipper.isNativeXaeroMapCoexistence()) {
            return null;
        }
        return skipper;
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!Level.NETHER.equals(dimension)) return null;
        Skipper skipper = getActiveHighlighterSkipper();
        if (skipper == null) return null;

        int color = skipper.getXaeroColor(chunkX, chunkZ);
        if (color == 0) return null;

        this.resultStore[0] = color;
        this.resultStore[1] = color;
        this.resultStore[2] = color;
        this.resultStore[3] = color;
        return this.resultStore;
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        if (!Level.NETHER.equals(dimension)) return 0;
        Skipper skipper = getActiveHighlighterSkipper();
        if (skipper == null) return 0;

        int hash = 1;
        int startX = regionX * 32;
        int startZ = regionZ * 32;

        for (int chunkX = startX; chunkX < startX + 32; chunkX++) {
            for (int chunkZ = startZ; chunkZ < startZ + 32; chunkZ++) {
                hash = 31 * hash + skipper.getXaeroColor(chunkX, chunkZ);
            }
        }

        return hash;
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        if (!Level.NETHER.equals(dimension)) return false;
        Skipper skipper = getActiveHighlighterSkipper();
        if (skipper == null) return false;

        int startX = regionX * 32;
        int startZ = regionZ * 32;
        for (int chunkX = startX; chunkX < startX + 32; chunkX++) {
            for (int chunkZ = startZ; chunkZ < startZ + 32; chunkZ++) {
                if (skipper.getXaeroColor(chunkX, chunkZ) != 0) return true;
            }
        }
        return false;
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!Level.NETHER.equals(dimension)) return false;
        Skipper skipper = getActiveHighlighterSkipper();
        return skipper != null && skipper.getXaeroColor(chunkX, chunkZ) != 0;
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public Component getBlockHighlightSubtleTooltip(ResourceKey<Level> dimension, int blockX, int blockZ) {
        return null;
    }

    @Override
    public Component getBlockHighlightBluntTooltip(ResourceKey<Level> dimension, int blockX, int blockZ) {
        return null;
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dimension, int blockX, int blockZ, int width) {
    }
}