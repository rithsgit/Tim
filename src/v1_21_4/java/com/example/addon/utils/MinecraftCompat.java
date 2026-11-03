package com.example.addon.utils;

import com.example.addon.mixin.PlayerInventoryAccessor;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

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
        player.getArmorItems().forEach(stacks::add);
        return stacks;
    }

    public static List<ItemStack> armorStacks(MobEntity mob) {
        List<ItemStack> stacks = new ArrayList<>();
        mob.getArmorItems().forEach(stacks::add);
        return stacks;
    }

    public static UUID ownerUuid(MobEntity mob) {
        if (mob instanceof TameableEntity tameable && tameable.getOwnerUuid() != null) {
            return tameable.getOwnerUuid();
        }
        if (mob instanceof AbstractHorseEntity horse) {
            return horse.getOwnerUuid();
        }
        return null;
    }

    public static double previousX(Entity entity) {
        return entity.prevX;
    }

    public static double previousZ(Entity entity) {
        return entity.prevZ;
    }

    public static float tickProgress(RenderTickCounter counter, boolean ignoreFreeze) {
        return counter.getTickDelta(ignoreFreeze);
    }

    public static Vec3d position(Entity entity) {
        return entity.getPos();
    }

    public static java.util.UUID profileId(com.mojang.authlib.GameProfile profile) {
        return profile.getId();
    }

    public static String profileName(com.mojang.authlib.GameProfile profile) {
        return profile.getName();
    }

    public static net.minecraft.component.type.ProfileComponent profileComponent(com.mojang.authlib.GameProfile profile) {
        return new net.minecraft.component.type.ProfileComponent(profile);
    }

    public static boolean isClient(net.minecraft.world.World world) {
        return world.isClient;
    }

    public static Vec3d cameraPosition(net.minecraft.client.render.Camera camera) {
        return camera.getPos();
    }

    public static SoundEvent itemBreakSound() {
        return SoundEvents.ENTITY_ITEM_BREAK;
    }
}
