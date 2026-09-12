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
    @Shadow protected int x;
    @Shadow protected int y;

    @Unique private ButtonWidget s1Button;
    @Unique private ButtonWidget s2Button;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // Reset buttons to prevent conflicts if switching between different screens
        s1Button = null;
        s2Button = null;

        Inventory101 inv101       = Modules.get().get(Inventory101.class);
        boolean      inv101Active = inv101 != null && inv101.isActive();

        if ((Object) this instanceof InventoryScreen && inv101Active) {
            int bx = this.x - 25;
            int by = this.y;

            s1Button = mouseOnly(Text.literal("S1"),
                btn -> inv101.startInvSort(1), bx, by, 20, 20,
                net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Sort to " + inv101.getPresetName(1))),
                () -> !inv101.isPresetEmpty(1));

            s2Button = mouseOnly(Text.literal("S2"),
                btn -> inv101.startInvSort(2), bx, by + 25, 20, 20,
                net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Sort to " + inv101.getPresetName(2))),
                () -> !inv101.isPresetEmpty(2));

            this.addDrawableChild(s1Button);
            this.addDrawableChild(s2Button);
            return;
        }

        HandledScreen<?> screen         = (HandledScreen<?>) (Object) this;
        int              containerSlots = screen.getScreenHandler().slots.size() - 36;

        // ── Inventory101 buttons ──────────────────────────────────────────
        if (inv101Active) {
            if ((Object) this instanceof ShulkerBoxScreen) {
                int bx = this.x - 25;
                int by = this.y;

                this.addDrawableChild(mouseOnly(Text.literal("S"),
                    btn -> inv101.toggleSaveMode(), bx, by, 20, 20,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Save Current Layout"))));
                by += 25;

                this.addDrawableChild(mouseOnly(Text.literal("1"),
                    btn -> {
                        if (!inv101.isSaveMode() && inv101.isPresetEmpty(1)) return;
                        inv101.handlePreset(1);
                    }, bx, by, 20, 20,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Load " + inv101.getPresetName(1))),
                    () -> !inv101.isPresetEmpty(1)));
                by += 25;

                this.addDrawableChild(mouseOnly(Text.literal("2"),
                    btn -> {
                        if (!inv101.isSaveMode() && inv101.isPresetEmpty(2)) return;
                        inv101.handlePreset(2);
                    }, bx, by, 20, 20,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Load " + inv101.getPresetName(2))),
                    () -> !inv101.isPresetEmpty(2)));
                by += 25;

                this.addDrawableChild(mouseOnly(Text.literal("C"),
                    btn -> inv101.clearPresets(), bx, by, 20, 20,
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Clear Presets"))));
                by += 25;

                if (inv101.isRegearButtonEnabled()) {
                    this.addDrawableChild(mouseOnly(Text.literal("G"),
                        btn -> inv101.startRegearing(), bx, by, 20, 20,
                        net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Equip armor and replenish essentials"))));
                    by += 25;
                }

                if (inv101.isReplenishButtonEnabled()) {
                    this.addDrawableChild(mouseOnly(Text.literal("R"),
                        btn -> inv101.startReplenishing(), bx, by, 20, 20,
                        net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Replenish whitelisted items from shulker"))));
                }

                return;
            }

            // ── CHEST SORT BUTTON: Inside top-right, to the left of S/D ──
            if ((Object) this instanceof GenericContainerScreen && inv101.isSortButtonEnabled()) {
                // Math: S/D take up 30px + 8px right padding = 38px from the right edge.
                // Sort is 30px wide + 2px gap = 32px. 
                // 38 + 32 = 70px total from the right edge.
                int bx = this.x + this.backgroundWidth - 70;
                int by = this.y + 2; // Matches S/D vertical alignment
                this.addDrawableChild(mouseOnly(Text.literal("Sort"),
                    btn -> inv101.startSorting(), bx, by, 30, 14, 
                    net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Sort shulkers by colour"))));
            }
        }

        // ── LootLens / DungeonAssistant Steal+Dump buttons ──────────────
        if (containerSlots <= 0) return;

        LootLens ll = Modules.get().get(LootLens.class);
        if (ll != null && ll.shouldShowStealDumpButtons()) {
            addStealDumpButtons(screen, containerSlots);
        } else {
            DungeonAssistant da = Modules.get().get(DungeonAssistant.class);
            if (da != null && da.isActive()) {
                addStealDumpButtons(screen, containerSlots);
            }
        }
    }

    /**
     * Dynamically reposition S1/S2 buttons every frame so they dodge the recipe book.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if ((Object) this instanceof InventoryScreen && s1Button != null && s2Button != null) {
            // Minecraft shifts the GUI exactly 177 pixels to the right when the recipe book opens.
            int defaultX = (this.width - this.backgroundWidth) / 2;
            boolean isRecipeBookOpen = this.x > defaultX + 50; 
            
            // If recipe book is open, move to the right side. Otherwise, left side.
            int bx = isRecipeBookOpen ? (this.x + this.backgroundWidth + 5) : (this.x - 25);
            int by = this.y;
            
            s1Button.setPosition(bx, by);
            s2Button.setPosition(bx, by + 25);
        }
    }

    /**
     * Creates a ButtonWidget that only reacts to mouse clicks.
     */
    private static ButtonWidget mouseOnly(Text label, ButtonWidget.PressAction action,
                                          int x, int y, int width, int height,
                                          net.minecraft.client.gui.tooltip.Tooltip tooltip) {
        return mouseOnly(label, action, x, y, width, height, tooltip, null);
    }

    private static ButtonWidget mouseOnly(Text label, ButtonWidget.PressAction action,
                                          int x, int y, int width, int height,
                                          net.minecraft.client.gui.tooltip.Tooltip tooltip,
                                          BooleanSupplier hasData) {
        ButtonWidget btn = new ButtonWidget(x, y, width, height, label, action,
                textSupplier -> textSupplier.get().copy()) {
            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                return false;
            }
            @Override
            public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
                return false;
            }
            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                if (hasData != null) {
                    boolean prev = this.active;
                    this.active = hasData.getAsBoolean();
                    super.renderWidget(context, mouseX, mouseY, delta);
                    this.active = prev;
                } else {
                    super.renderWidget(context, mouseX, mouseY, delta);
                }
            }
        };
        if (tooltip != null) btn.setTooltip(tooltip);
        return btn;
    }

    // ── Helper: Shared Steal/Dump buttons ────────────────────────────────────────────
    private void addStealDumpButtons(HandledScreen<?> screen, int containerSlots) {
        int buttonX, buttonY, buttonW, buttonH, buttonGap;

        if ((Object) this instanceof GenericContainerScreen) {
            // ── CHEST: Perfectly centered 14x14 buttons in the 18px title header (Right Side) ──
            buttonW = 14;
            buttonH = 14;
            buttonGap = 2;
            buttonX = this.x + this.backgroundWidth - 8 - buttonW - buttonGap - buttonW; // 8px right padding
            buttonY = this.y + 2; // Centers 14px button in 18px header (18 - 14 = 4 -> 2px top/bottom)
        } else {
            // ── OTHER CONTAINERS: Standard 20x20 buttons externally ──
            buttonW = 20;
            buttonH = 20;
            buttonGap = 4;

            int screenWidth = this.width;
            int rightEdge = this.x + this.backgroundWidth + 5 + buttonW;

            if (rightEdge <= screenWidth) {
                buttonX = this.x + this.backgroundWidth + 5;
                buttonY = this.y + 5;
            } else {
                int containerRows = (containerSlots + 8) / 9;
                buttonX = this.x + (this.backgroundWidth - (buttonW * 2 + buttonGap)) / 2;
                buttonY = this.y + containerRows * 18 + 2;
            }
        }

        // S = Steal: shift-click all items from container into player inventory
        this.addDrawableChild(mouseOnly(Text.literal("S"),
            button -> {
                for (int i = 0; i < containerSlots; i++) {
                    if (screen.getScreenHandler().getSlot(i).hasStack()) {
                        client.interactionManager.clickSlot(
                            screen.getScreenHandler().syncId, i, 0,
                            SlotActionType.QUICK_MOVE, client.player);
                    }
                }
            }, buttonX, buttonY, buttonW, buttonH,
            net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Steal all items from container"))));

        // D = Dump: shift-click all player inventory items into the container
        this.addDrawableChild(mouseOnly(Text.literal("D"),
            button -> {
                for (int i = containerSlots; i < screen.getScreenHandler().slots.size(); i++) {
                    if (screen.getScreenHandler().getSlot(i).getStack().isEmpty()) continue;
                    client.interactionManager.clickSlot(
                        screen.getScreenHandler().syncId, i, 0,
                        SlotActionType.QUICK_MOVE, client.player);
                }
            }, buttonX + buttonW + buttonGap, buttonY, buttonW, buttonH,
            net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Dump all inventory items into container"))));
    }
}