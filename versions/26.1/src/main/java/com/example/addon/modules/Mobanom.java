package com.example.addon.modules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.addon.Tim;
import com.example.addon.utils.GlowingRegistry;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;

public class Mobanom extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enums
    // ═══════════════════════════════════════════════════════════════════════════

    public enum AnomalyType {
        DIMENSION_NETHER,
        DIMENSION_END,
        DIMENSION_OVERWORLD,
        ENDERMITE,
        ITEM,
        CHESTED
    }

    public enum HighlightMode {
        Wireframe("Wireframe"),
        Spectral("Spectral"),
        Pulse("Pulse");

        private final String title;
        HighlightMode(String title) { this.title = title; }

        @Override public String toString() { return title; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgGeneral     = settings.getDefaultGroup();
    private final SettingGroup sgColors      = settings.createGroup("Colors");
    private final SettingGroup sgItemAnomaly = settings.createGroup("Item Anomaly");

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — General
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<HighlightMode> highlightMode = sgGeneral.add(new EnumSetting.Builder<HighlightMode>()
        .name("highlight-mode")
        .description("How anomalous mobs are outlined. Wireframe draws a box outline; Spectral uses the vanilla glow pipeline; Pulse uses a fading bloom effect.")
        .defaultValue(HighlightMode.Wireframe)
        .build()
    );

    private final Setting<Boolean> chatNotification = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notification")
        .description("Notify in chat when an anomaly is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> ignoredEntities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("ignored-entities")
        .description("Entities to ignore.")
        .defaultValue(new HashSet<>())
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("The range to detect anomalies.")
        .defaultValue(128).min(1).sliderMax(256)
        .build()
    );

    private final Setting<Boolean> trackEndermites = sgGeneral.add(new BoolSetting.Builder()
        .name("track-endermites")
        .description("Track Overworld endermites with a dedicated color, beam, and detection sound.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> endermiteBeam = sgGeneral.add(new BoolSetting.Builder()
        .name("endermite-beam")
        .description("Draw a vertical beam through tracked endermites.")
        .defaultValue(true)
        .visible(trackEndermites::get)
        .build()
    );

    // ── Highlight rendering ───────────────────────────────────────────────────

    private final Setting<Integer> glowLayers = sgGeneral.add(new IntSetting.Builder()
        .name("glow-layers")
        .description("Number of bloom layers rendered around each mob.")
        .defaultValue(4).min(1).sliderMax(8)
        .visible(() -> highlightMode.get() == HighlightMode.Wireframe || highlightMode.get() == HighlightMode.Pulse)
        .build()
    );

    private final Setting<Double> glowSpread = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-spread")
        .description("How far each bloom layer expands outward (in blocks).")
        .defaultValue(0.05).min(0.01).sliderMax(0.2)
        .visible(() -> highlightMode.get() == HighlightMode.Wireframe || highlightMode.get() == HighlightMode.Pulse)
        .build()
    );

    private final Setting<Integer> glowBaseAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("glow-base-alpha")
        .description("Alpha of the innermost glow layer (0-255).")
        .defaultValue(60).min(10).sliderMax(150)
        .visible(() -> highlightMode.get() == HighlightMode.Wireframe)
        .build()
    );

    private final Setting<Double> pulseSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("pulse-speed")
        .description("Pulse cycle speed. 1.0 = one full fade in/out per second.")
        .defaultValue(1.0).min(0.1).max(5.0).sliderMax(3.0)
        .visible(() -> highlightMode.get() == HighlightMode.Pulse)
        .build()
    );

    private final Setting<Integer> pulseMinAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("pulse-min-alpha")
        .description("Lowest alpha reached during the pulse (0 = invisible).")
        .defaultValue(15).min(0).max(255).sliderMax(100)
        .visible(() -> highlightMode.get() == HighlightMode.Pulse)
        .build()
    );

    private final Setting<Integer> pulseMaxAlpha = sgGeneral.add(new IntSetting.Builder()
        .name("pulse-max-alpha")
        .description("Peak alpha reached during the pulse.")
        .defaultValue(220).min(15).max(255).sliderMax(255)
        .visible(() -> highlightMode.get() == HighlightMode.Pulse)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Colors
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<SettingColor> overworldLineColor = sgColors.add(new ColorSetting.Builder()
        .name("overworld-line-color")
        .description("Glow color for Overworld mobs found in other dimensions.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );

    private final Setting<SettingColor> netherLineColor = sgColors.add(new ColorSetting.Builder()
        .name("nether-line-color")
        .description("Glow color for Nether mobs found in other dimensions.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<SettingColor> endLineColor = sgColors.add(new ColorSetting.Builder()
        .name("end-line-color")
        .description("Glow color for End mobs found in other dimensions.")
        .defaultValue(new SettingColor(255, 0, 255, 255))
        .build()
    );

    private final Setting<SettingColor> endermiteColor = sgColors.add(new ColorSetting.Builder()
        .name("endermite-color")
        .description("Highlight and beam color for tracked endermites.")
        .defaultValue(new SettingColor(138, 43, 226, 255))
        .visible(trackEndermites::get)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Item Anomaly
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> detectUnnaturalItems = sgItemAnomaly.add(new BoolSetting.Builder()
        .name("detect-unnatural-items")
        .description("Detects mobs holding or wearing player-like items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> itemAnomalyLineColor = sgItemAnomaly.add(new ColorSetting.Builder()
        .name("item-anomaly-line-color")
        .description("The glow color for mobs with unnatural items.")
        .defaultValue(new SettingColor(0, 255, 255, 255))
        .visible(detectUnnaturalItems::get)
        .build()
    );

    private final Setting<Boolean> detectPumpkins = sgItemAnomaly.add(new BoolSetting.Builder()
        .name("detect-pumpkins")
        .description("Detects mobs wearing carved pumpkins or jack-o'-lanterns.")
        .defaultValue(true)
        .visible(detectUnnaturalItems::get)
        .build()
    );

    private final Setting<Boolean> detectChestedAnimals = sgItemAnomaly.add(new BoolSetting.Builder()
        .name("detect-chested-animals")
        .description("Detects animals (donkeys, llamas, etc.) carrying a chest.")
        .defaultValue(true)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Native-origin sets
    // ═══════════════════════════════════════════════════════════════════════════

    private static final Set<EntityType<?>> OVERWORLD_NATIVES = Set.of(
        EntityType.ALLAY,
        EntityType.AXOLOTL,
        EntityType.BAT,
        EntityType.CAMEL,
        EntityType.CAT,
        EntityType.CHICKEN,
        EntityType.COD,
        EntityType.COW,
        EntityType.DONKEY,
        EntityType.FOX,
        EntityType.FROG,
        EntityType.GLOW_SQUID,
        EntityType.HORSE,
        EntityType.MOOSHROOM,
        EntityType.MULE,
        EntityType.OCELOT,
        EntityType.PARROT,
        EntityType.PIG,
        EntityType.RABBIT,
        EntityType.SALMON,
        EntityType.SHEEP,
        EntityType.SNIFFER,
        EntityType.SNOW_GOLEM,
        EntityType.SQUID,
        EntityType.TADPOLE,
        EntityType.TROPICAL_FISH,
        EntityType.TURTLE,
        EntityType.VILLAGER,
        EntityType.WANDERING_TRADER,
        EntityType.BEE,
        EntityType.IRON_GOLEM,
        EntityType.POLAR_BEAR,
        EntityType.WOLF,
        EntityType.ZOMBIE,
        EntityType.CREEPER,
        EntityType.WITCH,
        EntityType.PHANTOM,
        EntityType.DROWNED,
        EntityType.HUSK,
        EntityType.STRAY,
        EntityType.ZOMBIE_VILLAGER,
        EntityType.VINDICATOR,
        EntityType.EVOKER,
        EntityType.PILLAGER,
        EntityType.RAVAGER,
        EntityType.VEX,
        EntityType.ILLUSIONER,
        EntityType.GUARDIAN,
        EntityType.ELDER_GUARDIAN,
        EntityType.SILVERFISH,
        EntityType.ZOMBIE_HORSE
    );

    private static final Set<EntityType<?>> NETHER_NATIVES = Set.of(
        EntityType.GHAST,
        EntityType.BLAZE,
        EntityType.WITHER_SKELETON,
        EntityType.MAGMA_CUBE,
        EntityType.PIGLIN,
        EntityType.PIGLIN_BRUTE,
        EntityType.HOGLIN,
        EntityType.ZOGLIN,
        EntityType.STRIDER
    );

    private static final Set<EntityType<?>> END_NATIVES = Set.of(
        EntityType.SHULKER,
        EntityType.ENDERMITE,
        EntityType.ENDER_DRAGON
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    private final Map<Integer, AnomalyType> highlightedEntities = new HashMap<>();
    private final Set<Integer>              notifiedEntities    = new HashSet<>();
    private final Set<Integer>              notifiedEndermites  = new HashSet<>();
    private net.minecraft.client.multiplayer.ClientLevel trackedWorld;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor & Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    public Mobanom() {
        super(Tim.CATEGORY, "mobanom", "Detects and highlights mobs in the wrong dimension or with unnatural items.");
    }

    @Override
    public void onActivate() {
        highlightedEntities.clear();
        notifiedEntities.clear();
        notifiedEndermites.clear();
        trackedWorld = null;
        GlowingRegistry.clear();
    }

    @Override
    public void onDeactivate() {
        highlightedEntities.clear();
        notifiedEntities.clear();
        notifiedEndermites.clear();
        trackedWorld = null;
        GlowingRegistry.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tick & Render
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            highlightedEntities.clear();
            notifiedEntities.clear();
            notifiedEndermites.clear();
            trackedWorld = null;
            return;
        }
        if (trackedWorld != mc.level) {
            notifiedEntities.clear();
            notifiedEndermites.clear();
            trackedWorld = mc.level;
        }

        highlightedEntities.clear();
        GlowingRegistry.clear();
        notifiedEntities.removeIf(id -> mc.level.getEntity(id) == null);
        Set<Integer> currentEndermites = new HashSet<>();

        String dim = mc.level.dimension().identifier().toString();
        boolean spectral = highlightMode.get() == HighlightMode.Spectral;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob)) continue;
            if (ignoredEntities.get().contains(mob.getType())) continue;
            if (mc.player.distanceTo(mob) > range.get()) continue;

            boolean trackedEndermite = trackEndermites.get() && mc.level.dimension().equals(Level.OVERWORLD)
                && mob.getType() == EntityType.ENDERMITE && mob.isAlive();
            AnomalyType type = trackedEndermite ? AnomalyType.ENDERMITE : resolveAnomalyType(mob, dim);
            if (type == null) continue;

            highlightedEntities.put(mob.getId(), type);
            if (trackedEndermite) {
                currentEndermites.add(mob.getId());
                if (notifiedEndermites.add(mob.getId())) {
                    if (chatNotification.get()) info("Endermite detected.");
                    mc.player.playSound(SoundEvents.ENDERMITE_AMBIENT, 1.0f, 1.0f);
                }
            }

            if (spectral) {
                SettingColor c = getColorForType(type);
                GlowingRegistry.add(mob.getId(), (255 << 24) | (c.r << 16) | (c.g << 8) | c.b);
            }

            if (!trackedEndermite && chatNotification.get() && notifiedEntities.add(mob.getId())) {
                String mobName = mob.getType().getDescription().getString();
                switch (type) {
                    case CHESTED -> info("Chested animal detected: "    + mobName);
                    case ITEM    -> info("Item anomaly detected: "      + mobName);
                    default      -> info("Dimension anomaly detected: " + mobName);
                }
            }
        }
        notifiedEndermites.retainAll(currentEndermites);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null || highlightedEntities.isEmpty()) return;

        boolean wireframe = highlightMode.get() == HighlightMode.Wireframe;
        boolean pulse = highlightMode.get() == HighlightMode.Pulse;

        for (Map.Entry<Integer, AnomalyType> entry : highlightedEntities.entrySet()) {
            Entity entity = mc.level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob)) continue;

            SettingColor color = getColorForType(entry.getValue());
            if (entry.getValue() == AnomalyType.ENDERMITE && endermiteBeam.get()) {
                AABB box = mob.getBoundingBox();
                double x = (box.minX + box.maxX) / 2.0;
                double z = (box.minZ + box.maxZ) / 2.0;
                int bottom = mc.level.getMinY();
                event.renderer.box(new AABB(x - 0.15, bottom, z - 0.15,
                    x + 0.15, bottom + mc.level.getHeight(), z + 0.15),
                    withAlpha(color, 80), color, ShapeMode.Both, 0);
            }

            if (wireframe) {
                renderGlowLayers(event, mob.getBoundingBox(), color);
                event.renderer.box(mob.getBoundingBox(), withAlpha(color, 0), color, ShapeMode.Lines, 0);
            } else if (pulse) {
                renderPulseBox(event, mob.getBoundingBox(), color);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers & Detection Logic
    // ═══════════════════════════════════════════════════════════════════════════

    private void renderGlowLayers(Render3DEvent event, AABB box, SettingColor color) {
        int    layers    = glowLayers.get();
        double spread    = glowSpread.get();
        int    baseAlpha = glowBaseAlpha.get();

        for (int i = layers; i >= 1; i--) {
            double expansion = spread * i;
            int layerAlpha   = Math.max(4, (int)(baseAlpha * (1.0 - (double)(i - 1) / layers)));
            event.renderer.box(box.inflate(expansion), withAlpha(color, layerAlpha), withAlpha(color, 0), ShapeMode.Sides, 0);
        }
    }

    private float getPulseFactor() {
        double speed = pulseSpeed.get();
        double t = System.currentTimeMillis() / 1000.0;
        double phase = t * speed * Math.PI * 2.0;
        return (float)((Math.sin(phase) + 1.0) * 0.5);
    }

    private int applyPulse(int baseAlpha) {
        float f = getPulseFactor();
        int min = pulseMinAlpha.get();
        int max = pulseMaxAlpha.get();
        return Math.min(255, Math.max(0, (int)(min + (max - min) * f)));
    }

    private void renderPulseBox(Render3DEvent event, AABB box, SettingColor base) {
        int pa = applyPulse(base.a);
        SettingColor pColor = withAlpha(base, pa);
        int layers = glowLayers.get();
        double spread = glowSpread.get();
        for (int i = layers; i >= 1; i--) {
            double expansion = spread * i;
            double taper = 1.0 - ((double)(i - 1) / layers) * 0.6;
            int layerAlpha = Math.max(4, (int)(pa * taper));
            event.renderer.box(box.inflate(expansion),
                withAlpha(pColor, layerAlpha), withAlpha(pColor, 0), ShapeMode.Sides, 0);
        }
        event.renderer.box(box, withAlpha(pColor, pa / 3), pColor, ShapeMode.Both, 0);
    }

    private AnomalyType resolveAnomalyType(Mob mob, String dimension) {
        if (detectChestedAnimals.get() && hasChestAttachment(mob)) return AnomalyType.CHESTED;
        if (detectUnnaturalItems.get() && hasUnnaturalItems(mob))  return AnomalyType.ITEM;

        EntityType<?> type = mob.getType();

        return switch (dimension) {
            case "minecraft:overworld" -> {
                if (NETHER_NATIVES.contains(type)) yield AnomalyType.DIMENSION_NETHER;
                if (END_NATIVES.contains(type))    yield AnomalyType.DIMENSION_END;
                yield null;
            }
            case "minecraft:the_nether" -> {
                if (OVERWORLD_NATIVES.contains(type)) yield AnomalyType.DIMENSION_OVERWORLD;
                if (END_NATIVES.contains(type))       yield AnomalyType.DIMENSION_END;
                yield null;
            }
            case "minecraft:the_end" -> {
                if (OVERWORLD_NATIVES.contains(type)) yield AnomalyType.DIMENSION_OVERWORLD;
                if (NETHER_NATIVES.contains(type))    yield AnomalyType.DIMENSION_NETHER;
                yield null;
            }
            default -> null;
        };
    }

    private boolean hasUnnaturalItems(Mob mob) {
        for (ItemStack stack : com.example.addon.utils.MinecraftCompat.armorStacks(mob)) {
            if (isUnnatural(stack)) return true;
        }
        boolean skipMainHand = (mob.getType() == EntityType.PIGLIN
                                    && mob.getMainHandItem().is(Items.CROSSBOW))
                            || (mob.getType() == EntityType.ZOMBIFIED_PIGLIN
                                    && mob.getMainHandItem().is(Items.GOLDEN_SWORD));
        if (!skipMainHand && isUnnatural(mob.getMainHandItem())) return true;
        if (isUnnatural(mob.getOffhandItem())) return true;
        return false;
    }

    private boolean isUnnatural(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();

        if (item == Items.ELYTRA) return true;
        if (item instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) return true;
        if (detectPumpkins.get() && (item == Items.CARVED_PUMPKIN || item == Items.JACK_O_LANTERN)) return true;

        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId.getNamespace().equals("minecraft") && itemId.getPath().startsWith("netherite_")) return true;

        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants == null || enchants.isEmpty()) return false;

        for (Holder<Enchantment> enchantmentEntry : enchants.keySet()) {
            Enchantment enchantment = enchantmentEntry.value();
            if (enchantment == null) continue;
            if (enchantmentEntry.is(Enchantments.MENDING)) return true;
            if (enchants.getLevel(enchantmentEntry) > enchantment.getMaxLevel()) return true;
        }

        return false;
    }

    private boolean hasChestAttachment(Mob mob) {
        if (mob instanceof AbstractChestedHorse donkey) return donkey.hasChest();
        if (mob instanceof Llama llama)           return llama.hasChest();
        return false;
    }

    private SettingColor getColorForType(AnomalyType type) {
        return switch (type) {
            case ITEM, CHESTED       -> itemAnomalyLineColor.get();
            case ENDERMITE           -> endermiteColor.get();
            case DIMENSION_END       -> endLineColor.get();
            case DIMENSION_NETHER    -> netherLineColor.get();
            case DIMENSION_OVERWORLD -> overworldLineColor.get();
        };
    }

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }

    public Map<Integer, AnomalyType> getAnomalies() {
        return highlightedEntities;
    }
}