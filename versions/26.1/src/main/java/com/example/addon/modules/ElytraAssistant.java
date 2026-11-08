package com.example.addon.modules;

import org.lwjgl.glfw.GLFW;

import com.example.addon.Tim;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ElytraAssistant extends Module {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enums
    // ═══════════════════════════════════════════════════════════════════════════

    public enum MiddleClickAction {
        None,
        Rocket,
        Pearl
    }

    public enum WarningSound {
        Anvil,
        WitherSpawn,
        CreeperPrimed,
        ExperienceOrb,
        Bell,
        NoteBassDrum;

        public SoundEvent toSoundEvent() {
            return switch (this) {
                case Anvil         -> SoundEvents.ANVIL_LAND;
                case WitherSpawn   -> SoundEvents.WITHER_SPAWN;
                case CreeperPrimed -> SoundEvents.CREEPER_PRIMED;
                case ExperienceOrb -> SoundEvents.EXPERIENCE_ORB_PICKUP;
                case Bell          -> SoundEvents.BELL_BLOCK;
                case NoteBassDrum  -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
            };
        }
    }

    public enum ReplenishMode {
        Bind,
        Automatic
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Setting Groups
    // ═══════════════════════════════════════════════════════════════════════════

    private final SettingGroup sgAutoReplace     = settings.createGroup("Auto Replace");
    private final SettingGroup sgMiddleClick     = settings.createGroup("Middle Click");
    private final SettingGroup sgRocketReplenish = settings.createGroup("Rocket Replenish");

    // --- Multi-Version Registry Helpers (1.21.4 - 1.21.11) ---
    private static Item optItem(String path) {
        Identifier id = Identifier.withDefaultNamespace(path);
        return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Auto Replace
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> autoReplace = sgAutoReplace.add(new BoolSetting.Builder()
        .name("auto-replace")
        .description("Automatically replace elytra when durability is low.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> durabilityThreshold = sgAutoReplace.add(new IntSetting.Builder()
        .name("durability-threshold")
        .description("Minimum durability before replacing.")
        .defaultValue(10)
        .min(1)
        .sliderMax(100)
        .visible(autoReplace::get)
        .build()
    );

    private final Setting<WarningSound> warningSoundType = sgAutoReplace.add(new EnumSetting.Builder<WarningSound>()
        .name("warning-sound")
        .description("Sound played when no replacement elytra is available.")
        .defaultValue(WarningSound.Anvil)
        .visible(autoReplace::get)
        .build()
    );

    private final Setting<Double> warningSoundVolume = sgAutoReplace.add(new DoubleSetting.Builder()
        .name("warning-volume")
        .description("Volume of the warning sound.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(2.0)
        .visible(autoReplace::get)
        .build()
    );

    private final Setting<Keybind> toggleKey = sgAutoReplace.add(new KeybindSetting.Builder()
        .name("toggle-key")
        .description("Key to toggle auto replace.")
        .defaultValue(Keybind.none())
        .action(() -> {
            if (mc.screen != null) return;
            boolean enabled = !autoReplace.get();
            autoReplace.set(enabled);
            info("Auto Replace " + (enabled ? "enabled" : "disabled") + ".");
        })
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Middle Click
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<MiddleClickAction> middleClickAction = sgMiddleClick.add(new EnumSetting.Builder<MiddleClickAction>()
        .name("action")
        .description("Item to use when middle clicking.")
        .defaultValue(MiddleClickAction.None)
        .build()
    );

    public final Setting<Boolean> silentRocket = sgMiddleClick.add(new BoolSetting.Builder()
        .name("silent-rocket")
        .description("Prevents hand swing animation when using rockets.")
        .defaultValue(true)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Rocket Replenish
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> rocketReplenishEnabled = sgRocketReplenish.add(new BoolSetting.Builder()
        .name("rocket-replenish")
        .description("Enables the rocket replenish system.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ReplenishMode> replenishMode = sgRocketReplenish.add(new EnumSetting.Builder<ReplenishMode>()
        .name("replenish-mode")
        .description("Toggle between using a keybind or doing it automatically.")
        .defaultValue(ReplenishMode.Bind)
        .visible(rocketReplenishEnabled::get)
        .build()
    );

    private final Setting<Integer> autoThreshold = sgRocketReplenish.add(new IntSetting.Builder()
        .name("auto-threshold")
        .description("Rocket count at which the slot automatically refills.")
        .defaultValue(5)
        .min(1)
        .sliderMax(63)
        .visible(() -> rocketReplenishEnabled.get() && replenishMode.get() == ReplenishMode.Automatic)
        .build()
    );

    private final Setting<Boolean> useSelectedSlot = sgRocketReplenish.add(new BoolSetting.Builder()
        .name("use-selected-slot")
        .description("Replenishes the currently selected hotbar slot instead of a specific one.")
        .defaultValue(false)
        .visible(rocketReplenishEnabled::get)
        .build()
    );

    private final Setting<Integer> targetSlot = sgRocketReplenish.add(new IntSetting.Builder()
        .name("target-slot")
        .description("The specific hotbar slot to replenish (1-9).")
        .defaultValue(8)
        .min(1)
        .max(9)
        .visible(() -> rocketReplenishEnabled.get() && !useSelectedSlot.get())
        .build()
    );

    private final Setting<Keybind> rocketReplenishKey = sgRocketReplenish.add(new KeybindSetting.Builder()
        .name("replenish-key")
        .description("Replenishes the target hotbar slot's item to its max stack size from the main inventory.")
        .defaultValue(Keybind.none())
        .visible(() -> rocketReplenishEnabled.get() && replenishMode.get() == ReplenishMode.Bind)
        .action(() -> {
            if (mc.screen != null) return;
            if (mc.player == null || mc.level == null) return;
            if (!rocketReplenishEnabled.get()) return;
            handleRocketReplenish(false);
        })
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Constants
    // ═══════════════════════════════════════════════════════════════════════════

    private static final int MIDDLE_CLICK_COOLDOWN = 5;

    // ═══════════════════════════════════════════════════════════════════════════
    // State — Auto Replace
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean noReplacementWarned = false;

    // ═══════════════════════════════════════════════════════════════════════════
    // State — Middle Click
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean wasMiddlePressed = false;
    private int middleClickCooldown = 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════════════

    public ElytraAssistant() {
        super(Tim.CATEGORY, "elytra-assistant", "Smart elytra and rocket management.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void onActivate() {
        resetAutoReplaceState();
        resetMiddleClickState();
    }

    private void resetAutoReplaceState() {
        noReplacementWarned = false;
    }

    private void resetMiddleClickState() {
        wasMiddlePressed = false;
        middleClickCooldown = 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Event Handlers
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        handleMiddleClick();
        handleAutoReplace();
        handleAutoReplenish();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Auto Replace Feature
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleAutoReplace() {
        if (!autoReplace.get()) return;
        if (Modules.get().get(Mendbot.class).isActive()) return;

        ItemStack chestplate = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chestplate.is(Items.ELYTRA)) return;

        int remainingDurability = getRemainingDurability(chestplate);
        if (remainingDurability > durabilityThreshold.get()) {
            noReplacementWarned = false;
            return;
        }

        FindItemResult replacement = findBestReplacementElytra();
        if (replacement.found()) {
            equipElytraSilently(replacement.slot());
            warning("Elytra durability low! Replaced with fresh elytra.");
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            noReplacementWarned = false;
        } else if (!noReplacementWarned) {
            warning("No replacement elytra available!");
            playWarningSound();
            noReplacementWarned = true;
        }
    }

    private FindItemResult findBestReplacementElytra() {
        int bestSlot = -1;
        int bestDurability = -1;

        for (int i = 0; i < com.example.addon.utils.MinecraftCompat.mainStacks(mc.player.getInventory()).size(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isUsableElytra(stack)) continue;

            int durability = getRemainingDurability(stack);
            if (durability > bestDurability) {
                bestSlot = i;
                bestDurability = durability;
            }
        }

        return bestSlot != -1
            ? new FindItemResult(bestSlot, mc.player.getInventory().getItem(bestSlot).getCount())
            : new FindItemResult(-1, 0);
    }

    private boolean isUsableElytra(ItemStack stack) {
        return !stack.isEmpty()
            && stack.is(Items.ELYTRA)
            && getRemainingDurability(stack) > durabilityThreshold.get();
    }

    private int getRemainingDurability(ItemStack elytra) {
        return elytra.getMaxDamage() - elytra.getDamageValue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Middle Click Feature
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleMiddleClick() {
        if (middleClickCooldown > 0) middleClickCooldown--;

        if (middleClickAction.get() == MiddleClickAction.None) return;
        if (mc.screen != null) return;

        boolean isPressed = Input.isButtonPressed(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);

        if (isPressed && !wasMiddlePressed && middleClickCooldown == 0) {
            executeMiddleClickAction();
            wasMiddlePressed = true;
            middleClickCooldown = MIDDLE_CLICK_COOLDOWN;
        } else if (!isPressed) {
            wasMiddlePressed = false;
        }
    }

    private void executeMiddleClickAction() {
        MiddleClickAction action = middleClickAction.get();

        if (mc.player.onGround()) return;

        ItemUsage target = switch (action) {
            case Rocket -> new ItemUsage(Items.FIREWORK_ROCKET);
            case Pearl  -> new ItemUsage(Items.ENDER_PEARL);
            default     -> null;
        };

        if (target == null) return;
        useItemFromInventory(target.item());
    }

    private void useItemFromInventory(Item item) {
        FindItemResult result = InvUtils.find(item);
        if (!result.found()) return;

        int slot = result.slot();
        int previousSlot = com.example.addon.utils.MinecraftCompat.selectedSlot(mc.player.getInventory());

        if (isHotbarSlot(slot)) {
            InvUtils.swap(slot, silentRocket.get());
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
        } else {
            InvUtils.move().from(slot).toHotbar(previousSlot);
            InvUtils.swap(previousSlot, silentRocket.get());
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
            InvUtils.move().from(previousSlot).to(slot);
        }
    }

    private boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot < 9;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rocket Replenish Feature
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleAutoReplenish() {
        if (!rocketReplenishEnabled.get()) return;
        if (replenishMode.get() != ReplenishMode.Automatic) return;
        if (mc.screen != null) return;

        int selectedSlot = useSelectedSlot.get()
            ? com.example.addon.utils.MinecraftCompat.selectedSlot(mc.player.getInventory())
            : targetSlot.get() - 1;

        ItemStack targetStack = mc.player.getInventory().getItem(selectedSlot);
        
        if (!targetStack.isEmpty() && !targetStack.is(Items.FIREWORK_ROCKET)) return;

        int currentCount = targetStack.getCount();
        if (currentCount <= autoThreshold.get()) {
            handleRocketReplenish(true);
        }
    }

    private void handleRocketReplenish(boolean silent) {
        int selectedSlot = useSelectedSlot.get()
            ? com.example.addon.utils.MinecraftCompat.selectedSlot(mc.player.getInventory())
            : targetSlot.get() - 1;

        ItemStack targetStack = mc.player.getInventory().getItem(selectedSlot);
        Item targetItem = Items.FIREWORK_ROCKET;

        if (!targetStack.isEmpty() && targetStack.getItem() != targetItem) {
            if (!silent) info("Target slot has a different item — cannot replenish.");
            return;
        }

        if (!mc.player.containerMenu.getCarried().isEmpty()) {
            if (!silent) info("Cursor has an item — cannot replenish right now.");
            return;
        }

        int maxCount = targetItem.getDefaultMaxStackSize();
        int currentCount = targetStack.getCount();
        int needed = maxCount - currentCount;

        if (needed <= 0) {
            if (!silent) info("Stack is already full (" + maxCount + ").");
            return;
        }

        for (int i = 9; i < 36 && needed > 0; i++) {
            ItemStack sourceStack = mc.player.getInventory().getItem(i);
            if (sourceStack.isEmpty()) continue;
            if (sourceStack.getItem() != targetItem) continue;

            int available = sourceStack.getCount();
            InvUtils.move().from(i).toHotbar(selectedSlot);
            needed -= Math.min(needed, available);
        }

        int finalCount = maxCount - needed;

        if (needed > 0) {
            if (!silent) info("Replenished " + targetItem.getName(targetItem.getDefaultInstance()).getString()
                + " to " + finalCount + " (not enough items in inventory).");
        } else {
            if (!silent) info("Replenished " + targetItem.getName(targetItem.getDefaultInstance()).getString()
                + " to " + maxCount + ".");
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inventory Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private void equipElytraSilently(int slot) {
        InvUtils.move().from(convertToInventorySlot(slot)).toArmor(2);
    }

    private int convertToInventorySlot(int hotbarSlot) {
        return isHotbarSlot(hotbarSlot) ? 36 + hotbarSlot : hotbarSlot;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sound Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private void playWarningSound() {
        mc.player.playSound(
            warningSoundType.get().toSoundEvent(),
            warningSoundVolume.get().floatValue(),
            1.0f
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean shouldPreventRocketUse() {
        return isActive() && mc.player.onGround();
    }

    public boolean shouldSilentRocket() {
        return isActive() && silentRocket.get();
    }

    public boolean isAutoReplaceEnabled() {
        return isActive() && autoReplace.get();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner Classes
    // ═══════════════════════════════════════════════════════════════════════════

    private record ItemUsage(Item item) {}
}