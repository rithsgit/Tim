package com.example.addon.mixin;

import com.example.addon.modules.Illushine;
import com.example.addon.modules.Inventory101;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {

    @Inject(
        method = "extractCrosshair",
        at = @At("HEAD"),
        cancellable = true
    )
    private void illushine$cancelCrosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        Illushine mod = Modules.get().get(Illushine.class);
        if (mod != null && mod.isActive() && mod.getCrosshairMode() != Illushine.CrosshairMode.None) {
            mod.drawCrosshair(context);
            ci.cancel();
        }
    }

    @Inject(method = "extractItemHotbar", at = @At("TAIL"))
    private void onRenderHotbar(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        Inventory101 inv101 = Modules.get().get(Inventory101.class);
        if (inv101 == null || !inv101.isActive()) return;

        int scaledWidth = client.getWindow().getGuiScaledWidth();
        int scaledHeight = client.getWindow().getGuiScaledHeight();
        
        int startX = scaledWidth / 2 - 91;
        int startY = scaledHeight - 22;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (Inventory101.isShulker(stack)) {
                ItemStack dominant = Inventory101.getDominantItem(stack);
                if (!dominant.isEmpty()) {
                    float scale = (float) inv101.getIconScale();
                    int slotX = startX + i * 20 + 3;
                    int slotY = startY + 3;
                    float centerOffset = (16.0f * (1.0f - scale)) / 2.0f;

                    com.example.addon.utils.MinecraftApiCompat.drawScaledItem(
                        context, dominant, slotX + centerOffset, slotY + centerOffset, scale
                    );
                }
            }
        }
    }
}