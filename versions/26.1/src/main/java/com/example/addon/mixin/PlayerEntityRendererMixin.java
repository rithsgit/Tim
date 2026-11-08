package com.example.addon.mixin;

import com.example.addon.modules.Illushine;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void onScale(AvatarRenderState state, PoseStack matrices, CallbackInfo ci) {
        // SAFETY: Prevent crash when disconnecting
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().player == null) return;

        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        if (!(entity instanceof Player player)) return;

        Illushine illushine = Modules.get().get(Illushine.class);
        if (illushine == null || !illushine.isActive()) return;

        float scale = 1.0f;

        if (player.equals(Minecraft.getInstance().player)) {
            scale = (float) illushine.getPlayerScale();
        } 
        else if (illushine.getScaleOtherPlayers()) {
            scale = (float) illushine.getOtherPlayerScale();
        }

        if (scale != 1.0f) {
            matrices.scale(scale, scale, scale);
        }
    }
}