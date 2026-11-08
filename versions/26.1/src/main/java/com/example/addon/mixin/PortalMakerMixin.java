package com.example.addon.mixin;

import com.example.addon.modules.PortalMaker;
import com.example.addon.utils.MinecraftCompat;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlintAndSteelItem.class)
public abstract class PortalMakerMixin {

    @Inject(
        method = "useOn",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onUseOnBlock(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        PortalMaker portalMaker = Modules.get().get(PortalMaker.class);
        if (portalMaker == null || !portalMaker.isActive()) {
            return;
        }

        Level world = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockPos firePos = clickedPos.relative(context.getClickedFace());

        // Only proceed if we're trying to place fire inside what should be portal interior space
        boolean isPortalRelated = portalMaker.portalFramePositions.stream()
            .anyMatch(framePos -> {
                BlockPos up1 = framePos.above(1);
                BlockPos up2 = framePos.above(2);
                BlockPos up3 = framePos.above(3);

                return firePos.equals(up1) ||
                       firePos.equals(up2) ||
                       firePos.equals(up3);
            });

        if (!isPortalRelated) {
            return;
        }

        BlockState currentState = world.getBlockState(firePos);

        // Only place fire if the spot is air or replaceable (grass, vines, etc.)
        if (!currentState.isAir() && !currentState.canBeReplaced()) {
            return;
        }

        // Place the fire block
        BlockState fireState = Blocks.FIRE.defaultBlockState();
        world.setBlock(firePos, fireState, 11); // 11 = notify neighbors + send to clients

        // Play flint & steel sound for feedback (client + server)
        world.playSound(
            context.getPlayer(),
            firePos,
            SoundEvents.FLINTANDSTEEL_USE,
            SoundSource.BLOCKS,
            1.0F,
            world.getRandom().nextFloat() * 0.4F + 0.8F
        );

        // Only damage the item on the server (prevents double-damage desync)
        if (!MinecraftCompat.isClient(world) && context.getPlayer() != null) {
            EquipmentSlot slot = context.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            context.getItemInHand().hurtAndBreak(1, context.getPlayer(), slot);
        }

        // Mark as success and cancel vanilla logic
        cir.setReturnValue(InteractionResult.SUCCESS);
        cir.cancel();
    }
}