package com.example.addon.hud;

import com.example.addon.modules.Skipper;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import xaero.map.highlight.ChunkHighlighter;

import java.util.List;
import java.util.function.BiFunction;

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
        return true;
    }

    @Override
    public boolean chunkIsHighlit(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return colorProvider.apply(chunkX, chunkZ) != 0;
    }

    @Override
    public Text getChunkHighlightSubtleTooltip(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        int color = colorProvider.apply(chunkX, chunkZ);
        if (color == 0) return Text.empty();
        return Text.literal("§6[Skipper] §7Highlighted Trail Chunk");
    }

    @Override
    public Text getChunkHighlightBluntTooltip(RegistryKey<World> dimension, int chunkX, int chunkZ) {
        return getChunkHighlightSubtleTooltip(dimension, chunkX, chunkZ);
    }

    @Override
    public Text getBlockHighlightSubtleTooltip(RegistryKey<World> dimension, int blockX, int blockZ) {
        return getChunkHighlightSubtleTooltip(dimension, blockX >> 4, blockZ >> 4);
    }

    @Override
    public Text getBlockHighlightBluntTooltip(RegistryKey<World> dimension, int blockX, int blockZ) {
        return getChunkHighlightSubtleTooltip(dimension, blockX >> 4, blockZ >> 4);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Text> list, RegistryKey<World> dimension, int blockX, int blockZ, int width) {
        int color = colorProvider.apply(blockX >> 4, blockZ >> 4);
        if (color != 0) {
            list.add(Text.literal("§6[Skipper] §7Active Trail / Lava Marker"));
        }
    }
}