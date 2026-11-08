package com.example.addon.mixin;

import com.example.addon.modules.DungeonAssistant;
import com.example.addon.modules.Inventory101;
import com.example.addon.modules.LootLens;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    @Unique private Button s1Button;
    @Unique private Button s2Button;

    protected HandledScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRenderGlobalShulkerIcons(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Inventory101 inv101 = Modules.get().get(Inventory101.class);
        if (inv101 == null || !inv101.isActive()) return;

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        if (screen instanceof ContainerScreen containerScreen) {
            String titleStr = containerScreen.getTitle().getString().toLowerCase();
            if (titleStr.contains("ender chest") || containerScreen.getMenu().getContainer() instanceof net.minecraft.world.inventory.PlayerEnderChestContainer) {
                Inventory101.updateCachedEnderChest(containerScreen.getMenu().getContainer());
            }
        }

        for (Slot slot : screen.getMenu().slots) {
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem();
                if (Inventory101.isShulker(stack)) {
                    ItemStack dominant = Inventory101.getDominantItem(stack);
                    if (!dominant.isEmpty()) {
                        float scale = (float) inv101.getIconScale();
                        float centerOffset = (16.0f * (1.0f - scale)) / 2.0f;

                        com.example.addon.utils.MinecraftApiCompat.drawScaledItem(
                            context,
                            dominant,
                            this.leftPos + slot.x + 1 + centerOffset,
                            this.topPos + slot.y + 1 + centerOffset,
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

            s1Button = mouseOnly(Component.literal("1"),
                btn -> inv101.startInvSort(1), positions[0][0], positions[0][1], buttonSize, buttonSize,
                net.minecraft.client.gui.components.Tooltip.create(Component.literal("Sort to " + inv101.getPresetName(1))),
                () -> !inv101.isPresetEmpty(1));

            s2Button = mouseOnly(Component.literal("2"),
                btn -> inv101.startInvSort(2), positions[1][0], positions[1][1], buttonSize, buttonSize,
                net.minecraft.client.gui.components.Tooltip.create(Component.literal("Sort to " + inv101.getPresetName(2))),
                () -> !inv101.isPresetEmpty(2));

            this.addRenderableWidget(s1Button);
            this.addRenderableWidget(s2Button);
            return;
        }

        AbstractContainerScreen<?> screen         = (AbstractContainerScreen<?>) (Object) this;
        int              containerSlots = screen.getMenu().slots.size() - 36;

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

            this.addRenderableWidget(mouseOnly(Component.literal("S"),
                btn -> inv101.toggleSaveMode(), position[0], position[1], btnSize, btnSize,
                net.minecraft.client.gui.components.Tooltip.create(Component.literal("Save Current Layout"))));

            position = positions[buttonIndex++];
            this.addRenderableWidget(mouseOnly(Component.literal("1"),
                btn -> {
                    if (!inv101.isSaveMode() && inv101.isPresetEmpty(1)) return;
                    inv101.handlePreset(1);
                }, position[0], position[1], btnSize, btnSize,
                net.minecraft.client.gui.components.Tooltip.create(Component.literal("Load " + inv101.getPresetName(1))),
                () -> !inv101.isPresetEmpty(1)));

            position = positions[buttonIndex++];
            this.addRenderableWidget(mouseOnly(Component.literal("2"),
                btn -> {
                    if (!inv101.isSaveMode() && inv101.isPresetEmpty(2)) return;
                    inv101.handlePreset(2);
                }, position[0], position[1], btnSize, btnSize,
                net.minecraft.client.gui.components.Tooltip.create(Component.literal("Load " + inv101.getPresetName(2))),
                () -> !inv101.isPresetEmpty(2)));

            position = positions[buttonIndex++];
            this.addRenderableWidget(mouseOnly(Component.literal("C"),
                btn -> inv101.clearPresets(), position[0], position[1], btnSize, btnSize,
                net.minecraft.client.gui.components.Tooltip.create(Component.literal("Clear Presets"))));

            if (inv101.isRegearButtonEnabled()) {
                position = positions[buttonIndex++];
                this.addRenderableWidget(mouseOnly(Component.literal("G"),
                    btn -> inv101.startRegearing(), position[0], position[1], btnSize, btnSize,
                    net.minecraft.client.gui.components.Tooltip.create(Component.literal("Equip armor and replenish essentials"))));
            }

            if (inv101.isReplenishButtonEnabled()) {
                position = positions[buttonIndex];
                this.addRenderableWidget(mouseOnly(Component.literal("R"),
                    btn -> inv101.startReplenishing(), position[0], position[1], btnSize, btnSize,
                    net.minecraft.client.gui.components.Tooltip.create(Component.literal("Replenish whitelisted items from shulker"))));
            }

            return;
        }

        boolean showSort = inv101Active && (Object) this instanceof ContainerScreen && inv101.isSortButtonEnabled();

        LootLens ll = Modules.get().get(LootLens.class);
        DungeonAssistant da = Modules.get().get(DungeonAssistant.class);
        boolean showStealDump = containerSlots > 0 && ((ll != null && ll.shouldShowStealDumpButtons()) || (da != null && da.isActive()));

        if (showSort) {
            int[][] positions = findButtonPositions(1, 30, 14, 0, 1);
            int sortX = positions[0][0];
            int sortY = showStealDump ? positions[0][1] + 16 : positions[0][1];
            this.addRenderableWidget(mouseOnly(Component.literal("Sort"),
                btn -> inv101.startSorting(), sortX, sortY, 30, 14,
                net.minecraft.client.gui.components.Tooltip.create(Component.literal("Sort shulkers by colour"))));
        }

        if (showStealDump) {
            addStealDumpButtons(screen, containerSlots);
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
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
        int topLeftX = this.leftPos;
        int aboveInventoryY = this.topPos - buttonSize - margin;

        if (aboveInventoryY >= margin) {
            return new int[][] {
                { topLeftX, aboveInventoryY },
                { topLeftX + buttonSize + gap, aboveInventoryY }
            };
        }

        // Fallback if the screen height is tiny: empty top-right space inside the Crafting header
        int insideHeaderX = this.leftPos + this.imageWidth - (buttonSize * 2) - gap - 7;
        int insideHeaderY = this.topPos + 5;
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
        int sideY = Math.max(margin, Math.min(this.topPos + 5, this.height - groupH - margin));
        int centeredX = Math.max(margin, Math.min(
            this.leftPos + (this.imageWidth - groupW) / 2, this.width - groupW - margin));

        int[] left = { this.leftPos - groupW - margin, sideY };
        int[] right = { this.leftPos + this.imageWidth + margin, sideY };
        int[] bottom = { centeredX, this.topPos + this.imageHeight + margin };
        int[] top = { centeredX, this.topPos - groupH - margin };
        int[][] candidates = { right, left, bottom, top };

        for (int[] candidate : candidates) {
            int candidateX = candidate[0];
            int candidateY = candidate[1];
            boolean inScreen = candidateX >= margin && candidateY >= margin
                && candidateX + groupW <= this.width - margin
                && candidateY + groupH <= this.height - margin;
            boolean overlapsScreen = candidateX < this.leftPos + this.imageWidth
                && candidateX + groupW > this.leftPos
                && candidateY < this.topPos + this.imageHeight
                && candidateY + groupH > this.topPos;
            if (inScreen && !overlapsScreen) {
                return createButtonPositions(count, buttonW, buttonH, gap, columns, candidateX, candidateY);
            }
        }

        // On very small viewports, use the title area rather than covering container slots.
        int fallbackColumns = Math.min(count, Math.max(1,
            (this.imageWidth - 2 * margin + gap) / (buttonW + gap)));
        int fallbackRows = (count + fallbackColumns - 1) / fallbackColumns;
        int fallbackW = fallbackColumns * buttonW + (fallbackColumns - 1) * gap;
        int fallbackH = fallbackRows * buttonH + (fallbackRows - 1) * gap;
        int fallbackX = Math.max(this.leftPos + margin, this.leftPos + this.imageWidth - fallbackW - margin);
        int fallbackY = Math.max(margin, Math.min(this.topPos + 2, this.height - fallbackH - margin));
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
    private static Button mouseOnly(Component label, Button.OnPress action,
                                          int x, int y, int width, int height,
                                          net.minecraft.client.gui.components.Tooltip tooltip) {
        return mouseOnly(label, action, x, y, width, height, tooltip, null);
    }

    @Unique
    private static Button mouseOnly(Component label, Button.OnPress action,
                                          int x, int y, int width, int height,
                                          net.minecraft.client.gui.components.Tooltip tooltip,
                                          BooleanSupplier hasData) {
        return com.example.addon.utils.MinecraftApiCompat.mouseOnlyButton(
            label, action, x, y, width, height, tooltip, hasData
        );
    }

    @Unique
    private void addStealDumpButtons(AbstractContainerScreen<?> screen, int containerSlots) {
        int buttonW, buttonH, buttonGap;

        if ((Object) this instanceof ContainerScreen) {
            buttonW = 14;
            buttonH = 14;
            buttonGap = 2;
        } else {
            buttonW = 20;
            buttonH = 20;
            buttonGap = 4;
        }
        int[][] positions = findButtonPositions(2, buttonW, buttonH, buttonGap, 2);

        this.addRenderableWidget(mouseOnly(Component.literal("S"),
            button -> {
                for (int i = 0; i < containerSlots; i++) {
                    if (screen.getMenu().getSlot(i).hasItem()) {
                        minecraft.gameMode.handleContainerInput(
                            screen.getMenu().containerId, i, 0,
                            ContainerInput.QUICK_MOVE, minecraft.player);
                    }
                }
            }, positions[0][0], positions[0][1], buttonW, buttonH,
            net.minecraft.client.gui.components.Tooltip.create(Component.literal("Steal all items from container"))));

        this.addRenderableWidget(mouseOnly(Component.literal("D"),
            button -> {
                for (int i = containerSlots; i < screen.getMenu().slots.size(); i++) {
                    if (screen.getMenu().getSlot(i).getItem().isEmpty()) continue;
                    minecraft.gameMode.handleContainerInput(
                        screen.getMenu().containerId, i, 0,
                        ContainerInput.QUICK_MOVE, minecraft.player);
                }
            }, positions[1][0], positions[1][1], buttonW, buttonH,
            net.minecraft.client.gui.components.Tooltip.create(Component.literal("Dump all inventory items into container"))));
    }
}