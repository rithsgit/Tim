package com.example.addon.modules;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import org.lwjgl.glfw.GLFW;

import com.example.addon.Tim;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.DyeColor;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/** Replenish mode for each item — controls how many to keep in inventory. */
enum ReplenishMode {
    Single, // Keep exactly 1 item
    Fill,   // Fill inventory with as many as possible
    Custom  // Keep a specific number of items
}

public class Inventory101 extends Module {
    private final SettingGroup sgPresets   = settings.createGroup("Presets");
    private final SettingGroup sgRegear    = settings.createGroup("Regear");
    private final SettingGroup sgReplenish = settings.createGroup("Replenish");
    private final SettingGroup sgOrganizer = settings.createGroup("Organizer");
    private final SettingGroup sgCleaner   = settings.createGroup("Cleaner");
    private final SettingGroup sgAutoTool  = settings.createGroup("Auto-Tool");
    private final SettingGroup sgPreview   = settings.createGroup("Preview");

    // ── Presets ──
    private final Setting<String> preset1Name = sgPresets.add(new StringSetting.Builder()
        .name("preset-1-name").description("Custom name for Preset 1.")
        .defaultValue("1")
        .build()
    );

    private final Setting<String> preset2Name = sgPresets.add(new StringSetting.Builder()
        .name("preset-2-name").description("Custom name for Preset 2.")
        .defaultValue("2")
        .build()
    );

    private final Setting<String> preset1Data = sgPresets.add(new StringSetting.Builder()
        .name("preset-1-data").description("Saved data for inventory preset 1.")
        .defaultValue("").visible(() -> false)
        .build()
    );

    private final Setting<String> preset2Data = sgPresets.add(new StringSetting.Builder()
        .name("preset-2-data").description("Saved data for inventory preset 2.")
        .defaultValue("").visible(() -> false)
        .build()
    );

    // ── Regear ──
    private final Setting<Boolean> showRegearButton = sgRegear.add(new BoolSetting.Builder()
        .name("show-button")
        .description("Shows the G (Gear) button in shulker boxes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> regearDelay = sgRegear.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between armor movement actions.")
        .defaultValue(2).min(1).sliderMax(20)
        .build()
    );

    // ── Replenish ──
    private final Setting<Boolean> showReplenishButton = sgReplenish.add(new BoolSetting.Builder()
        .name("show-button")
        .description("Shows the R (Replenish) button in shulker boxes.")
        .defaultValue(true)
        .build()
    );

    // ── Ender Chest ──
    private final Setting<Boolean> replenishEnderChest = sgReplenish.add(new BoolSetting.Builder()
        .name("ender-chest")
        .description("Replenish Ender Chests from shulker boxes.")
        .defaultValue(true)
        .build()
    );
    private final Setting<ReplenishMode> enderChestMode = sgReplenish.add(new EnumSetting.Builder<ReplenishMode>()
        .name("ender-chest-mode")
        .description("How many Ender Chests to keep in inventory.")
        .defaultValue(ReplenishMode.Fill)
        .visible(replenishEnderChest::get)
        .build()
    );
    private final Setting<Integer> enderChestCount = sgReplenish.add(new IntSetting.Builder()
        .name("ender-chest-count")
        .description("Exact number of Ender Chests to maintain.")
        .defaultValue(1).min(1).sliderMax(512)
        .visible(() -> replenishEnderChest.get() && enderChestMode.get() == ReplenishMode.Custom)
        .build()
    );

    // ── Obsidian ──
    private final Setting<Boolean> replenishObsidian = sgReplenish.add(new BoolSetting.Builder()
        .name("obsidian")
        .description("Replenish Obsidian from shulker boxes.")
        .defaultValue(true)
        .build()
    );
    private final Setting<ReplenishMode> obsidianMode = sgReplenish.add(new EnumSetting.Builder<ReplenishMode>()
        .name("obsidian-mode")
        .description("How many Obsidian to keep in inventory.")
        .defaultValue(ReplenishMode.Fill)
        .visible(replenishObsidian::get)
        .build()
    );
    private final Setting<Integer> obsidianCount = sgReplenish.add(new IntSetting.Builder()
        .name("obsidian-count")
        .description("Exact number of Obsidian to maintain.")
        .defaultValue(64).min(1).sliderMax(512)
        .visible(() -> replenishObsidian.get() && obsidianMode.get() == ReplenishMode.Custom)
        .build()
    );

    // ── Firework Rockets ──
    private final Setting<Boolean> replenishFireworkRocket = sgReplenish.add(new BoolSetting.Builder()
        .name("firework-rocket")
        .description("Replenish Firework Rockets from shulker boxes.")
        .defaultValue(true)
        .build()
    );
    private final Setting<ReplenishMode> fireworkRocketMode = sgReplenish.add(new EnumSetting.Builder<ReplenishMode>()
        .name("firework-rocket-mode")
        .description("How many Firework Rockets to keep in inventory.")
        .defaultValue(ReplenishMode.Fill)
        .visible(replenishFireworkRocket::get)
        .build()
    );
    private final Setting<Integer> fireworkRocketCount = sgReplenish.add(new IntSetting.Builder()
        .name("firework-rocket-count")
        .description("Exact number of Firework Rockets to maintain.")
        .defaultValue(64).min(1).sliderMax(512)
        .visible(() -> replenishFireworkRocket.get() && fireworkRocketMode.get() == ReplenishMode.Custom)
        .build()
    );

    // ── Enchanted Golden Apples ──
    private final Setting<Boolean> replenishEnchantedGoldenApple = sgReplenish.add(new BoolSetting.Builder()
        .name("enchanted-golden-apple")
        .description("Replenish Enchanted Golden Apples from shulker boxes.")
        .defaultValue(true)
        .build()
    );
    private final Setting<ReplenishMode> enchantedGoldenAppleMode = sgReplenish.add(new EnumSetting.Builder<ReplenishMode>()
        .name("enchanted-golden-apple-mode")
        .description("How many Enchanted Golden Apples to keep in inventory.")
        .defaultValue(ReplenishMode.Single)
        .visible(replenishEnchantedGoldenApple::get)
        .build()
    );
    private final Setting<Integer> enchantedGoldenAppleCount = sgReplenish.add(new IntSetting.Builder()
        .name("enchanted-golden-apple-count")
        .description("Exact number of Enchanted Golden Apples to maintain.")
        .defaultValue(1).min(1).sliderMax(512)
        .visible(() -> replenishEnchantedGoldenApple.get() && enchantedGoldenAppleMode.get() == ReplenishMode.Custom)
        .build()
    );

    // ── Totem of Undying ──
    private final Setting<Boolean> replenishTotem = sgReplenish.add(new BoolSetting.Builder()
        .name("totem-of-undying")
        .description("Replenish Totems of Undying from shulker boxes.")
        .defaultValue(true)
        .build()
    );
    private final Setting<ReplenishMode> totemMode = sgReplenish.add(new EnumSetting.Builder<ReplenishMode>()
        .name("totem-mode")
        .description("How many Totems of Undying to keep in inventory.")
        .defaultValue(ReplenishMode.Single)
        .visible(replenishTotem::get)
        .build()
    );
    private final Setting<Integer> totemCount = sgReplenish.add(new IntSetting.Builder()
        .name("totem-count")
        .description("Exact number of Totems to maintain.")
        .defaultValue(1).min(1).sliderMax(36)
        .visible(() -> replenishTotem.get() && totemMode.get() == ReplenishMode.Custom)
        .build()
    );

