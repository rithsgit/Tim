package com.example.addon.modules;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.example.addon.Tim;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.util.Formatting;

public class DogTag extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilters = settings.createGroup("Filters");

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — General
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Range in blocks to scan for named or tamed mobs.")
        .defaultValue(128).min(1).sliderMax(256)
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("How often to scan for mobs (in ticks).")
        .defaultValue(10).min(1).sliderMax(100)
        .build()
    );

    private final Setting<Boolean> resolveUsername = sgGeneral.add(new BoolSetting.Builder()
        .name("resolve-username")
        .description("Resolves the owner's UUID to their Minecraft username via tablist or Mojang API.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showUuid = sgGeneral.add(new BoolSetting.Builder()
        .name("show-uuid")
        .description("Includes the owner's raw UUID alongside their username in chat.")
        .defaultValue(false)
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // Settings — Filters
    // ═══════════════════════════════════════════════════════════════════════════

    private final Setting<Boolean> scanNamed = sgFilters.add(new BoolSetting.Builder()
        .name("scan-named")
        .description("Notify when a mob with a custom nametag is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> scanUnnamedTamed = sgFilters.add(new BoolSetting.Builder()
        .name("scan-unnamed-tamed")
        .description("Notify when a tamed mob is found even if it doesn't have a nametag.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreSelfOwned = sgFilters.add(new BoolSetting.Builder()
        .name("ignore-self-owned")
        .description("Ignore tamed mobs that belong to you.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Set<EntityType<?>>> ignoredEntities = sgFilters.add(new EntityTypeListSetting.Builder()
        .name("ignored-entities")
        .description("Entities to ignore during scanning.")
        .defaultValue(new HashSet<>())
        .build()
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    private final Set<UUID>                 notified         = new HashSet<>();
    private final Map<UUID, String>         ownerCache       = new ConcurrentHashMap<>();
    private final Map<UUID, List<Runnable>> pendingCallbacks = new ConcurrentHashMap<>();
    private int timer = 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor & Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    public DogTag() {
        super(Tim.CATEGORY, "dog-tag", "Logs nearby named and tamed mobs along with their owner's username or UUID in chat.");
    }

    @Override
    public void onActivate() {
        notified.clear();
        pendingCallbacks.clear();
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        notified.clear();
        pendingCallbacks.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tick — Scanning
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (timer > 0) {
            timer--;
            return;
        }
        timer = scanInterval.get();

        Set<UUID> loadedUuids = new HashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            loadedUuids.add(entity.getUuid());
        }
        notified.retainAll(loadedUuids);

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof MobEntity mob)) continue;
            if (ignoredEntities.get().contains(mob.getType())) continue;
            if (mc.player.distanceTo(mob) > range.get()) continue;
            if (notified.contains(mob.getUuid())) continue;

            boolean hasName = mob.hasCustomName() && mob.getCustomName() != null;
            UUID ownerUuid  = getOwnerUuid(mob);
            boolean isTamed = ownerUuid != null;

            if (isTamed && ignoreSelfOwned.get() && ownerUuid.equals(mc.player.getUuid())) {
                continue;
            }

            boolean shouldNotify = (hasName && scanNamed.get()) || (isTamed && scanUnnamedTamed.get());
            if (!shouldNotify) continue;

            notified.add(mob.getUuid());

            String mobType   = mob.getType().getName().getString();
            String customTag = hasName
                ? Formatting.AQUA + "\"" + sanitizeName(mob.getCustomName().getString()) + "\"" + Formatting.GRAY
                : "Unnamed";

            if (isTamed) {
                reportWithOwner(mobType, customTag, ownerUuid);
            } else {
                info("Named %s: %s", mobType, customTag);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Coordinate Redaction & Owner Resolution
    // ═══════════════════════════════════════════════════════════════════════════

    private String sanitizeName(String input) {
        String cleaned = input.replaceAll("§.", "");
        // Redact any coordinate-like number sequences inside custom nametags
        return cleaned.replaceAll("-?\\d+[kKmM]?([\\s,]+-?\\d+[kKmM]?){1,2}", "XXXX");
    }

    private UUID getOwnerUuid(MobEntity mob) {
        if (mob instanceof Tameable tameable && tameable.getOwnerUuid() != null) {
            return tameable.getOwnerUuid();
        }
        if (mob instanceof TameableEntity tameableEntity && tameableEntity.getOwnerUuid() != null) {
            return tameableEntity.getOwnerUuid();
        }
        if (mob instanceof AbstractHorseEntity horse && horse.getOwnerUuid() != null) {
            return horse.getOwnerUuid();
        }
        return null;
    }

    private void reportWithOwner(String mobType, String customTag, UUID ownerUuid) {
        if (!resolveUsername.get()) {
            info("Tamed %s (%s) | Owner UUID: %s%s", mobType, customTag, Formatting.YELLOW, ownerUuid.toString());
            return;
        }

        // 1. Check local cache first
        if (ownerCache.containsKey(ownerUuid)) {
            sendOwnerChat(mobType, customTag, ownerCache.get(ownerUuid), ownerUuid);
            return;
        }

        // 2. Check online tab list (instant)
        if (mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(ownerUuid);
            if (entry != null && entry.getProfile() != null && entry.getProfile().getName() != null) {
                String name = entry.getProfile().getName();
                ownerCache.put(ownerUuid, name);
                sendOwnerChat(mobType, customTag, name, ownerUuid);
                return;
            }
        }

        // 3. Queue callback & query Mojang API asynchronously for offline players
        Runnable printTask = () -> sendOwnerChat(mobType, customTag, ownerCache.getOrDefault(ownerUuid, ownerUuid.toString()), ownerUuid);

        synchronized (pendingCallbacks) {
            if (pendingCallbacks.containsKey(ownerUuid)) {
                pendingCallbacks.get(ownerUuid).add(printTask);
                return;
            }
            List<Runnable> list = new ArrayList<>();
            list.add(printTask);
            pendingCallbacks.put(ownerUuid, list);
        }

        CompletableFuture.runAsync(() -> {
            String resolvedName = fetchMojangUsername(ownerUuid);
            ownerCache.put(ownerUuid, resolvedName);

            List<Runnable> callbacks;
            synchronized (pendingCallbacks) {
                callbacks = pendingCallbacks.remove(ownerUuid);
            }

            if (callbacks != null) {
                mc.execute(() -> callbacks.forEach(Runnable::run));
            }
        });
    }

    private void sendOwnerChat(String mobType, String customTag, String ownerName, UUID ownerUuid) {
        String ownerDisplay = Formatting.GREEN + ownerName + Formatting.GRAY;
        if (showUuid.get() && !ownerName.equals(ownerUuid.toString())) {
            ownerDisplay += " (" + ownerUuid.toString() + ")";
        }
        info("Tamed %s (%s) | Owner: %s", mobType, customTag, ownerDisplay);
    }

    private String fetchMojangUsername(UUID uuid) {
        try {
            String cleanUuid = uuid.toString().replace("-", "");
            URI uri = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + cleanUuid);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            if (conn.getResponseCode() == 200) {
                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("name")) {
                        return json.get("name").getAsString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return uuid.toString();
    }
}