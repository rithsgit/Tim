package com.example.addon.modules;

import com.example.addon.Tim;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.PandaEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Set;

public class Threaturn extends Module {
    private static final Set<String> NEUTRAL_MOBS = Set.of(
        "minecraft:bee", "minecraft:cave_spider", "minecraft:dolphin", "minecraft:drowned",
        "minecraft:enderman", "minecraft:fox", "minecraft:goat", "minecraft:iron_golem",
        "minecraft:llama", "minecraft:nautilus", "minecraft:panda", "minecraft:piglin",
        "minecraft:polar_bear", "minecraft:pufferfish", "minecraft:spider", "minecraft:trader_llama",
        "minecraft:wolf", "minecraft:zombie_nautilus", "minecraft:zombified_piglin"
    );

    public enum WarningSound {
        WARDEN_ROAR("Warden Roar", SoundEvents.ENTITY_WARDEN_ROAR),
        ELDER_GUARDIAN("Elder Guardian", SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE),
        WITHER_SPAWN("Wither Spawn", SoundEvents.ENTITY_WITHER_SPAWN),
        GHAST_SCREAM("Ghast Scream", SoundEvents.ENTITY_GHAST_SCREAM),
        ENDERMAN_STARE("Enderman Stare", SoundEvents.ENTITY_ENDERMAN_STARE),
        RAID_HORN("Raid Horn", SoundEvents.EVENT_RAID_HORN.value());

        private final String title;
        private final SoundEvent soundEvent;

        WarningSound(String title, SoundEvent soundEvent) {
            this.title = title;
            this.soundEvent = soundEvent;
        }

        public SoundEvent getSound() { return soundEvent; }
        @Override public String toString() { return title; }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Filters");
    private final SettingGroup sgAudio = settings.createGroup("Audio");

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("radius").description("Horizontal scan radius around you.")
        .defaultValue(12.0).min(1.0).sliderMax(32.0).build());

