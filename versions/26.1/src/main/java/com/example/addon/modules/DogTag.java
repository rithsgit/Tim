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
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

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
        if (mc.level == null || mc.player == null) return;

        if (timer > 0) {
            timer--;
            return;
        }
        timer = scanInterval.get();

        Set<UUID> loadedUuids = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            loadedUuids.add(entity.getUUID());
        }
        notified.retainAll(loadedUuids);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob)) continue;
            if (ignoredEntities.get().contains(mob.getType())) continue;
            if (mc.player.distanceTo(mob) > range.get()) continue;
            if (notified.contains(mob.getUUID())) continue;

            boolean hasName = mob.hasCustomName() && mob.getCustomName() != null;
            UUID ownerUuid  = getOwnerUuid(mob);
            boolean isTamed = ownerUuid != null;

            if (isTamed && ignoreSelfOwned.get() && ownerUuid.equals(mc.player.getUUID())) {
                continue;
            }

            boolean shouldNotify = (hasName && scanNamed.get()) || (isTamed && scanUnnamedTamed.get());
            if (!shouldNotify) continue;

            notified.add(mob.getUUID());

            String mobType   = mob.getType().getDescription().getString();
            String customTag = hasName
                ? ChatFormatting.AQUA + "\"" + sanitizeName(mob.getCustomName().getString()) + "\"" + ChatFormatting.GRAY
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

    private UUID getOwnerUuid(Mob mob) {
        return com.example.addon.utils.MinecraftCompat.ownerUuid(mob);
    }

    private void reportWithOwner(String mobType, String customTag, UUID ownerUuid) {
        if (!resolveUsername.get()) {
            info("Tamed %s (%s) | Owner UUID: %s%s", mobType, customTag, ChatFormatting.YELLOW, ownerUuid.toString());
            return;
        }

        // 1. Check local cache first
        if (ownerCache.containsKey(ownerUuid)) {
            sendOwnerChat(mobType, customTag, ownerCache.get(ownerUuid), ownerUuid);
            return;
        }

        // 2. Check online tab list (instant)
        if (mc.getConnection() != null) {
            PlayerInfo entry = mc.getConnection().getPlayerInfo(ownerUuid);
            if (entry != null && entry.getProfile() != null && com.example.addon.utils.MinecraftCompat.profileName(entry.getProfile()) != null) {
                String name = com.example.addon.utils.MinecraftCompat.profileName(entry.getProfile());
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
        String ownerDisplay = ChatFormatting.GREEN + ownerName + ChatFormatting.GRAY;
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