package com.example.addon.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import com.example.addon.Tim;
import com.example.addon.utils.Hotbar;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.process.IBaritoneProcess;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BlockSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class Baromine extends Module {
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgSafety = settings.createGroup("Safety & Limits");
    private final SettingGroup sgDeposit = settings.createGroup("Auto Deposit");
    private final SettingGroup sgMend = settings.createGroup("Auto Mend");
    private final SettingGroup sgSession = settings.createGroup("Session");
    private final SettingGroup sgNotifications = settings.createGroup("Notifications");

    // --- MULTI-VERSION REGISTRY HELPERS (1.21.4 - 1.21.11) ---
    private static Block optBlock(String path) {
        Identifier id = Identifier.withDefaultNamespace(path);
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id) : null;
    }

    private static Item optItem(String path) {
        Identifier id = Identifier.withDefaultNamespace(path);
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
    }

    // --- TARGET ---
    public enum TargetMode {
        Ores,
        Blocks
    }

    private final Setting<TargetMode> targetMode = sgTarget.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("What type of block to mine.")
        .defaultValue(TargetMode.Ores)
        .onChanged(mode -> updateBaritoneGoal())
        .build()
    );

    private final Setting<Block> targetOre = sgTarget.add(new BlockSetting.Builder()
        .name("target-ore")
        .description("The specific ore to mine.")
        .visible(() -> targetMode.get() == TargetMode.Ores)
        .defaultValue(Blocks.DIAMOND_ORE)
        .filter(this::isOre)
        .onChanged(block -> updateBaritoneGoal())
        .build()
    );

    private final Setting<Block> targetBlock = sgTarget.add(new BlockSetting.Builder()
        .name("target-block")
        .description("The specific block to mine.")
        .visible(() -> targetMode.get() == TargetMode.Blocks)
        .defaultValue(Blocks.STONE)
        .filter(block -> !isOre(block) && block != Blocks.AIR)
        .onChanged(block -> updateBaritoneGoal())
        .build()
    );

    private final Setting<Boolean> includeDeepslate = sgTarget.add(new BoolSetting.Builder()
        .name("include-deepslate")
        .description("Also mines the deepslate variant of the selected ore.")
        .visible(() -> targetMode.get() == TargetMode.Ores)
        .defaultValue(true)
        .onChanged(b -> updateBaritoneGoal())
        .build()
    );

    public final Setting<Integer> targetStacks = sgTarget.add(new IntSetting.Builder()
        .name("target-stacks")
        .description("Total amount of stacks to mine before stopping the module entirely.")
        .defaultValue(1)
        .sliderRange(1, 64)
        .build()
    );

    public enum CraftMode {
        Disabled,
        Ores,
        Blocks
    }

    private final Setting<CraftMode> autoCraft = sgTarget.add(new EnumSetting.Builder<CraftMode>()
        .name("auto-craft")
        .description("Automatically crafts items using a crafting table.")
        .defaultValue(CraftMode.Disabled)
        .build()
    );

    private final Setting<List<Block>> craftOreList = sgTarget.add(new BlockListSetting.Builder()
        .name("craft-ore-list")
        .description("Ore blocks to craft from raw materials (e.g. Iron Block, Diamond Block).")
        .visible(() -> autoCraft.get() == CraftMode.Ores)
        .filter(block -> getOreRecipe(block) != null)
        .build()
    );

    private final Setting<List<Block>> craftBlockList = sgTarget.add(new BlockListSetting.Builder()
        .name("craft-block-list")
        .description("Blocks to craft from mined materials (e.g. Stone Bricks, Slabs, Stairs).")
        .visible(() -> autoCraft.get() == CraftMode.Blocks)
        .filter(block -> getBlockRecipe(block) != null)
        .build()
    );

    // --- SAFETY & LIMITS ---
    private final Setting<Double> warningHealth = sgSafety.add(new DoubleSetting.Builder()
        .name("warning-health")
        .description("Health level to trigger a warning sound and chat message.")
        .defaultValue(12.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Double> criticalHealth = sgSafety.add(new DoubleSetting.Builder()
        .name("critical-health")
        .description("Health level to trigger an instant disconnect from the server.")
        .defaultValue(6.0)
        .sliderRange(1.0, 20.0)
        .build()
    );

    private final Setting<Boolean> disconnectOnPlayer = sgSafety.add(new BoolSetting.Builder()
        .name("disconnect-on-player")
        .description("Instantly disconnects if another player enters render distance.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseInCombat = sgSafety.add(new BoolSetting.Builder()
        .name("pause-in-combat")
        .description("Briefly pauses for a visible attacking mob within melee range, then resumes if combat stalls.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> goldenHelmet = sgSafety.add(new BoolSetting.Builder()
        .name("golden-helmet")
        .description("Stops mining if you are not wearing a Golden Helmet (prevents Piglin aggression).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> lavaAvoidance = sgSafety.add(new BoolSetting.Builder()
        .name("lava-avoidance")
        .description("Activates safety protocols only if lava is directly touching you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> waterAvoidance = sgSafety.add(new BoolSetting.Builder()
        .name("water-avoidance")
        .description("Activates safety protocols if water is directly touching you.")
        .defaultValue(false)
        .build()
    );

    public enum ExcessDropMode {
        Disabled,
        Cobblestone,
        Netherrack,
        CobbledDeepslate
    }

    private final Setting<ExcessDropMode> dropExcessMode = sgSafety.add(new EnumSetting.Builder<ExcessDropMode>()
        .name("drop-excess")
        .description("Automatically drops excess blocks, keeping only a single stack of 64.")
        .defaultValue(ExcessDropMode.Disabled)
        .build()
    );

    private final Setting<Boolean> avoidDeepDark = sgSafety.add(new BoolSetting.Builder()
        .name("avoid-deep-dark")
        .description("Retreats if you enter the Deep Dark biome.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> safeLogout = sgSafety.add(new BoolSetting.Builder()
        .name("safe-logout")
        .description("Hides inside a wall before turning off the module when the target is reached.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minYLevel = sgSafety.add(new IntSetting.Builder()
        .name("min-y-level")
        .description("Stops mining if the player goes below this Y-level.")
        .defaultValue(-64)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Integer> maxYLevel = sgSafety.add(new IntSetting.Builder()
        .name("max-y-level")
        .description("Stops mining if the player goes above this Y-level.")
        .defaultValue(320)
        .sliderRange(-64, 320)
        .build()
    );

    private final Setting<Boolean> radiusLimit = sgSafety.add(new BoolSetting.Builder()
        .name("radius-limit")
        .description("Stops mining if the player wanders too far from the starting position.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> radiusBlocks = sgSafety.add(new IntSetting.Builder()
        .name("radius-blocks")
        .description("The maximum block radius from the starting position.")
        .visible(radiusLimit::get)
        .defaultValue(500)
        .sliderRange(50, 5000)
        .build()
    );

    // --- AUTO DEPOSIT ---
    public enum DepositMode {
        Disabled,
        Inventory,
        EnderChest
    }

    public enum ToolEnchant {
        SilkTouch,
        Fortune
    }

    private final Setting<DepositMode> depositMode = sgDeposit.add(new EnumSetting.Builder<DepositMode>()
        .name("deposit-mode")
        .description("Where to stash items. Inventory uses Shulkers directly, EnderChest uses an Ender Chest.")
        .defaultValue(DepositMode.EnderChest)
        .build()
    );

    private final Setting<Integer> depositStacks = sgDeposit.add(new IntSetting.Builder()
        .name("deposit-stacks")
        .description("How many stacks of items in your inventory trigger the deposit process.")
        .visible(() -> depositMode.get() != DepositMode.Disabled)
        .defaultValue(1)
        .sliderRange(1, 16)
        .build()
    );

    private final Setting<Integer> swapSlot = sgDeposit.add(new IntSetting.Builder()
        .name("swap-slot")
        .description("The hotbar slot used to swap Shulker Boxes and Ender Chests into.")
        .defaultValue(1)
        .sliderRange(0, 8)
        .visible(() -> depositMode.get() != DepositMode.Disabled)
        .build()
    );

    private final Setting<ToolEnchant> toolEnchant = sgDeposit.add(new EnumSetting.Builder<ToolEnchant>()
        .name("tool-enchant")
        .description("Which enchantment to require when breaking blocks and replacing tools.")
        .visible(() -> depositMode.get() != DepositMode.Disabled)
        .defaultValue(ToolEnchant.SilkTouch)
        .build()
    );

    private final Setting<Item> foodItem = sgDeposit.add(new ItemSetting.Builder()
        .name("food-item")
        .description("Food item to automatically pull from the Ender Chest when low. Raw foods are excluded.")
        .visible(() -> depositMode.get() == DepositMode.EnderChest)
        .filter(item -> {
            if (!new ItemStack(item).has(DataComponents.FOOD)) return false;
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            return !id.contains("raw")
                && !id.contains("rotten")
                && !id.equals("cod")
                && !id.equals("salmon")
                && !id.equals("tropical_fish")
                && !id.equals("pufferfish")
                && !id.equals("potato")
                && !id.equals("poisonous_potato")
                && !id.equals("spider_eye");
        })
        .defaultValue(Items.ENCHANTED_GOLDEN_APPLE)
        .build()
    );

    private final Setting<Integer> minFoodCount = sgDeposit.add(new IntSetting.Builder()
        .name("min-food-count")
        .description("Minimum amount of selected food to keep in your inventory. Triggers regear if below.")
        .visible(() -> depositMode.get() == DepositMode.EnderChest && foodItem.get() != Items.AIR)
        .defaultValue(10)
        .sliderRange(1, 64)
        .build()
    );

    private final Setting<Keybind> highlightContainerKey = sgDeposit.add(new KeybindSetting.Builder()
        .name("highlight-craft-container")
        .description("Look at a Shulker Box or Chest and press this to set it as the material source for crafting.")
        .visible(() -> autoCraft.get() == CraftMode.Ores || autoCraft.get() == CraftMode.Blocks)
        .defaultValue(Keybind.none())
        .build()
    );

    // --- AUTO MEND ---
    private final Setting<Boolean> autoMend = sgMend.add(new BoolSetting.Builder()
        .name("auto-mend")
        .description("Mines XP ores to repair tools. Pauses mining, repairs, and resumes automatically.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Block> mendOreBlock = sgMend.add(new BlockSetting.Builder()
        .name("mend-ore-block")
        .description("The ore to mine for XP when auto-mending (e.g., Nether Quartz, Coal).")
        .visible(autoMend::get)
        .defaultValue(Blocks.NETHER_QUARTZ_ORE)
        .filter(this::isOre)
        .build()
    );

    private final Setting<Double> maxMendDurability = sgMend.add(new DoubleSetting.Builder()
        .name("max-mend-durability")
        .description("The durability percentage to reach before stopping Auto-Mend and resuming mining.")
        .visible(autoMend::get)
        .defaultValue(70.0)
        .sliderRange(10.0, 100.0)
        .build()
    );

    private final Setting<Double> minToolDurability = sgMend.add(new DoubleSetting.Builder()
        .name("min-tool-durability")
        .description("The durability percentage to trigger replacing tools or activating Auto-Mend.")
        .visible(() -> depositMode.get() != DepositMode.Disabled || autoMend.get())
        .defaultValue(10.0)
        .sliderRange(1.0, 50.0)
        .build()
    );

    // --- SESSION ---
    private final Setting<Boolean> antiAfk = sgSession.add(new BoolSetting.Builder()
        .name("anti-afk")
        .description("Periodically jumps and swings hand to prevent being kicked for AFK when stuck.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableMaxRuntime = sgSession.add(new BoolSetting.Builder()
        .name("enable-max-runtime")
        .description("Enables a maximum time limit before the module automatically turns off.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> maxRuntimeHours = sgSession.add(new DoubleSetting.Builder()
        .name("max-runtime-hours")
        .description("Maximum hours to run before automatically turning off.")
        .visible(enableMaxRuntime::get)
        .defaultValue(8.0)
        .sliderRange(0.5, 24.0)
        .build()
    );

    private final Setting<Boolean> autoReconnect = sgSession.add(new BoolSetting.Builder()
        .name("auto-reconnect")
        .description("Keeps the module enabled and reconnects after X hours if disconnected by safety or timeout.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> reconnectHours = sgSession.add(new IntSetting.Builder()
        .name("delay-hours")
        .description("How many hours to wait before attempting to reconnect.")
        .visible(autoReconnect::get)
        .defaultValue(2)
        .sliderRange(1, 24)
        .build()
    );

    // --- NOTIFICATIONS ---
    public enum PingMode {
        Chat,
        Sound,
        Both
    }

    public enum WarningSound {
        Pling,
        Bass,
        Harp,
        Bell,
        Anvil,
        LevelUp,
        OrbPickup,
        Beacon,
        GhastWarn,
        DragonGrowl,
        WitherSpawn,
        ChallengeComplete
    }

    private final Setting<PingMode> pingMode = sgNotifications.add(new EnumSetting.Builder<PingMode>()
        .name("ping-mode")
        .description("How you want to be notified of module events.")
        .defaultValue(PingMode.Both)
        .build()
    );

    private final Setting<WarningSound> warningSound = sgNotifications.add(new EnumSetting.Builder<WarningSound>()
        .name("warning-sound")
        .description("Which sound to play for notifications.")
        .visible(() -> pingMode.get() == PingMode.Sound || pingMode.get() == PingMode.Both)
        .defaultValue(WarningSound.Pling)
        .build()
    );

    private final Setting<Double> soundVolume = sgNotifications.add(new DoubleSetting.Builder()
        .name("volume")
        .description("The volume of the warning sound.")
        .visible(() -> pingMode.get() == PingMode.Sound || pingMode.get() == PingMode.Both)
        .defaultValue(1.0)
        .sliderRange(0.0, 1.0)
        .build()
    );

    private static final Predicate<ItemStack> SHULKER_PREDICATE = stack ->
        stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;

    // --- VARIABLES ---
    private boolean wasPausedForCombat = false;
    private int combatPauseTicks = 0;
    private int combatCooldownTicks = 0;
    private int navigationRetryTicks = 0;
    private boolean hasHealthWarned = false;
    private boolean playerLogoutPending = false;
    private boolean isFinalDeposit = false;
    private boolean autoMendFailed = false;

    // Auto-Mend Variables
    private boolean isAutoMending = false;
    private Block savedTargetBlock;
    private int mendToolSwapDelay = 0;

    private ServerData lastServer = null;
    private boolean isWaitingToReconnect = false;
    private long reconnectTime = 0;

    private long startTime = 0;
    private double startX = 0;
    private double startZ = 0;
    private int antiAfkTickCounter = 0;
    private int jumpTicks = 0;

    private boolean shulkerRecoveryAttempted = false;
    private int pickupTimeout = 0;
    private Item placedShulkerItem;
    private int shulkerCountBeforePickup;
    private ItemStack recoveredShulker = ItemStack.EMPTY;
    private final List<ItemStack> inventoryBeforeShulkerPickup = new ArrayList<>();
    private boolean shulkerPickupSnapshotTaken;
    private int transferSlot = -1;
    private int transferCount;
    private boolean depositTransferFailed;

    private boolean isHandlingLava = false;
    private int lavaSafetyDelay = 0;
    private int lavaSafetyCounter = 0;
    private int lavaMoveTicks = 0;

    private boolean isHandlingWater = false;
    private int waterSafetyDelay = 0;
    private int waterSafetyCounter = 0;
    private int waterMoveTicks = 0;

    private boolean wasPausedForPortalMaker = false;

    // Safe Logout Variables
    private enum HideoutState {
        IDLE,
        DIGGING,
        ENTERING,
        SEALING,
        DONE
    }
    private HideoutState hideoutState = HideoutState.IDLE;
    private int hideoutDelay = 0;
    private Direction hideoutDir = null;
    private BlockPos hideoutPos = null;

    // Deep Dark Retreat Variables
    private enum DeepDarkState {
        IDLE,
        RETREATING,
        ASCENDING,
        RUNNING_AWAY
    }
    private DeepDarkState deepDarkState = DeepDarkState.IDLE;
    private final Deque<BlockPos> safePosQueue = new ArrayDeque<>();
    private static final int MAX_QUEUE_SIZE = 10;
    private int safePosTimer = 0;
    private int retreatDelay = 0;
    private static final Random RANDOM = new Random();

    // Craft Variables
    private enum CraftState {
        IDLE,
        GATHERING_WOOD,
        FINDING_TABLE,
        PLACING_TABLE,
        OPENING_TABLE,
        PULLING_MATERIALS,
        CRAFTING,
        CLOSING_TABLE,
        BREAKING_TABLE,
        PICKING_UP_TABLE,
        RESUMING
    }
    private CraftState craftState = CraftState.IDLE;
    private int craftDelay = 0;
    private int craftStep = 0;
    private BlockPos craftTablePos = null;
    private BlockPos craftContainerPos1 = null;
    private BlockPos craftContainerPos2 = null;

    private enum DepositState {
        IDLE,
        PAUSING_BARITONE,
        CLEARING_SPACE,
        PLACING_ECHEST,
        OPENING_ECHEST,
        EXTRACTING_SHULKER,
        CLOSING_ECHEST,
        PLACING_SHULKER,
        OPENING_SHULKER,
        TRANSFERRING_ITEMS,
        CLOSING_SHULKER,
        BREAKING_SHULKER,
        PICKING_UP_SHULKER,
        REOPENING_ECHEST,
        DEPOSITING_SHULKER,
        REPLACING_TOOLS,
        REGEAR_FOOD,
        CLOSING_ECHEST_AGAIN,
        BREAKING_ECHEST,
        PICKING_UP_ECHEST,
        MINING_SURROUNDINGS_SHULKER,
        RESUMING
    }

    private DepositState depositState = DepositState.IDLE;
    private int depositDelay = 0;
    private BlockPos echestPos = null;
    private BlockPos shulkerPos = null;
    private boolean spaceClearingStarted = false;
    private int spaceClearAttempts = 0;
    private int spaceClearMinWait = 0;

    public Baromine() {
        super(Tim.CATEGORY, "baromine", "Automated Baritone miner for targeted ores or blocks with heavy safety protocols.");
    }

    @Override
    public void onActivate() {
        wasPausedForCombat = false;
        combatPauseTicks = 0;
        combatCooldownTicks = 0;
        navigationRetryTicks = 0;
        depositTransferFailed = false;
        hasHealthWarned = false;
        playerLogoutPending = false;
        isFinalDeposit = false;
        isAutoMending = false;
        autoMendFailed = false;
        hideoutState = HideoutState.IDLE;
        deepDarkState = DeepDarkState.IDLE;
        depositState = DepositState.IDLE;
        craftState = CraftState.IDLE;

        isWaitingToReconnect = false;
        lastServer = mc.getCurrentServer();

        startTime = System.currentTimeMillis();
        antiAfkTickCounter = 0;

        safePosQueue.clear();
        wasPausedForPortalMaker = false;

        if (mc.player != null) {
            startX = mc.player.getX();
            startZ = mc.player.getZ();
        }

        updateBaritoneGoal();

        int targetItems = targetStacks.get() * 64;
        sendPing("Baromine activated. Targeting " + getTargetBlock().getName().getString() + " x" + targetItems + " (" + targetStacks.get() + " stacks).");
    }

    @Override
    public void onDeactivate() {
        isWaitingToReconnect = false;
        antiAfkTickCounter = 0;
        jumpTicks = 0;
        isHandlingLava = false;
        lavaSafetyDelay = 0;
        lavaMoveTicks = 0;
        isHandlingWater = false;
        waterSafetyDelay = 0;
        waterMoveTicks = 0;
        playerLogoutPending = false;
        isFinalDeposit = false;
        isAutoMending = false;
        hideoutState = HideoutState.IDLE;
        deepDarkState = DeepDarkState.IDLE;
        craftState = CraftState.IDLE;
        safePosQueue.clear();
        wasPausedForPortalMaker = false;
        if (mc.options != null) {
            mc.options.keyJump.setDown(false);
            mc.options.keyUp.setDown(false);
        }
        if (!Modules.get().isActive(PortalMaker.class)) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
        }
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("set okIfWater false");
        sendPing("Baromine deactivated.");
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (autoReconnect.get() && lastServer != null) {
            isWaitingToReconnect = true;
            reconnectTime = System.currentTimeMillis() + (reconnectHours.get() * 3600000L);
            ChatUtils.sendMsg("Baromine", Component.literal("Disconnected. Waiting " + reconnectHours.get() + " hours before attempting to reconnect."));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (enableMaxRuntime.get() && System.currentTimeMillis() - startTime >= maxRuntimeHours.get() * 3600000L) {
            sendPing("Maximum runtime of " + maxRuntimeHours.get() + " hours reached. Stopping module.");
            toggle();
            return;
        }

        if (isWaitingToReconnect) {
            if (System.currentTimeMillis() >= reconnectTime) {
                isWaitingToReconnect = false;
                ChatUtils.sendMsg("Baromine", Component.literal("Reconnect delay finished. Attempting to reconnect..."));
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(lastServer.ip), lastServer, false, null);
            }
            return;
        }

        if (mc.player == null || mc.level == null) return;

        if (Modules.get().isActive(PortalMaker.class)) {
            if (!wasPausedForPortalMaker) {
                wasPausedForPortalMaker = true;
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
            }
            return;
        }

        if (wasPausedForPortalMaker) {
            wasPausedForPortalMaker = false;
            if (depositState == DepositState.IDLE && craftState == CraftState.IDLE && hideoutState == HideoutState.IDLE && deepDarkState == DeepDarkState.IDLE && !isAutoMending) {
                sendPing("PortalMaker disabled. Resuming Baromine operations.");
                updateBaritoneGoal();
            }
        }

        if (highlightContainerKey.get().isPressed() && craftState == CraftState.IDLE && depositState == DepositState.IDLE) {
            if (mc.hitResult instanceof BlockHitResult hit) {
                BlockState state = mc.level.getBlockState(hit.getBlockPos());
                Block hitBlock = state.getBlock();
                if (hitBlock instanceof ShulkerBoxBlock) {
                    craftContainerPos1 = hit.getBlockPos();
                    craftContainerPos2 = null;
                    sendPing("Highlighted Shulker Box for material pulling.");
                } else if (hitBlock instanceof ChestBlock) {
                    craftContainerPos1 = hit.getBlockPos();
                    craftContainerPos2 = null;
                    if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                        craftContainerPos2 = hit.getBlockPos().relative(ChestBlock.getConnectedDirection(state));
                    }
                    sendPing("Highlighted Chest for material pulling.");
                }
            }
        }

        if (hideoutState != HideoutState.IDLE) {
            handleHideoutState();
            return;
        }

        if (deepDarkState != DeepDarkState.IDLE) {
            handleDeepDarkRetreat();
            return;
        }

        float health = mc.player.getHealth();
        if (health <= criticalHealth.get()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
            disconnectSafely("CRITICAL HEALTH");
            return;
        }

        if (isAutoMending) {
            handleAutoMend();
            return;
        }

        if (autoMend.get() && !autoMendFailed && depositState == DepositState.IDLE && craftState == CraftState.IDLE && hasToolsBelowDurability(minToolDurability.get())) {
            startAutoMend();
            return;
        }

        if (disconnectOnPlayer.get()) {
            for (Player player : mc.level.players()) {
                if (player != mc.player) {
                    playerLogoutPending = true;
                    break;
                }
            }
        }

        if (playerLogoutPending) {
            if (depositState == DepositState.IDLE && craftState == CraftState.IDLE) {
                disconnectSafely("Player detected in render distance!");
                playerLogoutPending = false;
                return;
            }
        }

        if (avoidDeepDark.get() && depositState == DepositState.IDLE && craftState == CraftState.IDLE) {
            safePosTimer++;
            if (safePosTimer >= 40) {
                safePosTimer = 0;
                Optional<ResourceKey<Biome>> biomeKey = mc.level.getBiome(mc.player.blockPosition()).unwrapKey();
                boolean inDeepDark = biomeKey.isPresent() && biomeKey.get().equals(Biomes.DEEP_DARK);

                if (inDeepDark) {
                    triggerDeepDarkRetreat();
                    return;
                } else {
                    safePosQueue.addLast(mc.player.blockPosition());
                    while (safePosQueue.size() > MAX_QUEUE_SIZE) {
                        safePosQueue.removeFirst();
                    }
                }
            }
        }

        if (craftState != CraftState.IDLE) {
            handleCraftState();
            return;
        }

        if (autoCraft.get() == CraftMode.Ores && depositState == DepositState.IDLE && hasCraftableOre()) {
            craftState = CraftState.FINDING_TABLE;
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
            return;
        }

        if (autoCraft.get() == CraftMode.Blocks && depositState == DepositState.IDLE && hasCraftableBlock()) {
            craftState = CraftState.FINDING_TABLE;
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
            return;
        }

        if (depositState != DepositState.IDLE) {
            handleDepositState();
            return;
        }

        if (depositMode.get() == DepositMode.Disabled && mc.player.getInventory().getFreeSlot() == -1) {
            sendPing("Inventory is full and Auto Deposit is disabled. Stopping module to prevent wasted mining.");
            stopBaritoneSafely("Inventory full");
            toggle();
            return;
        }

        int requiredItems = targetStacks.get() * 64;
        if (getTotalAvailableTargetItems() >= requiredItems) {
            if (depositMode.get() != DepositMode.Disabled && getCurrentTargetCount() > 0) {
                isFinalDeposit = true;
                sendPing("Target stacks reached! Initiating final deposit...");
                depositState = DepositState.PAUSING_BARITONE;
                depositDelay = 5;
                return;
            } else {
                sendPing("Target stacks reached! Total items: " + getTotalAvailableTargetItems());
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
                if (safeLogout.get() && isInNetherOrOverworld()) {
                    startHideout();
                } else {
                    toggle();
                }
                return;
            }
        }

        if (depositMode.get() != DepositMode.Disabled) {
            int requiredDepositItems = depositStacks.get() * 64;
            if (getCurrentTargetCount() >= requiredDepositItems) {
                depositState = DepositState.PAUSING_BARITONE;
                depositDelay = 5;
                return;
            }
        }

        int playerY = mc.player.blockPosition().getY();
        if (playerY < minYLevel.get()) {
            stopBaritoneSafely("Went below minimum Y-level (" + minYLevel.get() + ")!");
            toggle();
            return;
        }
        if (playerY > maxYLevel.get()) {
            stopBaritoneSafely("Went above maximum Y-level (" + maxYLevel.get() + ")!");
            toggle();
            return;
        }

        if (radiusLimit.get()) {
            double dist = Math.sqrt(Math.pow(mc.player.getX() - startX, 2) + Math.pow(mc.player.getZ() - startZ, 2));
            if (dist > radiusBlocks.get()) {
                stopBaritoneSafely("Went outside allowed radius limit (" + radiusBlocks.get() + " blocks)!");
                toggle();
                return;
            }
        }

        if (health <= warningHealth.get()) {
            if (!hasHealthWarned) {
                sendPing("Warning: Health is low!");
                hasHealthWarned = true;
            }
        } else {
            hasHealthWarned = false;
        }

        if (lavaAvoidance.get()) {
            if (isHandlingLava) {
                if (lavaMoveTicks > 0) {
                    lavaMoveTicks--;
                    mc.options.keyUp.setDown(true);
                    return;
                }
                mc.options.keyUp.setDown(false);

                if (lavaSafetyDelay > 0) {
                    lavaSafetyDelay--;
                    return;
                }

                if (isImmediateLavaDanger()) {
                    placeSafetyBlock(false);
                    lavaSafetyDelay = 5;
                    lavaSafetyCounter++;

                    if (lavaSafetyCounter > 40) {
                        disconnectSafely("Lava safety failed!");
                        return;
                    }
                    return;
                } else {
                    isHandlingLava = false;
                    mc.options.keyJump.setDown(false);
                    mc.options.keyUp.setDown(false);
                    sendPing("Lava avoided. Resuming Baromine.");
                    updateBaritoneGoal();
                }
                return;
            } else if (isImmediateLavaDanger()) {
                isHandlingLava = true;
                lavaSafetyCounter = 0;
                stopBaritoneSafely("Lava detected! Activating safety protocols.");
                lavaSafetyDelay = 5;
                return;
            }
        }

        if (waterAvoidance.get()) {
            if (isHandlingWater) {
                if (waterMoveTicks > 0) {
                    waterMoveTicks--;
                    mc.options.keyUp.setDown(true);
                    return;
                }
                mc.options.keyUp.setDown(false);

                if (waterSafetyDelay > 0) {
                    waterSafetyDelay--;
                    return;
                }

                if (isImmediateWaterDanger()) {
                    placeSafetyBlock(true);
                    waterSafetyDelay = 5;
                    waterSafetyCounter++;

                    if (waterSafetyCounter > 40) {
                        stopBaritoneSafely("Water safety failed!");
                        return;
                    }
                    return;
                } else {
                    isHandlingWater = false;
                    mc.options.keyJump.setDown(false);
                    mc.options.keyUp.setDown(false);
                    sendPing("Water avoided. Resuming Baromine.");
                    updateBaritoneGoal();
                }
                return;
            } else if (isImmediateWaterDanger()) {
                isHandlingWater = true;
                waterSafetyCounter = 0;
                stopBaritoneSafely("Water detected! Activating safety protocols.");
                waterSafetyDelay = 5;
                return;
            }
        }

        if (goldenHelmet.get() && mc.player.getItemBySlot(EquipmentSlot.HEAD).getItem() != Items.GOLDEN_HELMET) {
            stopBaritoneSafely("Golden Helmet is not equipped!");
            return;
        }

        if (combatCooldownTicks > 0) combatCooldownTicks--;
        if (pauseInCombat.get()) {
            boolean inDanger = false;
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof Mob mob && mob.isAggressive() && mc.player.distanceTo(mob) < 3.0 && mc.player.hasLineOfSight(mob)) {
                    inDanger = true;
                    break;
                }
            }

            if (inDanger && !wasPausedForCombat && combatCooldownTicks == 0) {
                stopBaritoneSafely("Hostile entity detected! Pausing.");
                equipSword();
                wasPausedForCombat = true;
                combatPauseTicks = 0;
            } else if (!inDanger && wasPausedForCombat) {
                sendPing("Combat clear. Resuming Baromine.");
                wasPausedForCombat = false;
                updateBaritoneGoal();
            }
            if (wasPausedForCombat && ++combatPauseTicks >= 100) {
                sendPing("Combat stalled. Resuming mining.");
                wasPausedForCombat = false;
                combatCooldownTicks = 200;
                updateBaritoneGoal();
            }
        } else if (wasPausedForCombat) {
            wasPausedForCombat = false;
            updateBaritoneGoal();
        }
        if (wasPausedForCombat) return;

        if (navigationRetryTicks > 0) navigationRetryTicks--;
        if (navigationRetryTicks == 0 && isBaritoneIdle()) updateBaritoneGoal();

        if (antiAfk.get()) {
            antiAfkTickCounter++;
            if (antiAfkTickCounter >= 600) {
                antiAfkTickCounter = 0;
                if (isBaritoneIdle()) {
                    jumpTicks = 10;
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            }

            if (jumpTicks > 0) {
                mc.options.keyJump.setDown(true);
                jumpTicks--;
            } else {
                mc.options.keyJump.setDown(false);
            }
        }

        handleExcessDrop();
    }

    private void handleExcessDrop() {
        if (dropExcessMode.get() == ExcessDropMode.Disabled) return;

        Item targetItem = null;
        if (dropExcessMode.get() == ExcessDropMode.Cobblestone) targetItem = Items.COBBLESTONE;
        else if (dropExcessMode.get() == ExcessDropMode.Netherrack) targetItem = Items.NETHERRACK;
        else if (dropExcessMode.get() == ExcessDropMode.CobbledDeepslate) targetItem = Items.COBBLED_DEEPSLATE;

        if (targetItem == null) return;

        int total = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == targetItem) {
                total += stack.getCount();
            }
        }

        if (total <= 64) return;

        int[] order = new int[36];
        for (int i = 9; i < 36; i++) order[i - 9] = i;
        for (int i = 0; i < 9; i++) order[27 + i] = i;

        for (int i = 0; i < 36; i++) {
            int slot = order[i];
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.getItem() == targetItem) {
                int count = stack.getCount();
                if (total - count >= 64) {
                    int containerSlot = slot < 9 ? slot + 36 : slot;
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, containerSlot, 1, ContainerInput.THROW, mc.player);
                    return;
                }
            }
        }
    }

    private void triggerDeepDarkRetreat() {
        sendPing("Entered Deep Dark biome! Initiating tactical retreat.");
        deepDarkState = DeepDarkState.RETREATING;
        retreatDelay = 10;

        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
        equipSword();

        if (!safePosQueue.isEmpty()) {
            BlockPos target = safePosQueue.peekFirst();
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("goto " + target.getX() + " " + target.getY() + " " + target.getZ());
        } else {
            deepDarkState = DeepDarkState.ASCENDING;
            int x = mc.player.blockPosition().getX();
            int z = mc.player.blockPosition().getZ();
            sendPing("Retreated to safe spot. Ascending to Y=0.");
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("goto " + x + " 0 " + z);
        }
    }

    private void handleDeepDarkRetreat() {
        if (retreatDelay > 0) {
            retreatDelay--;
            return;
        }

        if (deepDarkState == DeepDarkState.RETREATING) {
            if (isBaritoneIdle()) {
                safePosQueue.clear();
                deepDarkState = DeepDarkState.ASCENDING;
                retreatDelay = 10;

                int x = mc.player.blockPosition().getX();
                int z = mc.player.blockPosition().getZ();
                sendPing("Retreated to safe spot. Ascending to Y=0.");
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("goto " + x + " 0 " + z);
            }
        } else if (deepDarkState == DeepDarkState.ASCENDING) {
            if (isBaritoneIdle()) {
                deepDarkState = DeepDarkState.RUNNING_AWAY;
                retreatDelay = 10;
                sendPing("Reached Y=0. Relocating 200 blocks away.");
                startRunningAway();
            }
        } else if (deepDarkState == DeepDarkState.RUNNING_AWAY) {
            if (isBaritoneIdle()) {
                sendPing("Evasion complete. Resuming mining operations.");
                deepDarkState = DeepDarkState.IDLE;
                safePosQueue.clear();
                updateBaritoneGoal();
            }
        }
    }

    private void startRunningAway() {
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        Direction dir = dirs[RANDOM.nextInt(dirs.length)];

        int dist = 200;
        int targetX = mc.player.blockPosition().getX() + (dir.getStepX() * dist);
        int targetZ = mc.player.blockPosition().getZ() + (dir.getStepZ() * dist);
        int targetY = 64;

        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("goto " + targetX + " " + targetY + " " + targetZ);

        deepDarkState = DeepDarkState.RUNNING_AWAY;
    }

    private void handleCraftState() {
        if (craftDelay > 0) {
            craftDelay--;
            return;
        }

        switch (craftState) {
            case GATHERING_WOOD:
                if (hasLogs()) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
                    craftState = CraftState.FINDING_TABLE;
                    craftDelay = 5;
                } else {
                    if (isBaritoneIdle()) {
                        sendPing("Searching for wood to craft a table...");
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(
                            "mine minecraft:oak_log minecraft:spruce_log minecraft:birch_log minecraft:jungle_log minecraft:acacia_log minecraft:dark_oak_log minecraft:mangrove_log minecraft:cherry_log minecraft:pale_oak_log minecraft:crimson_stem minecraft:warped_stem"
                        );
                        craftDelay = 20;
                    }
                }
                break;

            case FINDING_TABLE:
                if (InvUtils.find(Items.CRAFTING_TABLE).found()) {
                    craftState = CraftState.PLACING_TABLE;
                    craftDelay = 5;
                    return;
                }

                int plankCount = 0;
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (isPlank(stack.getItem())) plankCount += stack.getCount();
                }

                if (plankCount >= 4) {
                    if (craftStep == 0) {
                        Item plank = getAnyPlank();
                        if (plank == null) { abortCraft("No planks found!"); return; }
                        FindItemResult find = InvUtils.find(plank);
                        int invSlot = find.slot() < 9 ? find.slot() + 36 : find.slot();
                        mc.gameMode.handleContainerInput(0, invSlot, 0, ContainerInput.PICKUP, mc.player);
                        craftStep++;
                        craftDelay = 2;
                    } else if (craftStep <= 4) {
                        mc.gameMode.handleContainerInput(0, craftStep, 1, ContainerInput.PICKUP, mc.player);
                        craftStep++;
                        craftDelay = 2;
                    } else if (craftStep == 5) {
                        if (!mc.player.containerMenu.getCarried().isEmpty()) {
                            int emptySlot = mc.player.getInventory().getFreeSlot();
                            if (emptySlot != -1) {
                                int slotId = emptySlot < 9 ? emptySlot + 36 : emptySlot;
                                mc.gameMode.handleContainerInput(0, slotId, 0, ContainerInput.PICKUP, mc.player);
                            }
                        }
                        craftStep++;
                        craftDelay = 2;
                    } else if (craftStep == 6) {
                        mc.gameMode.handleContainerInput(0, 0, 0, ContainerInput.QUICK_MOVE, mc.player);
                        craftStep = 0;
                        craftDelay = 5;
                    }
                } else if (hasLogs()) {
                    Item log = getAnyLog();
                    if (craftStep == 0) {
                        FindItemResult find = InvUtils.find(log);
                        int invSlot = find.slot() < 9 ? find.slot() + 36 : find.slot();
                        mc.gameMode.handleContainerInput(0, invSlot, 0, ContainerInput.PICKUP, mc.player);
                        craftStep++;
                        craftDelay = 2;
                    } else if (craftStep == 1) {
                        mc.gameMode.handleContainerInput(0, 1, 1, ContainerInput.PICKUP, mc.player);
                        craftStep++;
                        craftDelay = 2;
                    } else if (craftStep == 2) {
                        if (!mc.player.containerMenu.getCarried().isEmpty()) {
                            int emptySlot = mc.player.getInventory().getFreeSlot();
                            if (emptySlot != -1) {
                                int slotId = emptySlot < 9 ? emptySlot + 36 : emptySlot;
                                mc.gameMode.handleContainerInput(0, slotId, 0, ContainerInput.PICKUP, mc.player);
                            }
                        }
                        craftStep++;
                        craftDelay = 2;
                    } else if (craftStep == 3) {
                        mc.gameMode.handleContainerInput(0, 0, 0, ContainerInput.QUICK_MOVE, mc.player);
                        craftStep = 0;
                        craftDelay = 5;
                    }
                } else {
                    craftState = CraftState.GATHERING_WOOD;
                }
                break;

            case PLACING_TABLE:
                if (isWaterNearby(mc.player.blockPosition(), 2) || isImmediateLavaDanger()) {
                    if (!isBaritoneIdle()) { return; }
                    int dx = RANDOM.nextInt(16) + 10;
                    int dz = RANDOM.nextInt(16) + 10;
                    if (RANDOM.nextBoolean()) dx *= -1;
                    if (RANDOM.nextBoolean()) dz *= -1;
                    int targetX = mc.player.blockPosition().getX() + dx;
                    int targetZ = mc.player.blockPosition().getZ() + dz;
                    int targetY = mc.player.blockPosition().getY();
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("goto " + targetX + " " + targetY + " " + targetZ);
                    craftDelay = 10;
                    return;
                }

                FindItemResult tableItem = InvUtils.findInHotbar(Items.CRAFTING_TABLE);
                if (!tableItem.found()) { abortCraft("Lost Crafting Table!"); return; }

                craftTablePos = findAndPlace(tableItem);
                if (craftTablePos == null) {
                    abortCraft("Failed to place Crafting Table!");
                    return;
                }
                craftState = CraftState.OPENING_TABLE;
                craftDelay = 5;
                break;

            case OPENING_TABLE:
                if (mc.level.getBlockState(craftTablePos).getBlock() != Blocks.CRAFTING_TABLE) {
                    abortCraft("Failed to place Crafting Table.");
                    return;
                }
                BlockHitResult craftHit = new BlockHitResult(Vec3.atCenterOf(craftTablePos), Direction.UP, craftTablePos, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, craftHit);
                mc.player.swing(InteractionHand.MAIN_HAND);
                craftState = CraftState.CRAFTING;
                craftDelay = 5;
                break;

            case PULLING_MATERIALS:
                if (craftContainerPos1 == null) {
                    abortCraft("No container highlighted for materials!");
                    return;
                }

                if (!(mc.player.containerMenu instanceof ChestMenu) && !(mc.player.containerMenu instanceof ShulkerBoxMenu)) {
                    BlockHitResult containerHit = new BlockHitResult(Vec3.atCenterOf(craftContainerPos1), Direction.UP, craftContainerPos1, false);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, containerHit);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    craftDelay = 5;
                    return;
                }

                ChestMenu containerHandler = (ChestMenu) mc.player.containerMenu;
                boolean needMaterials = false;
                Item rawItemNeeded = null;

                if (autoCraft.get() == CraftMode.Ores) {
                    CraftRecipe recipe = getCraftableOreRecipe();
                    if (recipe != null && countItem(recipe.raw) < recipe.count) {
                        needMaterials = true;
                        rawItemNeeded = recipe.raw;
                    }
                } else if (autoCraft.get() == CraftMode.Blocks) {
                    CraftRecipe recipe = getCraftableBlockRecipe();
                    if (recipe != null && countItem(recipe.raw) < recipe.count) {
                        needMaterials = true;
                        rawItemNeeded = recipe.raw;
                    }
                }

                if (!needMaterials) {
                    mc.player.closeContainer();
                    craftState = CraftState.OPENING_TABLE;
                    craftDelay = 5;
                    return;
                }

                boolean pulled = false;
                for (int i = 0; i < 27; i++) {
                    ItemStack stack = containerHandler.getSlot(i).getItem();
                    if (stack.getItem() == rawItemNeeded) {
                        mc.gameMode.handleContainerInput(containerHandler.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                        pulled = true;
                        break;
                    }
                }

                if (!pulled) {
                    abortCraft("Ran out of " + rawItemNeeded.getName(rawItemNeeded.getDefaultInstance()).getString() + " in the container!");
                    return;
                }

                craftDelay = 2;
                break;

            case CRAFTING:
                if (!(mc.player.containerMenu instanceof CraftingMenu)) {
                    abortCraft("Failed to open Crafting Table.");
                    return;
                }
                CraftingMenu craftHandler = (CraftingMenu) mc.player.containerMenu;

                Item rawItem = null;
                int requiredAmount = 0;
                int[] gridSlots = null;

                if (autoCraft.get() == CraftMode.Ores) {
                    CraftRecipe recipe = getCraftableOreRecipe();
                    if (recipe == null) {
                        mc.player.closeContainer();
                        craftState = CraftState.CLOSING_TABLE;
                        craftDelay = 5;
                        return;
                    }
                    rawItem = recipe.raw;
                    requiredAmount = recipe.count;
                    gridSlots = recipe.gridSlots;
                } else if (autoCraft.get() == CraftMode.Blocks) {
                    CraftRecipe recipe = getCraftableBlockRecipe();
                    if (recipe == null) {
                        mc.player.closeContainer();
                        craftState = CraftState.CLOSING_TABLE;
                        craftDelay = 5;
                        return;
                    }
                    rawItem = recipe.raw;
                    requiredAmount = recipe.count;
                    gridSlots = recipe.gridSlots;
                }

                if (rawItem == null) {
                    mc.player.closeContainer();
                    craftState = CraftState.CLOSING_TABLE;
                    craftDelay = 5;
                    return;
                }

                if (craftStep == 0) {
                    FindItemResult find = InvUtils.find(rawItem);
                    int slotId = find.slot() < 9 ? find.slot() + 37 : find.slot() + 10;
                    mc.gameMode.handleContainerInput(craftHandler.containerId, slotId, 0, ContainerInput.PICKUP, mc.player);
                    craftStep++;
                    craftDelay = 2;
                } else if (craftStep <= requiredAmount) {
                    mc.gameMode.handleContainerInput(craftHandler.containerId, gridSlots[craftStep - 1], 1, ContainerInput.PICKUP, mc.player);
                    craftStep++;
                    craftDelay = 2;
                } else if (craftStep == requiredAmount + 1) {
                    if (!mc.player.containerMenu.getCarried().isEmpty()) {
                        FindItemResult find = InvUtils.find(rawItem);
                        if (find.found()) {
                            int slotId = find.slot() < 9 ? find.slot() + 37 : find.slot() + 10;
                            mc.gameMode.handleContainerInput(craftHandler.containerId, slotId, 0, ContainerInput.PICKUP, mc.player);
                        } else {
                            int emptySlot = mc.player.getInventory().getFreeSlot();
                            if (emptySlot != -1) {
                                int slotId = emptySlot < 9 ? emptySlot + 37 : emptySlot + 10;
                                mc.gameMode.handleContainerInput(craftHandler.containerId, slotId, 0, ContainerInput.PICKUP, mc.player);
                            }
                        }
                    }
                    craftStep++;
                    craftDelay = 2;
                } else if (craftStep == requiredAmount + 2) {
                    mc.gameMode.handleContainerInput(craftHandler.containerId, 0, 0, ContainerInput.QUICK_MOVE, mc.player);
                    craftStep = 0;
                    craftDelay = 5;
                }
                break;

            case CLOSING_TABLE:
                mc.player.closeContainer();
                craftState = CraftState.BREAKING_TABLE;
                craftDelay = 5;
                break;

            case BREAKING_TABLE:
                if (!equipEnchantedPickaxe()) {
                    abortCraft("No " + toolEnchant.get() + " Pickaxe found!");
                    return;
                }
                if (mc.level.getBlockState(craftTablePos).getBlock() == Blocks.CRAFTING_TABLE) {
                    breakBlock(craftTablePos);
                    craftDelay = 1;
                    return;
                }
                pickupTimeout = 0;
                craftState = CraftState.PICKING_UP_TABLE;
                craftDelay = 5;
                break;

            case PICKING_UP_TABLE:
                if (InvUtils.find(Items.CRAFTING_TABLE).found()) {
                    mc.options.keyUp.setDown(false);
                    craftState = CraftState.RESUMING;
                    craftDelay = 5;
                    return;
                }

                ItemEntity targetTable = null;
                double closestTableDist = 6.0;
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof ItemEntity itemEntity && itemEntity.getItem().getItem() == Items.CRAFTING_TABLE) {
                        double dist = mc.player.distanceTo(entity);
                        if (dist < closestTableDist) {
                            closestTableDist = dist;
                            targetTable = itemEntity;
                        }
                    }
                }

                if (targetTable != null) {
                    double diffX = targetTable.getX() - mc.player.getX();
                    double diffZ = targetTable.getZ() - mc.player.getZ();
                    float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
                    mc.player.setYRot(yaw);
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, mc.player.getXRot(), mc.player.onGround(), false));
                    mc.options.keyUp.setDown(true);
                    craftDelay = 1;
                } else {
                    pickupTimeout++;
                    if (pickupTimeout > 60) {
                        mc.options.keyUp.setDown(false);
                        abortCraft("Lost Crafting Table after mining it!");
                    } else {
                        craftDelay = 1;
                    }
                }
                break;

            case RESUMING:
                ensureToolsInHotbar();
                craftState = CraftState.IDLE;
                craftStep = 0;
                sendPing("Successfully crafted items. Resuming Baromine.");
                updateBaritoneGoal();
                break;
        }
    }

    private boolean isPlank(Item item) {
        return item != Items.AIR && item.getDefaultInstance().is(ItemTags.PLANKS);
    }

    private Item getAnyPlank() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isPlank(stack.getItem())) return stack.getItem();
        }
        return null;
    }

    private boolean isLog(Item item) {
        return item != Items.AIR && item.getDefaultInstance().is(ItemTags.LOGS);
    }

    private Item getAnyLog() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isLog(stack.getItem())) return stack.getItem();
        }
        return null;
    }

    private boolean hasLogs() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isLog(stack.getItem())) return true;
        }
        return false;
    }

    private int countItem(Item item) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private record CraftRecipe(Item raw, int count, int[] gridSlots) {}

    private CraftRecipe getOreRecipe(Block block) {
        int[] grid9 = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] grid4 = new int[]{1, 2, 4, 5};
        if (block == Blocks.IRON_BLOCK) return new CraftRecipe(Items.RAW_IRON, 9, grid9);
        if (block == Blocks.GOLD_BLOCK) return new CraftRecipe(Items.RAW_GOLD, 9, grid9);
        if (block == Blocks.COPPER_BLOCK) return new CraftRecipe(Items.RAW_COPPER, 9, grid9);
        if (block == Blocks.DIAMOND_BLOCK) return new CraftRecipe(Items.DIAMOND, 9, grid9);
        if (block == Blocks.EMERALD_BLOCK) return new CraftRecipe(Items.EMERALD, 9, grid9);
        if (block == Blocks.COAL_BLOCK) return new CraftRecipe(Items.COAL, 9, grid9);
        if (block == Blocks.REDSTONE_BLOCK) return new CraftRecipe(Items.REDSTONE, 9, grid9);
        if (block == Blocks.LAPIS_BLOCK) return new CraftRecipe(Items.LAPIS_LAZULI, 9, grid9);
        if (block == Blocks.QUARTZ_BLOCK) return new CraftRecipe(Items.QUARTZ, 4, grid4);
        return null;
    }

    private CraftRecipe getBlockRecipe(Block block) {
        if (block == null) return null;
        int[] grid9 = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] grid4 = new int[]{1, 2, 4, 5};
        int[] grid3Bottom = new int[]{7, 8, 9};
        int[] grid6Top = new int[]{1, 2, 3, 4, 5, 6};
        int[] grid6Stairs = new int[]{1, 4, 5, 7, 8, 9};

        if (block == Blocks.STONE_BRICKS) return new CraftRecipe(Items.STONE, 4, grid4);
        if (block == Blocks.POLISHED_GRANITE) return new CraftRecipe(Items.GRANITE, 4, grid4);
        if (block == Blocks.POLISHED_DIORITE) return new CraftRecipe(Items.DIORITE, 4, grid4);
        if (block == Blocks.POLISHED_ANDESITE) return new CraftRecipe(Items.ANDESITE, 4, grid4);
        if (block == Blocks.POLISHED_DEEPSLATE) return new CraftRecipe(Items.DEEPSLATE, 4, grid4);
        if (block == Blocks.DEEPSLATE_BRICKS) return new CraftRecipe(Items.POLISHED_DEEPSLATE, 4, grid4);
        if (block == Blocks.DEEPSLATE_TILES) return new CraftRecipe(Items.DEEPSLATE_BRICKS, 4, grid4);
        if (block == Blocks.END_STONE_BRICKS) return new CraftRecipe(Items.END_STONE, 4, grid4);
        if (block == Blocks.MUD_BRICKS) return new CraftRecipe(Items.PACKED_MUD, 4, grid4);

        if (block == Blocks.NETHER_BRICKS) return new CraftRecipe(Items.NETHER_BRICK, 4, grid4);
        if (block == Blocks.QUARTZ_BRICKS) return new CraftRecipe(Items.QUARTZ_BLOCK, 4, grid4);
        if (block == Blocks.POLISHED_BASALT) return new CraftRecipe(Items.BASALT, 4, grid4);
        if (block == Blocks.POLISHED_BLACKSTONE) return new CraftRecipe(Items.BLACKSTONE, 4, grid4);
        if (block == Blocks.POLISHED_BLACKSTONE_BRICKS) return new CraftRecipe(Items.BLACKSTONE, 4, grid4);

        if (block == Blocks.SANDSTONE) return new CraftRecipe(Items.SAND, 4, grid4);
        if (block == Blocks.RED_SANDSTONE) return new CraftRecipe(Items.RED_SAND, 4, grid4);
        if (block == Blocks.CUT_SANDSTONE) return new CraftRecipe(Items.SANDSTONE, 4, grid4);
        if (block == Blocks.CUT_RED_SANDSTONE) return new CraftRecipe(Items.RED_SANDSTONE, 4, grid4);
        if (block == Blocks.BRICKS) return new CraftRecipe(Items.CLAY_BALL, 4, grid4);
        if (block == Blocks.RESIN_BLOCK) return new CraftRecipe(Items.RESIN_CLUMP, 9, grid9);
        if (block == Blocks.RESIN_BRICKS) return new CraftRecipe(Items.RESIN_BRICK, 4, grid4);

        if (block == Blocks.SNOW_BLOCK) return new CraftRecipe(Items.SNOWBALL, 4, grid4);
        if (block == Blocks.GLOWSTONE) return new CraftRecipe(Items.GLOWSTONE_DUST, 4, grid4);
        if (block == Blocks.MELON) return new CraftRecipe(Items.MELON_SLICE, 9, grid9);
        if (block == Blocks.DRIED_KELP_BLOCK) return new CraftRecipe(Items.DRIED_KELP, 9, grid9);
        if (block == Blocks.BAMBOO_BLOCK) return new CraftRecipe(Items.BAMBOO, 9, grid9);
        if (block == Blocks.HAY_BLOCK) return new CraftRecipe(Items.WHEAT, 9, grid9);
        if (block == Blocks.BONE_BLOCK) return new CraftRecipe(Items.BONE_MEAL, 9, grid9);
        if (block == Blocks.PACKED_ICE) return new CraftRecipe(Items.ICE, 9, grid9);
        if (block == Blocks.BLUE_ICE) return new CraftRecipe(Items.PACKED_ICE, 9, grid9);

        if (block == Blocks.IRON_BARS) return new CraftRecipe(Items.IRON_INGOT, 6, grid6Top);
        if (block == Blocks.GLASS_PANE) return new CraftRecipe(Items.GLASS, 6, grid6Top);

        if (block == Blocks.STONE_SLAB) return new CraftRecipe(Items.STONE, 3, grid3Bottom);
        if (block == Blocks.COBBLESTONE_SLAB) return new CraftRecipe(Items.COBBLESTONE, 3, grid3Bottom);
        if (block == Blocks.STONE_BRICK_SLAB) return new CraftRecipe(Items.STONE_BRICKS, 3, grid3Bottom);
        if (block == Blocks.SANDSTONE_SLAB) return new CraftRecipe(Items.SANDSTONE, 3, grid3Bottom);
        if (block == Blocks.RED_SANDSTONE_SLAB) return new CraftRecipe(Items.RED_SANDSTONE, 3, grid3Bottom);
        if (block == Blocks.NETHER_BRICK_SLAB) return new CraftRecipe(Items.NETHER_BRICKS, 3, grid3Bottom);
        if (block == Blocks.QUARTZ_SLAB) return new CraftRecipe(Items.QUARTZ_BLOCK, 3, grid3Bottom);
        if (block == Blocks.END_STONE_BRICK_SLAB) return new CraftRecipe(Items.END_STONE_BRICKS, 3, grid3Bottom);
        if (block == Blocks.POLISHED_GRANITE_SLAB) return new CraftRecipe(Items.POLISHED_GRANITE, 3, grid3Bottom);
        if (block == Blocks.POLISHED_DIORITE_SLAB) return new CraftRecipe(Items.POLISHED_DIORITE, 3, grid3Bottom);
        if (block == Blocks.POLISHED_ANDESITE_SLAB) return new CraftRecipe(Items.POLISHED_ANDESITE, 3, grid3Bottom);
        if (block == Blocks.POLISHED_DEEPSLATE_SLAB) return new CraftRecipe(Items.POLISHED_DEEPSLATE, 3, grid3Bottom);
        if (block == Blocks.DEEPSLATE_BRICK_SLAB) return new CraftRecipe(Items.DEEPSLATE_BRICKS, 3, grid3Bottom);
        if (block == Blocks.DEEPSLATE_TILE_SLAB) return new CraftRecipe(Items.DEEPSLATE_TILES, 3, grid3Bottom);
        if (block == Blocks.COBBLED_DEEPSLATE_SLAB) return new CraftRecipe(Items.COBBLED_DEEPSLATE, 3, grid3Bottom);
        if (block == Blocks.BLACKSTONE_SLAB) return new CraftRecipe(Items.BLACKSTONE, 3, grid3Bottom);
        if (block == Blocks.POLISHED_BLACKSTONE_SLAB) return new CraftRecipe(Items.POLISHED_BLACKSTONE, 3, grid3Bottom);
        if (block == Blocks.POLISHED_BLACKSTONE_BRICK_SLAB) return new CraftRecipe(Items.POLISHED_BLACKSTONE_BRICKS, 3, grid3Bottom);
        if (block == Blocks.MUD_BRICK_SLAB) return new CraftRecipe(Items.MUD_BRICKS, 3, grid3Bottom);
        if (block == Blocks.RESIN_BRICK_SLAB) return new CraftRecipe(Items.RESIN_BRICKS, 3, grid3Bottom);
        if (block == Blocks.OAK_SLAB) return new CraftRecipe(Items.OAK_PLANKS, 3, grid3Bottom);
        if (block == Blocks.SPRUCE_SLAB) return new CraftRecipe(Items.SPRUCE_PLANKS, 3, grid3Bottom);
        if (block == Blocks.BIRCH_SLAB) return new CraftRecipe(Items.BIRCH_PLANKS, 3, grid3Bottom);
        if (block == Blocks.JUNGLE_SLAB) return new CraftRecipe(Items.JUNGLE_PLANKS, 3, grid3Bottom);
        if (block == Blocks.ACACIA_SLAB) return new CraftRecipe(Items.ACACIA_PLANKS, 3, grid3Bottom);
        if (block == Blocks.DARK_OAK_SLAB) return new CraftRecipe(Items.DARK_OAK_PLANKS, 3, grid3Bottom);
        if (block == Blocks.MANGROVE_SLAB) return new CraftRecipe(Items.MANGROVE_PLANKS, 3, grid3Bottom);
        if (block == Blocks.CHERRY_SLAB) return new CraftRecipe(Items.CHERRY_PLANKS, 3, grid3Bottom);
        if (block == Blocks.PALE_OAK_SLAB) return new CraftRecipe(Items.PALE_OAK_PLANKS, 3, grid3Bottom);

        if (block == Blocks.STONE_STAIRS) return new CraftRecipe(Items.STONE, 6, grid6Stairs);
        if (block == Blocks.COBBLESTONE_STAIRS) return new CraftRecipe(Items.COBBLESTONE, 6, grid6Stairs);
        if (block == Blocks.STONE_BRICK_STAIRS) return new CraftRecipe(Items.STONE_BRICKS, 6, grid6Stairs);
        if (block == Blocks.SANDSTONE_STAIRS) return new CraftRecipe(Items.SANDSTONE, 6, grid6Stairs);
        if (block == Blocks.RED_SANDSTONE_STAIRS) return new CraftRecipe(Items.RED_SANDSTONE, 6, grid6Stairs);
        if (block == Blocks.NETHER_BRICK_STAIRS) return new CraftRecipe(Items.NETHER_BRICKS, 6, grid6Stairs);
        if (block == Blocks.QUARTZ_STAIRS) return new CraftRecipe(Items.QUARTZ_BLOCK, 6, grid6Stairs);
        if (block == Blocks.END_STONE_BRICK_STAIRS) return new CraftRecipe(Items.END_STONE_BRICKS, 6, grid6Stairs);
        if (block == Blocks.GRANITE_STAIRS) return new CraftRecipe(Items.GRANITE, 6, grid6Stairs);
        if (block == Blocks.DIORITE_STAIRS) return new CraftRecipe(Items.DIORITE, 6, grid6Stairs);
        if (block == Blocks.ANDESITE_STAIRS) return new CraftRecipe(Items.ANDESITE, 6, grid6Stairs);
        if (block == Blocks.POLISHED_GRANITE_STAIRS) return new CraftRecipe(Items.POLISHED_GRANITE, 6, grid6Stairs);
        if (block == Blocks.POLISHED_DIORITE_STAIRS) return new CraftRecipe(Items.POLISHED_DIORITE, 6, grid6Stairs);
        if (block == Blocks.POLISHED_ANDESITE_STAIRS) return new CraftRecipe(Items.POLISHED_ANDESITE, 6, grid6Stairs);
        if (block == Blocks.DEEPSLATE_BRICK_STAIRS) return new CraftRecipe(Items.DEEPSLATE_BRICKS, 6, grid6Stairs);
        if (block == Blocks.DEEPSLATE_TILE_STAIRS) return new CraftRecipe(Items.DEEPSLATE_TILES, 6, grid6Stairs);
        if (block == Blocks.COBBLED_DEEPSLATE_STAIRS) return new CraftRecipe(Items.COBBLED_DEEPSLATE, 6, grid6Stairs);
        if (block == Blocks.POLISHED_DEEPSLATE_STAIRS) return new CraftRecipe(Items.POLISHED_DEEPSLATE, 6, grid6Stairs);
        if (block == Blocks.BLACKSTONE_STAIRS) return new CraftRecipe(Items.BLACKSTONE, 6, grid6Stairs);
        if (block == Blocks.POLISHED_BLACKSTONE_STAIRS) return new CraftRecipe(Items.POLISHED_BLACKSTONE, 6, grid6Stairs);
        if (block == Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS) return new CraftRecipe(Items.POLISHED_BLACKSTONE_BRICKS, 6, grid6Stairs);
        if (block == Blocks.MUD_BRICK_STAIRS) return new CraftRecipe(Items.MUD_BRICKS, 6, grid6Stairs);
        if (block == Blocks.RESIN_BRICK_STAIRS) return new CraftRecipe(Items.RESIN_BRICKS, 6, grid6Stairs);
        if (block == Blocks.OAK_STAIRS) return new CraftRecipe(Items.OAK_PLANKS, 6, grid6Stairs);
        if (block == Blocks.SPRUCE_STAIRS) return new CraftRecipe(Items.SPRUCE_PLANKS, 6, grid6Stairs);
        if (block == Blocks.BIRCH_STAIRS) return new CraftRecipe(Items.BIRCH_PLANKS, 6, grid6Stairs);
        if (block == Blocks.JUNGLE_STAIRS) return new CraftRecipe(Items.JUNGLE_PLANKS, 6, grid6Stairs);
        if (block == Blocks.ACACIA_STAIRS) return new CraftRecipe(Items.ACACIA_PLANKS, 6, grid6Stairs);
        if (block == Blocks.DARK_OAK_STAIRS) return new CraftRecipe(Items.DARK_OAK_PLANKS, 6, grid6Stairs);
        if (block == Blocks.MANGROVE_STAIRS) return new CraftRecipe(Items.MANGROVE_PLANKS, 6, grid6Stairs);
        if (block == Blocks.CHERRY_STAIRS) return new CraftRecipe(Items.CHERRY_PLANKS, 6, grid6Stairs);
        if (block == Blocks.PALE_OAK_STAIRS) return new CraftRecipe(Items.PALE_OAK_PLANKS, 6, grid6Stairs);

        if (block == Blocks.COBBLESTONE_WALL) return new CraftRecipe(Items.COBBLESTONE, 6, grid6Top);
        if (block == Blocks.STONE_BRICK_WALL) return new CraftRecipe(Items.STONE_BRICKS, 6, grid6Top);
        if (block == Blocks.SANDSTONE_WALL) return new CraftRecipe(Items.SANDSTONE, 6, grid6Top);
        if (block == Blocks.RED_SANDSTONE_WALL) return new CraftRecipe(Items.RED_SANDSTONE, 6, grid6Top);
        if (block == Blocks.NETHER_BRICK_WALL) return new CraftRecipe(Items.NETHER_BRICKS, 6, grid6Top);
        if (block == Blocks.END_STONE_BRICK_WALL) return new CraftRecipe(Items.END_STONE_BRICKS, 6, grid6Top);
        if (block == Blocks.GRANITE_WALL) return new CraftRecipe(Items.GRANITE, 6, grid6Top);
        if (block == Blocks.DIORITE_WALL) return new CraftRecipe(Items.DIORITE, 6, grid6Top);
        if (block == Blocks.ANDESITE_WALL) return new CraftRecipe(Items.ANDESITE, 6, grid6Top);
        if (block == Blocks.COBBLED_DEEPSLATE_WALL) return new CraftRecipe(Items.COBBLED_DEEPSLATE, 6, grid6Top);
        if (block == Blocks.POLISHED_DEEPSLATE_WALL) return new CraftRecipe(Items.POLISHED_DEEPSLATE, 6, grid6Top);
        if (block == Blocks.DEEPSLATE_BRICK_WALL) return new CraftRecipe(Items.DEEPSLATE_BRICKS, 6, grid6Top);
        if (block == Blocks.DEEPSLATE_TILE_WALL) return new CraftRecipe(Items.DEEPSLATE_TILES, 6, grid6Top);
        if (block == Blocks.BLACKSTONE_WALL) return new CraftRecipe(Items.BLACKSTONE, 6, grid6Top);
        if (block == Blocks.POLISHED_BLACKSTONE_WALL) return new CraftRecipe(Items.POLISHED_BLACKSTONE, 6, grid6Top);
        if (block == Blocks.POLISHED_BLACKSTONE_BRICK_WALL) return new CraftRecipe(Items.POLISHED_BLACKSTONE_BRICKS, 6, grid6Top);
        if (block == Blocks.MUD_BRICK_WALL) return new CraftRecipe(Items.MUD_BRICKS, 6, grid6Top);
        if (block == Blocks.RESIN_BRICK_WALL) return new CraftRecipe(Items.RESIN_BRICKS, 6, grid6Top);

        Block copperBars = optBlock("copper_bars");
        if (copperBars != null && block == copperBars) return new CraftRecipe(Items.COPPER_INGOT, 6, grid6Top);

        Block driedGhast = optBlock("dried_ghast");
        if (driedGhast != null && block == driedGhast) return new CraftRecipe(Items.GHAST_TEAR, 8, new int[]{1, 2, 3, 4, 6, 7, 8, 9});

        return null;
    }

    private boolean hasCraftableOre() {
        return getCraftableOreRecipe() != null;
    }

    private CraftRecipe getCraftableOreRecipe() {
        for (Block block : craftOreList.get()) {
            CraftRecipe recipe = getOreRecipe(block);
            if (recipe != null && countItem(recipe.raw) >= recipe.count) {
                return recipe;
            }
        }
        return null;
    }

    private boolean hasCraftableBlock() {
        return getCraftableBlockRecipe() != null;
    }

    private CraftRecipe getCraftableBlockRecipe() {
        for (Block block : craftBlockList.get()) {
            CraftRecipe recipe = getBlockRecipe(block);
            if (recipe != null && countItem(recipe.raw) >= recipe.count) {
                return recipe;
            }
        }
        return null;
    }

    private void abortCraft(String reason) {
        sendPing("CRAFT ABORTED: " + reason);
        if (mc.player.containerMenu != null) {
            mc.player.closeContainer();
        }
        craftState = CraftState.IDLE;
        craftStep = 0;
        craftDelay = 0;
        updateBaritoneGoal();
    }

    private boolean isInNetherOrOverworld() {
        if (mc.level == null) return false;
        return mc.level.dimension().equals(Level.OVERWORLD) || mc.level.dimension().equals(Level.NETHER);
    }

    private void startHideout() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
        hideoutState = HideoutState.DIGGING;
        hideoutDir = null;
        hideoutPos = null;
        hideoutDelay = 0;
        sendPing("Target reached! Finding a safe wall to hide in before logging out.");
    }

    private void handleHideoutState() {
        if (hideoutDelay > 0) {
            hideoutDelay--;
            return;
        }

        switch (hideoutState) {
            case DIGGING:
                if (hideoutDir == null) {
                    Direction[] dirs = {mc.player.getDirection(), Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
                    for (Direction d : dirs) {
                        BlockPos feet = mc.player.blockPosition().relative(d);
                        BlockPos head = feet.above();
                        if (!mc.level.isEmptyBlock(feet) && !mc.level.isEmptyBlock(head) && mc.level.getBlockState(feet).getDestroySpeed(mc.level, feet) > 0 && mc.level.getBlockState(head).getDestroySpeed(mc.level, head) > 0) {
                            hideoutDir = d;
                            hideoutPos = feet;
                            break;
                        }
                    }
                    if (hideoutDir == null) {
                        sendPing("Could not find a valid wall to hide in. Logging out anyway.");
                        hideoutState = HideoutState.DONE;
                        hideoutDelay = 10;
                        return;
                    }
                }

                boolean feetBroken = mc.level.isEmptyBlock(hideoutPos);
                boolean headBroken = mc.level.isEmptyBlock(hideoutPos.above());

                if (!feetBroken) {
                    breakBlock(hideoutPos);
                    hideoutDelay = 2;
                } else if (!headBroken) {
                    breakBlock(hideoutPos.above());
                    hideoutDelay = 2;
                } else {
                    hideoutState = HideoutState.ENTERING;
                    hideoutDelay = 2;
                }
                break;

            case ENTERING:
                if (mc.player.blockPosition().equals(hideoutPos)) {
                    mc.options.keyUp.setDown(false);
                    hideoutState = HideoutState.SEALING;
                    hideoutDelay = 2;
                } else {
                    mc.options.keyUp.setDown(true);
                    hideoutDelay = 1;
                }
                break;

            case SEALING:
                mc.options.keyUp.setDown(false);

                BlockPos sealPos = mc.player.blockPosition().relative(hideoutDir.getOpposite());
                FindItemResult blockItem = InvUtils.findInHotbar(item ->
                    item.getItem() instanceof net.minecraft.world.item.BlockItem &&
                    !SHULKER_PREDICATE.test(item) &&
                    item.getItem() != Items.ENDER_CHEST
                );

                if (blockItem.found() && mc.level.isEmptyBlock(sealPos)) {
                    lookAtBlock(sealPos);
                    BlockUtils.place(sealPos, blockItem, 0);
                    sendPing("Sealed inside wall. Safe logout complete.");
                } else {
                    sendPing("Warning: No blocks to seal the wall. Logging out in open air.");
                }

                hideoutState = HideoutState.DONE;
                hideoutDelay = 10;
                break;

            case DONE:
                toggle();
                hideoutState = HideoutState.IDLE;
                break;
        }
    }

    private void startAutoMend() {
        isAutoMending = true;
        savedTargetBlock = getTargetBlock();

        sendPing("Tool durability low! Pausing mining to auto-mend tools.");
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
        updateMendingTools();

        if (!isAutoMending) return;

        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("set minYLevelWhileMining 0");
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("set maxYLevelWhileMining 320");
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("mine " + BuiltInRegistries.BLOCK.getKey(mendOreBlock.get()).toString());
    }

    private void handleAutoMend() {
        if (mendToolSwapDelay > 0) {
            mendToolSwapDelay--;
        } else {
            updateMendingTools();
            mendToolSwapDelay = 20;
        }

        if (!hasToolsBelowDurability(maxMendDurability.get())) {
            stopAutoMend();
        }
    }

    private void stopAutoMend() {
        isAutoMending = false;
        sendPing("Tools repaired. Resuming mining operations.");

        if (getToolType(mc.player.getOffhandItem()) != null) {
            int emptySlot = mc.player.getInventory().getFreeSlot();
            if (emptySlot != -1) InvUtils.move().fromOffhand().to(emptySlot);
        }

        updateBaritoneGoal(savedTargetBlock);
    }

    private void updateMendingTools() {
        int mainHandSlot = Hotbar.selected();
        ItemStack mainHand = mc.player.getMainHandItem();

        boolean needsSwap = false;
        if (getToolType(mainHand) == null) {
            needsSwap = true;
        } else if (hasSilkTouch(mainHand)) {
            needsSwap = true;
        } else if (getDurabilityPercent(mainHand) >= maxMendDurability.get()) {
            if (findMostDamagedNonSilkTool(mainHandSlot) != -1) {
                needsSwap = true;
            }
        }

        if (needsSwap) {
            int bestSlot = findMostDamagedNonSilkTool(mainHandSlot);
            if (bestSlot == -1) {
                sendPing("Auto-Mend failed: No non-Silk Touch tool found to mine XP!");
                autoMendFailed = true;
                isAutoMending = false;
                updateBaritoneGoal(savedTargetBlock);
                return;
            }

            if (bestSlot < 9) {
                Hotbar.set(bestSlot);
            } else {
                InvUtils.move().from(bestSlot).toHotbar(Hotbar.selected());
            }
            return;
        }

        ItemStack offHand = mc.player.getOffhandItem();
        boolean needsOffhandSwap = false;
        if (getToolType(offHand) == null) {
            needsOffhandSwap = true;
        } else if (hasSilkTouch(offHand)) {
            needsOffhandSwap = true;
        } else if (getDurabilityPercent(offHand) >= maxMendDurability.get()) {
            if (findMostDamagedNonSilkTool(mainHandSlot) != -1) {
                needsSwap = true;
            }
        }

        if (needsOffhandSwap) {
            int bestSlot = findMostDamagedNonSilkTool(mainHandSlot);
            if (bestSlot != -1) {
                if (!offHand.isEmpty()) {
                    int emptySlot = mc.player.getInventory().getFreeSlot();
                    if (emptySlot != -1) InvUtils.move().fromOffhand().to(emptySlot);
                }
                InvUtils.move().from(bestSlot).toOffhand();
            }
        }
    }

    private int findMostDamagedNonSilkTool(int... excludeSlots) {
        Set<Integer> excluded = new HashSet<>();
        for (int s : excludeSlots) excluded.add(s);

        int worstSlot = -1;
        double worstDurability = 101.0;

        for (int i = 0; i < 36; i++) {
            if (excluded.contains(i)) continue;
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (getToolType(stack) != null && !hasSilkTouch(stack)) {
                double dur = getDurabilityPercent(stack);
                if (dur < maxMendDurability.get() && dur < worstDurability) {
                    worstDurability = dur;
                    worstSlot = i;
                }
            }
        }

        if (worstSlot == -1) {
            for (int i = 0; i < 36; i++) {
                if (excluded.contains(i)) continue;
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (getToolType(stack) != null && !hasSilkTouch(stack)) {
                    return i;
                }
            }
        }

        return worstSlot;
    }

    private boolean hasSilkTouch(ItemStack stack) {
        if (mc.level == null || stack.isEmpty()) return false;
        Holder<Enchantment> silkTouch = mc.level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silkTouch, stack) > 0;
    }

    private double getDurabilityPercent(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxDamage() == 0) return 100.0;
        return (double)(stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage() * 100.0;
    }

    private void handleDepositState() {
        if (depositDelay > 0) {
            depositDelay--;
            return;
        }

        Set<Item> validItems = getValidTargetItems();

        switch (depositState) {
            case PAUSING_BARITONE:
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
                if (!isBaritoneIdle()) {
                    depositDelay = 5;
                    return;
                }

                sendPing("Inventory threshold reached. Pausing Baritone to deposit items.");
                spaceClearingStarted = false;
                spaceClearAttempts = 0;

                depositState = DepositState.CLEARING_SPACE;
                depositDelay = 5;
                break;

            case CLEARING_SPACE:
                if (!spaceClearingStarted) {
                    spaceClearingStarted = true;
                    sendPing("Clearing 2x2 area with Baritone...");
                    spaceClearAttempts = 0;
                    spaceClearMinWait = 40;

                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("sel pos1 ~-1 ~ ~-1");
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("sel pos2 ~1 ~1 ~1");
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("sel cleararea");
                }

                spaceClearMinWait--;

                if (spaceClearMinWait <= 0 && isBaritoneIdle()) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("sel clear");
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
                    spaceClearingStarted = false;
                    if (depositMode.get() == DepositMode.EnderChest) {
                        depositState = DepositState.PLACING_ECHEST;
                    } else {
                        depositState = DepositState.PLACING_SHULKER;
                    }
                    depositDelay = 5;
                }
                break;

            case PLACING_ECHEST:
                FindItemResult echestItemFind = InvUtils.find(Items.ENDER_CHEST);
                if (!echestItemFind.found()) {
                    sendPing("No Ender Chest in inventory! Resuming mining.");
                    depositState = DepositState.RESUMING;
                    return;
                }

                if (echestItemFind.slot() >= 9) {
                    int targetSlot = swapSlot.get();
                    InvUtils.move().from(echestItemFind.slot()).toHotbar(targetSlot);
                    Hotbar.set(targetSlot);
                } else {
                    Hotbar.set(echestItemFind.slot());
                }

                echestPos = findAndPlace(InvUtils.findInHotbar(Items.ENDER_CHEST));
                if (echestPos == null) {
                    spaceClearAttempts++;
                    if (spaceClearAttempts >= 3) {
                        sendPing("Failed to place Ender Chest after multiple attempts! Resuming mining.");
                        spaceClearAttempts = 0;
                        depositState = DepositState.RESUMING;
                        return;
                    }
                    sendPing("Failed to place Ender Chest! Retrying...");
                    depositState = DepositState.CLEARING_SPACE;
                    depositDelay = 10;
                    return;
                }

                depositState = DepositState.OPENING_ECHEST;
                depositDelay = 5;
                break;

            case OPENING_ECHEST:
                if (mc.level.getBlockState(echestPos).getBlock() != Blocks.ENDER_CHEST) {
                    sendPing("Failed to place/find Ender Chest. Resuming mining.");
                    depositState = DepositState.RESUMING;
                    return;
                }
                BlockHitResult echestHit = new BlockHitResult(Vec3.atCenterOf(echestPos), Direction.UP, echestPos, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, echestHit);
                mc.player.swing(InteractionHand.MAIN_HAND);
                depositState = DepositState.EXTRACTING_SHULKER;
                depositDelay = 5;
                break;

            case EXTRACTING_SHULKER:
                if (!(mc.player.containerMenu instanceof ChestMenu)) {
                    sendPing("Failed to open Ender Chest. Resuming mining.");
                    depositState = DepositState.RESUMING;
                    return;
                }
                ChestMenu echestHandler = (ChestMenu) mc.player.containerMenu;

                boolean hasShulkerInInv = false;
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (SHULKER_PREDICATE.test(stack) && isValidShulkerForDeposit(stack, validItems)) {
                        hasShulkerInInv = true;
                        break;
                    }
                }

                if (hasShulkerInInv) {
                    depositState = DepositState.CLOSING_ECHEST;
                    depositDelay = 2;
                    return;
                }

                int shulkerSlot = -1;
                for (int i = 0; i < 27; i++) {
                    ItemStack stack = echestHandler.getSlot(i).getItem();
                    if (SHULKER_PREDICATE.test(stack) && isValidShulkerForDeposit(stack, validItems)) {
                        shulkerSlot = i;
                        break;
                    }
                }

                if (shulkerSlot == -1) {
                    mc.player.closeContainer();
                    sendPing("No available Shulker Boxes in the Ender Chest; stopping with items still in inventory.");
                    toggle();
                    return;
                }

                mc.gameMode.handleContainerInput(echestHandler.containerId, shulkerSlot, 0, ContainerInput.QUICK_MOVE, mc.player);
                depositState = DepositState.CLOSING_ECHEST;
                depositDelay = 2;
                break;

            case CLOSING_ECHEST:
                mc.player.closeContainer();
                depositState = DepositState.PLACING_SHULKER;
                depositDelay = 5;
                break;

            case PLACING_SHULKER:
                shulkerRecoveryAttempted = false;

                int shulkerInvSlot = -1;
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (SHULKER_PREDICATE.test(stack) && isValidShulkerForDeposit(stack, validItems)) {
                        shulkerInvSlot = i;
                        break;
                    }
                }

                if (shulkerInvSlot == -1) {
                    sendPing("No available Shulker Boxes found; stopping with items still in inventory.");
                    toggle();
                    return;
                }

                ItemStack selectedShulker = mc.player.getInventory().getItem(shulkerInvSlot).copy();
                placedShulkerItem = selectedShulker.getItem();
                if (shulkerInvSlot >= 9) {
                    int targetSlot = swapSlot.get();
                    InvUtils.move().from(shulkerInvSlot).toHotbar(targetSlot);
                    Hotbar.set(targetSlot);
                } else {
                    Hotbar.set(shulkerInvSlot);
                }

                BlockPos placeExclude = (depositMode.get() == DepositMode.EnderChest) ? echestPos : null;
                BlockPos placeExcludeUp = (depositMode.get() == DepositMode.EnderChest) ? echestPos.above() : null;
                shulkerPos = findAndPlace(InvUtils.findInHotbar(stack -> ItemStack.matches(stack, selectedShulker)), placeExclude, placeExcludeUp);

                if (shulkerPos == null) {
                    spaceClearAttempts++;
                    if (spaceClearAttempts >= 3) {
                        sendPing("Failed to place Shulker Box after 3 attempts! Resuming mining.");
                        spaceClearAttempts = 0;
                        depositState = DepositState.RESUMING;
                        return;
                    }
                    depositState = DepositState.CLEARING_SPACE;
                    depositDelay = 10;
                    return;
                }

                spaceClearAttempts = 0;
                transferSlot = -1;
                depositState = DepositState.OPENING_SHULKER;
                depositDelay = 5;
                break;

            case OPENING_SHULKER:
                if (!(mc.level.getBlockState(shulkerPos).getBlock() instanceof ShulkerBoxBlock)) {
                    sendPing("Failed to place Shulker Box. Resuming mining.");
                    depositState = DepositState.RESUMING;
                    return;
                }
                BlockHitResult shulkerHit = new BlockHitResult(Vec3.atCenterOf(shulkerPos), Direction.UP, shulkerPos, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, shulkerHit);
                mc.player.swing(InteractionHand.MAIN_HAND);
                depositState = DepositState.TRANSFERRING_ITEMS;
                depositDelay = 5;
                break;

            case TRANSFERRING_ITEMS:
                if (!(mc.player.containerMenu instanceof ShulkerBoxMenu)) {
                    if (!shulkerRecoveryAttempted) {
                        sendPing("Warning: Unable to open Shulker Box. Checking for obstructions...");
                        shulkerRecoveryAttempted = true;
                        depositState = DepositState.MINING_SURROUNDINGS_SHULKER;
                        depositDelay = 5;
                    } else {
                        sendPing("Failed to open the placed Shulker Box. Stopping; recover it manually.");
                        toggle();
                    }
                    return;
                }

                ShulkerBoxMenu shulkerHandler = (ShulkerBoxMenu) mc.player.containerMenu;

                if (transferSlot >= 0 && shulkerHandler.getSlot(transferSlot).getItem().getCount() >= transferCount) {
                    sendPing("Shulker transfer did not complete; closing and recovering the box.");
                    depositTransferFailed = true;
                    transferSlot = -1;
                    depositState = DepositState.CLOSING_SHULKER;
                    depositDelay = 2;
                    break;
                }
                transferSlot = -1;
                boolean moved = false;
                for (int i = 27; i < shulkerHandler.slots.size(); i++) {
                    ItemStack stack = shulkerHandler.getSlot(i).getItem();
                    if (validItems.contains(stack.getItem()) && hasShulkerSpace(shulkerHandler, stack)) {
                        transferSlot = i;
                        transferCount = stack.getCount();
                        mc.gameMode.handleContainerInput(shulkerHandler.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                        moved = true;
                        break;
                    }
                }

                if (moved) {
                    depositDelay = 2;
                } else {
                    depositState = DepositState.CLOSING_SHULKER;
                    depositDelay = 2;
                }
                break;

            case MINING_SURROUNDINGS_SHULKER:
                if (!mc.level.isEmptyBlock(shulkerPos.above())) {
                    breakBlock(shulkerPos.above());
                    depositDelay = 10;
                    return;
                }

                sendPing("Surroundings cleared. Attempting to open Shulker Box again.");
                BlockHitResult shulkerReopenHit = new BlockHitResult(Vec3.atCenterOf(shulkerPos), Direction.UP, shulkerPos, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, shulkerReopenHit);
                mc.player.swing(InteractionHand.MAIN_HAND);
                depositState = DepositState.TRANSFERRING_ITEMS;
                depositDelay = 5;
                break;

            case CLOSING_SHULKER:
                mc.player.closeContainer();
                shulkerPickupSnapshotTaken = false;
                depositState = DepositState.BREAKING_SHULKER;
                depositDelay = 5;
                break;

            case BREAKING_SHULKER:
                if (!equipEnchantedPickaxe()) {
                    sendPing("No " + toolEnchant.get() + " Pickaxe found! Stopping; recover the placed Shulker Box manually.");
                    toggle();
                    return;
                }

                if (!shulkerPickupSnapshotTaken) {
                    shulkerCountBeforePickup = countInventoryItem(placedShulkerItem);
                    inventoryBeforeShulkerPickup.clear();
                    for (int i = 0; i < 36; i++) inventoryBeforeShulkerPickup.add(mc.player.getInventory().getItem(i).copy());
                    recoveredShulker = ItemStack.EMPTY;
                    shulkerPickupSnapshotTaken = true;
                }

                if (mc.level.getBlockState(shulkerPos).getBlock() instanceof ShulkerBoxBlock) {
                    breakBlock(shulkerPos);
                    depositDelay = 1;
                    return;
                }

                pickupTimeout = 0;
                depositState = DepositState.PICKING_UP_SHULKER;
                depositDelay = 5;
                break;

            case PICKING_UP_SHULKER:
                if (countInventoryItem(placedShulkerItem) > shulkerCountBeforePickup) {
                    for (int i = 0; i < 36; i++) {
                        ItemStack stack = mc.player.getInventory().getItem(i);
                        if (stack.getItem() == placedShulkerItem && !ItemStack.matches(stack, inventoryBeforeShulkerPickup.get(i))) {
                            recoveredShulker = stack.copy();
                            break;
                        }
                    }
                    mc.options.keyUp.setDown(false);
                    mc.options.keyJump.setDown(false);
                    mc.options.keyLeft.setDown(false);
                    mc.options.keyRight.setDown(false);

                    if (playerLogoutPending) {
                        sendPing("Shulker Box secured. Skipping Ender Chest cleanup to log out safely!");
                        depositState = DepositState.IDLE;
                        return;
                    }

                    if (depositMode.get() == DepositMode.EnderChest) {
                        depositState = DepositState.REOPENING_ECHEST;
                    } else {
                        depositState = DepositState.RESUMING;
                    }
                    depositDelay = 5;
                    return;
                }

                ItemEntity targetShulker = null;
                double closestShulkerDist = 8.0;
                boolean shulkerExists = false;
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof ItemEntity itemEntity && itemEntity.getItem().getItem() == placedShulkerItem && itemEntity.blockPosition().distSqr(shulkerPos) < 64) {
                        double dist = mc.player.distanceTo(entity);
                        if (dist < 32.0) shulkerExists = true;
                        if (dist < closestShulkerDist) {
                            closestShulkerDist = dist;
                            targetShulker = itemEntity;
                        }
                    }
                }

                if (++pickupTimeout > 200) {
                    mc.options.keyUp.setDown(false);
                    mc.options.keyJump.setDown(false);
                    sendPing("Failed to recover the Shulker Box. Stopping to avoid losing its contents.");
                    toggle();
                    return;
                }

                if (targetShulker != null) {
                    double diffX = targetShulker.getX() - mc.player.getX();
                    double diffZ = targetShulker.getZ() - mc.player.getZ();
                    float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
                    mc.player.setYRot(yaw);
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, mc.player.getXRot(), mc.player.onGround(), false));

                    mc.options.keyUp.setDown(true);
                    if (Math.abs(targetShulker.getY() - mc.player.getY()) > 0.5 || mc.player.horizontalCollision) {
                        mc.options.keyJump.setDown(true);
                    } else {
                        mc.options.keyJump.setDown(false);
                    }

                    depositDelay = 1;
                } else {
                    mc.options.keyUp.setDown(false);
                    mc.options.keyJump.setDown(false);
                    if (pickupTimeout > 100) {
                        mc.options.keyUp.setDown(false);
                        mc.options.keyJump.setDown(false);

                        if (playerLogoutPending) {
                            sendPing("Failed to pick up Shulker, but logging out due to player!");
                            depositState = DepositState.IDLE;
                            return;
                        }

                        if (!shulkerExists) {
                            sendPing("Shulker Box drop not found. Stopping to avoid losing its contents.");
                            toggle();
                            return;
                        }
                        depositDelay = 10;
                    } else {
                        depositDelay = 1;
                    }
                }
                break;

            case REOPENING_ECHEST:
                if (mc.level.getBlockState(echestPos).getBlock() != Blocks.ENDER_CHEST) {
                    sendPing("Ender Chest disappeared before reopening; stopping with the Shulker Box in inventory.");
                    toggle();
                    return;
                }
                BlockHitResult echestHit2 = new BlockHitResult(Vec3.atCenterOf(echestPos), Direction.UP, echestPos, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, echestHit2);
                mc.player.swing(InteractionHand.MAIN_HAND);
                depositState = DepositState.DEPOSITING_SHULKER;
                depositDelay = 5;
                break;

            case DEPOSITING_SHULKER:
                if (!(mc.player.containerMenu instanceof ChestMenu)) {
                    sendPing("Failed to reopen Ender Chest; stopping with the Shulker Box in inventory.");
                    toggle();
                    return;
                }
                ChestMenu echestHandler2 = (ChestMenu) mc.player.containerMenu;
                int shulkerReturnSlot = -1;

                for (int i = 27; i < echestHandler2.slots.size(); i++) {
                    if (!recoveredShulker.isEmpty() && ItemStack.matches(recoveredShulker, echestHandler2.getSlot(i).getItem())) {
                        shulkerReturnSlot = i;
                        break;
                    }
                }

                if (shulkerReturnSlot == -1) {
                    mc.player.closeContainer();
                    sendPing("Recovered Shulker Box no longer found in inventory; stopping.");
                    toggle();
                    return;
                }

                boolean placed = false;
                for (int i = 0; i < 27; i++) {
                    if (echestHandler2.getSlot(i).getItem().isEmpty()) {
                        mc.gameMode.handleContainerInput(echestHandler2.containerId, shulkerReturnSlot, 0, ContainerInput.PICKUP, mc.player);
                        mc.gameMode.handleContainerInput(echestHandler2.containerId, i, 0, ContainerInput.PICKUP, mc.player);
                        if (!mc.player.containerMenu.getCarried().isEmpty()) {
                            mc.gameMode.handleContainerInput(echestHandler2.containerId, shulkerReturnSlot, 0, ContainerInput.PICKUP, mc.player);
                        }
                        placed = true;
                        break;
                    }
                }

                if (!placed) {
                    mc.player.closeContainer();
                    sendPing("Ender Chest is full; stopping with the Shulker Box in inventory.");
                    toggle();
                    return;
                }

                depositState = DepositState.REPLACING_TOOLS;
                depositDelay = 2;
                break;

            case REPLACING_TOOLS:
                if (!(mc.player.containerMenu instanceof ChestMenu)) {
                    sendPing("Failed to reopen Ender Chest for tool check. Resuming mining.");
                    depositState = DepositState.RESUMING;
                    return;
                }
                ChestMenu echestHandler3 = (ChestMenu) mc.player.containerMenu;

                ResourceKey<Enchantment> enchantKey = toolEnchant.get() == ToolEnchant.SilkTouch ? Enchantments.SILK_TOUCH : Enchantments.FORTUNE;
                Holder<Enchantment> enchantment = mc.level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(enchantKey);

                for (int i = 27; i < echestHandler3.slots.size(); i++) {
                    ItemStack invStack = echestHandler3.getSlot(i).getItem();
                    String type = getToolType(invStack);
                    if (type != null) {
                        double durability = getDurabilityPercent(invStack);
                        if (durability <= minToolDurability.get()) {
                            int newToolSlot = -1;

                            for (int j = 0; j < 27; j++) {
                                ItemStack echestStack = echestHandler3.getSlot(j).getItem();
                                if (getToolType(echestStack) != null && getToolType(echestStack).equals(type)) {
                                    if (EnchantmentHelper.getItemEnchantmentLevel(enchantment, echestStack) > 0) {
                                        newToolSlot = j;
                                        break;
                                    }
                                }
                            }

                            if (newToolSlot == -1) {
                                for (int j = 0; j < 27; j++) {
                                    ItemStack echestStack = echestHandler3.getSlot(j).getItem();
                                    if (getToolType(echestStack) != null && getToolType(echestStack).equals(type)) {
                                        newToolSlot = j;
                                        break;
                                    }
                                }
                            }

                            if (newToolSlot != -1) {
                                mc.gameMode.handleContainerInput(echestHandler3.containerId, newToolSlot, 0, ContainerInput.PICKUP, mc.player);
                                mc.gameMode.handleContainerInput(echestHandler3.containerId, i, 0, ContainerInput.PICKUP, mc.player);
                                if (!mc.player.containerMenu.getCarried().isEmpty()) {
                                    mc.gameMode.handleContainerInput(echestHandler3.containerId, newToolSlot, 0, ContainerInput.PICKUP, mc.player);
                                }
                                sendPing("Replaced low durability " + type + ".");
                            } else {
                                sendPing("Warning: " + type + " durability low, but no replacement found in Ender Chest.");
                            }
                        }
                    }
                }

                depositState = DepositState.REGEAR_FOOD;
                depositDelay = 2;
                break;

            case REGEAR_FOOD:
                if (!(mc.player.containerMenu instanceof ChestMenu)) {
                    sendPing("Failed to reopen Ender Chest for food check. Resuming mining.");
                    depositState = DepositState.RESUMING;
                    return;
                }
                ChestMenu echestHandler4 = (ChestMenu) mc.player.containerMenu;

                Item targetFood = foodItem.get();
                if (targetFood != Items.AIR) {
                    int currentFoodCount = 0;
                    for (int i = 27; i < echestHandler4.slots.size(); i++) {
                        ItemStack invStack = echestHandler4.getSlot(i).getItem();
                        if (invStack.getItem() == targetFood) {
                            currentFoodCount += invStack.getCount();
                        }
                    }

                    if (currentFoodCount < minFoodCount.get()) {
                        boolean movedFood = false;
                        for (int i = 0; i < 27; i++) {
                            ItemStack echestStack = echestHandler4.getSlot(i).getItem();
                            if (echestStack.getItem() == targetFood) {
                                mc.gameMode.handleContainerInput(echestHandler4.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                                movedFood = true;
                                break;
                            }
                        }
                        if (movedFood) {
                            sendPing("Low food detected. Regearing food from Ender Chest...");
                            depositDelay = 2;
                            return;
                        }
                    }
                }

                depositState = DepositState.CLOSING_ECHEST_AGAIN;
                depositDelay = 2;
                break;

            case CLOSING_ECHEST_AGAIN:
                mc.player.closeContainer();
                depositState = DepositState.BREAKING_ECHEST;
                depositDelay = 5;
                break;

            case BREAKING_ECHEST:
                if (!equipEnchantedPickaxe()) {
                    sendPing("No " + toolEnchant.get() + " Pickaxe found! Resuming mining.");
                    depositState = DepositState.RESUMING;
                    return;
                }

                if (mc.level.getBlockState(echestPos).getBlock() == Blocks.ENDER_CHEST) {
                    breakBlock(echestPos);
                    depositDelay = 1;
                    return;
                }

                pickupTimeout = 0;

                if (toolEnchant.get() == ToolEnchant.Fortune) {
                    sendPing("Fortune mode active: Leaving Ender Chest drops behind.");
                    depositState = DepositState.RESUMING;
                    depositDelay = 5;
                } else {
                    depositState = DepositState.PICKING_UP_ECHEST;
                    depositDelay = 5;
                }
                break;

            case PICKING_UP_ECHEST:
                if (InvUtils.find(Items.ENDER_CHEST).found()) {
                    mc.options.keyUp.setDown(false);
                    mc.options.keyJump.setDown(false);
                    depositState = DepositState.RESUMING;
                    depositDelay = 5;
                    return;
                }

                ItemEntity targetEchest = null;
                double closestEchestDist = 8.0;
                boolean echestExists = false;
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof ItemEntity itemEntity && itemEntity.getItem().getItem() == Items.ENDER_CHEST) {
                        double dist = mc.player.distanceTo(entity);
                        if (dist < 32.0) echestExists = true;
                        if (dist < closestEchestDist) {
                            closestEchestDist = dist;
                            targetEchest = itemEntity;
                        }
                    }
                }

                if (targetEchest != null) {
                    double diffX = targetEchest.getX() - mc.player.getX();
                    double diffZ = targetEchest.getZ() - mc.player.getZ();
                    float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
                    mc.player.setYRot(yaw);
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, mc.player.getXRot(), mc.player.onGround(), false));
                    mc.options.keyUp.setDown(true);
                    if (Math.abs(targetEchest.getY() - mc.player.getY()) > 0.5 || mc.player.horizontalCollision) {
                        mc.options.keyJump.setDown(true);
                    } else {
                        mc.options.keyJump.setDown(false);
                    }
                    depositDelay = 1;
                } else {
                    pickupTimeout++;
                    if (pickupTimeout > 100) {
                        mc.options.keyUp.setDown(false);
                        mc.options.keyJump.setDown(false);

                        if ((isFinalDeposit || safeLogout.get()) && echestExists) {
                            sendPing("Waiting for Ender Chest to drop or come into range...");
                            pickupTimeout = 0;
                            depositDelay = 10;
                        } else {
                            sendPing("Lost Ender Chest after mining it! Resuming mining.");
                            depositState = DepositState.RESUMING;
                        }
                    } else {
                        depositDelay = 1;
                    }
                }
                break;

            case RESUMING:
                ensureToolsInHotbar();
                depositState = DepositState.IDLE;

                if (depositTransferFailed) {
                    sendPing("Deposit failed; stopping with remaining items in inventory.");
                    toggle();
                } else if (isFinalDeposit && getCurrentTargetCount() > 0) {
                    depositState = DepositState.PAUSING_BARITONE;
                    depositDelay = 5;
                } else if (isFinalDeposit) {
                    isFinalDeposit = false;
                    sendPing("Final deposit complete. Target stacks reached!");
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
                    if (safeLogout.get() && isInNetherOrOverworld()) {
                        startHideout();
                    } else {
                        toggle();
                    }
                } else if (playerLogoutPending) {
                    // Handled by onTick logout logic
                } else {
                    sendPing("Successfully deposited items. Resuming Baromine.");
                    updateBaritoneGoal();
                }
                break;
        }
    }

    // --- HELPER METHODS: BARITONE & TARGETING ---

    private void updateBaritoneGoal() {
        updateBaritoneGoal(getTargetBlock());
    }

    private void updateBaritoneGoal(Block target) {
        if (!isActive()) return;
        if (mc.player == null) return;
        if (Modules.get().isActive(PortalMaker.class)) return;

        navigationRetryTicks = 40;
        Block deepslateVariant = targetMode.get() == TargetMode.Ores && includeDeepslate.get()
            ? getDeepslateVariant(target) : null;
        var commands = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager();

        // Strictly uses #mine as the sole navigation/mining command
        String command = "mine " + BuiltInRegistries.BLOCK.getKey(target);
        if (deepslateVariant != null) command += " " + BuiltInRegistries.BLOCK.getKey(deepslateVariant);
        commands.execute("set minYLevelWhileMining " + (minYLevel.get() + 64));
        commands.execute("set maxYLevelWhileMining " + (maxYLevel.get() + 64));
        commands.execute(command);
    }

    public Block getTargetBlock() {
        return targetMode.get() == TargetMode.Ores ? targetOre.get() : targetBlock.get();
    }

    public int getCurrentTargetCount() {
        Set<Item> validItems = getValidTargetItems();

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (validItems.contains(stack.getItem())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public int getTotalAvailableTargetItems() {
        Set<Item> validItems = getValidTargetItems();
        int count = 0;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (validItems.contains(stack.getItem())) {
                count += stack.getCount();
            } else if (SHULKER_PREDICATE.test(stack)) {
                count += countItemsInShulker(stack, validItems);
            }
        }

        if (mc.player.getEnderChestInventory() != null) {
            for (int i = 0; i < mc.player.getEnderChestInventory().getContainerSize(); i++) {
                ItemStack stack = mc.player.getEnderChestInventory().getItem(i);
                if (validItems.contains(stack.getItem())) {
                    count += stack.getCount();
                } else if (SHULKER_PREDICATE.test(stack)) {
                    count += countItemsInShulker(stack, validItems);
                }
            }
        }

        return count;
    }

    private int countItemsInShulker(ItemStack shulkerStack, Set<Item> validItems) {
        ItemContainerContents container = shulkerStack.get(DataComponents.CONTAINER);
        if (container == null) return 0;
        return (int) container.nonEmptyItemCopyStream()
            .filter(stack -> !stack.isEmpty() && validItems.contains(stack.getItem()))
            .mapToInt(ItemStack::getCount)
            .sum();
    }

    private Set<Item> getValidTargetItems() {
        Set<Item> items = new HashSet<>();
        Block target = getTargetBlock();
        items.add(target.asItem());

        Item drop = getOreDrop(target);
        if (drop != null) items.add(drop);

        Block deepslate = getDeepslateVariant(target);
        if (deepslate != null) items.add(deepslate.asItem());

        return items;
    }

    private Block getDeepslateVariant(Block block) {
        if (block == Blocks.DIAMOND_ORE) return Blocks.DEEPSLATE_DIAMOND_ORE;
        if (block == Blocks.IRON_ORE) return Blocks.DEEPSLATE_IRON_ORE;
        if (block == Blocks.GOLD_ORE) return Blocks.DEEPSLATE_GOLD_ORE;
        if (block == Blocks.COPPER_ORE) return Blocks.DEEPSLATE_COPPER_ORE;
        if (block == Blocks.COAL_ORE) return Blocks.DEEPSLATE_COAL_ORE;
        if (block == Blocks.LAPIS_ORE) return Blocks.DEEPSLATE_LAPIS_ORE;
        if (block == Blocks.REDSTONE_ORE) return Blocks.DEEPSLATE_REDSTONE_ORE;
        if (block == Blocks.EMERALD_ORE) return Blocks.DEEPSLATE_EMERALD_ORE;
        return null;
    }

    private Item getOreDrop(Block block) {
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return Items.DIAMOND;
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return Items.RAW_IRON;
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) return Items.RAW_GOLD;
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return Items.RAW_COPPER;
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return Items.COAL;
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return Items.LAPIS_LAZULI;
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return Items.REDSTONE;
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return Items.EMERALD;
        if (block == Blocks.NETHER_GOLD_ORE) return Items.GOLD_NUGGET;
        if (block == Blocks.NETHER_QUARTZ_ORE) return Items.QUARTZ;
        return null;
    }

    private void stopBaritoneSafely(String reason) {
        if (Modules.get().isActive(PortalMaker.class)) return;
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
        sendPing("SAFETY STOP: " + reason);
    }

    private boolean isOre(Block block) {
        if (block == Blocks.ANCIENT_DEBRIS) return true;
        if (block == Blocks.SPORE_BLOSSOM || block == Blocks.HEAVY_CORE) return false;
        return block.getName().getString().toLowerCase().contains("ore");
    }

    private boolean hasToolsBelowDurability(double threshold) {
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            String type = getToolType(stack);
            if (type != null) {
                double durability = getDurabilityPercent(stack);
                if (durability < threshold) {
                    return true;
                }
            }
        }
        return false;
    }

    private void disconnectSafely(String reason) {
        sendPing(reason);
        if (mc.player != null) mc.player.playSound(getSoundEvent(), soundVolume.get().floatValue(), 1.0f);
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(new DisconnectionDetails(Component.literal(reason + " Baromine disconnect.")));
        }
        depositState = DepositState.IDLE;
        if (!autoReconnect.get()) toggle();
    }

    private BlockPos findAndPlace(FindItemResult item, BlockPos... exclude) {
        if (!item.found()) return null;
        Set<BlockPos> excluded = new HashSet<>(Arrays.asList(exclude));

        BlockPos playerPos = mc.player.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();

        Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (Direction dir : horizontal) candidates.add(playerPos.relative(dir));
        for (Direction dir : horizontal) candidates.add(playerPos.above().relative(dir));
        candidates.add(playerPos.above(2));
        candidates.add(playerPos.below());

        for (BlockPos pos : candidates) {
            if (excluded.contains(pos)) continue;
            if (mc.level.isEmptyBlock(pos) || mc.level.getBlockState(pos).canBeReplaced()) {
                if (mc.level.isEmptyBlock(pos.above()) || mc.level.getBlockState(pos.above()).canBeReplaced()) {
                    if (BlockUtils.place(pos, item, 0)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private boolean isValidShulkerForDeposit(ItemStack shulkerStack, Set<Item> validItems) {
        ItemContainerContents container = shulkerStack.get(DataComponents.CONTAINER);
        if (container == null) return true;

        long filledSlots = container.nonEmptyItemCopyStream().filter(stack -> !stack.isEmpty()).count();
        if (filledSlots >= 27 && container.nonEmptyItemCopyStream().noneMatch(stored -> {
            if (!validItems.contains(stored.getItem()) || stored.getCount() >= stored.getMaxStackSize()) return false;
            for (int i = 0; i < 36; i++) {
                if (ItemStack.matches(stored, mc.player.getInventory().getItem(i))) return true;
            }
            return false;
        })) return false;

        return container.nonEmptyItemCopyStream()
            .filter(stack -> !stack.isEmpty())
            .allMatch(stack -> validItems.contains(stack.getItem()));
    }

    private boolean hasShulkerSpace(ShulkerBoxMenu handler, ItemStack stack) {
        for (int i = 0; i < 27; i++) {
            ItemStack stored = handler.getSlot(i).getItem();
            if (stored.isEmpty() || (ItemStack.matches(stored, stack) && stored.getCount() < stored.getMaxStackSize())) return true;
        }
        return false;
    }

    private int countInventoryItem(Item item) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private String getToolType(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(ItemTags.PICKAXES)) return "pickaxe";
        if (stack.is(ItemTags.AXES)) return "axe";
        if (stack.is(ItemTags.SHOVELS)) return "shovel";
        if (stack.is(ItemTags.HOES)) return "hoe";
        return null;
    }

    private boolean equipEnchantedPickaxe() {
        if (mc.level == null) return false;
        ResourceKey<Enchantment> enchantKey = toolEnchant.get() == ToolEnchant.SilkTouch ? Enchantments.SILK_TOUCH : Enchantments.FORTUNE;
        Holder<Enchantment> enchantment = mc.level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(enchantKey);

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(ItemTags.PICKAXES)) {
                if (EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) > 0) {
                    if (i < 9) {
                        Hotbar.set(i);
                    } else {
                        int targetSlot = -1;
                        for (int j = 0; j < 9; j++) {
                            if (mc.player.getInventory().getItem(j).isEmpty()) {
                                targetSlot = j;
                                break;
                            }
                        }
                        if (targetSlot == -1) {
                            targetSlot = Hotbar.selected();
                        }

                        InvUtils.move().from(i).toHotbar(targetSlot);
                        Hotbar.set(targetSlot);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void ensureToolsInHotbar() {
        if (mc.player == null) return;

        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (getToolType(stack) != null) {
                int targetSlot = -1;
                for (int j = 0; j < 9; j++) {
                    if (mc.player.getInventory().getItem(j).isEmpty()) {
                        targetSlot = j;
                        break;
                    }
                }

                if (targetSlot != -1) {
                    InvUtils.move().from(i).toHotbar(targetSlot);
                } else {
                    InvUtils.move().from(i).toHotbar(Hotbar.selected());
                }
            }
        }
    }

    private boolean isCombatWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ItemTags.SWORDS)) return true;
        Item item = stack.getItem();
        return item == optItem("netherite_spear")
            || item == optItem("diamond_spear")
            || item == optItem("iron_spear")
            || item == optItem("copper_spear")
            || item == optItem("golden_spear")
            || item == optItem("stone_spear")
            || item == optItem("wooden_spear");
    }

    private void equipSword() {
        if (mc.player == null) return;
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (isCombatWeapon(mc.player.getInventory().getItem(i))) {
                bestSlot = i;
                break;
            }
        }
        if (bestSlot == -1) {
            for (int i = 9; i < 36; i++) {
                if (isCombatWeapon(mc.player.getInventory().getItem(i))) {
                    int targetSlot = -1;
                    for (int j = 0; j < 9; j++) {
                        if (mc.player.getInventory().getItem(j).isEmpty()) {
                            targetSlot = j;
                            break;
                        }
                    }
                    if (targetSlot == -1) targetSlot = Hotbar.selected();
                    InvUtils.move().from(i).toHotbar(targetSlot);
                    bestSlot = targetSlot;
                    break;
                }
            }
        }
        if (bestSlot != -1) {
            Hotbar.set(bestSlot);
        }
    }

    private boolean isWaterNearby(BlockPos center, int radius) {
        if (mc.level == null) return false;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = center.offset(x, y, z);
                    var fluidState = mc.level.getBlockState(checkPos).getFluidState();
                    if (fluidState.is(Fluids.WATER) || fluidState.is(Fluids.FLOWING_WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isImmediateLavaDanger() {
        BlockPos playerPos = mc.player.blockPosition();
        if (mc.level.getBlockState(playerPos).getBlock() == Blocks.LAVA) return true;
        if (mc.level.getBlockState(playerPos.above()).getBlock() == Blocks.LAVA) return true;

        for (Direction dir : Direction.values()) {
            if (dir.getAxis().isHorizontal()) {
                if (mc.level.getBlockState(playerPos.relative(dir)).getBlock() == Blocks.LAVA) return true;
            }
        }
        return false;
    }

    private boolean isImmediateWaterDanger() {
        BlockPos playerPos = mc.player.blockPosition();
        if (mc.level.getBlockState(playerPos).getFluidState().is(FluidTags.WATER)) return true;
        if (mc.level.getBlockState(playerPos.above()).getFluidState().is(FluidTags.WATER)) return true;

        for (Direction dir : Direction.values()) {
            if (dir.getAxis().isHorizontal()) {
                if (mc.level.getBlockState(playerPos.relative(dir)).getFluidState().is(FluidTags.WATER)) return true;
            }
        }
        return false;
    }

    private void placeSafetyBlock(boolean isWater) {
        Block fluidBlock = isWater ? Blocks.WATER : Blocks.LAVA;

        FindItemResult blockItem = InvUtils.findInHotbar(itemStack ->
            itemStack.getItem() instanceof net.minecraft.world.item.BlockItem &&
            !SHULKER_PREDICATE.test(itemStack) &&
            itemStack.getItem() != Items.ENDER_CHEST
        );

        if (!blockItem.found()) {
            if (isWater) stopBaritoneSafely("No blocks for water safety!");
            else disconnectSafely("No blocks for lava safety!");
            return;
        }

        BlockPos playerPos = mc.player.blockPosition();
        Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (Direction dir : horizontal) {
            BlockPos sidePos = playerPos.relative(dir);
            if (mc.level.getBlockState(sidePos).getBlock() == fluidBlock) {
                lookAtBlock(sidePos);
                if (BlockUtils.place(sidePos, blockItem, 0)) {
                    return;
                }
            }
        }

        for (Direction dir : horizontal) {
            BlockPos sidePos = playerPos.relative(dir);
            BlockPos sideUpPos = sidePos.above();
            BlockPos sideDownPos = sidePos.below();

            boolean sideClear = mc.level.isEmptyBlock(sidePos) || mc.level.getBlockState(sidePos).getBlock() == fluidBlock;
            boolean sideUpClear = mc.level.isEmptyBlock(sideUpPos) || mc.level.getBlockState(sideUpPos).getBlock() == fluidBlock;

            if (sideClear && sideUpClear) {
                if (mc.level.getBlockState(sidePos).getBlock() == fluidBlock) {
                    lookAtBlock(sidePos);
                    BlockUtils.place(sidePos, blockItem, 0);
                }
                if (mc.level.isEmptyBlock(sideDownPos) || mc.level.getBlockState(sideDownPos).getBlock() == fluidBlock) {
                    lookAtBlock(sideDownPos);
                    BlockUtils.place(sideDownPos, blockItem, 0);
                }

                float yaw = switch (dir) {
                    case NORTH -> 180f;
                    case SOUTH -> 0f;
                    case WEST -> 90f;
                    case EAST -> -90f;
                    default -> 0f;
                };

                mc.player.setYRot(yaw);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, mc.player.getXRot(), mc.player.onGround(), false));
                mc.options.keyUp.setDown(true);
                if (isWater) waterMoveTicks = 10;
                else lavaMoveTicks = 10;
                return;
            }
        }

        BlockPos headPos = playerPos.above(2);
        boolean headClear = mc.level.isEmptyBlock(headPos) || mc.level.getBlockState(headPos).getBlock() == fluidBlock;

        if (headClear) {
            BlockPos downPos = playerPos.below();
            if (mc.level.isEmptyBlock(downPos) || mc.level.getBlockState(downPos).getBlock() == fluidBlock) {
                lookAtBlock(downPos);
                BlockUtils.place(downPos, blockItem, 0);
            }
            mc.options.keyJump.setDown(true);
            jumpTicks = 5;
        }
    }

    private void lookAtBlock(BlockPos pos) {
        Vec3 posVec = Vec3.atCenterOf(pos);
        double diffX = posVec.x - mc.player.getX();
        double diffY = posVec.y - mc.player.getEyeY();
        double diffZ = posVec.z - mc.player.getZ();

        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));

        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround(), false));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    private void breakBlock(BlockPos pos) {
        if (mc.level.getBlockState(pos).isAir()) return;
        lookAtBlock(pos);
        mc.gameMode.continueDestroyBlock(pos, Direction.UP);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void sendPing(String message) {
        if (pingMode.get() == PingMode.Chat || pingMode.get() == PingMode.Both) {
            ChatUtils.sendMsg("Baromine", Component.literal(message));
        }
        if (pingMode.get() == PingMode.Sound || pingMode.get() == PingMode.Both) {
            if (mc.player != null) {
                mc.player.playSound(getSoundEvent(), soundVolume.get().floatValue(), 1.0f);
            }
        }
    }

    private SoundEvent getSoundEvent() {
        return switch (warningSound.get()) {
            case Bass -> SoundEvents.NOTE_BLOCK_BASS.value();
            case Harp -> SoundEvents.NOTE_BLOCK_HARP.value();
            case Bell -> SoundEvents.BELL_BLOCK;
            case Anvil -> SoundEvents.ANVIL_LAND;
            case LevelUp -> SoundEvents.PLAYER_LEVELUP;
            case OrbPickup -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case Beacon -> SoundEvents.BEACON_POWER_SELECT;
            case GhastWarn -> SoundEvents.GHAST_WARN;
            case DragonGrowl -> SoundEvents.ENDER_DRAGON_GROWL;
            case WitherSpawn -> SoundEvents.WITHER_SPAWN;
            case ChallengeComplete -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            default -> SoundEvents.NOTE_BLOCK_PLING.value();
        };
    }

    public static boolean isBaritoneIdle() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        IPathingBehavior pathing = baritone.getPathingBehavior();

        boolean activeProcess = baritone.getPathingControlManager()
                .mostRecentInControl()
                .map(IBaritoneProcess::isActive)
                .orElse(false);

        boolean executingPath = pathing.isPathing();
        boolean holdingPath = pathing.hasPath();
        boolean calculatingPath = pathing.getInProgress().isPresent();

        return !activeProcess
                && !executingPath
                && !holdingPath
                && !calculatingPath;
    }

    public String getCurrentStatus() {
        if (!isActive()) return "Inactive";
        if (isAutoMending) return "Auto-Mending";
        if (hideoutState != HideoutState.IDLE) return "Hiding for Logout";
        if (deepDarkState == DeepDarkState.RETREATING) return "Retreating (Deep Dark)";
        if (deepDarkState == DeepDarkState.ASCENDING) return "Ascending (Deep Dark)";
        if (deepDarkState == DeepDarkState.RUNNING_AWAY) return "Running Away";
        if (craftState != CraftState.IDLE) return "Crafting";
        if (depositState != DepositState.IDLE) return "Depositing: " + depositState.name().replace("_", " ");
        if (isHandlingLava) return "Avoiding Lava";
        if (isHandlingWater) return "Avoiding Water";
        if (wasPausedForCombat) return "Paused (Combat)";
        if (isWaitingToReconnect) return "Waiting to Reconnect";
        return "Mining";
    }

    public double getMainHandDurabilityPercent() {
        if (mc.player == null) return 100.0;
        return getDurabilityPercent(mc.player.getMainHandItem());
    }

    public long getSessionStartTime() {
        return startTime;
    }
}