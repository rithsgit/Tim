package com.example.addon.mixin;

import com.example.addon.modules.DungeonAssistant;
import com.example.addon.modules.Inventory101;
import com.example.addon.modules.LootLens;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;
    @Shadow protected int x;
    @Shadow protected int y;

    @Unique private ButtonWidget s1Button;
    @Unique private ButtonWidget s2Button;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderGlobalShulkerIcons(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Inventory101 inv101 = Modules.get().get(Inventory101.class);
        if (inv101 == null || !inv101.isActive()) return;

        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;

        if (screen instanceof GenericContainerScreen containerScreen) {
            String titleStr = containerScreen.getTitle().getString().toLowerCase();
            if (titleStr.contains("ender chest") || containerScreen.getScreenHandler().getInventory() instanceof net.minecraft.inventory.EnderChestInventory) {
                Inventory101.updateCachedEnderChest(containerScreen.getScreenHandler().getInventory());
            }
        }

        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.hasStack()) {
                ItemStack stack = slot.getStack();
                if (Inventory101.isShulker(stack)) {
                    ItemStack dominant = Inventory101.getDominantItem(stack);
                    if (!dominant.isEmpty()) {
                        float scale = (float) inv101.getIconScale();
                        float centerOffset = (16.0f * (1.0f - scale)) / 2.0f;

                        com.example.addon.utils.MinecraftApiCompat.drawScaledItem(
                            context,
                            dominant,
                            this.x + slot.x + 1 + centerOffset,
                            this.y + slot.y + 1 + centerOffset,
                            scale
                        );
                    }
                }
            }
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        s1Button = null;
        s2Button = null;

        Inventory101 inv101       = Modules.get().get(Inventory101.class);
        boolean      inv101Active = inv101 != null && inv101.isActive();

        if ((Object) this instanceof InventoryScreen && inv101Active) {
            int buttonSize = 18;
            int gap = 3;
            int[][] positions = findInventoryPresetButtonPositions(buttonSize, gap);

            s1Button = mouseOnly(Text.literal("1"),
                btn -> inv101.startInvSort(1), positions[0][0], positions[0][1], buttonSize, buttonSize,
                net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Sort to " + inv101.getPresetName(1))),
                () -> !inv101.isPresetEmpty(1));

            s2Button = mouseOnly(Text.literal("2"),
                btn -> inv101.startInvSort(2), positions[1][0], positions[1][1], buttonSize, buttonSize,
                net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Sort to " + inv101.getPresetName(2))),
                () -> !inv101.isPresetEmpty(2));

            this.addDrawableChild(s1Button);
            this.addDrawableChild(s2Button);
            return;
        }

        HandledScreen<?> screen         = (HandledScreen<?>) (Object) this;
        int              containerSlots = screen.getScreenHandler().slots.size() - 36;

        if (inv101Active && (Object) this instanceof ShulkerBoxScreen) {
            int buttonIndex = 0;
            int buttonCount = 4 + (inv101.isRegearButtonEnabled() ? 1 : 0)
                + (inv101.isReplenishButtonEnabled() ? 1 : 0);

            int btnSize = 18;
            int btnGap = 3;
            // Single vertical line (1 column) alongside the Shulker Box.
            // (If you prefer a single horizontal row across the top/side, change the last argument from 1 to buttonCount)
            int[][] positions = findButtonPositions(buttonCount, btnSize, btnSize, btnGap, 1);
            int[] position = positions[buttonIndex++];

            this.addDrawableChild(mouseOnly(Text.literal("S"),
                btn -> inv101.toggleSaveMode(), position[0], position[1], btnSize, btnSize,
                net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Save Current Layout"))));

            position = positions[buttonIndex++];
            this.addDrawableChild(mouseOnly(Text.literal("1"),
                btn -> {
                    if (!inv101.isSaveMode() && inv101.isPresetEmpty(1)) return;
                    inv101.handlePreset(1);
                }, position[0], position[1], btnSize, btnSize,
                net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Load " + inv101.getPresetName(1))),
                () -> !inv101.isPresetEmpty(1)));

            position = positions[buttonIndex++];
            this.addDrawableChild(mouseOnly(Text.literal("2"),
                btn -> {
                    if (!inv101.isSaveMode() && inv101.isPresetEmpty(2)) return;
                    inv101.handlePreset(2);
                }, position[0], position[1], btnSize, btnSize,
                net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Load " + inv101.getPresetName(2))),
                () -> !inv101.isPresetEmpty(2)));

            position = positions[buttonIndex++];
            this.addDrawableChild(mouseOnly(Text.literal("C"),
                btn -> inv101.clearPresets(), position[0], position[1], btnSize, btnSize,
                net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Clear Presets"))));

            if (inv101.isRegearButtonEnabled()) {
                position = positions[buttonIndex++];
                this.addDrawableChild(mouseOnly(Text.literal("G"),
                    btn -> inv101.startRegearing(), position[0], position[1], btnSize, btnSize,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Equip armor and replenish essentials"))));
            }

            if (inv101.isReplenishButtonEnabled()) {
                position = positions[buttonIndex];
                this.addDrawableChild(mouseOnly(Text.literal("R"),
                    btn -> inv101.startReplenishing(), position[0], position[1], btnSize, btnSize,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Replenish whitelisted items from shulker"))));
            }

            return;
        }

        boolean showSort = inv101Active && (Object) this instanceof GenericContainerScreen && inv101.isSortButtonEnabled();

        LootLens ll = Modules.get().get(LootLens.class);
        DungeonAssistant da = Modules.get().get(DungeonAssistant.class);
        boolean showStealDump = containerSlots > 0 && ((ll != null && ll.shouldShowStealDumpButtons()) || (da != null && da.isActive()));

        if (showSort) {
            int[][] positions = findButtonPositions(1, 30, 14, 0, 1);
            int sortX = positions[0][0];
            int sortY = showStealDump ? positions[0][1] + 16 : positions[0][1];
            this.addDrawableChild(mouseOnly(Text.literal("Sort"),
                btn -> inv101.startSorting(), sortX, sortY, 30, 14,
                net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Sort shulkers by colour"))));
        }

        if (showStealDump) {
            addStealDumpButtons(screen, containerSlots);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object) this instanceof InventoryScreen && s1Button != null && s2Button != null) {
            int[][] positions = findInventoryPresetButtonPositions(s1Button.getWidth(), 3);
            s1Button.setPosition(positions[0][0], positions[0][1]);
            s2Button.setPosition(positions[1][0], positions[1][1]);
        }
    }

    @Unique
    private int[][] findInventoryPresetButtonPositions(int buttonSize, int gap) {
        int margin = 3;

        // Top-left exterior of the main inventory panel (above the helmet slot)
        int topLeftX = this.x;
        int aboveInventoryY = this.y - buttonSize - margin;

        if (aboveInventoryY >= margin) {
            return new int[][] {
                { topLeftX, aboveInventoryY },
                { topLeftX + buttonSize + gap, aboveInventoryY }
            };
        }

        // Fallback if the screen height is tiny: empty top-right space inside the Crafting header
        int insideHeaderX = this.x + this.backgroundWidth - (buttonSize * 2) - gap - 7;
        int insideHeaderY = this.y + 5;
        return new int[][] {
            { insideHeaderX, insideHeaderY },
            { insideHeaderX + buttonSize + gap, insideHeaderY }
        };
    }

    @Unique
    private int[][] findButtonPositions(int count, int buttonW, int buttonH, int gap, int preferredColumns) {
        int columns = Math.min(count, preferredColumns);
        int rows = (count + columns - 1) / columns;
        int groupW = columns * buttonW + (columns - 1) * gap;
        int groupH = rows * buttonH + (rows - 1) * gap;
        int margin = 4;
        int sideY = Math.max(margin, Math.min(this.y + 5, this.height - groupH - margin));
        int centeredX = Math.max(margin, Math.min(
            this.x + (this.backgroundWidth - groupW) / 2, this.width - groupW - margin));

        int[] left = { this.x - groupW - margin, sideY };
        int[] right = { this.x + this.backgroundWidth + margin, sideY };
        int[] bottom = { centeredX, this.y + this.backgroundHeight + margin };
        int[] top = { centeredX, this.y - groupH - margin };
        int[][] candidates = { right, left, bottom, top };

        for (int[] candidate : candidates) {
            int candidateX = candidate[0];
            int candidateY = candidate[1];
            boolean inScreen = candidateX >= margin && candidateY >= margin
                && candidateX + groupW <= this.width - margin
                && candidateY + groupH <= this.height - margin;
            boolean overlapsScreen = candidateX < this.x + this.backgroundWidth
                && candidateX + groupW > this.x
                && candidateY < this.y + this.backgroundHeight
                && candidateY + groupH > this.y;
            if (inScreen && !overlapsScreen) {
                return createButtonPositions(count, buttonW, buttonH, gap, columns, candidateX, candidateY);
            }
        }

        // On very small viewports, use the title area rather than covering container slots.
        int fallbackColumns = Math.min(count, Math.max(1,
            (this.backgroundWidth - 2 * margin + gap) / (buttonW + gap)));
        int fallbackRows = (count + fallbackColumns - 1) / fallbackColumns;
        int fallbackW = fallbackColumns * buttonW + (fallbackColumns - 1) * gap;
        int fallbackH = fallbackRows * buttonH + (fallbackRows - 1) * gap;
        int fallbackX = Math.max(this.x + margin, this.x + this.backgroundWidth - fallbackW - margin);
        int fallbackY = Math.max(margin, Math.min(this.y + 2, this.height - fallbackH - margin));
        return createButtonPositions(count, buttonW, buttonH, gap, fallbackColumns, fallbackX, fallbackY);
    }

    @Unique
    private int[][] createButtonPositions(int count, int buttonW, int buttonH, int gap,
                                          int columns, int left, int top) {
        int[][] positions = new int[count][2];
        for (int i = 0; i < count; i++) {
            positions[i][0] = left + (i % columns) * (buttonW + gap);
            positions[i][1] = top + (i / columns) * (buttonH + gap);
        }
        return positions;
    }

    @Unique
    private static ButtonWidget mouseOnly(Text label, ButtonWidget.PressAction action,
                                          int x, int y, int width, int height,
                                          net.minecraft.client.gui.tooltip.Tooltip tooltip) {
        return mouseOnly(label, action, x, y, width, height, tooltip, null);
    }

    @Unique
    private static ButtonWidget mouseOnly(Text label, ButtonWidget.PressAction action,
                                          int x, int y, int width, int height,
                                          net.minecraft.client.gui.tooltip.Tooltip tooltip,
                                          BooleanSupplier hasData) {
        return com.example.addon.utils.MinecraftApiCompat.mouseOnlyButton(
            label, action, x, y, width, height, tooltip, hasData
        );
    }

    @Unique
    private void addStealDumpButtons(HandledScreen<?> screen, int containerSlots) {
        int buttonW, buttonH, buttonGap;

        if ((Object) this instanceof GenericContainerScreen) {
            buttonW = 14;
            buttonH = 14;
            buttonGap = 2;
        } else {
            buttonW = 20;
            buttonH = 20;
            buttonGap = 4;
        }
        int[][] positions = findButtonPositions(2, buttonW, buttonH, buttonGap, 2);

        this.addDrawableChild(mouseOnly(Text.literal("S"),
            button -> {
                for (int i = 0; i < containerSlots; i++) {
                    if (screen.getScreenHandler().getSlot(i).hasStack()) {
                        client.interactionManager.clickSlot(
                            screen.getScreenHandler().syncId, i, 0,
                            SlotActionType.QUICK_MOVE, client.player);
                    }
                }
            }, positions[0][0], positions[0][1], buttonW, buttonH,
            net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Steal all items from container"))));

        this.addDrawableChild(mouseOnly(Text.literal("D"),
            button -> {
                for (int i = containerSlots; i < screen.getScreenHandler().slots.size(); i++) {
                    if (screen.getScreenHandler().getSlot(i).getStack().isEmpty()) continue;
                    client.interactionManager.clickSlot(
                        screen.getScreenHandler().syncId, i, 0,
                        SlotActionType.QUICK_MOVE, client.player);
                }
            }, positions[1][0], positions[1][1], buttonW, buttonH,
            net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Dump all inventory items into container"))));
    }
}