    // ── Elytra ──
    private final Setting<Boolean> replenishElytra = sgReplenish.add(new BoolSetting.Builder()
        .name("elytra")
        .description("Replenish Elytras from shulker boxes (durability-aware swapping).")
        .defaultValue(true)
        .build()
    );
    private final Setting<ReplenishMode> elytraMode = sgReplenish.add(new EnumSetting.Builder<ReplenishMode>()
        .name("elytra-mode")
        .description("How many Elytras to keep in inventory (not counting armor slot).")
        .defaultValue(ReplenishMode.Single)
        .visible(replenishElytra::get)
        .build()
    );
    private final Setting<Integer> elytraCount = sgReplenish.add(new IntSetting.Builder()
        .name("elytra-count")
        .description("Exact number of Elytras to maintain (inventory only, not armor slot).")
        .defaultValue(1).min(1).sliderMax(36)
        .visible(() -> replenishElytra.get() && elytraMode.get() == ReplenishMode.Custom)
        .build()
    );
    private final Setting<Integer> elytraThreshold = sgReplenish.add(new IntSetting.Builder()
        .name("elytra-threshold")
        .description("Durability threshold to consider an elytra as needing replacement.")
        .defaultValue(15).min(1).sliderMax(100)
        .visible(replenishElytra::get)
        .build()
    );

    // ── End Crystal ──
    private final Setting<Boolean> replenishEndCrystal = sgReplenish.add(new BoolSetting.Builder()
        .name("end-crystal")
        .description("Replenish End Crystals from shulker boxes.")
        .defaultValue(true)
        .build()
    );
    private final Setting<ReplenishMode> endCrystalMode = sgReplenish.add(new EnumSetting.Builder<ReplenishMode>()
        .name("end-crystal-mode")
        .description("How many End Crystals to keep in inventory.")
        .defaultValue(ReplenishMode.Fill)
        .visible(replenishEndCrystal::get)
        .build()
    );
    private final Setting<Integer> endCrystalCount = sgReplenish.add(new IntSetting.Builder()
        .name("end-crystal-count")
        .description("Exact number of End Crystals to maintain.")
        .defaultValue(16).min(1).sliderMax(512)
        .visible(() -> replenishEndCrystal.get() && endCrystalMode.get() == ReplenishMode.Custom)
        .build()
    );

    // ── Custom Items ──
    private final Setting<Boolean> replenishCustom = sgReplenish.add(new BoolSetting.Builder()
        .name("custom-items-enabled")
        .description("Enable replenishing custom items from shulker boxes.")
        .defaultValue(false)
        .build()
    );
    private final Setting<List<Item>> customReplenishItems = sgReplenish.add(new ItemListSetting.Builder()
        .name("custom-items")
        .description("Additional items to replenish. Order determines priority — items at the top are restocked first.")
        .defaultValue(new ArrayList<>())
        .visible(replenishCustom::get)
        .build()
    );
    private final Setting<ReplenishMode> customMode = sgReplenish.add(new EnumSetting.Builder<ReplenishMode>()
        .name("custom-mode")
        .description("Replenish mode for all custom items.")
        .defaultValue(ReplenishMode.Fill)
        .visible(() -> replenishCustom.get() && !customReplenishItems.get().isEmpty())
        .build()
    );
    private final Setting<Integer> customCount = sgReplenish.add(new IntSetting.Builder()
        .name("custom-count")
        .description("Exact number to maintain for each custom item.")
        .defaultValue(64).min(1).sliderMax(512)
        .visible(() -> replenishCustom.get() && !customReplenishItems.get().isEmpty() && customMode.get() == ReplenishMode.Custom)
        .build()
    );

