package com.example.addon.modules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.addon.Tim;
import com.example.addon.utils.GlowingRegistry;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

public class Illushine extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enums
    // ═══════════════════════════════════════════════════════════════════════════

    private enum MobCategory { PASSIVE, NEUTRAL, HOSTILE }

    /**
     * Wireframe — custom geometry outline drawn by WireframeEntityRenderer.
     * Spectral  — vanilla glowing outline (spectral arrow effect) driven by
     *             GlowingRegistry → existing EntityGlowingMixin pipeline.
     */
    public enum HighlightMode {
        Wireframe("Wireframe"),
        Spectral("Spectral");

        private final String title;
        HighlightMode(String title) { this.title = title; }

        @Override public String toString() { return title; }
    }

    public enum CrosshairMode {
        None("None"),
        WhiteDot("White Dot"),
        Normal("Normal"),
        CustomItem("Custom Item");

        private final String title;
        CrosshairMode(String title) { this.title = title; }

        @Override public String toString() { return title; }
    }

    // --- Multi-Version Registry & Entity Helpers (1.21.4 - 1.21.11) ---
    private static Item optItem(String path) {
        Identifier id = Identifier.withDefaultNamespace(path);
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
    }

    private static EntityType<?> optEntityType(String path) {
        Identifier id = Identifier.withDefaultNamespace(path);
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.getValue(id) : null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Category override table (including 1.21.5 - 1.21.11 additions)
    // ═══════════════════════════════════════════════════════════════════════════

    private static final Map<EntityType<?>, MobCategory> CATEGORY_OVERRIDES = new HashMap<>();

    static {
        // ── Passive ──
        CATEGORY_OVERRIDES.put(EntityType.FOX, MobCategory.PASSIVE);

        // ── Neutral ──
        CATEGORY_OVERRIDES.put(EntityType.PIGLIN, MobCategory.HOSTILE);
        CATEGORY_OVERRIDES.put(EntityType.ZOMBIFIED_PIGLIN, MobCategory.NEUTRAL);
        CATEGORY_OVERRIDES.put(EntityType.ENDERMAN, MobCategory.NEUTRAL);
        CATEGORY_OVERRIDES.put(EntityType.GOAT, MobCategory.NEUTRAL);

        // ── Hostile ──
        CATEGORY_OVERRIDES.put(EntityType.PIGLIN_BRUTE, MobCategory.HOSTILE);
        CATEGORY_OVERRIDES.put(EntityType.GHAST, MobCategory.HOSTILE);
        CATEGORY_OVERRIDES.put(EntityType.SHULKER, MobCategory.HOSTILE);
        CATEGORY_OVERRIDES.put(EntityType.PHANTOM, MobCategory.HOSTILE);
        CATEGORY_OVERRIDES.put(EntityType.SLIME, MobCategory.HOSTILE);
        CATEGORY_OVERRIDES.put(EntityType.MAGMA_CUBE, MobCategory.HOSTILE);
        CATEGORY_OVERRIDES.put(EntityType.HOGLIN, MobCategory.HOSTILE);

        // ── 1.21.5 / 1.21.11 Additions (Safe optional bindings) ──
        EntityType<?> creaking = optEntityType("creaking");
        if (creaking != null) CATEGORY_OVERRIDES.put(creaking, MobCategory.HOSTILE);

        EntityType<?> nautilus = optEntityType("nautilus");
        if (nautilus != null) CATEGORY_OVERRIDES.put(nautilus, MobCategory.NEUTRAL);

        EntityType<?> zombieNautilus = optEntityType("zombie_nautilus");
        if (zombieNautilus != null) CATEGORY_OVERRIDES.put(zombieNautilus, MobCategory.HOSTILE);

        EntityType<?> camelHusk = optEntityType("camel_husk");
        if (camelHusk != null) CATEGORY_OVERRIDES.put(camelHusk, MobCategory.HOSTILE);

        EntityType<?> parched = optEntityType("parched");
        if (parched != null) CATEGORY_OVERRIDES.put(parched, MobCategory.HOSTILE);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgGeneral   = settings.getDefaultGroup();
    private final SettingGroup sgCrosshair = settings.createGroup("Crosshair");
    private final SettingGroup sgPassive   = settings.createGroup("Passive");
    private final SettingGroup sgNeutral   = settings.createGroup("Neutral");
    private final SettingGroup sgHostile   = settings.createGroup("Hostile");
    private final SettingGroup sgPlayer    = settings.createGroup("Player Scale");
    private final SettingGroup sgBlocks    = settings.createGroup("Block Anomalies");

    private final Setting<Boolean> misrotatedDeepslate = sgBlocks.add(new BoolSetting.Builder()
        .name("misrotated-deepslate")
        .description("Highlight horizontally oriented deepslate in the Overworld.")
        .defaultValue(false)
        .onChanged(value -> clearDeepslate())
        .build()
    );

    private final Setting<Integer> deepslateChunkRadius = sgBlocks.add(new IntSetting.Builder()
        .name("deepslate-chunk-radius")
        .description("Loaded chunk radius to scan for misrotated deepslate.")
        .defaultValue(8).range(1, 16).sliderRange(1, 16)
        .visible(misrotatedDeepslate::get)
        .onChanged(value -> clearDeepslate())
        .build()
    );

    private final Setting<SettingColor> deepslateColor = sgBlocks.add(new ColorSetting.Builder()
        .name("misrotated-deepslate-color")
        .description("Highlight color for misrotated deepslate.")
        .defaultValue(new SettingColor(0, 180, 255, 255))
        .visible(misrotatedDeepslate::get)
        .build()
    );

    private final Map<ChunkPos, Set<BlockPos>> deepslateByChunk = new HashMap<>();
    private ClientLevel deepslateWorld;

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — General
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<HighlightMode> highlightMode = sgGeneral.add(new EnumSetting.Builder<HighlightMode>()
        .name("highlight-mode")
        .description("How mobs are outlined. Wireframe draws custom geometry; Spectral uses the vanilla glow pipeline.")
        .defaultValue(HighlightMode.Wireframe)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Range to highlight mobs.")
        .defaultValue(64).min(1).sliderMax(256)
        .build()
    );

    private final Setting<Set<EntityType<?>>> ignoredEntities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("ignored-entities")
        .description("Entities to ignore.")
        .defaultValue(new HashSet<>())
        .build()
    );

    private final Setting<Double> outlineScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("outline-scale")
        .description("Scale of the wireframe outline (Wireframe mode only).")
        .defaultValue(1.0).min(0.1).sliderMax(2.0)
        .visible(() -> highlightMode.get() == HighlightMode.Wireframe)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Crosshair
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<CrosshairMode> crosshairMode = sgCrosshair.add(new EnumSetting.Builder<CrosshairMode>()
        .name("crosshair-mode")
        .description("The crosshair style to display while Illushine is active.")
        .defaultValue(CrosshairMode.Normal)
        .build()
    );

    private final Setting<SettingColor> crosshairColor = sgCrosshair.add(new ColorSetting.Builder()
        .name("crosshair-color")
        .description("Color of the Normal crosshair lines.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> crosshairMode.get() == CrosshairMode.Normal)
        .build()
    );

    private final Setting<Integer> crosshairSize = sgCrosshair.add(new IntSetting.Builder()
        .name("crosshair-size").description("Half-length of each crosshair arm in pixels.")
        .defaultValue(5).min(1).sliderMax(20)
        .visible(() -> crosshairMode.get() == CrosshairMode.Normal)
        .build()
    );

    private final Setting<Integer> crosshairGap = sgCrosshair.add(new IntSetting.Builder()
        .name("crosshair-gap").description("Gap (in pixels) between center and each arm.")
        .defaultValue(2).min(0).sliderMax(10)
        .visible(() -> crosshairMode.get() == CrosshairMode.Normal)
        .build()
    );

    private final Setting<Integer> crosshairThickness = sgCrosshair.add(new IntSetting.Builder()
        .name("crosshair-thickness").description("Thickness of the crosshair lines in pixels.")
        .defaultValue(1).min(1).sliderMax(5)
        .visible(() -> crosshairMode.get() == CrosshairMode.Normal)
        .build()
    );

    private final Setting<Item> crosshairItem = sgCrosshair.add(new ItemSetting.Builder()
        .name("crosshair-item")
        .description("The item to display as the crosshair.")
        .defaultValue(Items.DIAMOND)
        .visible(() -> crosshairMode.get() == CrosshairMode.CustomItem)
        .build()
    );

    private final Setting<Double> crosshairItemScale = sgCrosshair.add(new DoubleSetting.Builder()
        .name("crosshair-item-scale")
        .description("Scale of the custom item crosshair icon.")
        .defaultValue(1.0).min(0.25).sliderMax(3.0)
        .visible(() -> crosshairMode.get() == CrosshairMode.CustomItem)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Passive / Neutral / Hostile
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> highlightPassive = sgPassive.add(new BoolSetting.Builder()
        .name("highlight-passive").description("Highlight passive mobs.").defaultValue(true).build());

    private final Setting<SettingColor> passiveColor = sgPassive.add(new ColorSetting.Builder()
        .name("passive-color").description("Outline color for passive mobs.")
        .defaultValue(new SettingColor(0, 255, 100, 255)).visible(highlightPassive::get).build());

    private final Setting<Double> passiveScale = sgPassive.add(new DoubleSetting.Builder()
        .name("passive-scale").description("Visual scale for passive mobs.")
        .defaultValue(1.0).min(0.1).sliderMax(3.0).visible(highlightPassive::get).build());

    private final Setting<Boolean> highlightNeutral = sgNeutral.add(new BoolSetting.Builder()
        .name("highlight-neutral").description("Highlight neutral mobs.").defaultValue(true).build());

    private final Setting<SettingColor> neutralColor = sgNeutral.add(new ColorSetting.Builder()
        .name("neutral-color").description("Outline color for neutral mobs.")
        .defaultValue(new SettingColor(255, 200, 0, 255)).visible(highlightNeutral::get).build());

    private final Setting<Double> neutralScale = sgNeutral.add(new DoubleSetting.Builder()
        .name("neutral-scale").description("Visual scale for neutral mobs.")
        .defaultValue(1.0).min(0.1).sliderMax(3.0).visible(highlightNeutral::get).build());

    private final Setting<Boolean> highlightHostile = sgHostile.add(new BoolSetting.Builder()
        .name("highlight-hostile").description("Highlight hostile mobs.").defaultValue(true).build());

    private final Setting<SettingColor> hostileColor = sgHostile.add(new ColorSetting.Builder()
        .name("hostile-color").description("Outline color for hostile mobs.")
        .defaultValue(new SettingColor(255, 50, 50, 255)).visible(highlightHostile::get).build());

    private final Setting<Double> hostileScale = sgHostile.add(new DoubleSetting.Builder()
        .name("hostile-scale").description("Visual scale for hostile mobs.")
        .defaultValue(1.0).min(0.1).sliderMax(3.0).visible(highlightHostile::get).build());

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Player Scale
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Double> playerScale = sgPlayer.add(new DoubleSetting.Builder()
        .name("player-scale")
        .description("Visually scales your own player model and camera height.")
        .defaultValue(1.0).min(0.1).sliderMax(3.0)
        .build()
    );

    private final Setting<Boolean> scaleOtherPlayers = sgPlayer.add(new BoolSetting.Builder()
        .name("scale-other-players")
        .description("Apply a visual scale to other players.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> otherPlayerScale = sgPlayer.add(new DoubleSetting.Builder()
        .name("other-player-scale")
        .description("Visual scale applied to other players.")
        .defaultValue(1.0).min(0.1).sliderMax(3.0)
        .visible(scaleOtherPlayers::get)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    private final Map<Integer, MobCategory> activelyOutlined = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public Illushine() {
        super(Tim.CATEGORY, "illushine",
            "Highlights mobs with a wireframe or spectral outline by hostility type.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════════

    public CrosshairMode getCrosshairMode() { return crosshairMode.get(); }
    public double getPlayerScale() { return playerScale.get(); }
    public boolean getScaleOtherPlayers() { return scaleOtherPlayers.get(); }
    public double getOtherPlayerScale() { return otherPlayerScale.get(); }

    public double getMobScale(Mob mob) {
        MobCategory cat = categorise(mob);
        return switch (cat) {
            case PASSIVE -> passiveScale.get();
            case NEUTRAL -> neutralScale.get();
            case HOSTILE -> hostileScale.get();
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        activelyOutlined.clear();
        clearDeepslate();
    }

    @Override
    public void onDeactivate() {
        for (Integer id : activelyOutlined.keySet()) {
            GlowingRegistry.remove(id);
        }
        activelyOutlined.clear();
        clearDeepslate();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tick
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            clearDeepslate();
            return;
        }

        tickDeepslate();

        boolean spectral = highlightMode.get() == HighlightMode.Spectral;

        Map<Integer, MobCategory> newOutlined = new HashMap<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob)) continue;
            if (ignoredEntities.get().contains(mob.getType())) continue;
            if (mc.player.distanceTo(mob) > range.get()) continue;

            MobCategory category = categorise(mob);
            boolean shouldHighlight = switch (category) {
                case PASSIVE -> highlightPassive.get();
                case NEUTRAL -> highlightNeutral.get();
                case HOSTILE -> highlightHostile.get();
            };
            if (!shouldHighlight) continue;

            newOutlined.put(mob.getId(), category);
        }

        if (spectral) {
            for (Integer id : activelyOutlined.keySet()) {
                if (!newOutlined.containsKey(id)) GlowingRegistry.remove(id);
            }
            for (Map.Entry<Integer, MobCategory> entry : newOutlined.entrySet()) {
                SettingColor c = colorForCategory(entry.getValue());
                GlowingRegistry.add(entry.getKey(), (255 << 24) | (c.r << 16) | (c.g << 8) | c.b);
            }
        } else {
            for (Integer id : activelyOutlined.keySet()) {
                GlowingRegistry.remove(id);
            }
        }

        activelyOutlined.clear();
        activelyOutlined.putAll(newOutlined);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Render
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        if (misrotatedDeepslate.get() && mc.level == deepslateWorld) {
            SettingColor color = deepslateColor.get();
            for (Set<BlockPos> positions : deepslateByChunk.values()) {
                for (BlockPos pos : positions) {
                    event.renderer.box(pos, withAlpha(color, 25), color, ShapeMode.Both, 0);
                }
            }
        }

        boolean wireframe = highlightMode.get() == HighlightMode.Wireframe;

        for (Map.Entry<Integer, MobCategory> entry : activelyOutlined.entrySet()) {
            Entity entity = mc.level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob)) continue;

            SettingColor color = colorForCategory(entry.getValue());

            if (wireframe) {
                WireframeEntityRenderer.render(
                    event, mob, outlineScale.get(),
                    withAlpha(color, 25), color, ShapeMode.Both
                );
            }
        }
    }

    private void clearDeepslate() {
        deepslateByChunk.clear();
        deepslateWorld = null;
    }

    private static boolean isMisrotatedDeepslate(BlockState state) {
        return state.is(Blocks.DEEPSLATE) && state.hasProperty(BlockStateProperties.AXIS)
            && state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y;
    }

    private void tickDeepslate() {
        if (!misrotatedDeepslate.get() || !mc.level.dimension().equals(Level.OVERWORLD)) {
            clearDeepslate();
            return;
        }
        if (deepslateWorld != mc.level) {
            clearDeepslate();
            deepslateWorld = mc.level;
        }
        int centerX = mc.player.blockPosition().getX() >> 4;
        int centerZ = mc.player.blockPosition().getZ() >> 4;
        int radius = deepslateChunkRadius.get();
        deepslateByChunk.keySet().removeIf(pos -> Math.abs(pos.x() - centerX) > radius
            || Math.abs(pos.z() - centerZ) > radius || !mc.level.getChunkSource().hasChunk(pos.x(), pos.z()));

        int scanned = 0;
        for (int d = 0; d <= radius && scanned < 4; d++) {
            for (int x = -d; x <= d && scanned < 4; x++) {
                for (int z = -d; z <= d && scanned < 4; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != d) continue;
                    ChunkPos pos = new ChunkPos(centerX + x, centerZ + z);
                    if (deepslateByChunk.containsKey(pos) || !mc.level.getChunkSource().hasChunk(pos.x(), pos.z())) continue;
                    scanDeepslate(mc.level.getChunk(pos.x(), pos.z()));
                    scanned++;
                }
            }
        }
    }

    private void scanDeepslate(ChunkAccess chunk) {
        Set<BlockPos> matches = new HashSet<>();
        ChunkPos pos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(Illushine::isMisrotatedDeepslate)) continue;
            int bottomY = (chunk.getMinSectionY() + i) * 16;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (isMisrotatedDeepslate(section.getBlockState(x, y, z)))
                            matches.add(new BlockPos((pos.x() << 4) + x, bottomY + y, (pos.z() << 4) + z));
                    }
                }
            }
        }
        deepslateByChunk.put(pos, matches);
    }

    @EventHandler
    private void onDeepslateBlockUpdate(BlockUpdateEvent event) {
        if (mc.level != deepslateWorld || deepslateWorld == null) return;
        Set<BlockPos> matches = deepslateByChunk.get(ChunkPos.containing(event.pos));
        if (matches == null) return;
        if (isMisrotatedDeepslate(event.newState)) matches.add(event.pos.immutable());
        else matches.remove(event.pos);
    }

    @EventHandler
    private void onDeepslateChunkData(ChunkDataEvent event) {
        if (mc.level == deepslateWorld && deepslateWorld != null) deepslateByChunk.remove(event.chunk().getPos());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Crosshair
    // ═══════════════════════════════════════════════════════════════════════════

    public void drawCrosshair(GuiGraphicsExtractor context) {
        if (mc.getWindow() == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;
        if (mc.screen != null) return;

        int cx = mc.getWindow().getGuiScaledWidth()  / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;

        switch (crosshairMode.get()) {
            case WhiteDot -> context.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
            case Normal   -> drawNormalCrosshair(context, cx, cy);
            case CustomItem -> drawCustomItemCrosshair(context, cx, cy);
            default       -> {}
        }
    }

    private void drawNormalCrosshair(GuiGraphicsExtractor context, int cx, int cy) {
        int arm = crosshairSize.get();
        int gap = crosshairGap.get();
        int th  = crosshairThickness.get();
        int col = toARGB(crosshairColor.get());

        int halfU = th / 2;
        int halfD = th - halfU;

        context.fill(cx - arm - gap, cy - halfU, cx - gap,       cy + halfD, col);
        context.fill(cx + gap,       cy - halfU, cx + arm + gap, cy + halfD, col);
        context.fill(cx - halfU,     cy - arm - gap, cx + halfD, cy - gap,   col);
        context.fill(cx - halfU,     cy + gap,       cx + halfD, cy + arm + gap, col);
    }

    private void drawCustomItemCrosshair(GuiGraphicsExtractor context, int cx, int cy) {
        Item item = crosshairItem.get();
        if (item == null) item = Items.DIAMOND;
        ItemStack stack = new ItemStack(item);

        float scale = crosshairItemScale.get().floatValue();
        com.example.addon.utils.MinecraftApiCompat.drawScaledItem(
            context, stack, cx - (8.0f * scale), cy - (8.0f * scale), scale
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Categorisation
    // ═══════════════════════════════════════════════════════════════════════════

    private MobCategory categorise(Mob mob) {
        if (mob.getType() == EntityType.PIGLIN && mob.isBaby()) return MobCategory.PASSIVE;

        if (mob.getType() == EntityType.SPIDER || mob.getType() == EntityType.CAVE_SPIDER) {
            long time = mc.level.getOverworldClockTime() % 24000;
            boolean isDay = time < 13000;
            boolean canSeeSky = mc.level.canSeeSky(mob.blockPosition());
            return (isDay && canSeeSky) ? MobCategory.NEUTRAL : MobCategory.HOSTILE;
        }

        MobCategory override = CATEGORY_OVERRIDES.get(mob.getType());
        if (override != null) return override;

        if (mob instanceof Monster) return MobCategory.HOSTILE;
        if (mob instanceof NeutralMob)     return MobCategory.NEUTRAL;
        if (mob instanceof AgeableMob) return MobCategory.PASSIVE;
        return MobCategory.NEUTRAL;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private SettingColor colorForCategory(MobCategory cat) {
        return switch (cat) {
            case PASSIVE -> passiveColor.get();
            case NEUTRAL -> neutralColor.get();
            case HOSTILE -> hostileColor.get();
        };
    }

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }

    private static int toARGB(SettingColor c) {
        return (c.a << 24) | (c.r << 16) | (c.g << 8) | c.b;
    }
}
