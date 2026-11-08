package com.example.addon.utils;

import com.example.addon.mixin.PlayerInventoryAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.DeltaTracker;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class MinecraftCompat {
    private MinecraftCompat() {
    }

    public static int selectedSlot(Inventory inventory) {
        return ((PlayerInventoryAccessor) (Object) inventory).tim$getSelectedSlot();
    }

    public static List<ItemStack> mainStacks(Inventory inventory) {
        return ((PlayerInventoryAccessor) (Object) inventory).tim$getMain();
    }

    public static List<ItemStack> allPlayerStacks(Player player) {
        List<ItemStack> stacks = new ArrayList<>(mainStacks(player.getInventory()));
        stacks.add(player.getOffhandItem());
        stacks.addAll(armorStacks(player));
        return stacks;
    }

    public static List<ItemStack> armorStacks(LivingEntity entity) {
        List<ItemStack> stacks = new ArrayList<>(4);
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            stacks.add(entity.getItemBySlot(slot));
        }
        return stacks;
    }

    public static List<ItemStack> armorStacks(Mob mob) {
        return armorStacks((LivingEntity) mob);
    }

    public static UUID ownerUuid(Mob mob) {
        if (mob instanceof OwnableEntity tameable && tameable.getOwnerReference() != null) {
            return tameable.getOwnerReference().getUUID();
        }
        return null;
    }

    public static double previousX(Entity entity) {
        return entity.xo;
    }

    public static double previousZ(Entity entity) {
        return entity.zo;
    }

    public static float tickProgress(DeltaTracker counter, boolean ignoreFreeze) {
        return counter.getGameTimeDeltaPartialTick(ignoreFreeze);
    }

    public static Vec3 position(Entity entity) {
        return new Vec3(entity.getX(), entity.getY(), entity.getZ());
    }

    public static UUID profileId(com.mojang.authlib.GameProfile profile) {
        return profile.id();
    }

    public static String profileName(com.mojang.authlib.GameProfile profile) {
        return profile.name();
    }

    public static net.minecraft.world.item.component.ResolvableProfile profileComponent(com.mojang.authlib.GameProfile profile) {
        return net.minecraft.world.item.component.ResolvableProfile.createResolved(profile);
    }

    public static boolean isClient(Level world) {
        return world.isClientSide();
    }

    public static SoundEvent itemBreakSound() {
        return SoundEvents.ITEM_BREAK.value();
    }
}