    private final Setting<Double> maxVertical = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-vertical-distance").description("Maximum Y-level difference to prevent counting cave/roof mobs.")
        .defaultValue(5.0).min(1.0).sliderMax(16.0).build());

    private final Setting<Integer> clusterThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("cluster-threshold").description("Cluster of mobs needed to trigger disconnect.")
        .defaultValue(6).min(1).sliderMax(25).build());

    private final Setting<Integer> checkDelay = sgGeneral.add(new IntSetting.Builder()
        .name("check-delay-ticks").description("Ticks between entity scans to reduce lag.")
        .defaultValue(5).min(1).sliderMax(20).build());

    private final Setting<Boolean> toggleOff = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-toggle-off").description("Automatically disable Threaturn on disconnect.")
        .defaultValue(true).build());

    private final Setting<Boolean> onlyHostiles = sgFilters.add(new BoolSetting.Builder()
        .name("only-hostiles").description("Count monsters and neutral mobs showing aggression.")
        .defaultValue(false).build());

    private final Setting<Boolean> onlyTargeting = sgFilters.add(new BoolSetting.Builder()
        .name("only-targeting-you").description("Only count mobs targeting you; server targets may not be synced to the client.")
        .defaultValue(false).build());

    private final Setting<Boolean> ignoreNamed = sgFilters.add(new BoolSetting.Builder()
        .name("ignore-named").description("Ignore custom nametagged mobs.")
        .defaultValue(true).build());

    private final Setting<Boolean> ignoreTamed = sgFilters.add(new BoolSetting.Builder()
        .name("ignore-tamed").description("Ignore pets tamed by you.")
        .defaultValue(true).build());

    private final Setting<Set<EntityType<?>>> entityFilter = sgFilters.add(new EntityTypeListSetting.Builder()
        .name("entities").description("Specific entity types to count; neutral mobs must show aggression.")
        .defaultValue(new ObjectOpenHashSet<>()).build());

    private final Setting<Boolean> playSound = sgAudio.add(new BoolSetting.Builder()
        .name("play-warning-sound").description("Plays an aggressive warning sound upon cluster detection.")
        .defaultValue(true).build());

    private final Setting<WarningSound> soundChoice = sgAudio.add(new EnumSetting.Builder<WarningSound>()
        .name("warning-sound").description("Which sound to play.")
        .defaultValue(WarningSound.WARDEN_ROAR).visible(playSound::get).build());

    private final Setting<Double> soundVolume = sgAudio.add(new DoubleSetting.Builder()
        .name("sound-volume").description("Volume multiplier.")
        .defaultValue(1.0).min(0.0).max(2.0).sliderRange(0.0, 2.0).visible(playSound::get).build());

    private final Setting<Double> soundPitch = sgAudio.add(new DoubleSetting.Builder()
        .name("sound-pitch").description("Pitch of the audio cue.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).visible(playSound::get).build());

    private int ticksPassed = 0;
    private int currentNearbyCount = 0;

    public Threaturn() {
        super(Tim.CATEGORY, "threaturn", "Disconnects safely when a dangerous mob cluster is detected nearby.");
    }

    @Override
    public void onActivate() {
        ticksPassed = 0;
        currentNearbyCount = 0;
    }

    @Override
    public String getInfoString() {
        return String.format("%d/%d", currentNearbyCount, clusterThreshold.get());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (ticksPassed++ < checkDelay.get()) return;
        ticksPassed = 0;

        if (entityFilter.get().isEmpty() && !onlyHostiles.get()) {
            currentNearbyCount = 0;
            return;
        }

        double rSq = radius.get() * radius.get();
        double maxYDiff = maxVertical.get();
        int count = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entity.isAlive()) continue;

            if (Math.abs(entity.getY() - mc.player.getY()) > maxYDiff) continue;

            double dx = entity.getX() - mc.player.getX();
            double dz = entity.getZ() - mc.player.getZ();
            if ((dx * dx + dz * dz) > rSq) continue;

            if (ignoreNamed.get() && entity.hasCustomName()) continue;
            if (ignoreTamed.get() && entity instanceof TameableEntity tameable && tameable.isOwner(mc.player)) continue;

            boolean neutral = entity instanceof Angerable || NEUTRAL_MOBS.contains(Registries.ENTITY_TYPE.getId(entity.getType()).toString());
            if (neutral && !isAggressiveNeutral(entity)) continue;

            if (onlyTargeting.get()) {
                if (!(entity instanceof MobEntity mob) || mob.getTarget() != mc.player) {
                    continue;
                }
            }

            boolean isHostile = onlyHostiles.get() && (neutral || entity.getType().getSpawnGroup() == SpawnGroup.MONSTER);
            boolean isInList = entityFilter.get().contains(entity.getType());

            if (isHostile || isInList) {
                count++;
            }

            if (count >= clusterThreshold.get()) {
                currentNearbyCount = count;
                triggerEmergency(count);
                return;
            }
        }

        currentNearbyCount = count;
    }

    private static boolean isAggressiveNeutral(Entity entity) {
        if (entity instanceof EndermanEntity enderman && enderman.isAngry()) return true;
        if (entity instanceof BeeEntity bee && bee.hasAngerTime()) return true;
        if (entity instanceof WolfEntity wolf && wolf.hasAngerTime()) return true;
        if (entity instanceof PolarBearEntity bear && bear.isWarning()) return true;

        // Pandas override isAttacking() to mean the aggressive gene, not an active attack.
        return entity instanceof MobEntity mob && !(entity instanceof PandaEntity) && mob.isAttacking();
    }

    private void triggerEmergency(int detectedCount) {
        if (playSound.get() && mc.getSoundManager() != null) {
            mc.getSoundManager().play(
                PositionedSoundInstance.master(
                    soundChoice.get().getSound(),
                    soundPitch.get().floatValue(),
                    soundVolume.get().floatValue()
                )
            );
        }

        if (toggleOff.get() && isActive()) {
            toggle();
        }

        if (mc.player != null && mc.player.networkHandler != null) {
            String coords = String.format("%.0f, %.0f, %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
            Tim.LOG.warn("Threaturn triggered at [{}] with {} nearby entities.", coords, detectedCount);

            Text reason = Text.literal(String.format(
                "§c[Threaturn] §fEmergency disconnect triggered!\n\n" +
                "§7Reason: §fMob cluster threshold exceeded in area.\n" +
                "§7Detected: §c%d mobs §7within §e%.1fm §7(Y-span: §e%.1fm§7)\n" +
                "§7Coordinates: §b%s",
                detectedCount, radius.get(), maxVertical.get(), coords
            ));

            mc.player.networkHandler.getConnection().disconnect(reason);
        }
    }
}