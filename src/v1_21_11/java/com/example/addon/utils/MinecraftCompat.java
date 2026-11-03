package com.example.addon.utils;

import com.example.addon.mixin.PlayerInventoryAccessor;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MinecraftCompat {
    private MinecraftCompat() {
    }

    public static int selectedSlot(PlayerInventory inventory) {
        return ((PlayerInventoryAccessor) (Object) inventory).tim$getSelectedSlot();
    }

    public static List<ItemStack> mainStacks(PlayerInventory inventory) {
        return ((PlayerInventoryAccessor) (Object) inventory).tim$getMain();
    }

    public static List<ItemStack> allPlayerStacks(PlayerEntity player) {
        List<ItemStack> stacks = new ArrayList<>(mainStacks(player.getInventory()));
        stacks.add(player.getOffHandStack());
        stacks.addAll(armorStacks(player));
        return stacks;
    }

    public static List<ItemStack> armorStacks(LivingEntity entity) {
        List<ItemStack> stacks = new ArrayList<>(4);
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            stacks.add(entity.getEquippedStack(slot));
        }
        return stacks;
    }

    public static List<ItemStack> armorStacks(MobEntity mob) {
        return armorStacks((LivingEntity) mob);
    }

    public static UUID ownerUuid(MobEntity mob) {
        if (mob instanceof Tameable tameable && tameable.getOwnerReference() != null) {
            return tameable.getOwnerReference().getUuid();
        }
        return null;
    }

    public static double previousX(Entity entity) {
        return entity.lastX;
    }

    public static double previousZ(Entity entity) {
        return entity.lastZ;
    }

    public static float tickProgress(RenderTickCounter counter, boolean ignoreFreeze) {
        return counter.getTickProgress(ignoreFreeze);
    }

    public static Vec3d position(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    public static UUID profileId(com.mojang.authlib.GameProfile profile) {
        return profile.id();
    }

    public static String profileName(com.mojang.authlib.GameProfile profile) {
        return profile.name();
    }

    public static net.minecraft.component.type.ProfileComponent profileComponent(com.mojang.authlib.GameProfile profile) {
        return net.minecraft.component.type.ProfileComponent.ofStatic(profile);
    }

    public static boolean isClient(World world) {
        return world.isClient();
    }

    public static SoundEvent itemBreakSound() {
        return SoundEvents.ENTITY_ITEM_BREAK.value();
    }
}