    private final Setting<Integer> replenishDelay = sgReplenish.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between movement actions.")
        .defaultValue(2).min(1).sliderMax(20)
        .build()
    );

    // ── Preview Settings ──
    private final Setting<Integer> iconSize = sgPreview.add(new IntSetting.Builder()
        .name("icon-size")
        .description("Size of the content icon overlay.")
        .defaultValue(10)
        .min(5)
        .sliderMax(16)
        .build()
    );

    private final Setting<Integer> xOffset = sgPreview.add(new IntSetting.Builder()
        .name("x-offset")
        .description("Horizontal content icon offset.")
        .defaultValue(3)
        .min(-10)
        .sliderMax(5)
        .build()
    );

    private final Setting<Integer> yOffset = sgPreview.add(new IntSetting.Builder()
        .name("y-offset")
        .description("Vertical content icon offset.")
        .defaultValue(3)
        .min(-10)
        .sliderMax(5)
        .build()
    );

    public double getIconScale() {
        return iconSize.get() / 16.0;
    }

    private List<Item> getReplenishWhitelist() {
        List<Item> whitelist = new ArrayList<>();
        if (replenishEnderChest.get())                whitelist.add(Items.ENDER_CHEST);
        if (replenishObsidian.get())                  whitelist.add(Items.OBSIDIAN);
        if (replenishFireworkRocket.get())            whitelist.add(Items.FIREWORK_ROCKET);
        if (replenishEnchantedGoldenApple.get())      whitelist.add(Items.ENCHANTED_GOLDEN_APPLE);
        if (replenishTotem.get())                     whitelist.add(Items.TOTEM_OF_UNDYING);
        if (replenishElytra.get())                    whitelist.add(Items.ELYTRA);
        if (replenishEndCrystal.get())                whitelist.add(Items.END_CRYSTAL);
        if (replenishCustom.get()) {
            for (Item item : customReplenishItems.get()) {
                if (!whitelist.contains(item)) whitelist.add(item);
            }
        }
        return whitelist;
    }

    private int getPullLimit(Item item) {
        if (item == Items.ENDER_CHEST) {
            return switch (enderChestMode.get()) {
                case Single -> item.getMaxCount();
                case Fill -> Integer.MAX_VALUE;
                case Custom -> enderChestCount.get();
            };
        }
        if (item == Items.OBSIDIAN) {
            return switch (obsidianMode.get()) {
                case Single -> item.getMaxCount();
                case Fill -> Integer.MAX_VALUE;
                case Custom -> obsidianCount.get();
            };
        }
        if (item == Items.FIREWORK_ROCKET) {
            return switch (fireworkRocketMode.get()) {
                case Single -> item.getMaxCount();
                case Fill -> Integer.MAX_VALUE;
                case Custom -> fireworkRocketCount.get();
            };
        }
        if (item == Items.ENCHANTED_GOLDEN_APPLE) {
            return switch (enchantedGoldenAppleMode.get()) {
                case Single -> item.getMaxCount();
                case Fill -> Integer.MAX_VALUE;
                case Custom -> enchantedGoldenAppleCount.get();
            };
        }
        if (item == Items.TOTEM_OF_UNDYING) {
            return switch (totemMode.get()) {
                case Single -> item.getMaxCount();
                case Fill -> Integer.MAX_VALUE;
                case Custom -> totemCount.get();
            };
        }
        if (item == Items.ELYTRA) {
            return switch (elytraMode.get()) {
                case Single -> item.getMaxCount();
                case Fill -> Integer.MAX_VALUE;
                case Custom -> elytraCount.get();
            };
        }
        if (item == Items.END_CRYSTAL) {
            return switch (endCrystalMode.get()) {
                case Single -> item.getMaxCount();
                case Fill -> Integer.MAX_VALUE;
                case Custom -> endCrystalCount.get();
            };
        }
        return switch (customMode.get()) {
            case Single -> item.getMaxCount();
            case Fill -> Integer.MAX_VALUE;
            case Custom -> customCount.get();
        };
    }

    // ── Organizer ──
    private final Setting<Boolean> showSortButton = sgOrganizer.add(new BoolSetting.Builder()
        .name("show-sort-button").description("Show a sort button in chests.")
        .defaultValue(true).build()
    );

    private final Setting<Integer> sortDelay = sgOrganizer.add(new IntSetting.Builder()
        .name("sort-delay").description("Delay in ticks between sort actions.")
        .defaultValue(2).min(1).visible(showSortButton::get)
        .build()
    );

    private final Setting<Boolean> shiftClickAll = sgOrganizer.add(new BoolSetting.Builder()
        .name("shift-click-all")
        .description("When shift-clicking an item, moves all items of the same type from that inventory.")
        .defaultValue(true)
        .build()
    );

    // ── Cleaner ──
    private final Setting<Boolean> autoTrash = sgCleaner.add(new BoolSetting.Builder()
        .name("auto-trash")
        .description("Automatically discards whitelisted items (drops in world/chat, throws in containers).")
        .defaultValue(false).build()
    );

    private final Setting<List<Item>> trashItems = sgCleaner.add(new ItemListSetting.Builder()
        .name("trash-items").description("Items to automatically discard.")
        .defaultValue(new ArrayList<>()).visible(autoTrash::get)
        .build()
    );

    private final Setting<Integer> trashDelay = sgCleaner.add(new IntSetting.Builder()
        .name("trash-delay").description("Delay in ticks between discard actions.")
        .defaultValue(2).min(1).visible(autoTrash::get)
        .build()
    );

    // ── Auto Tool ──
    private final Setting<Boolean> autoTool = sgAutoTool.add(new BoolSetting.Builder()
        .name("auto-tool")
        .description("Automatically swaps to the best tool when breaking blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> silentAutoTool = sgAutoTool.add(new BoolSetting.Builder()
        .name("silent-swap")
        .description("Swaps to the tool silently.")
        .defaultValue(true)
        .visible(autoTool::get)
        .build()
    );

    // ── State ──
    private boolean saveMode      = false;
    private boolean isRegearing    = false;
    private int     regearTimer    = 0;
    private int     regearPresetIndex = 0;
    private boolean isReplenishing = false;
    private int     replenishTimer = 0;
    private boolean replenishedForCurrentScreen = false;
    private Map<Item, Integer> pulledThisSession = new LinkedHashMap<>();
    private boolean isSorting    = false;
    private int     sortTimer    = 0;
    private boolean isInvSorting  = false;
    private int     invSortTimer  = 0;
    private int     invSortPreset = 0;
    private boolean isTrashing    = false;
    private int     trashTimer    = 0;
    private boolean trashedForCurrentScreen = false;

    private boolean wasClicking   = false;
    private double  lastMouseX    = -1;
    private double  lastMouseY    = -1;
    private final Set<Integer> processedInDrag = new HashSet<>();

    private boolean moveAllActionTaken  = false;
    private boolean wasBreaking         = false;
    private int     prevSlotAutoTool    = -1;

    // ── Preview Cache & State ──
    private boolean drawingOverlay = false;
    private static final WeakHashMap<ItemStack, CachedDominant> dominantCache = new WeakHashMap<>();

    // ── Cross-Version Reflection Caches (1.21.4 - 1.21.11) ──
    private static Method  GET_SELECTED_SLOT_METHOD = null;
    private static Field   SELECTED_SLOT_FIELD      = null;
    private static boolean SLOT_REFLECTION_INIT     = false;

    private static Method  NBT_PARSE_METHOD         = null;
    private static Method  NBT_GET_LIST_1_METHOD    = null;
    private static Method  NBT_GET_LIST_2_METHOD    = null;
    private static Method  NBT_LIST_GET_CMP_METHOD  = null;
    private static Method  NBT_CMP_GET_INT_METHOD   = null;
    private static boolean NBT_REFLECTION_INIT      = false;

    private static Method  MATRIX_PUSH_METHOD       = null;
    private static Method  MATRIX_POP_METHOD        = null;
    private static Method  MATRIX_TRANSLATE_METHOD  = null;
    private static Method  MATRIX_SCALE_METHOD      = null;
    private static boolean MATRIX_IS_2D             = false;
    private static boolean MATRIX_REFLECTION_INIT   = false;

    private static class CachedDominant {
        private final Object data;
        private final ItemStack stack;

        private CachedDominant(Object data, ItemStack stack) {
            this.data = data;
            this.stack = stack;
        }
    }

    public Inventory101() {
        super(Tim.CATEGORY, "inventory-101", "Manages inventory layouts with shulker boxes, bundles, and ender chests.");
    }

    @Override
    public void onActivate() {
        dominantCache.clear();
        drawingOverlay = false;
    }

    @Override
    public void onDeactivate() {
        isRegearing    = false;
        regearTimer    = 0;
        regearPresetIndex = 0;
        isReplenishing = false;
        replenishTimer = 0;
        isSorting    = false;
        isInvSorting = false;
        isTrashing   = false;
        wasClicking  = false;
        lastMouseX   = -1;
        lastMouseY   = -1;
        saveMode     = false;
        processedInDrag.clear();
        moveAllActionTaken   = false;
        wasBreaking          = false;
        prevSlotAutoTool     = -1;
        trashTimer           = 0;
        trashedForCurrentScreen = false;
        replenishedForCurrentScreen = false;
        pulledThisSession.clear();
        dominantCache.clear();
        drawingOverlay = false;
    }

    // ── Cross-Version Helpers (1.21.4, 1.21.5, 1.21.8, 1.21.11) ──────────────

    private int getSelectedSlot() {
        if (mc.player == null) return 0;
        PlayerInventory inv = mc.player.getInventory();

        if (!SLOT_REFLECTION_INIT) {
            SLOT_REFLECTION_INIT = true;
            try {
                GET_SELECTED_SLOT_METHOD = PlayerInventory.class.getMethod("getSelectedSlot");
            } catch (NoSuchMethodException e1) {
                try {
                    GET_SELECTED_SLOT_METHOD = PlayerInventory.class.getMethod("method_67532");
                } catch (NoSuchMethodException ignored) {}
            }

            if (GET_SELECTED_SLOT_METHOD == null) {
                for (String fieldName : new String[]{"selectedSlot", "field_7545"}) {
                    try {
                        Field f = PlayerInventory.class.getDeclaredField(fieldName);
                        f.setAccessible(true);
                        SELECTED_SLOT_FIELD = f;
                        break;
                    } catch (NoSuchFieldException ignored) {}
                }
            }
        }

        try {
            if (GET_SELECTED_SLOT_METHOD != null) {
                return (int) GET_SELECTED_SLOT_METHOD.invoke(inv);
            } else if (SELECTED_SLOT_FIELD != null) {
                return SELECTED_SLOT_FIELD.getInt(inv);
            }
        } catch (Exception ignored) {}

        return 0;
    }

    @SuppressWarnings("unchecked")
    private static Object unwrapOptional(Object val) {
        if (val instanceof Optional<?> opt) return opt.orElse(null);
        return val;
    }

    private static void initNbtReflection() {
        if (NBT_REFLECTION_INIT) return;
        NBT_REFLECTION_INIT = true;

        for (String name : new String[]{"readCompound", "parse", "method_68122", "method_10718"}) {
            try {
                NBT_PARSE_METHOD = StringNbtReader.class.getMethod(name, String.class);
                break;
            } catch (NoSuchMethodException ignored) {}
        }

        for (String name : new String[]{"getList", "method_10554"}) {
            try {
                NBT_GET_LIST_1_METHOD = NbtCompound.class.getMethod(name, String.class);
                break;
            } catch (NoSuchMethodException ignored) {}
            try {
                NBT_GET_LIST_2_METHOD = NbtCompound.class.getMethod(name, String.class, int.class);
                break;
            } catch (NoSuchMethodException ignored) {}
        }

        for (String name : new String[]{"getCompound", "method_10602"}) {
            try {
                NBT_LIST_GET_CMP_METHOD = NbtList.class.getMethod(name, int.class);
                break;
            } catch (NoSuchMethodException ignored) {}
        }

        for (String name : new String[]{"getInt", "method_10550"}) {
            try {
                NBT_CMP_GET_INT_METHOD = NbtCompound.class.getMethod(name, String.class);
                break;
            } catch (NoSuchMethodException ignored) {}
        }
    }

    private static NbtCompound parseNbtCompoundCompat(String nbtString) throws Exception {
        initNbtReflection();
        if (NBT_PARSE_METHOD != null) {
            return (NbtCompound) NBT_PARSE_METHOD.invoke(null, nbtString);
        }
        return null;
    }

    private static NbtList getNbtListCompat(NbtCompound nbt, String key) throws Exception {
        initNbtReflection();
        if (NBT_GET_LIST_1_METHOD != null) {
            return (NbtList) unwrapOptional(NBT_GET_LIST_1_METHOD.invoke(nbt, key));
        } else if (NBT_GET_LIST_2_METHOD != null) {
            return (NbtList) unwrapOptional(NBT_GET_LIST_2_METHOD.invoke(nbt, key, (int) NbtElement.COMPOUND_TYPE));
        }
        return null;
    }

    private static NbtCompound getCompoundAtCompat(NbtList list, int index) throws Exception {
        initNbtReflection();
        if (NBT_LIST_GET_CMP_METHOD != null) {
            return (NbtCompound) unwrapOptional(NBT_LIST_GET_CMP_METHOD.invoke(list, index));
        }
        return null;
    }

    private static int getIntCompat(NbtCompound cmp, String key) throws Exception {
        initNbtReflection();
        if (NBT_CMP_GET_INT_METHOD != null) {
            Object val = unwrapOptional(NBT_CMP_GET_INT_METHOD.invoke(cmp, key));
            if (val instanceof Number num) return num.intValue();
        }
        return -1;
    }

    // ── Public APIs ──────────────────────────────────────────────────────────

    public String getPresetName(int index) {
        return (index == 1) ? preset1Name.get() : preset2Name.get();
    }

    public boolean isRegearButtonEnabled() {
        return showRegearButton.get();
    }

    public void startRegearing() {
        if (isBusy()) return;
        isRegearing = true;
        regearTimer = 0;
        regearPresetIndex = 0;
        info("Regearing Essentials...");
    }

    public void toggleSaveMode() {
        if (isBusy()) return;
        saveMode = !saveMode;
        info(saveMode ? "§eSelect a preset slot (1 or 2) to SAVE current layout." : "§7Save mode §ccancelled§7.");
    }

    public boolean isSaveMode() { return saveMode; }

    public boolean isPresetEmpty(int index) {
        String data = (index == 1) ? preset1Data.get() : preset2Data.get();
        return data == null || data.isEmpty();
    }

    public void handlePreset(int index) {
        if (isBusy() && !saveMode) return;
        if (saveMode) {
            saveInventory(index);
            saveMode = false;
            info("Inventory layout saved to Preset §a" + getPresetName(index) + "§7.");
        } else {
            isRegearing = true;
            regearTimer = 0;
            regearPresetIndex = index;
            info("Loading Preset §6" + getPresetName(index) + "§7...");
        }
    }

    public void clearPresets() {
        preset1Data.set("");
        preset2Data.set("");
        saveMode = false;
        info("Presets cleared.");
    }

    public void startInvSort(int presetIndex) {
        if (isBusy()) return;
        if (isPresetEmpty(presetIndex)) {
            warning("Preset " + getPresetName(presetIndex) + " is empty.");
            return;
        }
        invSortPreset = presetIndex;
        isInvSorting  = true;
        invSortTimer  = 0;
        info("Sorting inventory to Preset §6" + getPresetName(presetIndex) + "§7...");
    }

    public boolean isSortButtonEnabled() { return showSortButton.get(); }

    public void startSorting() {
        if (isBusy()) return;
        isSorting = true;
        sortTimer = 0;
    }

    public boolean isReplenishButtonEnabled() {
        if (getReplenishWhitelist().isEmpty()) return false;
        return showReplenishButton.get();
    }

    public void startReplenishing() {
        if (isBusy()) return;
        if (getReplenishWhitelist().isEmpty()) {
            warning("No items to replenish (whitelist is empty).");
            return;
        }
        pulledThisSession.clear();
        isReplenishing = true;
        replenishTimer = 0;
        info("Restocking whitelisted items...");
    }

    // ── Preview APIs ──

    public void openPreview(ItemStack stack) {
        if (mc.player == null || stack.isEmpty()) return;

        Screen previousScreen = mc.currentScreen;
        Object data = getContainerData(stack);

        if (data instanceof ContainerComponent container) {
            SimpleInventory inventory = new SimpleInventory(27);
            int slot = 0;
            for (ItemStack item : container.iterateNonEmpty()) {
                if (slot < 27) {
                    inventory.setStack(slot++, item.copy());
                }
            }

            mc.setScreen(new ShulkerBoxScreen(
                new ShulkerBoxScreenHandler(mc.player.playerScreenHandler.syncId, mc.player.getInventory(), inventory),
                mc.player.getInventory(),
                stack.getName()
            ) {
                @Override
                public void close() {
                    client.setScreen(previousScreen);
                }
            });
        } else if (data instanceof BundleContentsComponent bundle) {
            SimpleInventory inventory = new SimpleInventory(64);
            int slot = 0;
            for (ItemStack item : bundle.iterate()) {
                if (slot < 64) {
                    inventory.setStack(slot++, item.copy());
                }
            }

            mc.setScreen(new GenericContainerScreen(
                GenericContainerScreenHandler.createGeneric9x6(mc.player.playerScreenHandler.syncId, mc.player.getInventory(), inventory),
                mc.player.getInventory(),
                stack.getName()
            ) {
                @Override
                public void close() {
                    client.setScreen(previousScreen);
                }
            });
        }
    }

    public void openEnderChestPreview(ItemStack chestStack) {
        if (mc.player == null) return;
        
        Screen previousScreen = mc.currentScreen;
        SimpleInventory inventory = new SimpleInventory(27);
        
        ContainerComponent container = chestStack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            int slot = 0;
            for (ItemStack item : container.iterateNonEmpty()) {
                if (slot < 27) {
                    inventory.setStack(slot, item.copy());
                    slot++;
                }
            }
        } else {
            net.minecraft.inventory.EnderChestInventory enderChest = mc.player.getEnderChestInventory();
            if (enderChest != null) {
                for (int i = 0; i < 27; i++) {
                    ItemStack st = enderChest.getStack(i);
                    inventory.setStack(i, st.isEmpty() ? cachedEnderChest[i] : st);
                }
            } else {
                for (int i = 0; i < 27; i++) {
                    inventory.setStack(i, cachedEnderChest[i].copy());
                }
            }
        }

        mc.setScreen(new ShulkerBoxScreen(
            new ShulkerBoxScreenHandler(mc.player.playerScreenHandler.syncId, mc.player.getInventory(), inventory),
            mc.player.getInventory(),
            chestStack.getName()
        ) {
            @Override
            public void close() {
                client.setScreen(previousScreen);
            }
        });
    }

    public static boolean isEnderChest(ItemStack stack) {
        return stack.isOf(Items.ENDER_CHEST);
    }

    public static boolean isBundle(ItemStack stack) {
        return stack.contains(DataComponentTypes.BUNDLE_CONTENTS);
    }

    private static final ItemStack[] cachedEnderChest = new ItemStack[27];
    static {
        for (int i = 0; i < 27; i++) cachedEnderChest[i] = ItemStack.EMPTY;
    }

    public static void updateCachedEnderChest(net.minecraft.inventory.Inventory inventory) {
        for (int i = 0; i < 27 && i < inventory.size(); i++) {
            cachedEnderChest[i] = inventory.getStack(i).copy();
        }
    }

    public static Object getContainerData(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock) {
            return stack.get(DataComponentTypes.CONTAINER);
        }
        return stack.get(DataComponentTypes.BUNDLE_CONTENTS);
    }

    public static ItemStack getDominantItem(ItemStack containerStack) {
        if (containerStack.isEmpty()) return ItemStack.EMPTY;

        Object data = getContainerData(containerStack);
        if (data == null) return ItemStack.EMPTY;

        CachedDominant cached = dominantCache.get(containerStack);
        if (cached != null && Objects.equals(cached.data, data)) {
            return cached.stack;
        }

        Iterable<ItemStack> items;
        if (data instanceof ContainerComponent container) {
            items = container.iterateNonEmpty();
        } else if (data instanceof BundleContentsComponent bundle) {
            items = bundle.iterate();
        } else {
            return ItemStack.EMPTY;
        }

        Map<Item, DominantCounter> counts = new LinkedHashMap<>();
        for (ItemStack s : items) {
            if (s.isEmpty()) continue;
            DominantCounter counter = counts.computeIfAbsent(
                s.getItem(),
                k -> new DominantCounter(s.copyWithCount(1))
            );
            counter.slots++;
        }

        DominantCounter best = null;
        for (DominantCounter counter : counts.values()) {
            if (best == null || counter.slots > best.slots) {
                best = counter;
            }
        }

        ItemStack result = (best != null) ? best.stack : ItemStack.EMPTY;
        dominantCache.put(containerStack, new CachedDominant(data, result));
        return result;
    }

    public void renderIconOverlay(DrawContext context, ItemStack stack, int px, int py) {
        if (!this.isActive() || this.drawingOverlay || stack.isEmpty()) return;

        ItemStack dominant = getDominantItem(stack);
        if (dominant.isEmpty()) return;

        int size = this.iconSize.get();
        float scale = size / 16.0F;
        int ox = px + (16 - size) / 2 + this.xOffset.get();
        int oy = py + (16 - size) / 2 + this.yOffset.get();

        this.drawingOverlay = true;
        try {
            Object matrices = context.getMatrices();
            if (!MATRIX_REFLECTION_INIT) {
                MATRIX_REFLECTION_INIT = true;
                Class<?> mClass = matrices.getClass();
                try {
                    MATRIX_PUSH_METHOD      = mClass.getMethod("pushMatrix");
                    MATRIX_POP_METHOD       = mClass.getMethod("popMatrix");
                    MATRIX_TRANSLATE_METHOD = mClass.getMethod("translate", float.class, float.class);
                    MATRIX_SCALE_METHOD     = mClass.getMethod("scale", float.class, float.class);
                    MATRIX_IS_2D            = true;
                } catch (NoSuchMethodException e) {
                    for (String pushName : new String[]{"push", "method_22903"}) {
                        try { MATRIX_PUSH_METHOD = mClass.getMethod(pushName); break; } catch (NoSuchMethodException ignored) {}
                    }
                    for (String popName : new String[]{"pop", "method_22909"}) {
                        try { MATRIX_POP_METHOD = mClass.getMethod(popName); break; } catch (NoSuchMethodException ignored) {}
                    }
                    for (String transName : new String[]{"translate", "method_46416"}) {
                        try { MATRIX_TRANSLATE_METHOD = mClass.getMethod(transName, float.class, float.class, float.class); break; } catch (NoSuchMethodException ignored) {}
                    }
                    for (String scaleName : new String[]{"scale", "method_22905"}) {
                        try { MATRIX_SCALE_METHOD = mClass.getMethod(scaleName, float.class, float.class, float.class); break; } catch (NoSuchMethodException ignored) {}
                    }
                    MATRIX_IS_2D = false;
                }
            }

            if (MATRIX_PUSH_METHOD != null && MATRIX_POP_METHOD != null) {
                MATRIX_PUSH_METHOD.invoke(matrices);
                if (MATRIX_IS_2D) {
                    MATRIX_TRANSLATE_METHOD.invoke(matrices, (float) ox, (float) oy);
                    MATRIX_SCALE_METHOD.invoke(matrices, scale, scale);
                } else {
                    MATRIX_TRANSLATE_METHOD.invoke(matrices, (float) ox, (float) oy, 200.0F);
                    MATRIX_SCALE_METHOD.invoke(matrices, scale, scale, 1.0F);
                }
                context.drawItem(dominant, 0, 0);
                MATRIX_POP_METHOD.invoke(matrices);
            }
        } catch (Exception ignored) {
        } finally {
            this.drawingOverlay = false;
        }
    }

    private static class DominantCounter {
        private final ItemStack stack;
        private int slots = 0;

        private DominantCounter(ItemStack stack) {
            this.stack = stack;
        }
    }

    public static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickAutoTool();

        if (isRegearing) {
            if (!(mc.currentScreen instanceof ShulkerBoxScreen)) { isRegearing = false; return; }
            if (regearTimer > 0) { regearTimer--; return; }
            if (performRegearStep()) {
                regearTimer = regearDelay.get();
            } else {
                boolean wasPresetRegear = regearPresetIndex != 0;
                isRegearing = false;
                info("Regear §acomplete§7.");
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                if (!wasPresetRegear) startReplenishing();
            }
            return;
        }

        if (isReplenishing) {
            if (!(mc.currentScreen instanceof ShulkerBoxScreen)) { isReplenishing = false; return; }
            if (replenishTimer > 0) { replenishTimer--; return; }
            if (performReplenishStep()) {
                replenishTimer = replenishDelay.get();
            } else {
                info("Restock §acomplete§7.");
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                isReplenishing = false;
            }
            return;
        }

        if (isInvSorting) {
            if (!(mc.currentScreen instanceof InventoryScreen)) { isInvSorting = false; return; }
            if (invSortTimer > 0) { invSortTimer--; return; }
            if (performInvSortStep()) {
                invSortTimer = sortDelay.get();
            } else {
                isInvSorting = false;
                info(getPresetName(invSortPreset) + " sort §acomplete§7.");
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            return;
        }

        if (isSorting) {
            if (!(mc.currentScreen instanceof GenericContainerScreen)) { isSorting = false; return; }
            if (sortTimer > 0) { sortTimer--; return; }
            if (performSortStep()) {
                sortTimer = sortDelay.get();
            } else {
                isSorting = false;
                info("Sorting §acomplete§7.");
            }
            return;
        }

        tickMouseInteractions();
        tickAutoTrash();
        tickAutoDrop();
    }

    private void tickAutoTool() {
        if (!autoTool.get()) return;
        if (mc.interactionManager.isBreakingBlock()) {
            HitResult hit = mc.crosshairTarget;
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                BlockState state = mc.world.getBlockState(bhr.getBlockPos());
                if (!state.isAir()) {
                    int bestSlot = findBestTool(state);
                    int currentSlot = getSelectedSlot();
                    if (bestSlot != -1 && bestSlot != currentSlot) {
                        if (!wasBreaking) {
                            prevSlotAutoTool = currentSlot;
                            wasBreaking = true;
                        }
                        InvUtils.swap(bestSlot, silentAutoTool.get());
                    }
                }
            }
        } else if (wasBreaking) {
            if (silentAutoTool.get() && prevSlotAutoTool != -1) {
                InvUtils.swap(prevSlotAutoTool, true);
            }
            wasBreaking = false;
            prevSlotAutoTool = -1;
        }
    }

    private void tickMouseInteractions() {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            if (wasClicking) {
                processedInDrag.clear();
                lastMouseX = -1;
                moveAllActionTaken = false;
            }
            wasClicking = false;
            return;
        }

        boolean isClicking = Input.isButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean isShift    = Input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)
                          || Input.isKeyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT);

        if (isClicking && isShift) {
            if (!wasClicking) {
                if (shiftClickAll.get()) {
                    Slot focused = getFocusedSlot(screen);
                    if (focused != null && focused.hasStack()) {
                        moveAllActionTaken = true;
                        Item targetItem = focused.getStack().getItem();
                        boolean clickedInPlayerInventory = focused.inventory == mc.player.getInventory();
                        for (Slot slot : screen.getScreenHandler().slots) {
                            boolean slotInPlayerInventory = slot.inventory == mc.player.getInventory();
                            if (slot.hasStack() && slot.getStack().getItem() == targetItem) {
                                if (clickedInPlayerInventory == slotInPlayerInventory) {
                                    mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                                }
                            }
                        }
                    }
                }
                if (!moveAllActionTaken) {
                    processedInDrag.clear();
                    lastMouseX = mc.mouse.getX();
                    lastMouseY = mc.mouse.getY();
                    Slot focused = getFocusedSlot(screen);
                    if (focused != null && focused.hasStack() && !processedInDrag.contains(focused.id)) {
                        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, focused.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                        processedInDrag.add(focused.id);
                    }
                }
            } else if (!moveAllActionTaken) {
                double mouseX = mc.mouse.getX();
                double mouseY = mc.mouse.getY();
                
                if (lastMouseX != -1) {
                    double deltaX = mouseX - lastMouseX;
                    double deltaY = mouseY - lastMouseY;
                    double dist   = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                    
                    if (dist > 1 && Math.abs(deltaY) < 14) {
                        int steps = (int) Math.ceil(dist / 2.0);
                        for (int i = 0; i <= steps; i++) {
                            double currentX = lastMouseX + (deltaX * i / steps);
                            double currentY = lastMouseY + (deltaY * i / steps);
                            Slot slot = getSlotAt(screen, currentX, currentY);
                            if (slot != null && slot.hasStack() && !processedInDrag.contains(slot.id)) {
                                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                                processedInDrag.add(slot.id);
                            }
                        }
                    }
                }
                
                Slot focused = getFocusedSlot(screen);
                if (focused != null && focused.hasStack() && !processedInDrag.contains(focused.id)) {
                    mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, focused.id, 0, SlotActionType.QUICK_MOVE, mc.player);
                    processedInDrag.add(focused.id);
                }
                
                lastMouseX = mouseX;
                lastMouseY = mouseY;
            }
            wasClicking = true;
        } else {
            if (wasClicking) {
                processedInDrag.clear();
                lastMouseX = -1;
                moveAllActionTaken = false;
            }
            wasClicking = false;
        }
    }

    private boolean performRegearStep() {
        if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler)) return false;
        if (regearPresetIndex == 0) return performGenericRegearStep(handler);

        List<ItemStack> preset = getPreset(regearPresetIndex);
        int[] slotOrder = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            36,
            37, 38, 39, 40
        };

        for (int presetSlot : slotOrder) {
            ItemStack desired = preset.get(presetSlot);
            if (desired.isEmpty()) continue;
            if (isPresetSlotMatch(presetSlot, desired)) continue;

            if (presetSlot >= 36) {
                for (int j = 0; j < 27; j++) {
                    ItemStack shulkerStack = handler.getSlot(j).getStack();
                    if (shulkerStack.isEmpty() || !isSameItemType(shulkerStack, desired)) continue;
                    if (shulkerStack.isOf(Items.ELYTRA) && isLowDurabilityElytra(shulkerStack)) continue;

                    if (presetSlot == 36) {
                        quickMove(j);
                    } else {
                        int armorIndex = switch (presetSlot) {
                            case 37 -> 0; case 38 -> 1; case 39 -> 2; case 40 -> 3;
                            default -> -1;
                        };
                        if (armorIndex == -1) continue;
                        quickMove(j);
                    }
                    return true;
                }
            } else {
                int targetSlotId = mapInventoryToSlotId(presetSlot);
                if (targetSlotId == -1) continue;

                for (int i = 27; i < 63; i++) {
                    if (i == targetSlotId) continue;
                    ItemStack invStack = handler.getSlot(i).getStack();
                    if (invStack.isEmpty() || !isSameItemType(invStack, desired)) continue;

                    int sourceInvIndex = screenHandlerSlotToInvIndex(i);
                    if (sourceInvIndex >= 0 && sourceInvIndex < 36) {
                        ItemStack sourceDesired = preset.get(sourceInvIndex);
                        if (!sourceDesired.isEmpty() && isSameItemType(invStack, sourceDesired)) continue;
                    }
                    smartMove(i, targetSlotId);
                    return true;
                }

                for (int j = 0; j < 27; j++) {
                    ItemStack shulkerStack = handler.getSlot(j).getStack();
                    if (shulkerStack.isEmpty() || !isSameItemType(shulkerStack, desired)) continue;
                    if (shulkerStack.isOf(Items.ELYTRA) && isLowDurabilityElytra(shulkerStack)) continue;
                    smartMove(j, targetSlotId);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPresetSlotMatch(int presetSlot, ItemStack desired) {
        ItemStack current;
        if (presetSlot < 36) current = mc.player.getInventory().getStack(presetSlot);
        else if (presetSlot == 36) current = mc.player.getOffHandStack();
        else {
            EquipmentSlot eqSlot = presetIndexToArmorSlot(presetSlot);
            if (eqSlot == null) return false;
            current = mc.player.getEquippedStack(eqSlot);
        }
        if (!isSameItemType(current, desired)) return false;
        if (current.isOf(Items.ELYTRA) && isLowDurabilityElytra(current)) return false;
        return true;
    }

    private int screenHandlerSlotToInvIndex(int slotId) {
        if (slotId >= 27 && slotId <= 53) return (slotId - 27) + 9;
        if (slotId >= 54 && slotId <= 62) return slotId - 54;
        return -1;
    }

    private boolean performGenericRegearStep(ShulkerBoxScreenHandler handler) {
        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : armorSlots) {
            ItemStack current = mc.player.getEquippedStack(slot);
            boolean needsEquip = current.isEmpty();
            if (slot == EquipmentSlot.CHEST && !current.isEmpty() && current.isOf(Items.ELYTRA) && isLowDurabilityElytra(current)) {
                needsEquip = true;
            }
            if (needsEquip) {
                int armorIndex = switch (slot) {
                    case FEET -> 0; case LEGS -> 1; case CHEST -> 2; case HEAD -> 3;
                    default -> -1;
                };
                if (armorIndex == -1) continue;
                for (int j = 0; j < 27; j++) {
                    ItemStack shulkerStack = handler.getSlot(j).getStack();
                    if (shulkerStack.isEmpty()) continue;
                    var equippable = shulkerStack.get(DataComponentTypes.EQUIPPABLE);
                    if (equippable != null && equippable.slot() == slot) {
                        quickMove(j);
                        return true;
                    }
                    if (slot == EquipmentSlot.CHEST && shulkerStack.isOf(Items.ELYTRA) && !isLowDurabilityElytra(shulkerStack)) {
                        quickMove(j);
                        return true;
                    }
                }
            }
        }

        ItemStack offhand = mc.player.getOffHandStack();
        if (!offhand.isOf(Items.TOTEM_OF_UNDYING)) {
            for (int j = 0; j < 27; j++) {
                if (handler.getSlot(j).getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    quickMove(j);
                    return true;
                }
            }
        }
        return false;
    }

    private EquipmentSlot presetIndexToArmorSlot(int i) {
        return switch (i) {
            case 37 -> EquipmentSlot.FEET;
            case 38 -> EquipmentSlot.LEGS;
            case 39 -> EquipmentSlot.CHEST;
            case 40 -> EquipmentSlot.HEAD;
            default -> null;
        };
    }

    private static class InvItemState {
        int totalCount = 0;
        int partialSlotId = -1;
        int partialCount = 0;
        int maxCount = 64;
        int badElytraSlotId = -1;
    }

    private boolean performReplenishStep() {
        if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler)) return false;

        List<Item> whitelist = getReplenishWhitelist();
        if (whitelist.isEmpty()) return false;

        Map<Item, List<Integer>> shulkerSlots = new LinkedHashMap<>();
        for (int j = 0; j < 27; j++) {
            ItemStack stack = handler.getSlot(j).getStack();
            if (stack.isEmpty()) continue;
            if (whitelist.contains(stack.getItem())) {
                shulkerSlots.computeIfAbsent(stack.getItem(), k -> new ArrayList<>()).add(j);
            }
        }

        Map<Item, InvItemState> invState = new LinkedHashMap<>();
        boolean hasEmptyInvSlot = false;
        for (int i = 27; i < 63; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) {
                hasEmptyInvSlot = true;
                continue;
            }
            Item item = stack.getItem();
            InvItemState state = invState.computeIfAbsent(item, k -> new InvItemState());
            state.maxCount = stack.getMaxCount();

            if (isBadElytra(stack)) {
                state.badElytraSlotId = i;
                continue;
            }

            state.totalCount += stack.getCount();
            if (stack.getCount() < stack.getMaxCount() && state.partialSlotId == -1) {
                state.partialSlotId = i;
                state.partialCount = stack.getCount();
            }
        }

        if (whitelist.contains(Items.ELYTRA)) {
            if (handleElytraSwaps(handler, shulkerSlots.getOrDefault(Items.ELYTRA, List.of()))) return true;
        }

        for (Item item : whitelist) {
            if (item == Items.ELYTRA) continue;
            List<Integer> slots = shulkerSlots.get(item);
            if (slots == null) continue;
            InvItemState state = invState.get(item);
            if (state == null || state.partialSlotId == -1) continue;

            int pullLimit = getPullLimit(item);
            int alreadyPulled = pulledThisSession.getOrDefault(item, 0);
            if (alreadyPulled >= pullLimit) continue;

            int space = state.maxCount - state.partialCount;
            if (space <= 0) continue;

            for (int shulkerSlot : slots) {
                ItemStack shulkerStack = handler.getSlot(shulkerSlot).getStack();
                if (shulkerStack.isEmpty() || shulkerStack.getItem() != item) continue;

                int itemsMoved = Math.min(shulkerStack.getCount(), space);
                if (shulkerStack.getCount() <= space) {
                    quickMove(shulkerSlot);
                } else {
                    smartMove(shulkerSlot, state.partialSlotId);
                }
                pulledThisSession.merge(item, itemsMoved, Integer::sum);
                return true;
            }
        }

        for (Item item : whitelist) {
            if (item == Items.ELYTRA) continue;
            List<Integer> slots = shulkerSlots.get(item);
            if (slots == null) continue;

            int pullLimit = getPullLimit(item);
            int alreadyPulled = pulledThisSession.getOrDefault(item, 0);
            if (alreadyPulled >= pullLimit) continue;

            boolean hasRoom = hasEmptyInvSlot
                || (invState.get(item) != null && invState.get(item).partialSlotId != -1 && invState.get(item).partialCount < invState.get(item).maxCount);
            if (!hasRoom) continue;

            for (int shulkerSlot : slots) {
                ItemStack shulkerStack = handler.getSlot(shulkerSlot).getStack();
                if (shulkerStack.isEmpty() || shulkerStack.getItem() != item) continue;

                int itemsMoved = shulkerStack.getCount();
                quickMove(shulkerSlot);
                pulledThisSession.merge(item, itemsMoved, Integer::sum);
                return true;
            }
        }

        return false;
    }

    private boolean handleElytraSwaps(ShulkerBoxScreenHandler handler, List<Integer> elytraSlots) {
        int bestShulkerSlot = -1;
        int bestShulkerDurability = -1;
        for (int slot : elytraSlots) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (!stack.isOf(Items.ELYTRA)) continue;
            int remaining = stack.getMaxDamage() - stack.getDamage();
            if (remaining > bestShulkerDurability) {
                bestShulkerDurability = remaining;
                bestShulkerSlot = slot;
            }
        }

        if (bestShulkerSlot != -1) {
            int worstInvSlot = -1;
            int worstInvDurability = Integer.MAX_VALUE;
            for (int i = 27; i < 63; i++) {
                ItemStack invStack = handler.getSlot(i).getStack();
                if (!invStack.isOf(Items.ELYTRA)) continue;
                int remaining = invStack.getMaxDamage() - invStack.getDamage();
                if (remaining < worstInvDurability) {
                    worstInvDurability = remaining;
                    worstInvSlot = i;
                }
            }
            if (worstInvSlot != -1 && bestShulkerDurability > worstInvDurability) {
                smartMove(bestShulkerSlot, worstInvSlot);
                pulledThisSession.merge(Items.ELYTRA, 1, Integer::sum);
                return true;
            }
        }

        int elytraPullLimit = getPullLimit(Items.ELYTRA);
        int elytrasPulled = pulledThisSession.getOrDefault(Items.ELYTRA, 0);
        if (elytrasPulled < elytraPullLimit) {
            int goodSlot = -1;
            for (int slot : elytraSlots) {
                ItemStack stack = handler.getSlot(slot).getStack();
                if (stack.isOf(Items.ELYTRA) && !isLowDurabilityElytra(stack)) {
                    goodSlot = slot;
                    break;
                }
            }

            boolean hasAnyElytra = false;
            if (mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) hasAnyElytra = true;
            for (int i = 27; i < 63; i++) {
                if (handler.getSlot(i).getStack().isOf(Items.ELYTRA)) { hasAnyElytra = true; break; }
            }

            int pullSlot = goodSlot != -1 ? goodSlot : (!hasAnyElytra ? bestShulkerSlot : -1);

            if (pullSlot != -1) {
                for (int i = 27; i < 63; i++) {
                    if (handler.getSlot(i).getStack().isEmpty()) {
                        smartMove(pullSlot, i);
                        pulledThisSession.merge(Items.ELYTRA, 1, Integer::sum);
                        return true;
                    }
                }
            }
        }

        for (int i = 27; i < 63; i++) {
            ItemStack invStack = handler.getSlot(i).getStack();
            if (!invStack.isOf(Items.ELYTRA) || !isLowDurabilityElytra(invStack)) continue;
            for (int j = 0; j < 27; j++) {
                if (handler.getSlot(j).getStack().isEmpty()) {
                    smartMove(i, j);
                    return true;
                }
            }
            break;
        }

        return false;
    }

    private boolean isBadElytra(ItemStack stack) {
        return stack.isOf(Items.ELYTRA) && isLowDurabilityElytra(stack);
    }

    private boolean performInvSortStep() {
        List<ItemStack> preset = getPreset(invSortPreset);
        boolean[] satisfied = new boolean[36];
        boolean[] inventoryClaimed = new boolean[36];

        for (int i = 0; i < 36; i++) {
            ItemStack desired = preset.get(i);
            if (desired.isEmpty()) {
                satisfied[i] = mc.player.getInventory().getStack(i).isEmpty();
                continue;
            }
            if (!inventoryClaimed[i] && isSameItemType(mc.player.getInventory().getStack(i), desired)) {
                satisfied[i] = true;
                inventoryClaimed[i] = true;
            } else {
                for (int j = 0; j < 36; j++) {
                    if (!inventoryClaimed[j] && isSameItemType(mc.player.getInventory().getStack(j), desired)) {
                        inventoryClaimed[j] = true;
                        break;
                    }
                }
            }
        }

        for (int i = 0; i < 36; i++) {
            if (satisfied[i]) continue;
            ItemStack desired = preset.get(i);
            if (desired.isEmpty() || !mc.player.getInventory().getStack(i).isEmpty()) continue;
            for (int j = 0; j < 36; j++) {
                if (j == i || !isSameItemType(mc.player.getInventory().getStack(j), desired) || satisfied[j]) continue;
                InvUtils.move().from(j).to(i);
                return true;
            }
        }

        for (int i = 0; i < 36; i++) {
            if (satisfied[i] || mc.player.getInventory().getStack(i).isEmpty()) continue;
            for (int j = 0; j < 36; j++) {
                if (j == i || !mc.player.getInventory().getStack(j).isEmpty()) continue;
                InvUtils.move().from(i).to(j);
                return true;
            }
        }
        return false;
    }

    private boolean performSortStep() {
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) return false;
        int invSize = handler.getRows() * 9;
        List<ItemStack> current = new ArrayList<>();
        for (int i = 0; i < invSize; i++) current.add(handler.getSlot(i).getStack());
        List<ItemStack> sorted = new ArrayList<>(current);
        sorted.sort(new ShulkerColorComparator());
        for (int i = 0; i < invSize; i++) {
            if (!ItemStack.areEqual(current.get(i), sorted.get(i))) {
                for (int j = i + 1; j < invSize; j++) {
                    if (ItemStack.areEqual(current.get(j), sorted.get(i))) {
                        smartMove(j, i);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean performTrashStep() {
        if (mc.player.currentScreenHandler == null) return false;
        ScreenHandler handler = mc.player.currentScreenHandler;
        for (Slot slot : handler.slots) {
            if (slot.inventory == mc.player.getInventory()) {
                int invIndex = slot.getIndex();
                if (invIndex >= 0 && invIndex < 36) {
                    ItemStack stack = slot.getStack();
                    if (!stack.isEmpty() && trashItems.get().contains(stack.getItem())) {
                        mc.interactionManager.clickSlot(handler.syncId, slot.id, 1, SlotActionType.THROW, mc.player);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasTrashInPlayerSlots() {
        if (mc.player == null || trashItems.get().isEmpty()) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && trashItems.get().contains(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    private void smartMove(int from, int to) {
        if (mc.interactionManager == null || mc.player == null) return;
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (!handler.getCursorStack().isEmpty()) return;
        int syncId = handler.syncId;

        ItemStack targetStack = handler.getSlot(to).getStack();
        if (targetStack.isEmpty()) {
            mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, to,   0, SlotActionType.PICKUP, mc.player);
        } else {
            mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, to,   0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private void quickMove(int slot) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            slot, 0, SlotActionType.QUICK_MOVE, mc.player
        );
    }

    private void tickAutoTrash() {
        if (!autoTrash.get() || trashItems.get().isEmpty()) {
            isTrashing = false;
            return;
        }

        if (!(mc.currentScreen instanceof HandledScreen<?>)) {
            isTrashing = false;
            return;
        }

        if (isSorting || isReplenishing || isRegearing || isInvSorting) return;

        if (!isTrashing && hasTrashInPlayerSlots()) {
            isTrashing = true;
            trashTimer = 0;
        }

        if (isTrashing) {
            if (trashTimer > 0) {
                trashTimer--;
            } else if (performTrashStep()) {
                trashTimer = trashDelay.get();
            } else {
                isTrashing = false;
            }
        }
    }

    private void tickAutoDrop() {
        if (!autoTrash.get() || trashItems.get().isEmpty()) return;

        boolean canDropInCurrentScreen = (mc.currentScreen == null) || (mc.currentScreen instanceof ChatScreen);
        if (!canDropInCurrentScreen) return;

        if (trashTimer > 0) {
            trashTimer--;
        } else {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && trashItems.get().contains(stack.getItem())) {
                    InvUtils.drop().slot(i);
                    trashTimer = trashDelay.get();
                    break;
                }
            }
        }
    }

    private boolean isBusy() {
        return isSorting || isTrashing || isReplenishing || isRegearing || isInvSorting;
    }

    private void saveInventory(int index) {
        NbtCompound nbt  = new NbtCompound();
        NbtList     list = new NbtList();
        for (int i = 0; i < 36; i++) encodeSlot(list, mc.player.getInventory().getStack(i), i, mc);
        encodeSlot(list, mc.player.getOffHandStack(), 36, mc);
        encodeSlot(list, mc.player.getEquippedStack(EquipmentSlot.FEET),  37, mc);
        encodeSlot(list, mc.player.getEquippedStack(EquipmentSlot.LEGS),  38, mc);
        encodeSlot(list, mc.player.getEquippedStack(EquipmentSlot.CHEST), 39, mc);
        encodeSlot(list, mc.player.getEquippedStack(EquipmentSlot.HEAD), 40, mc);
        nbt.put("Items", list);
        if (index == 1) preset1Data.set(nbt.toString());
        else            preset2Data.set(nbt.toString());
    }

    private void encodeSlot(NbtList list, ItemStack stack, int slot, net.minecraft.client.MinecraftClient mc) {
        if (stack.isEmpty()) return;
        NbtCompound itemTag = new NbtCompound();
        itemTag.putInt("Slot", slot);
        NbtElement encodedItem = ItemStack.CODEC
            .encodeStart(RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager()), stack)
            .getOrThrow();
        itemTag.put("item", encodedItem);
        list.add(itemTag);
    }

    private static final int PRESET_SIZE = 41;

    private List<ItemStack> getPreset(int index) {
        String nbtString = (index == 1) ? preset1Data.get() : preset2Data.get();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < PRESET_SIZE; i++) items.add(ItemStack.EMPTY);
        if (nbtString == null || nbtString.isEmpty()) return items;
        try {
            NbtCompound nbt = parseNbtCompoundCompat(nbtString);
            if (nbt != null && nbt.contains("Items")) {
                NbtList list = getNbtListCompat(nbt, "Items");
                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        NbtCompound itemTag = getCompoundAtCompat(list, i);
                        if (itemTag == null) continue;
                        int slot = getIntCompat(itemTag, "Slot");
                        NbtElement itemNbt = itemTag.get("item");
                        if (slot >= 0 && slot < PRESET_SIZE && itemNbt != null) {
                            ItemStack.CODEC
                                .parse(RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager()), itemNbt)
                                .result()
                                .ifPresent(s -> items.set(slot, s));
                        }
                    }
                }
            }
        } catch (Exception e) {
            error("Failed to parse inventory preset: " + e.getMessage());
        }
        return items;
    }

    private Slot getSlotAt(HandledScreen<?> screen, double mouseX, double mouseY) {
        double scaledMouseX = mouseX * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
        double scaledMouseY = mouseY * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();
        int[] pos = getGuiPos(screen);
        if (pos == null) return null;
        for (Slot slot : screen.getScreenHandler().slots) {
            int x = pos[0] + slot.x, y = pos[1] + slot.y;
            if (scaledMouseX >= x && scaledMouseX < x + 16 && scaledMouseY >= y && scaledMouseY < y + 16) return slot;
        }
        return null;
    }

    private int[] getGuiPos(HandledScreen<?> screen) {
        try {
            Field fX = HandledScreen.class.getDeclaredField("x"); fX.setAccessible(true);
            Field fY = HandledScreen.class.getDeclaredField("y"); fY.setAccessible(true);
            return new int[]{ fX.getInt(screen), fY.getInt(screen) };
        } catch (Exception ignored) {}
        try {
            Field fX = HandledScreen.class.getDeclaredField("field_2776"); fX.setAccessible(true);
            Field fY = HandledScreen.class.getDeclaredField("field_2777"); fY.setAccessible(true);
            return new int[]{ fX.getInt(screen), fY.getInt(screen) };
        } catch (Exception ignored) {}
        try {
            Field fW = HandledScreen.class.getDeclaredField("backgroundWidth");  fW.setAccessible(true);
            Field fH = HandledScreen.class.getDeclaredField("backgroundHeight"); fH.setAccessible(true);
            int bgW = fW.getInt(screen);
            int bgH = fH.getInt(screen);
            return new int[]{ (screen.width - bgW) / 2, (screen.height - bgH) / 2 };
        } catch (Exception ignored) {}
        return new int[]{ (screen.width - 176) / 2, (screen.height - 166) / 2 };
    }

    private Slot getFocusedSlot(HandledScreen<?> screen) {
        try {
            Field f = HandledScreen.class.getDeclaredField("focusedSlot");
            f.setAccessible(true);
            return (Slot) f.get(screen);
        } catch (Exception e) {
            try {
                Field f = HandledScreen.class.getDeclaredField("field_2787");
                f.setAccessible(true);
                return (Slot) f.get(screen);
            } catch (Exception e2) {
                return getSlotUnderMouse(screen);
            }
        }
    }

    private Slot getSlotUnderMouse(HandledScreen<?> screen) {
        return getSlotAt(screen, mc.mouse.getX(), mc.mouse.getY());
    }

    private int mapInventoryToSlotId(int invIndex) {
        if (invIndex >= 0 && invIndex < 9)  return 54 + invIndex;
        if (invIndex >= 9 && invIndex < 36) return 27 + (invIndex - 9);
        return -1;
    }

    private int findBestTool(BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) { bestSpeed = speed; bestSlot = i; }
        }
        return bestSlot;
    }

    private boolean isSameItemType(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.getItem() == b.getItem();
    }

    private boolean isLowDurabilityElytra(ItemStack stack) {
        return stack.isOf(Items.ELYTRA) && (stack.getMaxDamage() - stack.getDamage() < elytraThreshold.get());
    }

    private static class ShulkerColorComparator implements Comparator<ItemStack> {
        @Override
        public int compare(ItemStack o1, ItemStack o2) {
            boolean s1 = isShulker(o1), s2 = isShulker(o2);
            if (s1 && !s2) return -1;
            if (!s1 && s2) return 1;
            if (!s1)       return 0;
            return Integer.compare(getColorId(o1), getColorId(o2));
        }
        private boolean isShulker(ItemStack stack) {
            return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
        }
        private int getColorId(ItemStack stack) {
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock sb) {
                DyeColor c = sb.getColor();
                return c == null ? 16 : c.ordinal();
            }
            return 17;
        }
    }
}