package com.example.addon.modules;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.example.addon.Tim;
import com.example.addon.gui.WaypearlMessengerScreen;
import com.example.addon.utils.GlowingRegistry;
import com.example.addon.utils.Hotbar;
import com.example.addon.utils.MeteorCompat;
import com.example.addon.utils.WaypearlArmedPullPolicy;
import com.example.addon.utils.WaypearlFollowupPolicy;
import com.example.addon.utils.WaypearlDropRotation;
import com.example.addon.utils.WaypearlInteractionHand;
import com.example.addon.utils.WaypearlLocalMessenger;
import com.example.addon.utils.WaypearlMessengerMessages;
import com.example.addon.utils.WaypearlPathfinder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

public class Waypearl extends MouseAwareModule {
    private void sendPlayerMessage(Component message, boolean overlay) {
        if (overlay) mc.player.sendOverlayMessage(message);
        else mc.player.sendSystemMessage(message);
    }

    // ═══════════════════════════════════════════════════════════════
    // Enums
    // ═══════════════════════════════════════════════════════════════

    public enum PingSound {
        BeaconActivate   ("Beacon Activate",    SoundEvents.BEACON_ACTIVATE),
        BeaconDeactivate ("Beacon Deactivate",  SoundEvents.BEACON_DEACTIVATE),
        BellUse          ("Bell",               SoundEvents.BELL_BLOCK),
        ExperienceOrb    ("Experience Orb",     SoundEvents.EXPERIENCE_ORB_PICKUP),
        PlayerLevelUp    ("Level Up",           SoundEvents.PLAYER_LEVELUP),
        NoteBlockBell    ("Note Bell",          reg("block.note_block.bell")),
        NoteBlockChime   ("Note Chime",         reg("block.note_block.chime")),
        NoteBlockPling   ("Note Pling",         reg("block.note_block.pling")),
        EnderEye         ("Ender Eye",          SoundEvents.ENDER_EYE_LAUNCH),
        EndermanTeleport ("Enderman Teleport",  SoundEvents.ENDERMAN_TELEPORT);

        public final String label;
        public final SoundEvent sound;
        PingSound(String label, SoundEvent sound) { this.label = label; this.sound = sound; }
        @Override public String toString() { return label; }
        private static SoundEvent reg(String id) {
            return BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(id));
        }
    }

    public enum ModuleMode { Assistant, Requester }
    public enum PullOrder { DISCOVERY, NEAREST }
    public enum RenderMode { Default, PearlsOnly }

    private enum WalkState { IDLE, WALKING_TO, PULLING, WALKING_BACK, ABORTED }

    // ═══════════════════════════════════════════════════════════════
    // Constants & Helpers
    // ═══════════════════════════════════════════════════════════════

    private static final Identifier BEAM_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/beacon_beam.png");
    private static final Map<Integer, Vec3> BEAM_POS_CACHE = new ConcurrentHashMap<>();
    private static final Gson PEARL_CACHE_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PEARL_CACHE_PATH = FabricLoader.getInstance().getConfigDir()
        .resolve("tim")
        .resolve("waypearl-pearls.json");

    // ═══════════════════════════════════════════════════════════════
    // Setting Groups
    // ═══════════════════════════════════════════════════════════════

    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgColumns    = settings.createGroup("Bubble Columns");
    private final SettingGroup sgCap        = settings.createGroup("Cap Box");
    private final SettingGroup sgSound      = settings.createGroup("Sound");
    private final SettingGroup sgBot        = settings.createGroup("Bot Assistant");
    private final SettingGroup sgMessenger  = settings.createGroup("Messenger");
    private final SettingGroup sgWalker     = settings.createGroup("Walker");
    private final SettingGroup sgPearlsOnly = settings.createGroup("Pearls Only");
    private final SettingGroup sgDisconnect = settings.createGroup("Auto Disconnect");

    // ═════════════════════════════════════════════════════════════════════
    // Settings — General
    // ═════════════════════════════════════════════════════════════════════

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range").description("Detection radius in blocks.")
        .defaultValue(64).min(16).sliderMax(128).build());

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval").description("Ticks between bubble column background scans.")
        .defaultValue(40).min(10).sliderMax(200).build());

    private final Setting<RenderMode> renderMode = sgGeneral.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode").description("Default = bubble column beams. Pearls Only = entity outlines & soul sand beams.")
        .defaultValue(RenderMode.Default).build());

    private final Setting<Boolean> highlightOwnPearl = sgGeneral.add(new BoolSetting.Builder()
        .name("highlight-own-pearl").description("Use a distinct color for your own pearls.")
        .defaultValue(true).build());

    private final Setting<SettingColor> ownPearlColor = sgGeneral.add(new ColorSetting.Builder()
        .name("own-pearl-color").description("Color for your own pearl's outline in Default mode.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .visible(() -> renderMode.get() == RenderMode.Default && highlightOwnPearl.get()).build());

    // ═══════════════════════════════════════════════════════════════
    // Settings — Bubble Columns (Hidden if PearlsOnly)
    // ═══════════════════════════════════════════════════════════════

    private final Setting<Boolean> columnsEnabled = sgColumns.add(new BoolSetting.Builder()
        .name("highlight-columns").defaultValue(true)
        .visible(() -> renderMode.get() == RenderMode.Default).build());

    private final Setting<SettingColor> coreColor = sgColumns.add(new ColorSetting.Builder()
        .name("core-color").defaultValue(new SettingColor(180, 230, 255, 255))
        .visible(() -> renderMode.get() == RenderMode.Default && columnsEnabled.get()).build());

    private final Setting<SettingColor> glowColor = sgColumns.add(new ColorSetting.Builder()
        .name("glow-color").defaultValue(new SettingColor(0, 180, 255, 50))
        .visible(() -> renderMode.get() == RenderMode.Default && columnsEnabled.get()).build());

    private final Setting<Double> coreWidth = sgColumns.add(new DoubleSetting.Builder()
        .name("core-width").defaultValue(0.03).min(0.005).sliderMax(0.25)
        .visible(() -> renderMode.get() == RenderMode.Default && columnsEnabled.get()).build());

    private final Setting<Double> glowSpread = sgColumns.add(new DoubleSetting.Builder()
        .name("glow-spread").defaultValue(0.08).min(0.01).sliderMax(0.5)
        .visible(() -> renderMode.get() == RenderMode.Default && columnsEnabled.get()).build());

    private final Setting<Integer> glowLayers = sgColumns.add(new IntSetting.Builder()
        .name("glow-layers").defaultValue(4).min(1).sliderMax(8)
        .visible(() -> renderMode.get() == RenderMode.Default && columnsEnabled.get()).build());

    private final Setting<Integer> glowBaseAlpha = sgColumns.add(new IntSetting.Builder()
        .name("glow-base-alpha").defaultValue(50).min(4).sliderMax(150)
        .visible(() -> renderMode.get() == RenderMode.Default && columnsEnabled.get()).build());

    // ═══════════════════════════════════════════════════════════════
    // Settings — Cap Box (Hidden if PearlsOnly)
    // ═══════════════════════════════════════════════════════════════

    public enum CapPosition { NONE, BOTTOM, TOP, BOTH }

    private final Setting<CapPosition> capPosition = sgCap.add(new EnumSetting.Builder<CapPosition>()
        .name("cap-position").defaultValue(CapPosition.BOTTOM)
        .visible(() -> renderMode.get() == RenderMode.Default).build());

    private final Setting<SettingColor> capColor = sgCap.add(new ColorSetting.Builder()
        .name("cap-color").defaultValue(new SettingColor(0, 200, 255, 160))
        .visible(() -> renderMode.get() == RenderMode.Default && capPosition.get() != CapPosition.NONE).build());

    private final Setting<Double> capSize = sgCap.add(new DoubleSetting.Builder()
        .name("cap-size").defaultValue(0.4).min(0.05).sliderMax(2.0)
        .visible(() -> renderMode.get() == RenderMode.Default && capPosition.get() != CapPosition.NONE).build());

    private final Setting<Double> capThickness = sgCap.add(new DoubleSetting.Builder()
        .name("cap-thickness").defaultValue(0.04).min(0.01).sliderMax(0.5)
        .visible(() -> renderMode.get() == RenderMode.Default && capPosition.get() != CapPosition.NONE).build());

    private final Setting<ShapeMode> capShapeMode = sgCap.add(new EnumSetting.Builder<ShapeMode>()
        .name("cap-shape-mode").defaultValue(ShapeMode.Both)
        .visible(() -> renderMode.get() == RenderMode.Default && capPosition.get() != CapPosition.NONE).build());

    private final Setting<Boolean> capGlow = sgCap.add(new BoolSetting.Builder()
        .name("cap-glow").defaultValue(true)
        .visible(() -> renderMode.get() == RenderMode.Default && capPosition.get() != CapPosition.NONE).build());

    // ═══════════════════════════════════════════════════════════════
    // Settings — Sound
    // ═══════════════════════════════════════════════════════════════

    private final Setting<Boolean> soundEnabled = sgSound.add(new BoolSetting.Builder()
        .name("sound-ping").defaultValue(true).build());

    private final Setting<PingSound> pingSound = sgSound.add(new EnumSetting.Builder<PingSound>()
        .name("sound").defaultValue(PingSound.BeaconActivate).visible(soundEnabled::get).build());

    private final Setting<Double> soundVolume = sgSound.add(new DoubleSetting.Builder()
        .name("volume").defaultValue(1.0).min(0.1).sliderMax(2.0).visible(soundEnabled::get).build());

    private final Setting<Double> soundPitch = sgSound.add(new DoubleSetting.Builder()
        .name("pitch").defaultValue(1.8).min(0.5).sliderMax(2.0).visible(soundEnabled::get).build());

    // ═══════════════════════════════════════════════════════════════
    // Settings — Bot Assistant
    // ═══════════════════════════════════════════════════════════════

    private final Setting<ModuleMode> moduleMode = sgBot.add(new EnumSetting.Builder<ModuleMode>()
        .name("mode").defaultValue(ModuleMode.Requester).build());

    private final Setting<String> botUsername = sgBot.add(new StringSetting.Builder()
        .name("bot-username").defaultValue("").build());

    private final Setting<String> triggerPhrase = sgBot.add(new StringSetting.Builder()
        .name("trigger-phrase").defaultValue("tp,pull,pearl").build());

    private final Setting<List<String>> whitelist = sgBot.add(new StringListSetting.Builder()
        .name("whitelist").defaultValue(Collections.emptyList())
        .visible(() -> moduleMode.get() == ModuleMode.Assistant).build());

    private final Setting<Keybind> selfTriggerKey = sgBot.add(new KeybindSetting.Builder()
        .name("self-trigger-key").defaultValue(Keybind.none())
        .visible(() -> moduleMode.get() == ModuleMode.Requester).build());

    private final Setting<Integer> pullCooldown = sgBot.add(new IntSetting.Builder()
        .name("cooldown").defaultValue(5).min(1).sliderMax(30).build());

    private final Setting<PullOrder> pullOrder = sgBot.add(new EnumSetting.Builder<PullOrder>()
        .name("pull-order").defaultValue(PullOrder.DISCOVERY)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant).build());

    private final Setting<Boolean> notifyEnabled = sgBot.add(new BoolSetting.Builder()
        .name("notify-requester").defaultValue(true)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant).build());

    private final Setting<String> notifyMessage = sgBot.add(new StringSetting.Builder()
        .name("notify-message").defaultValue("Pulling your pearl now, {player}.")
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && notifyEnabled.get()).build());

    private final Setting<Boolean> messengerEnabled = sgMessenger.add(new BoolSetting.Builder()
        .name("messenger-enabled").description("Enables the local encrypted messenger.")
        .defaultValue(false).build());
    private final Setting<Boolean> messengerAutoPull = sgMessenger.add(new BoolSetting.Builder()
        .name("auto-pull").description("Automatically process direct requests from whitelisted players.")
        .defaultValue(true).visible(() -> messengerEnabled.get() && moduleMode.get() == ModuleMode.Assistant).build());
    private final Setting<Keybind> messengerOpenKey = sgMessenger.add(new KeybindSetting.Builder()
        .name("open-messenger-key").defaultValue(Keybind.none()).visible(messengerEnabled::get).build());
    private final Setting<Keybind> messengerPullKey = sgMessenger.add(new KeybindSetting.Builder()
        .name("messenger-pull-key").defaultValue(Keybind.none())
        .visible(() -> messengerEnabled.get() && moduleMode.get() == ModuleMode.Requester).build());
    private final Setting<String> messengerQuickMessage = sgMessenger.add(new StringSetting.Builder()
        .name("quick-message").description("Custom message used by the quick-message hotkey.")
        .defaultValue("pull please").visible(messengerEnabled::get).build());
    private final Setting<Keybind> messengerQuickKey = sgMessenger.add(new KeybindSetting.Builder()
        .name("quick-message-key").defaultValue(Keybind.none()).visible(messengerEnabled::get).build());
    private final Setting<List<String>> messengerPresets = sgMessenger.add(new StringListSetting.Builder()
        .name("message-presets").description("Custom messages displayed as buttons in the messenger.")
        .defaultValue(List.of("pull please", "Thank you!")).visible(messengerEnabled::get).build());

    private final Setting<Boolean> replacePearl = sgBot.add(new BoolSetting.Builder()
        .name("drop-replacement-pearl").description("Drop one pearl for the requester after a confirmed pull.")
        .defaultValue(true).visible(() -> moduleMode.get() == ModuleMode.Assistant).build());
    private final Setting<Boolean> pearlReminder = sgBot.add(new BoolSetting.Builder()
        .name("replacement-reminder").description("Remind a requester when no new owned stasis pearl is detected.")
        .defaultValue(true).visible(() -> moduleMode.get() == ModuleMode.Assistant).build());
    private final Setting<Integer> pearlReminderDelay = sgBot.add(new IntSetting.Builder()
        .name("reminder-delay-seconds").description("Seconds to wait after a confirmed pull before reminding.")
        .defaultValue(120).min(10).sliderMax(600)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && pearlReminder.get()).build());

    // ═══════════════════════════════════════════════════════════════
    // Settings — Walker
    // ═══════════════════════════════════════════════════════════════

    private final Setting<Boolean> walkerEnabled = sgWalker.add(new BoolSetting.Builder()
        .name("walker-enabled").defaultValue(true)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant).build());

    private final Setting<Double> interactReach = sgWalker.add(new DoubleSetting.Builder()
        .name("interact-reach").defaultValue(3.5).min(1.0).sliderMax(5.0)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    private final Setting<Double> slowZoneRadius = sgWalker.add(new DoubleSetting.Builder()
        .name("slow-zone-radius").defaultValue(6.0).min(2.0).sliderMax(16.0)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    private final Setting<Double> waypointSpacing = sgWalker.add(new DoubleSetting.Builder()
        .name("waypoint-spacing").description("Controls waypoint arrival tolerance without skipping path corners.")
        .defaultValue(6.0).min(2.0).sliderMax(20.0)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    private final Setting<Integer> walkTimeoutTicks = sgWalker.add(new IntSetting.Builder()
        .name("walk-timeout").defaultValue(200).min(40).sliderMax(600)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    private final Setting<Integer> returnTimeoutTicks = sgWalker.add(new IntSetting.Builder()
        .name("return-timeout").defaultValue(200).min(40).sliderMax(600)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    private final Setting<Double> returnTolerance = sgWalker.add(new DoubleSetting.Builder()
        .name("return-tolerance").defaultValue(1.0).min(0.3).sliderMax(5.0)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    private final Setting<Integer> stuckThresholdTicks = sgWalker.add(new IntSetting.Builder()
        .name("stuck-threshold").defaultValue(30).min(10).sliderMax(100)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    private final Setting<Double> stuckMovementMin = sgWalker.add(new DoubleSetting.Builder()
        .name("stuck-min-movement").defaultValue(0.02).min(0.005).sliderMax(0.2)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    private final Setting<Double> voidAbortY = sgWalker.add(new DoubleSetting.Builder()
        .name("void-abort-y").defaultValue(0.0).min(-64.0).sliderMax(64.0)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    private final Setting<Boolean> returnAfterPull = sgWalker.add(new BoolSetting.Builder()
        .name("return-after-pull").defaultValue(true)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    private final Setting<Boolean> walkerSprint = sgWalker.add(new BoolSetting.Builder()
        .name("sprint").defaultValue(true)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    private final Setting<Boolean> walkerJump = sgWalker.add(new BoolSetting.Builder()
        .name("auto-jump").defaultValue(true)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    private final Setting<Integer> jumpAttemptTicks = sgWalker.add(new IntSetting.Builder()
        .name("jump-attempt-ticks").defaultValue(8).min(2).sliderMax(30)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get() && walkerJump.get()).build());

    private final Setting<Integer> jumpCooldownTicks = sgWalker.add(new IntSetting.Builder()
        .name("jump-cooldown").defaultValue(12).min(4).sliderMax(40)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get() && walkerJump.get()).build());

    private final Setting<Boolean> abortOnLowHealth = sgWalker.add(new BoolSetting.Builder()
        .name("abort-on-low-health").defaultValue(true)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant).build());

    private final Setting<Integer> lowHealthThreshold = sgWalker.add(new IntSetting.Builder()
        .name("low-health-threshold").defaultValue(4).min(1).sliderMax(10)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get() && abortOnLowHealth.get()).build());

    private final Setting<Boolean> abortOnFire = sgWalker.add(new BoolSetting.Builder()
        .name("abort-on-fire").defaultValue(false)
        .visible(() -> moduleMode.get() == ModuleMode.Assistant && walkerEnabled.get()).build());

    // ═══════════════════════════════════════════════════════════════
    // Settings — Pearls Only (Hidden if Default)
    // ═══════════════════════════════════════════════════════════════

    private final Setting<Boolean> poOutlineEnabled = sgPearlsOnly.add(new BoolSetting.Builder()
        .name("outline").description("Renders an outline around the pearl entity.")
        .defaultValue(true).visible(() -> renderMode.get() == RenderMode.PearlsOnly).build());

    private final Setting<SettingColor> poOutlineColor = sgPearlsOnly.add(new ColorSetting.Builder()
        .name("outline-color").defaultValue(new SettingColor(0, 200, 255, 255))
        .visible(() -> renderMode.get() == RenderMode.PearlsOnly && poOutlineEnabled.get()).build());

    private final Setting<Integer> poGlowStrength = sgPearlsOnly.add(new IntSetting.Builder()
        .name("glow-strength").description("Brightens the outline color channels (1-8).")
        .defaultValue(3).min(1).max(8).sliderMax(8)
        .visible(() -> renderMode.get() == RenderMode.PearlsOnly && poOutlineEnabled.get()).build());

    private final Setting<Boolean> poOwnOutlineEnabled = sgPearlsOnly.add(new BoolSetting.Builder()
        .name("own-outline").description("Renders an outline around your own pearl.")
        .defaultValue(true).visible(() -> renderMode.get() == RenderMode.PearlsOnly && highlightOwnPearl.get()).build());

    private final Setting<SettingColor> poOwnOutlineColor = sgPearlsOnly.add(new ColorSetting.Builder()
        .name("own-outline-color").defaultValue(new SettingColor(255, 255, 0, 255))
        .visible(() -> renderMode.get() == RenderMode.PearlsOnly && poOwnOutlineEnabled.get()).build());

    private final Setting<Integer> poOwnGlowStrength = sgPearlsOnly.add(new IntSetting.Builder()
        .name("own-glow-strength").defaultValue(3).min(1).max(8).sliderMax(8)
        .visible(() -> renderMode.get() == RenderMode.PearlsOnly && poOwnOutlineEnabled.get()).build());

    private final Setting<Boolean> poBeamEnabled = sgPearlsOnly.add(new BoolSetting.Builder()
        .name("beam").description("Renders a beacon beam down to the soul sand.")
        .defaultValue(true).visible(() -> renderMode.get() == RenderMode.PearlsOnly).build());

    private final Setting<SettingColor> poBeamColor = sgPearlsOnly.add(new ColorSetting.Builder()
        .name("beam-color").defaultValue(new SettingColor(255, 140, 0, 200))
        .visible(() -> renderMode.get() == RenderMode.PearlsOnly && poBeamEnabled.get()).build());

    private final Setting<Double> poBeamInnerRadius = sgPearlsOnly.add(new DoubleSetting.Builder()
        .name("beam-inner-radius").defaultValue(0.1).min(0.01).sliderMax(1.0)
        .visible(() -> renderMode.get() == RenderMode.PearlsOnly && poBeamEnabled.get()).build());

    private final Setting<Double> poBeamOuterRadius = sgPearlsOnly.add(new DoubleSetting.Builder()
        .name("beam-glow-radius").defaultValue(0.175).min(0.01).sliderMax(2.0)
        .visible(() -> renderMode.get() == RenderMode.PearlsOnly && poBeamEnabled.get()).build());

    private final Setting<Integer> poBeamHeight = sgPearlsOnly.add(new IntSetting.Builder()
        .name("beam-height").description("How high up the beacon beam shoots.")
        .defaultValue(64).min(1).max(319).sliderMax(319)
        .visible(() -> renderMode.get() == RenderMode.PearlsOnly && poBeamEnabled.get()).build());

    private final Setting<Boolean> poOwnBeamEnabled = sgPearlsOnly.add(new BoolSetting.Builder()
        .name("own-beam").description("Renders a beacon beam for your own pearl.")
        .defaultValue(true).visible(() -> renderMode.get() == RenderMode.PearlsOnly && highlightOwnPearl.get()).build());

    private final Setting<SettingColor> poOwnBeamColor = sgPearlsOnly.add(new ColorSetting.Builder()
        .name("own-beam-color").defaultValue(new SettingColor(0, 255, 0, 200))
        .visible(() -> renderMode.get() == RenderMode.PearlsOnly && poOwnBeamEnabled.get()).build());

    private final Setting<Double> poOwnBeamInnerRadius = sgPearlsOnly.add(new DoubleSetting.Builder()
        .name("own-beam-inner-radius").defaultValue(0.1).min(0.01).sliderMax(1.0)
        .visible(() -> renderMode.get() == RenderMode.PearlsOnly && poOwnBeamEnabled.get()).build());

    private final Setting<Double> poOwnBeamOuterRadius = sgPearlsOnly.add(new DoubleSetting.Builder()
        .name("own-beam-glow-radius").defaultValue(0.175).min(0.01).sliderMax(2.0)
        .visible(() -> renderMode.get() == RenderMode.PearlsOnly && poOwnBeamEnabled.get()).build());

    private final Setting<Integer> poOwnBeamHeight = sgPearlsOnly.add(new IntSetting.Builder()
        .name("own-beam-height").defaultValue(64).min(1).max(319).sliderMax(319)
        .visible(() -> renderMode.get() == RenderMode.PearlsOnly && poOwnBeamEnabled.get()).build());

    // ═══════════════════════════════════════════════════════════════
    // Settings — Auto Disconnect
    // ═══════════════════════════════════════════════════════════════

    private final Setting<Boolean> disconnectEnabled = sgDisconnect.add(new BoolSetting.Builder()
        .name("disconnect-enabled")
        .description("Disconnects when another player enters render distance.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> ignoreFriendsOnDisconnect = sgDisconnect.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Doesn't disconnect if the entering player is a friend (checked via NeighbourhoodWatch).")
        .defaultValue(true)
        .visible(disconnectEnabled::get)
        .build());

    private final Setting<Boolean> ignoreBotUser = sgDisconnect.add(new BoolSetting.Builder()
        .name("ignore-bot-user")
        .description("Doesn't trigger if the entering player is the configured bot username.")
        .defaultValue(true)
        .visible(disconnectEnabled::get)
        .build());

    private final Setting<String> disconnectUser = sgDisconnect.add(new StringSetting.Builder()
        .name("disconnect-notify-user")
        .description("Username to notify before disconnecting. If offline, the bot leaves immediately.")
        .defaultValue("")
        .visible(() -> disconnectEnabled.get() && moduleMode.get() == ModuleMode.Assistant)
        .build());

    private final Setting<String> disconnectMessage = sgDisconnect.add(new StringSetting.Builder()
        .name("disconnect-message")
        .description("Message sent to the notify user. Use {player} for the triggering player's name.")
        .defaultValue("Disconnecting — {player} entered render distance.")
        .visible(() -> disconnectEnabled.get() && moduleMode.get() == ModuleMode.Assistant)
        .build());

    private final Setting<Integer> disconnectAntiSpam = sgDisconnect.add(new IntSetting.Builder()
        .name("anti-spam-interval")
        .description("Minimum seconds between outgoing disconnect notifications to prevent chat spam.")
        .defaultValue(5)
        .min(1)
        .sliderMax(60)
        .visible(() -> disconnectEnabled.get() && moduleMode.get() == ModuleMode.Assistant)
        .build());

    // ═══════════════════════════════════════════════════════════════
    // Pearl memory record
    // ═══════════════════════════════════════════════════════════════

    private record PearlRecord(int entityId, UUID uuid, String server, String dimension, String owner,
                               BlockPos pearlPos, BlockPos trapdoor, long discoveredMs) {}

    private static final class PullFollowup {
        final PearlRecord pearl;
        final String requester;
        final boolean direct;
        final Set<UUID> existingPearls;
        boolean interactionSent;
        boolean dropPending;
        final WaypearlDropRotation dropRotation = new WaypearlDropRotation();
        boolean reminderPending;
        long confirmationDeadline;
        long dropDeadline;
        long reminderDeadline;

        PullFollowup(PearlRecord pearl, String requester, boolean direct, Set<UUID> existingPearls) {
            this.pearl = pearl;
            this.requester = requester;
            this.direct = direct;
            this.existingPearls = existingPearls;
        }
    }

    private record FollowupMessage(String server, String requester, String text) {}
    private record ArmedPull(String requester, String server, String dimension, long connectionId) {}

    private static final class PearlCache {
        int version = 1;
        List<PearlCacheEntry> pearls = new ArrayList<>();
    }

    private static final class PearlCacheEntry {
        String server;
        String dimension;
        String uuid;
        String owner;
        int pearlX;
        int pearlY;
        int pearlZ;
        boolean hasTrapdoor;
        int trapdoorX;
        int trapdoorY;
        int trapdoorZ;
        long discoveredMs;
    }

    // ═══════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════

    private final AtomicReference<Map<String, Vec3[]>> columnLines = new AtomicReference<>(Collections.emptyMap());
    private final LinkedHashMap<String, PearlRecord> pearlMemory = new LinkedHashMap<>();
    private final Set<Integer> seenPearlIds = Collections.synchronizedSet(new HashSet<>());

    private final AtomicBoolean scanPending   = new AtomicBoolean(false);
    private final AtomicBoolean pingQueued    = new AtomicBoolean(false);
    private final AtomicBoolean pullQueued    = new AtomicBoolean(false);
    private final AtomicLong    lastTriggerMs = new AtomicLong(0L);

    private volatile String pendingNotifyTarget = null;
    private boolean pendingStrictOwner;
    private ArmedPull armedPull;
    private PullFollowup activePull;
    private final List<PullFollowup> followups = new ArrayList<>();
    private final Deque<FollowupMessage> followupMessages = new ArrayDeque<>();
    private long lastFollowupMessageMs;
    private final Deque<String> messengerHistory = new ArrayDeque<>();
    private WaypearlLocalMessenger messengerTransport;
    private ModuleMode messengerTransportMode;
    private volatile Set<String> messengerAllowed = Set.of();
    private String messengerStatus = "Disconnected";
    private boolean wasMessengerOpenPressed, wasMessengerPullPressed, wasMessengerQuickPressed;
    private boolean wasSelfTriggerPressed = false;
    private int tickCounter = 0;

    private WalkState          walkState    = WalkState.IDLE;
    private BlockPos           walkTarget   = null;
    private Level              walkWorld;
    private UUID               walkPearlId;
    private boolean            walkInteractionPending;
    private long               walkGeneration;
    private Vec3              idlePosition = null;
    private final Deque<Vec3> waypoints    = new ArrayDeque<>();
    private int                walkTicks    = 0;
    private Vec3              lastPos      = null;
    private int                stuckTicks   = 0;
    private int                jumpCooldown = 0;
    
    private final Set<Integer> trackedGlowIds = Collections.synchronizedSet(new HashSet<>());

    private volatile long lastDisconnectMsgMs = 0L;
    private volatile boolean disconnectArmed  = true;

    // ═══════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════

    public Waypearl() {
        super(Tim.CATEGORY, "Waypearl", "Detects nearby stasis pearls sitting above bubble columns.");
        runInMainMenu = true;
    }

    public int getRange() { return range.get(); }
    public Set<Integer> getSeenPearlIds() { return seenPearlIds; }

    private Map<String, Vec3[]> getColumnLines() {
        return columnLines.get();
    }

    @Override
    public void onActivate() {
        armedPull = null;
        activePull = null;
        followups.clear();
        followupMessages.clear();
        lastFollowupMessageMs = 0;
        columnLines.set(Collections.emptyMap());
        loadPearlCache();
        seenPearlIds.clear();
        pingQueued.set(false);
        pullQueued.set(false);
        scanPending.set(false);
        pendingNotifyTarget = null;
        pendingStrictOwner = false;
        synchronized (messengerHistory) { messengerHistory.clear(); }
        wasMessengerOpenPressed = wasMessengerPullPressed = wasMessengerQuickPressed = false;
        tickCounter = 0;
        resetWalker();
        wasSelfTriggerPressed = false;

        lastDisconnectMsgMs = 0L;
        disconnectArmed = true;

        for (int id : trackedGlowIds) GlowingRegistry.remove(id);
        trackedGlowIds.clear();
        BEAM_POS_CACHE.clear();
    }

    @Override
    public void onDeactivate() {
        activePull = null;
        followups.clear();
        followupMessages.clear();
        stopMessenger();
        columnLines.set(Collections.emptyMap());
        pingQueued.set(false);
        pullQueued.set(false);
        scanPending.set(false);
        stopMovement();
        resetWalker();

        lastDisconnectMsgMs = 0L;
        disconnectArmed = true;

        for (int id : trackedGlowIds) GlowingRegistry.remove(id);
        trackedGlowIds.clear();
        BEAM_POS_CACHE.clear();
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        activePull = null;
        followups.clear();
        followupMessages.clear();
        if (!messengerRequester()) stopMessenger();
        disconnectArmed = true;
        lastDisconnectMsgMs = 0L;
        if (!messengerRequester()) synchronized (messengerHistory) { messengerHistory.clear(); }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        activePull = null;
        followups.clear();
        followupMessages.clear();
        pullQueued.set(false);
        pendingNotifyTarget = null;
        pendingStrictOwner = false;
        columnLines.set(Collections.emptyMap());
        seenPearlIds.clear();
        pingQueued.set(false);
        stopMovement();
        resetWalker();
        if (!messengerRequester()) stopMessenger();
    }

    @EventHandler
    private void onMenuKey(KeyInputEvent event) {
        if (mc.level == null && mc.screen instanceof TitleScreen && messengerAvailable()
            && event.action == KeyAction.Press
            && messengerOpenKey.get().matches(true, MeteorCompat.key(event), MeteorCompat.modifiers(event))) {
            mc.setScreen(new WaypearlMessengerScreen(GuiThemes.get(), this));
            event.cancel();
        }
    }

    @Override
    protected boolean onMouseButton(int button, KeyAction action) {
        if (mc.level == null && mc.screen instanceof TitleScreen && messengerAvailable()
            && action == KeyAction.Press && messengerOpenKey.get().matches(false, button, 0)) {
            mc.setScreen(new WaypearlMessengerScreen(GuiThemes.get(), this));
            return true;
        }
        return false;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String raw = event.getMessage().getString().trim();
        if (moduleMode.get() != ModuleMode.Assistant) return;

        if (raw.startsWith("<")) {
            int closeAngle = raw.indexOf('>');
            if (closeAngle >= 2) {
                evaluateTrigger(raw.substring(1, closeAngle), raw.substring(closeAngle + 1).trim(), false);
                return;
            }
        }
        if (raw.startsWith("[") && raw.contains("->") && raw.contains("]:")) {
            int arrowIdx = raw.indexOf("->");
            int bracketEnd = raw.indexOf("]:");
            if (arrowIdx > 1 && bracketEnd > arrowIdx) {
                evaluateTrigger(raw.substring(1, arrowIdx).trim(), raw.substring(bracketEnd + 2).trim(), true);
                return;
            }
        }
        String tag3 = " whispers to you: ";
        int idx3 = raw.indexOf(tag3);
        if (idx3 > 0) {
            evaluateTrigger(raw.substring(0, idx3).trim(), raw.substring(idx3 + tag3.length()).trim(), true);
            return;
        }
        String tag4 = " whispers: ";
        int idx4 = raw.indexOf(tag4);
        if (idx4 > 0) {
            evaluateTrigger(raw.substring(0, idx4).trim(), raw.substring(idx4 + tag4.length()).trim(), true);
        }
    }

    private void receiveMessenger(String sender, WaypearlLocalMessenger.MessageType type, String message) {
        if (!messengerAvailable()) return;
        if (!messengerRequester() && (mc.level == null || mc.player == null)) return;
        if (moduleMode.get() == ModuleMode.Assistant && !isMessengerPeerAllowed(sender)) return;
        recordMessenger((messengerRequester() ? "Assistant" : sender) + ": " + message);
        if (messengerRequester() && mc.player != null) {
            sendPlayerMessage(Component.literal("[Waypearl Messenger] " + message), false);
        }
        if (messengerRequester()) return;
        if (type == WaypearlLocalMessenger.MessageType.CANCEL) {
            if (armedPull != null && armedPull.requester().equalsIgnoreCase(sender)) {
                armedPull = null;
                sendMessenger(sender, "Armed pull cancelled.");
            } else sendMessenger(sender, "No armed pull to cancel.");
            return;
        }
        if (type == WaypearlLocalMessenger.MessageType.ARM) {
            armPull(sender);
            return;
        }
        boolean pull = type == WaypearlLocalMessenger.MessageType.PULL;
        if (moduleMode.get() == ModuleMode.Assistant && !messengerAutoPull.get()
            && (pull || matchesTrigger(message))) {
            sendMessenger(sender, "Pull unavailable: assistant auto-pull is disabled.");
            return;
        }
        if (moduleMode.get() == ModuleMode.Assistant && messengerAutoPull.get()
            && (pull || matchesTrigger(message))) {
            if (mc.level == null || mc.level.dimension() == Level.NETHER) {
                warning("Messenger pull unavailable in the Nether.");
                sendMessenger(sender, "Pull unavailable: assistant is in the Nether.");
            } else if (!isPlayerOnline(sender)) {
                sendMessenger(sender, "You are not online on this server. Use Arm Pull for Server Join instead.");
            } else if (armedPull != null || pullQueued.get() || activePull != null || (walkState != WalkState.IDLE && walkState != WalkState.ABORTED)) {
                warning("Messenger pull ignored: assistant is busy.");
                sendMessenger(sender, "Pull unavailable: assistant is busy.");
            } else {
                pendingNotifyTarget = sender;
                pendingStrictOwner = true;
                pullQueued.set(true);
            }
        }
    }

    private void armPull(String sender) {
        if (!messengerAutoPull.get()) {
            sendMessenger(sender, "Cannot arm: assistant auto-pull is disabled.");
        } else if (mc.level.dimension() == Level.NETHER) {
            sendMessenger(sender, "Cannot arm: assistant is in the Nether.");
        } else if (isPlayerOnline(sender)) {
            sendMessenger(sender, "Cannot arm: you are already online on the assistant's server. Use Request Pearl Pull instead.");
        } else if (armedPull != null || pullQueued.get() || activePull != null
            || (walkState != WalkState.IDLE && walkState != WalkState.ABORTED)) {
            sendMessenger(sender, "Cannot arm: assistant is already busy or waiting.");
        } else {
            armedPull = new ArmedPull(sender, currentServerKey(), currentDimensionKey(), messengerTransport.connectionId());
            sendMessenger(sender, WaypearlMessengerMessages.ARMED_CONFIRMATION);
        }
    }

    private boolean matchesTrigger(String message) {
        for (String phrase : triggerPhrase.get().split(",")) {
            String keyword = phrase.trim();
            String normalized = message.toLowerCase(Locale.ROOT);
            String trigger = keyword.toLowerCase(Locale.ROOT);
            if (!keyword.isEmpty() && (normalized.equals(trigger) || normalized.startsWith(trigger + " "))) return true;
        }
        return false;
    }

    private boolean isMessengerPeerAllowed(String peer) {
        if (peer == null || !peer.matches("[A-Za-z0-9_]{1,16}")) return false;
        if (moduleMode.get() == ModuleMode.Requester) return peer.equalsIgnoreCase(botUsername.get().trim());
        return whitelist.get().stream().anyMatch(name -> name.equalsIgnoreCase(peer));
    }

    private void recordMessenger(String message) {
        synchronized (messengerHistory) {
            if (messengerHistory.size() >= 8) messengerHistory.removeFirst();
            messengerHistory.addLast(message);
        }
        mc.execute(() -> {
            if (mc.screen instanceof WaypearlMessengerScreen screen) screen.refreshMessages();
        });
    }

    public List<String> messengerMessages() {
        synchronized (messengerHistory) {
            return List.copyOf(messengerHistory);
        }
    }
    public List<String> messengerPresets() { return List.copyOf(messengerPresets.get()); }
    public String messengerPeer() {
        if (messengerRequester()) return botUsername.get().trim();
        return messengerTransport == null ? "" : messengerTransport.peer();
    }
    public boolean messengerAvailable() { return isActive() && messengerEnabled.get(); }
    public boolean messengerRequester() { return moduleMode.get() == ModuleMode.Requester; }

    public String messengerStatus() { return messengerStatus; }
    public String messengerFingerprint() { return messengerTransport == null ? "" : messengerTransport.fingerprint(); }

    private void refreshMessengerAllowed() {
        Set<String> allowed = new HashSet<>();
        for (String name : whitelist.get()) allowed.add(name.toLowerCase(Locale.ROOT));
        messengerAllowed = Set.copyOf(allowed);
    }

    private WaypearlLocalMessenger createMessenger() {
        final WaypearlLocalMessenger[] holder = new WaypearlLocalMessenger[1];
        holder[0] = new WaypearlLocalMessenger(FabricLoader.getInstance().getConfigDir()
            .resolve("tim").resolve("waypearl-messenger-credentials.json"), new WaypearlLocalMessenger.Listener() {
            @Override public boolean allowed(String identity) {
                return messengerAllowed.contains(identity.toLowerCase(Locale.ROOT));
            }
            @Override public void status(String message) {
                mc.execute(() -> {
                    if (message.startsWith("Failed to close")) warning("%s", message);
                    if (messengerTransport != holder[0]) return;
                    messengerStatus = message;
                    if (message.startsWith("Host error:") || message.startsWith("Connection error:")
                        || message.startsWith("Send error:") || message.startsWith("Requester authentication failed")) {
                        warning("%s", message);
                    }
                    if (mc.screen instanceof WaypearlMessengerScreen screen) screen.refreshMessages();
                });
            }
            @Override public void received(String identity, long connectionId, WaypearlLocalMessenger.MessageType type, String text) {
                mc.execute(() -> {
                    if (messengerTransport == holder[0] && connectionId == holder[0].connectionId()
                        && identity.equalsIgnoreCase(holder[0].peer())) {
                        receiveMessenger(identity, type, text);
                    }
                });
            }
        });
        return holder[0];
    }

    public void startMessengerHost() {
        if (!messengerAvailable() || messengerRequester() || mc.level == null) {
            error("Enable Waypearl messenger in assistant mode while connected to a world.");
            return;
        }
        stopMessenger();
        refreshMessengerAllowed();
        messengerTransport = createMessenger();
        messengerTransportMode = moduleMode.get();
        messengerStatus = "Starting local listener...";
        messengerTransport.startHost();
    }

    public void connectMessenger(String fingerprint, String password) {
        if (!messengerAvailable() || !messengerRequester()) {
            messengerStatus = "Enable Waypearl messenger in requester mode.";
            return;
        }
        stopMessenger();
        messengerTransport = createMessenger();
        messengerTransportMode = moduleMode.get();
        messengerStatus = "Connecting...";
        messengerTransport.connect(mc.getUser().getName(), password, fingerprint);
    }

    public void enrollMessenger(String name, String password) {
        if (!messengerAvailable() || messengerRequester() || !isMessengerPeerAllowed(name)) {
            error("Add the requester to the assistant whitelist before creating credentials.");
            return;
        }
        if (!WaypearlLocalMessenger.validPassword(password)) {
            error("Use a password of 16-128 characters.");
            return;
        }
        stopMessenger();
        try {
            createMessenger().enroll(name, password);
            messengerStatus = "Credentials saved for " + name + ". Start the listener.";
        } catch (Exception e) {
            error("Could not save credentials: " + e.getMessage());
        }
    }

    public void stopMessenger() {
        WaypearlLocalMessenger previous = messengerTransport;
        messengerTransport = null;
        messengerTransportMode = null;
        messengerStatus = "Disconnected";
        armedPull = null;
        if (pendingStrictOwner) {
            pullQueued.set(false);
            pendingStrictOwner = false;
            pendingNotifyTarget = null;
        }
        if (previous != null) previous.close();
    }

    public boolean sendMessenger(String peer, String message) {
        if (!messengerAvailable() || messengerTransport == null || (!messengerRequester() && mc.level == null)) {
            recordMessenger("Messenger is unavailable.");
            return false;
        }
        String text = message.trim();
        if (!messengerRequester() && (!isMessengerPeerAllowed(peer) || !peer.equalsIgnoreCase(messengerTransport.peer()))) {
            error("Recipient is not the connected, whitelisted requester.");
            return false;
        }
        try {
            messengerTransport.send(false, text);
            recordMessenger("You: " + text);
            return true;
        } catch (IOException e) {
            recordMessenger("Could not send message: " + e.getMessage());
            return false;
        }
    }

    public void sendMessengerPull() {
        String phrase = triggerPhrase.get().split(",", -1)[0].trim();
        if (phrase.isEmpty()) {
            recordMessenger("Configure a pull trigger phrase first.");
            return;
        }
        if (!messengerAvailable() || !messengerRequester() || messengerTransport == null) {
            recordMessenger("Messenger is not connected.");
            return;
        }
        try {
            messengerTransport.send(true, phrase);
            recordMessenger("You: " + phrase);
        } catch (IOException e) {
            recordMessenger("Could not send pull request: " + e.getMessage());
        }
    }

    public void sendArmedPull(boolean cancel) {
        if (!messengerAvailable() || !messengerRequester() || messengerTransport == null) {
            messengerStatus = "Connect to the assistant first.";
            return;
        }
        try {
            messengerTransport.send(cancel ? WaypearlLocalMessenger.MessageType.CANCEL : WaypearlLocalMessenger.MessageType.ARM,
                cancel ? "Cancel armed pull" : "Arm pull for server join");
            recordMessenger(cancel ? "You: cancel armed pull" : "You: arm pull for server join");
        } catch (IOException e) {
            messengerStatus = "Could not send request: " + e.getMessage();
            recordMessenger(messengerStatus);
        }
    }

    private void evaluateTrigger(String senderName, String content, boolean isWhisper) {
        if (mc.player == null) return;
        String bot = botUsername.get().trim();
        if (bot.isEmpty()) return;

        boolean whitelisted = false;
        for (String name : whitelist.get()) {
            if (name.equals(senderName)) {
                whitelisted = true;
                break;
            }
        }
        if (!whitelisted) return;

        String phraseRaw = triggerPhrase.get().trim();
        if (phraseRaw.isEmpty()) return;
        boolean keywordMatched = false;
        for (String kw : phraseRaw.split(",")) {
            String k = kw.trim();
            if (!k.isEmpty() && content.contains(k)) {
                keywordMatched = true;
                break;
            }
        }
        if (!keywordMatched) return;

        if (!isWhisper && !content.contains(bot)) return;

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastTriggerMs.get() < pullCooldown.get() * 1000L) {
            sendPlayerMessage(Component.literal("[Waypearl] Cooldown active — ignoring."), false);
            return;
        }

        if (armedPull != null || pullQueued.get() || activePull != null || (walkState != WalkState.IDLE && walkState != WalkState.ABORTED)) {
            sendPlayerMessage(Component.literal("[Waypearl] Walker active — ignoring."), false);
            return;
        }

        lastTriggerMs.set(nowMs);
        pendingNotifyTarget = senderName;
        pendingStrictOwner = false;
        pullQueued.set(true);
        sendPlayerMessage(Component.literal("[Waypearl] Pull triggered by " + senderName + "."), false);
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!disconnectEnabled.get()) return;
        if (mc.level == null || mc.player == null || mc.player.connection == null) return;

        if (!(event.entity instanceof Player enteringPlayer)) return;
        if (enteringPlayer == mc.player || enteringPlayer.getId() == mc.player.getId()) return;

        String enteringName = enteringPlayer.getName().getString();
        String botName = botUsername.get().trim();
        if (ignoreBotUser.get() && !botName.isEmpty() && enteringName.equalsIgnoreCase(botName)) return;

        if (ignoreFriendsOnDisconnect.get()) {
            NeighbourhoodWatch nw = getNeighbourhoodWatch();
            if (nw != null && nw.isActive()) {
                if (nw.isFriend(enteringName)) return;
            }
        }

        if (!disconnectArmed) return;
        disconnectArmed = false;

        long now = System.currentTimeMillis();
        boolean isAssistant = moduleMode.get() == ModuleMode.Assistant;

        if (isAssistant) {
            String targetUser = disconnectUser.get().trim();
            if (targetUser.isEmpty()) {
                info("No target user configured — leaving immediately.");
            } else if (isPlayerOnline(targetUser)) {
                long minMs = disconnectAntiSpam.get() * 1000L;
                if (now - lastDisconnectMsgMs < minMs) {
                    info("Anti-spam active — skipping message, disconnecting.");
                } else {
                    lastDisconnectMsgMs = now;
                    try {
                        String msg = disconnectMessage.get().replace("{player}", enteringName);
                        mc.player.connection.sendChat("/msg " + targetUser + " " + msg);
                        info("Sent disconnect notice to §b" + targetUser + "§r — " + enteringName + " entered render distance.");
                    } catch (Exception ignored) {}
                }
            } else {
                info("Target user §b" + targetUser + "§r is offline — leaving immediately.");
            }
        } else {
            info("Requester mode — disconnecting without message (" + enteringName + " entered render distance).");
        }

        Thread.ofVirtual().name("Waypearl-disconnect").start(() -> {
            try {
                Thread.sleep(150L);
                if (mc.level != null && mc.player != null) {
                    com.example.addon.utils.MinecraftApiCompat.disconnect(mc);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private NeighbourhoodWatch getNeighbourhoodWatch() {
        return Modules.get().get(NeighbourhoodWatch.class);
    }

    private boolean isPlayerOnline(String username) {
        if (username == null || username.isEmpty() || mc.player == null || mc.player.connection == null) return false;
        PlayerInfo entry = mc.player.connection.getPlayerInfo(username);
        if (entry != null) return true;

        for (PlayerInfo ple : mc.player.connection.getOnlinePlayers()) {
            if (ple != null && ple.getProfile() != null && username.equalsIgnoreCase(com.example.addon.utils.MinecraftCompat.profileName(ple.getProfile()))) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        refreshMessengerAllowed();
        if (messengerTransport != null && (!messengerEnabled.get() || messengerTransportMode != moduleMode.get()
            || (moduleMode.get() == ModuleMode.Assistant && (mc.level == null || mc.player == null))
            || (moduleMode.get() == ModuleMode.Assistant && !messengerTransport.peer().isEmpty()
                && !isMessengerPeerAllowed(messengerTransport.peer())))) stopMessenger();
        if (mc.level == null || mc.player == null) return;
        if (moduleMode.get() != ModuleMode.Assistant) {
            if (walkState != WalkState.IDLE && walkState != WalkState.ABORTED) abortWalk("Assistant mode changed.");
            activePull = null;
            followups.clear();
            followupMessages.clear();
        }
        flushFollowupMessages();
        tickArmedPull();
        if (activePull != null && !activePull.interactionSent
            && (!isMessengerPeerAllowed(activePull.requester) || (activePull.direct
                && (!messengerAvailable() || messengerTransport == null
                    || !activePull.requester.equalsIgnoreCase(messengerTransport.peer()))))) {
            if (walkState == WalkState.WALKING_TO || walkState == WalkState.PULLING) {
                abortWalk("Requester disconnected or is no longer whitelisted.");
            } else failActivePull("Requester disconnected or is no longer whitelisted.");
        }
        boolean openPressed = messengerOpenKey.get().isPressed();
        boolean pullPressed = messengerPullKey.get().isPressed();
        boolean quickPressed = messengerQuickKey.get().isPressed();
        if (messengerEnabled.get() && mc.screen == null) {
            if (openPressed && !wasMessengerOpenPressed) mc.setScreen(new WaypearlMessengerScreen(GuiThemes.get(), this));
            else if (pullPressed && !wasMessengerPullPressed && messengerRequester()) sendMessengerPull();
            else if (quickPressed && !wasMessengerQuickPressed) sendMessenger(messengerPeer(), messengerQuickMessage.get());
        }
        wasMessengerOpenPressed = openPressed;
        wasMessengerPullPressed = pullPressed;
        wasMessengerQuickPressed = quickPressed;
        if (walkWorld != null && mc.level != walkWorld) abortWalk("World changed.");
        if (mc.level.dimension() == Level.NETHER) return;

        boolean selfPressed = selfTriggerKey.get().isPressed();
        if (selfPressed && !wasSelfTriggerPressed && moduleMode.get() == ModuleMode.Requester) {
            if (mc.screen == null && (walkState == WalkState.IDLE || walkState == WalkState.ABORTED)) {
                long now = System.currentTimeMillis();
                long cooldownMs = pullCooldown.get() * 1000L;
                long elapsedMs = now - lastTriggerMs.get();
                if (elapsedMs >= cooldownMs) {
                    sendSelfTrigger();
                } else {
                    long remainingSeconds = (cooldownMs - elapsedMs + 999L) / 1000L;
                    sendPlayerMessage(Component.literal(
                        "[Waypearl] Cooldown active — try again in " + remainingSeconds + "s."), false);
                }
            }
        }
        wasSelfTriggerPressed = selfPressed;

        if (pingQueued.compareAndSet(true, false) && soundEnabled.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                pingSound.get().sound,
                soundPitch.get().floatValue(),
                soundVolume.get().floatValue()));
        }

        if (pullQueued.compareAndSet(true, false)) {
            String requester = pendingNotifyTarget;
            if (!pendingStrictOwner || (messengerAvailable() && moduleMode.get() == ModuleMode.Assistant
                && messengerTransport != null && isMessengerPeerAllowed(requester)
                && requester.equalsIgnoreCase(messengerTransport.peer()))) executePull(requester, pendingStrictOwner);
            pendingNotifyTarget = null;
            pendingStrictOwner = false;
        }

        tickWalker();
        updatePearlMemory();
        tickPullFollowups();

        tickCounter++;
        if (tickCounter >= scanInterval.get()) {
            tickCounter = 0;
            triggerColumnScan();
        }
    }

    private void tickArmedPull() {
        ArmedPull job = armedPull;
        if (job == null) return;
        boolean connected = messengerTransport != null && messengerTransport.connectionId() == job.connectionId()
            && job.requester().equalsIgnoreCase(messengerTransport.peer());
        boolean sameWorld = job.server().equals(currentServerKey()) && job.dimension().equals(currentDimensionKey());
        WaypearlArmedPullPolicy.Decision decision = WaypearlArmedPullPolicy.evaluate(
            connected, isMessengerPeerAllowed(job.requester()), messengerAvailable() && messengerAutoPull.get(),
            sameWorld, isPlayerOnline(job.requester()),
            !pullQueued.get() && activePull == null && (walkState == WalkState.IDLE || walkState == WalkState.ABORTED));
        if (decision == WaypearlArmedPullPolicy.Decision.CANCEL) {
            armedPull = null;
            if (connected) sendMessenger(job.requester(), sameWorld
                ? "Armed pull cancelled: whitelist or auto-pull changed."
                : "Armed pull cancelled: assistant changed server or dimension.");
            warning("Armed pull cancelled: connection, whitelist, settings, server, or dimension changed.");
            return;
        }
        if (decision == WaypearlArmedPullPolicy.Decision.WAIT) return;
        armedPull = null;
        sendMessenger(job.requester(), "You joined. Checking your pearl now.");
        pendingNotifyTarget = job.requester();
        pendingStrictOwner = true;
        pullQueued.set(true);
    }

    private void sendSelfTrigger() {
        String bot = botUsername.get().trim();
        if (bot.isEmpty()) return;

        String[] phrases = triggerPhrase.get().split(",");
        List<String> validPhrases = new ArrayList<>();
        for (String p : phrases) {
            String t = p.trim();
            if (!t.isEmpty()) validPhrases.add(t);
        }

        String phrase = validPhrases.isEmpty() ? "tp" : validPhrases.get((int) (Math.random() * validPhrases.size()));
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }

        mc.player.connection.sendCommand("msg " + bot + " " + phrase + " " + sb.toString());
        lastTriggerMs.set(System.currentTimeMillis());
        info("Sent pull request to §6" + bot + " §r(phrase: " + phrase + ").");
    }

    private void updatePearlMemory() {
        Map<String, Vec3[]> lines = getColumnLines();
        String server = currentServerKey();
        String dimension = currentDimensionKey();
        String botName = botUsername.get().trim();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getType() != EntityType.ENDER_PEARL) continue;
            if (mc.player.distanceTo(e) > range.get()) continue;

            int    px  = (int) Math.floor(e.getX());
            int    pz  = (int) Math.floor(e.getZ());
            String key = px + "," + pz;
            if (!lines.containsKey(key)) continue;

            Vec3[] line     = lines.get(key);
            int     topY     = (int) Math.floor(line[1].y);
            BlockPos trapdoor = findTrapdoor(px, topY, pz);
            UUID pearlUuid = e.getUUID();
            String memoryKey = pearlMemoryKey(server, dimension, pearlUuid);
            PearlRecord previous;
            synchronized (pearlMemory) {
                previous = pearlMemory.get(memoryKey);
            }

            String ownerName = "unknown";
            if (e instanceof ThrownEnderpearl pearl) {
                Entity owner = pearl.getOwner();
                if (owner != null) ownerName = owner.getName().getString();
            }
            if (ownerName.equals("unknown") && previous != null) ownerName = previous.owner();
            if (!botName.isEmpty() && ownerName.equalsIgnoreCase(botName)) continue;
            if (trapdoor == null && previous != null) trapdoor = previous.trapdoor();

            PearlRecord record = new PearlRecord(e.getId(), pearlUuid, server, dimension, ownerName,
                BlockPos.containing(e.getX(), e.getY(), e.getZ()), trapdoor,
                previous == null ? System.currentTimeMillis() : previous.discoveredMs());
            boolean newlyDiscovered = previous == null;
            rememberPearl(record);
            seenPearlIds.add(e.getId());

            if (newlyDiscovered) {
                pingQueued.set(true);
                sendPlayerMessage(Component.literal("[Waypearl] New stasis pearl — owner: " + ownerName), false);
            }
        }
    }

    private void sendNotifyMessage(String target) {
        if (!notifyEnabled.get() || target == null || target.isEmpty() || mc.player == null) return;
        String msg = notifyMessage.get().replace("{player}", target);
        mc.player.connection.sendCommand("msg " + target + " " + msg);
    }

    private void executePull(String requester, boolean strictOwner) {
        if (mc.level == null || mc.player == null) return;
        if ((walkState != WalkState.IDLE && walkState != WalkState.ABORTED) || activePull != null) {
            warning("Cannot start another pull while the assistant is busy.");
            if (strictOwner && requester != null) sendMessenger(requester, "Assistant is busy with another pull.");
            return;
        }

        String botName = botUsername.get().trim();
        String server = currentServerKey();
        String dimension = currentDimensionKey();
        List<PearlRecord> candidates = new ArrayList<>();
        synchronized (pearlMemory) {
            for (PearlRecord rec : pearlMemory.values()) {
                if (!rec.server().equals(server) || !rec.dimension().equals(dimension)) continue;
                if (rec.trapdoor() == null) continue;
                if (!pearlStillExists(rec.uuid())) continue;
                if (!isAnyTrapdoor(mc.level.getBlockState(rec.trapdoor()).getBlock())) continue;
                if (!botName.isEmpty() && rec.owner().equalsIgnoreCase(botName)) continue;
                candidates.add(rec);
            }
        }

        if (candidates.isEmpty() && !strictOwner) candidates = scanLiveTargets();

        if (requester != null) {
            candidates.removeIf(rec -> !rec.owner().equalsIgnoreCase(requester)
                && (strictOwner || !rec.owner().equalsIgnoreCase("unknown")));
        }

        if (candidates.isEmpty()) {
            sendPlayerMessage(Component.literal("[Waypearl] No valid stasis pearls found for " + (requester != null ? requester : "anyone") + "."), false);
            if (strictOwner && requester != null) sendMessenger(requester, "No available pearl found for you.");
            return;
        }

        Vec3 playerPos = com.example.addon.utils.MinecraftCompat.position(mc.player);
        candidates.sort((a, b) -> {
            if (requester != null) {
                boolean aMine = a.owner().equalsIgnoreCase(requester);
                boolean bMine = b.owner().equalsIgnoreCase(requester);
                if (aMine && !bMine) return -1;
                if (!aMine && bMine) return 1;
            }
            if (pullOrder.get() == PullOrder.NEAREST) {
                double da = playerPos.distanceToSqr(Vec3.atCenterOf(a.trapdoor()));
                double db = playerPos.distanceToSqr(Vec3.atCenterOf(b.trapdoor()));
                return Double.compare(da, db);
            }
            return 0;
        });

        if (strictOwner && requester != null && notifyEnabled.get()) {
            sendMessenger(requester, notifyMessage.get().replace("{player}", requester));
        } else if (!strictOwner) sendNotifyMessage(requester);
        PearlRecord selected = candidates.get(0);
        if (requester != null && requester.equalsIgnoreCase(selected.owner())) {
            Set<UUID> existing = new HashSet<>();
            synchronized (pearlMemory) {
                for (PearlRecord pearl : pearlMemory.values()) {
                    if (pearl.server().equals(server) && pearl.dimension().equals(dimension)) existing.add(pearl.uuid());
                }
            }
            activePull = new PullFollowup(selected, requester, strictOwner, existing);
        }
        dispatchTarget(selected);
    }

    private boolean pearlStillExists(UUID pearlUuid) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getUUID().equals(pearlUuid) && e.getType() == EntityType.ENDER_PEARL) return true;
        }
        return false;
    }

    private void failActivePull(String message) {
        PullFollowup job = activePull;
        if (job == null) return;
        activePull = null;
        notifyFollowup(job, message);
    }

    private void notifyFollowup(PullFollowup job, String message) {
        if (job.direct) {
            if (messengerTransport != null && isMessengerPeerAllowed(job.requester)
                && job.requester.equalsIgnoreCase(messengerTransport.peer())) {
                sendMessenger(job.requester, message);
            } else {
                warning("Could not send follow-up to %s: messenger disconnected.", job.requester);
            }
        } else if (followupMessages.size() < 64) {
            followupMessages.addLast(new FollowupMessage(job.pearl.server(), job.requester, message));
        } else {
            warning("Could not queue follow-up for %s: message queue is full.", job.requester);
        }
    }

    private void flushFollowupMessages() {
        if (mc.player.connection == null) return;
        long now = System.currentTimeMillis();
        long lastSent = Math.max(lastFollowupMessageMs, lastTriggerMs.get());
        if (now - lastSent < pullCooldown.get() * 1000L) return;
        Iterator<FollowupMessage> iterator = followupMessages.iterator();
        while (iterator.hasNext()) {
            FollowupMessage message = iterator.next();
            if (!isMessengerPeerAllowed(message.requester())) {
                iterator.remove();
                warning("Cancelled queued follow-up for %s: requester is no longer whitelisted.", message.requester());
                continue;
            }
            if (!message.server().equals(currentServerKey()) || !isPlayerOnline(message.requester())) continue;
            mc.player.connection.sendCommand("msg " + message.requester() + " " + message.text());
            lastFollowupMessageMs = now;
            iterator.remove();
            return;
        }
    }

    private boolean followupChannelAvailable(PullFollowup job) {
        if (job.direct) {
            return messengerAvailable() && messengerTransport != null && isMessengerPeerAllowed(job.requester)
                && job.requester.equalsIgnoreCase(messengerTransport.peer());
        }
        return isPlayerOnline(job.requester);
    }

    private Player requesterInWorld(String requester) {
        for (Player player : mc.level.players()) {
            if (player != mc.player && player.getName().getString().equalsIgnoreCase(requester)) return player;
        }
        return null;
    }

    private boolean sameFollowupWorld(PullFollowup job) {
        return job.pearl.server().equals(currentServerKey()) && job.pearl.dimension().equals(currentDimensionKey());
    }

    private void tickPullFollowups() {
        long now = System.currentTimeMillis();
        if (activePull != null && activePull.interactionSent) {
            PullFollowup job = activePull;
            if (!isMessengerPeerAllowed(job.requester)) {
                failActivePull("Requester is no longer whitelisted.");
                return;
            }
            Player requester = sameFollowupWorld(job) ? requesterInWorld(job.requester) : null;
            BlockPos pearlPos = job.pearl.pearlPos();
            boolean chunkLoaded = sameFollowupWorld(job)
                && mc.level.getChunkSource().getChunk(pearlPos.getX() >> 4, pearlPos.getZ() >> 4, false) != null;
            double distance = requester == null ? Double.POSITIVE_INFINITY
                : requester.distanceToSqr(Vec3.atCenterOf(pearlPos));
            if (WaypearlFollowupPolicy.pullConfirmed(sameFollowupWorld(job), chunkLoaded,
                pearlStillExists(job.pearl.uuid()), requester != null, distance)) {
                activePull = null;
                job.dropPending = replacePearl.get();
                job.reminderPending = pearlReminder.get();
                job.dropDeadline = now + 30_000L;
                job.reminderDeadline = now + pearlReminderDelay.get() * 1000L;
                followups.add(job);
                notifyFollowup(job, "Pull confirmed. Please place a new stasis pearl.");
            } else if (now >= job.confirmationDeadline) {
                failActivePull("Could not confirm the pull; no replacement pearl was dropped.");
            }
        }

        Iterator<PullFollowup> iterator = followups.iterator();
        while (iterator.hasNext()) {
            PullFollowup job = iterator.next();
            if (!isMessengerPeerAllowed(job.requester)) {
                iterator.remove();
                followupMessages.removeIf(message -> message.requester().equalsIgnoreCase(job.requester));
                warning("Cancelled follow-up for %s: requester is no longer whitelisted.", job.requester);
                continue;
            }
            if (job.reminderPending && hasReplacementPearl(job)) {
                job.reminderPending = false;
                if (followupChannelAvailable(job)) notifyFollowup(job, "Your new stasis pearl has been detected.");
            }
            if (job.dropPending && !replacePearl.get()) job.dropPending = false;
            if (job.reminderPending && !pearlReminder.get()) job.reminderPending = false;
            if (job.dropPending && now >= job.dropDeadline) {
                job.dropPending = false;
                warning("Could not deliver a replacement pearl to %s within 30 seconds.", job.requester);
                notifyFollowup(job, "I couldn't deliver a replacement pearl. Please bring one.");
            } else if (job.dropPending && job.dropRotation.ready(now, job.dropDeadline) && sameFollowupWorld(job)) {
                tryDropReplacement(job, now);
            }
            if (WaypearlFollowupPolicy.reminderDue(job.reminderPending, now, job.reminderDeadline)
                && followupChannelAvailable(job)) {
                String reminder = "You have forgotten to place down a pearl, come back.";
                if (job.direct) {
                    notifyFollowup(job, reminder);
                    job.reminderPending = false;
                } else if (now - Math.max(lastFollowupMessageMs, lastTriggerMs.get()) >= pullCooldown.get() * 1000L) {
                    mc.player.connection.sendCommand("msg " + job.requester + " " + reminder);
                    lastFollowupMessageMs = now;
                    job.reminderPending = false;
                }
            }
            if (!job.dropPending && !job.reminderPending) iterator.remove();
        }
    }

    private boolean hasReplacementPearl(PullFollowup job) {
        if (!sameFollowupWorld(job)) return false;
        synchronized (pearlMemory) {
            for (PearlRecord pearl : pearlMemory.values()) {
                boolean correctWorld = pearl.server().equals(job.pearl.server())
                    && pearl.dimension().equals(job.pearl.dimension());
                boolean validTrapdoor = correctWorld && pearl.trapdoor() != null
                    && isAnyTrapdoor(mc.level.getBlockState(pearl.trapdoor()).getBlock());
                if (WaypearlFollowupPolicy.isReplacement(correctWorld, pearl.owner().equalsIgnoreCase(job.requester),
                    pearl.uuid(), job.existingPearls, validTrapdoor, correctWorld && pearlStillExists(pearl.uuid()))) return true;
            }
        }
        return false;
    }

    private void tryDropReplacement(PullFollowup job, long now) {
        if (job.direct && !followupChannelAvailable(job)) return;
        Player target = requesterInWorld(job.requester);
        double distance = target == null ? Double.POSITIVE_INFINITY : mc.player.distanceToSqr(target);
        if (mc.gameMode == null || !WaypearlFollowupPolicy.canDrop(sameFollowupWorld(job), target != null,
            distance, mc.player.containerMenu == mc.player.inventoryMenu)) return;
        int slotId = -1;
        for (Slot slot : mc.player.inventoryMenu.slots) {
            if (slot.container == mc.player.getInventory() && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 36
                && slot.getItem().is(Items.ENDER_PEARL)) {
                slotId = slot.index;
                break;
            }
        }
        if (slotId < 0) {
            job.dropPending = false;
            notifyFollowup(job, "I don't have a replacement pearl. Please bring one.");
            return;
        }
        int selectedSlot = slotId;
        long attempt = job.dropRotation.begin(now);
        Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target), () -> {
            if (!isActive() || !job.dropPending || !followups.contains(job) || mc.player == null || mc.level == null
                || !job.dropRotation.accept(attempt, System.currentTimeMillis(), job.dropDeadline)
                || !replacePearl.get() || mc.gameMode == null || !sameFollowupWorld(job)
                || !isMessengerPeerAllowed(job.requester)
                || (job.direct && !followupChannelAvailable(job))
                || mc.player.containerMenu != mc.player.inventoryMenu) return;
            Player currentTarget = requesterInWorld(job.requester);
            if (currentTarget == null || mc.player.distanceToSqr(currentTarget) > 9) return;
            Slot slot = mc.player.inventoryMenu.getSlot(selectedSlot);
            if (slot.container != mc.player.getInventory() || !slot.getItem().is(Items.ENDER_PEARL)) return;
            job.dropRotation.complete();
            job.dropPending = false;
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, selectedSlot, 0, ContainerInput.THROW, mc.player);
            notifyFollowup(job, "I attempted to drop a replacement pearl nearby. Please check and pick it up.");
        });
    }

    private List<PearlRecord> scanLiveTargets() {
        List<PearlRecord> found = new ArrayList<>();
        Map<String, Vec3[]> lines = getColumnLines();
        String botName = botUsername.get().trim();
        String server = currentServerKey();
        String dimension = currentDimensionKey();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getType() != EntityType.ENDER_PEARL) continue;
            if (mc.player.distanceTo(e) > range.get()) continue;

            String ownerName = "unknown";
            if (e instanceof ThrownEnderpearl pearl) {
                Entity owner = pearl.getOwner();
                if (owner != null) ownerName = owner.getName().getString();
            }
            PearlRecord previous;
            synchronized (pearlMemory) {
                previous = pearlMemory.get(pearlMemoryKey(server, dimension, e.getUUID()));
            }
            if (ownerName.equals("unknown") && previous != null) ownerName = previous.owner();
            if (!botName.isEmpty() && ownerName.equalsIgnoreCase(botName)) continue;

            int    px  = (int) Math.floor(e.getX());
            int    pz  = (int) Math.floor(e.getZ());
            String key = px + "," + pz;
            Vec3[] line = lines.get(key);
            if (line == null) continue;

            int topY = (int) Math.floor(line[1].y);
            BlockPos trapdoor = findTrapdoor(px, topY, pz);
            if (trapdoor == null) continue;

            PearlRecord record = new PearlRecord(e.getId(), e.getUUID(), server, dimension, ownerName,
                BlockPos.containing(e.getX(), e.getY(), e.getZ()), trapdoor,
                previous == null ? System.currentTimeMillis() : previous.discoveredMs());
            rememberPearl(record);
            found.add(record);
        }
        return found;
    }

    private String currentServerKey() {
        if (mc.getCurrentServer() == null) return "singleplayer";
        return mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
    }

    private String currentDimensionKey() {
        return mc.level.dimension().identifier().toString();
    }

    private String pearlMemoryKey(String server, String dimension, UUID pearlUuid) {
        return server + "|" + dimension + "|" + pearlUuid;
    }

    private void rememberPearl(PearlRecord record) {
        String key = pearlMemoryKey(record.server(), record.dimension(), record.uuid());
        boolean changed;
        synchronized (pearlMemory) {
            PearlRecord previous = pearlMemory.put(key, record);
            changed = previous == null
                || !previous.owner().equals(record.owner())
                || !previous.pearlPos().equals(record.pearlPos())
                || !Objects.equals(previous.trapdoor(), record.trapdoor());
        }
        if (changed) savePearlCache();
    }

    private void loadPearlCache() {
        if (!Files.exists(PEARL_CACHE_PATH)) return;
        try (Reader reader = Files.newBufferedReader(PEARL_CACHE_PATH, StandardCharsets.UTF_8)) {
            PearlCache cache = PEARL_CACHE_GSON.fromJson(reader, PearlCache.class);
            if (cache == null || cache.pearls == null || cache.version != 1) {
                throw new JsonParseException("Unsupported or invalid Waypearl cache format.");
            }
            synchronized (pearlMemory) {
                for (PearlCacheEntry entry : cache.pearls) {
                    if (entry == null || entry.server == null || entry.dimension == null || entry.uuid == null
                        || entry.owner == null) {
                        Tim.LOG.warn("Skipping incomplete Waypearl cache record in {}", PEARL_CACHE_PATH);
                        continue;
                    }
                    try {
                        UUID uuid = UUID.fromString(entry.uuid);
                        BlockPos pearlPos = new BlockPos(entry.pearlX, entry.pearlY, entry.pearlZ);
                        BlockPos trapdoor = entry.hasTrapdoor
                            ? new BlockPos(entry.trapdoorX, entry.trapdoorY, entry.trapdoorZ)
                            : null;
                        PearlRecord record = new PearlRecord(-1, uuid, entry.server, entry.dimension,
                            entry.owner, pearlPos, trapdoor, entry.discoveredMs);
                        pearlMemory.put(pearlMemoryKey(entry.server, entry.dimension, uuid), record);
                    } catch (IllegalArgumentException e) {
                        Tim.LOG.warn("Skipping invalid Waypearl cache record in {}", PEARL_CACHE_PATH, e);
                    }
                }
            }
        } catch (IOException | JsonParseException e) {
            Tim.LOG.warn("Unable to load Waypearl pearl cache from {}", PEARL_CACHE_PATH, e);
        }
    }

    private void savePearlCache() {
        PearlCache cache = new PearlCache();
        synchronized (pearlMemory) {
            for (PearlRecord record : pearlMemory.values()) {
                PearlCacheEntry entry = new PearlCacheEntry();
                entry.server = record.server();
                entry.dimension = record.dimension();
                entry.uuid = record.uuid().toString();
                entry.owner = record.owner();
                entry.pearlX = record.pearlPos().getX();
                entry.pearlY = record.pearlPos().getY();
                entry.pearlZ = record.pearlPos().getZ();
                if (record.trapdoor() != null) {
                    entry.hasTrapdoor = true;
                    entry.trapdoorX = record.trapdoor().getX();
                    entry.trapdoorY = record.trapdoor().getY();
                    entry.trapdoorZ = record.trapdoor().getZ();
                }
                entry.discoveredMs = record.discoveredMs();
                cache.pearls.add(entry);
            }
        }

        Path temporaryPath = PEARL_CACHE_PATH.resolveSibling(PEARL_CACHE_PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(PEARL_CACHE_PATH.getParent());
            Files.writeString(temporaryPath, PEARL_CACHE_GSON.toJson(cache), StandardCharsets.UTF_8);
            try {
                Files.move(temporaryPath, PEARL_CACHE_PATH,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryPath, PEARL_CACHE_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Tim.LOG.warn("Unable to save Waypearl pearl cache to {}", PEARL_CACHE_PATH, e);
        }
    }

    private void dispatchTarget(PearlRecord pearl) {
        BlockPos trapdoor = pearl.trapdoor();
        double dist = com.example.addon.utils.MinecraftCompat.position(mc.player).distanceTo(Vec3.atCenterOf(trapdoor));
        if (dist <= interactReach.get()) {
            interactTrapdoor(trapdoor);
        } else if (walkerEnabled.get()) {
            beginWalk(pearl);
        } else {
            sendPlayerMessage(Component.literal("[Waypearl] Trapdoor out of reach and walker is disabled."), false);
            failActivePull("The trapdoor is out of reach and the walker is disabled.");
        }
    }

    private void beginWalk(PearlRecord pearl) {
        walkTarget   = pearl.trapdoor();
        walkPearlId  = pearl.uuid();
        walkWorld    = mc.level;
        idlePosition = com.example.addon.utils.MinecraftCompat.position(mc.player);
        walkGeneration++;
        walkInteractionPending = false;
        walkState    = WalkState.WALKING_TO;
        walkTicks    = 0;
        stuckTicks   = 0;
        jumpCooldown = 0;
        lastPos      = idlePosition;
        waypoints.clear();

        List<Vec3> path = findPath(mc.player.blockPosition(), walkTarget, true);
        if (path != null) {
            waypoints.addAll(path);
        } else {
            abortWalk("No clear path to trapdoor found.");
            return;
        }

        sendPlayerMessage(Component.literal("[Waypearl] Path found (" + waypoints.size() + " waypoints)."), false);
    }

    private void tickWalker() {
        if (walkState == WalkState.IDLE || walkState == WalkState.ABORTED) return;
        if (mc.player == null || mc.level == null) {
            abortWalk("Player/world null.");
            return;
        }

        Vec3 pos = com.example.addon.utils.MinecraftCompat.position(mc.player);

        if (pos.y < voidAbortY.get()) {
            abortWalk("Fell below void-abort Y (" + voidAbortY.get() + ").");
            return;
        }

        if (abortOnLowHealth.get() && mc.player.getHealth() <= lowHealthThreshold.get() * 2f) {
            abortWalk("Health low (" + String.format("%.1f", mc.player.getHealth()) + ").");
            return;
        }

        if (mc.player.isOnFire()) {
            if (abortOnFire.get()) {
                abortWalk("Player on fire.");
                return;
            }
        }

        if (jumpCooldown > 0) jumpCooldown--;

        switch (walkState) {
            case WALKING_TO -> {
                walkTicks++;
                if (walkTicks > walkTimeoutTicks.get()) {
                    abortWalk("Timed out after " + walkTicks + " ticks.");
                    return;
                }

                if (lastPos != null) {
                    double moved = xzDist(pos, lastPos);
                    if (moved < stuckMovementMin.get()) {
                        stuckTicks++;
                        if (walkerJump.get() && stuckTicks >= jumpAttemptTicks.get() && jumpCooldown == 0 && mc.player.onGround()) {
                            mc.player.jumpFromGround();
                            jumpCooldown = jumpCooldownTicks.get();
                        }
                        if (stuckTicks >= stuckThresholdTicks.get()) {
                            abortWalk("Stuck for " + stuckTicks + " ticks.");
                            return;
                        }
                    } else {
                        stuckTicks = 0;
                    }
                }
                lastPos = pos;

                if (walkTarget != null && !isAnyTrapdoor(mc.level.getBlockState(walkTarget).getBlock())) {
                    abortWalk("Trapdoor vanished.");
                    return;
                }
                if (walkPearlId == null || !pearlStillExists(walkPearlId)) {
                    abortWalk("The target pearl is no longer loaded.");
                    return;
                }

                if (pos.distanceTo(Vec3.atCenterOf(walkTarget)) <= interactReach.get()) {
                    stopMovement();
                    walkState = WalkState.PULLING;
                    walkTicks = 0;
                    return;
                }

                Vec3 currentWaypoint = waypoints.peek();
                if (currentWaypoint == null) {
                    abortWalk("Path ended outside trapdoor reach.");
                    return;
                }
                if (!isWalkable(BlockPos.containing(currentWaypoint))) {
                    abortWalk("The route is no longer clear.");
                    return;
                }

                if (atWaypoint(pos, currentWaypoint, waypointTolerance())) {
                    stopMovement();
                    waypoints.poll();
                    return;
                }

                double distToFinal = pos.distanceTo(Vec3.atCenterOf(walkTarget));
                boolean inSlowZone = distToFinal <= slowZoneRadius.get();
                jumpForWaypoint(pos, currentWaypoint);
                faceAndWalkToward(pos, currentWaypoint, !inSlowZone);
            }

            case PULLING -> {
                if (++walkTicks > walkTimeoutTicks.get()) {
                    abortWalk("Trapdoor interaction timed out.");
                    return;
                }
                if (walkInteractionPending) return;
                stopMovement();
                if (mc.player.isShiftKeyDown()) return;
                walkInteractionPending = true;
                long generation = walkGeneration;
                interactTrapdoor(walkTarget, () -> {
                    if (walkState != WalkState.PULLING || generation != walkGeneration) return;
                    walkInteractionPending = false;
                    stopMovement();
                    if (returnAfterPull.get() && idlePosition != null) beginReturn();
                    else {
                        walkState = WalkState.IDLE;
                        resetWalkerFields();
                    }
                });
            }

            case WALKING_BACK -> {
                walkTicks++;
                if (walkTicks > returnTimeoutTicks.get()) {
                    sendPlayerMessage(Component.literal("[Waypearl] Return timed out; stopping here."), false);
                    stopMovement();
                    walkState = WalkState.IDLE;
                    resetWalkerFields();
                    return;
                }

                if (lastPos != null) {
                    double moved = xzDist(pos, lastPos);
                    if (moved < stuckMovementMin.get()) {
                        stuckTicks++;
                        if (walkerJump.get() && stuckTicks >= jumpAttemptTicks.get() && jumpCooldown == 0 && mc.player.onGround()) {
                            mc.player.jumpFromGround();
                            jumpCooldown = jumpCooldownTicks.get();
                        }
                        if (stuckTicks >= stuckThresholdTicks.get()) {
                            sendPlayerMessage(Component.literal("[Waypearl] Stuck returning; stopping here."), false);
                            stopMovement();
                            walkState = WalkState.IDLE;
                            resetWalkerFields();
                            return;
                        }
                    } else {
                        stuckTicks = 0;
                    }
                }
                lastPos = pos;

                Vec3 currentWaypoint = waypoints.peek();
                if (currentWaypoint == null) {
                    abortWalk("Return path ended before reaching the idle position.");
                    return;
                }
                if (!isWalkable(BlockPos.containing(currentWaypoint))) {
                    abortWalk("The return route is no longer clear.");
                    return;
                }

                boolean isFinal = waypoints.size() == 1;
                double tolerance = isFinal ? returnTolerance.get() : waypointTolerance();
                if (atWaypoint(pos, currentWaypoint, tolerance)) {
                    stopMovement();
                    waypoints.poll();
                    if (isFinal) finishReturn();
                    return;
                }

                double distToHome = pos.distanceTo(idlePosition);
                boolean inSlowZone = distToHome <= slowZoneRadius.get();
                jumpForWaypoint(pos, currentWaypoint);
                faceAndWalkToward(pos, currentWaypoint, !inSlowZone);
            }

            default -> {}
        }
    }

    private void finishReturn() {
        stopMovement();
        sendPlayerMessage(Component.literal("[Waypearl] Returned to idle position."), false);
        walkState = WalkState.IDLE;
        resetWalkerFields();
    }

    private void beginReturn() {
        waypoints.clear();
        List<Vec3> path = findPath(mc.player.blockPosition(), BlockPos.containing(idlePosition), false);
        if (path == null) {
            warning("No safe return path to the saved idle position.");
            stopMovement();
            walkState = WalkState.IDLE;
            resetWalkerFields();
            return;
        }
        waypoints.addAll(path);
        waypoints.addLast(idlePosition);
        walkState = WalkState.WALKING_BACK;
        walkTicks = 0;
        stuckTicks = 0;
        lastPos = com.example.addon.utils.MinecraftCompat.position(mc.player);
        sendPlayerMessage(Component.literal("[Waypearl] Returning to idle."), false);
    }

    private List<Vec3> findPath(BlockPos start, BlockPos end, boolean interactionGoal) {
        List<BlockPos> blocks = WaypearlPathfinder.find(start,
            pos -> interactionGoal ? footPosition(pos).distanceTo(Vec3.atCenterOf(end)) <= interactReach.get() - waypointTolerance()
                : pos.equals(end),
            this::walkNeighbours, 6000);
        if (blocks == null) return null;
        List<Vec3> path = new ArrayList<>();
        for (int i = 1; i < blocks.size(); i++) path.add(footPosition(blocks.get(i)));
        return path;
    }

    private List<BlockPos> walkNeighbours(BlockPos current) {
        List<BlockPos> result = new ArrayList<>(4);
        for (Direction dir : Direction.values()) {
            if (!dir.getAxis().isHorizontal()) continue;
            BlockPos next = current.relative(dir);
            if (isWalkable(next)) result.add(next);
            else if (walkerJump.get() && isPassable(current.above(2)) && isWalkable(next.above())) result.add(next.above());
            else if (isPassable(next) && isPassable(next.above()) && isWalkable(next.below())) {
                result.add(next.below());
            }
        }
        return result;
    }

    private Vec3 footPosition(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private double waypointTolerance() {
        return Math.min(0.35, waypointSpacing.get() * 0.05);
    }

    private boolean atWaypoint(Vec3 position, Vec3 target, double tolerance) {
        return xzDist(position, target) <= tolerance && Math.abs(position.y - target.y) < 0.6;
    }

    private void jumpForWaypoint(Vec3 position, Vec3 waypoint) {
        if (walkerJump.get() && waypoint.y > position.y + 0.5 && jumpCooldown == 0 && mc.player.onGround()) {
            mc.player.jumpFromGround();
            jumpCooldown = jumpCooldownTicks.get();
        }
    }

    private boolean isPassable(BlockPos pos) {
        if (mc.level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, false) == null) return false;
        BlockState state = mc.level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getBlock() != Blocks.FIRE && state.getBlock() != Blocks.SOUL_FIRE
            && state.getCollisionShape(mc.level, pos).isEmpty();
    }

    private boolean isWalkable(BlockPos pos) {
        if (!isPassable(pos) || !isPassable(pos.above())) return false;
        BlockPos support = pos.below();
        BlockState state = mc.level.getBlockState(support);
        return state.getFluidState().isEmpty() && !state.getCollisionShape(mc.level, support).isEmpty();
    }

    private void faceAndWalkToward(Vec3 from, Vec3 to, boolean sprint) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;

        float pYaw = Mth.wrapDegrees(mc.player.getYRot());
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float diff = Mth.wrapDegrees(targetYaw - pYaw);

        mc.options.keyUp.setDown(diff > -67.5 && diff <= 67.5);
        mc.options.keyDown.setDown(diff > 112.5 || diff <= -112.5);
        mc.options.keyLeft.setDown(diff > -157.5 && diff <= -22.5);
        mc.options.keyRight.setDown(diff > 22.5 && diff <= 157.5);

        boolean doSprint = walkerSprint.get() && sprint;
        mc.options.keySprint.setDown(doSprint);
        mc.player.setSprinting(doSprint);
    }

    private void stopMovement() {
        if (mc.options == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyShift.setDown(false);
        if (mc.player != null) {
            mc.player.setSprinting(false);
            mc.player.setShiftKeyDown(false);
        }
    }

    private void abortWalk(String reason) {
        failActivePull("Pull cancelled: " + reason);
        stopMovement();
        walkState = WalkState.ABORTED;
        resetWalkerFields();
        if (mc.player != null)
            sendPlayerMessage(Component.literal("[Waypearl] Walker aborted: " + reason), false);
    }

    private void resetWalkerFields() {
        walkGeneration++;
        walkPearlId = null;
        walkWorld = null;
        walkInteractionPending = false;
        walkTarget   = null;
        idlePosition = null;
        walkTicks    = 0;
        stuckTicks   = 0;
        jumpCooldown = 0;
        lastPos      = null;
        waypoints.clear();
    }

    private void resetWalker() {
        walkState = WalkState.IDLE;
        resetWalkerFields();
    }

    private BlockPos findTrapdoor(int x, int topY, int z) {
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos pos = new BlockPos(x, topY + dy, z);
            if (isAnyTrapdoor(mc.level.getBlockState(pos).getBlock())) return pos;
        }
        return null;
    }

    private boolean isAnyTrapdoor(Block block) {
        return block instanceof TrapDoorBlock
            || block == Blocks.OAK_TRAPDOOR      || block == Blocks.SPRUCE_TRAPDOOR
            || block == Blocks.BIRCH_TRAPDOOR    || block == Blocks.JUNGLE_TRAPDOOR
            || block == Blocks.ACACIA_TRAPDOOR   || block == Blocks.DARK_OAK_TRAPDOOR
            || block == Blocks.MANGROVE_TRAPDOOR || block == Blocks.CHERRY_TRAPDOOR
            || block == Blocks.BAMBOO_TRAPDOOR || block == Blocks.CRIMSON_TRAPDOOR
            || block == Blocks.WARPED_TRAPDOOR   || block == Blocks.IRON_TRAPDOOR;
    }

    private void interactTrapdoor(BlockPos pos) {
        interactTrapdoor(pos, null);
    }

    private void interactTrapdoor(BlockPos pos, Runnable onComplete) {
        if (pos == null) return;
        PullFollowup job = activePull;
        Level interactionWorld = mc.level;
        long generation = walkGeneration;
        Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), () -> {
            if (!isActive() || mc.level != interactionWorld || mc.player == null || mc.gameMode == null
                || (onComplete != null && (generation != walkGeneration || walkState != WalkState.PULLING))
                || (job != null && activePull != job)) return;
            if (job != null && (!isMessengerPeerAllowed(job.requester) || (job.direct
                && (!messengerAvailable() || messengerTransport == null
                    || !job.requester.equalsIgnoreCase(messengerTransport.peer()))))) {
                failActivePull("Requester disconnected or is no longer whitelisted.");
                if (onComplete != null) onComplete.run();
                return;
            }
            if (!isAnyTrapdoor(mc.level.getBlockState(pos).getBlock())
                || com.example.addon.utils.MinecraftCompat.position(mc.player).distanceTo(Vec3.atCenterOf(pos)) > interactReach.get()
                || (onComplete != null && (walkPearlId == null || !pearlStillExists(walkPearlId)))) {
                warning("The pearl or trapdoor is no longer available or within reach.");
                failActivePull("The pearl or trapdoor is no longer available or within reach.");
                if (onComplete != null) onComplete.run();
                return;
            }
            if (mc.player.isShiftKeyDown()) {
                warning("Cannot activate the trapdoor while sneaking; release sneak and try again.");
                failActivePull("Cannot activate the trapdoor while sneaking.");
                if (onComplete != null) onComplete.run();
                return;
            }
            if (mc.level.getBlockState(pos).is(Blocks.IRON_TRAPDOOR)) {
                warning("Iron trapdoors require redstone and cannot be opened by hand.");
                failActivePull("The iron trapdoor requires redstone and cannot be opened by hand.");
                if (onComplete != null) onComplete.run();
                return;
            }
            int previousSlot = com.example.addon.utils.MinecraftCompat.selectedSlot(mc.player.getInventory());
            boolean[] emptySlots = new boolean[9];
            for (int i = 0; i < emptySlots.length; i++) {
                emptySlots[i] = mc.player.getInventory().getItem(i).isEmpty();
            }
            int slot = WaypearlInteractionHand.select(previousSlot, mc.player.getOffhandItem().isEmpty(), emptySlots);
            if (slot == WaypearlInteractionHand.UNAVAILABLE) {
                warning("Cannot safely interact: empty an offhand or hotbar slot first.");
                failActivePull("Cannot safely interact: the assistant needs an empty offhand or hotbar slot.");
                if (onComplete != null) onComplete.run();
                return;
            }
            Vec3          hitVec = Vec3.atCenterOf(pos);
            BlockHitResult hit    = new BlockHitResult(hitVec, Direction.UP, pos, false);
            InteractionHand hand = slot == WaypearlInteractionHand.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            boolean switched = hand == InteractionHand.MAIN_HAND && slot != previousSlot;
            if (switched) Hotbar.select(slot);
            InteractionResult result;
            try {
                result = mc.gameMode.useItemOn(mc.player, hand, hit);
            } finally {
                if (switched) Hotbar.select(previousSlot);
            }

            String base = result.consumesAction() ? "[Waypearl] Trapdoor interaction sent." : "[Waypearl] Interaction failed.";
            sendPlayerMessage(Component.literal(base), false);
            if (job != null) {
                if (result.consumesAction()) {
                    job.interactionSent = true;
                    job.confirmationDeadline = System.currentTimeMillis() + 15_000L;
                } else failActivePull("The trapdoor interaction failed.");
            }
            if (onComplete != null) onComplete.run();
        });
    }

    private void triggerColumnScan() {
        if (!scanPending.compareAndSet(false, true)) return;

        final BlockPos origin   = mc.player.blockPosition();
        final int      r        = range.get();
        final int      chunkR   = (r >> 4) + 1;
        final int      originCX = origin.getX() >> 4;
        final int      originCZ = origin.getZ() >> 4;

        final Map<Long, LevelChunk> snapshot = new HashMap<>();
        for (int cx = originCX - chunkR; cx <= originCX + chunkR; cx++) {
            for (int cz = originCZ - chunkR; cz <= originCZ + chunkR; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                if (chunk != null) snapshot.put(ChunkPos.pack(cx, cz), chunk);
            }
        }

        Thread.ofVirtual().name("Waypearl-scan").start(() -> {
            try { runColumnScan(origin, r, snapshot); }
            finally { scanPending.set(false); }
        });
    }

    private void runColumnScan(BlockPos origin, int r, Map<Long, LevelChunk> chunks) {
        Map<String, int[]> yExtents = new HashMap<>();
        Map<String, Vec3[]> newLines = new LinkedHashMap<>();

        int minX = origin.getX() - r, maxX = origin.getX() + r,
            minY = Math.max(origin.getY() - r, -64),
            maxY = Math.min(origin.getY() + r, 320),
            minZ = origin.getZ() - r, maxZ = origin.getZ() + r;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                LevelChunk chunk = chunks.get(ChunkPos.pack(x >> 4, z >> 4));
                if (chunk == null) continue;

                int lowestY = Integer.MAX_VALUE, highestY = Integer.MIN_VALUE;
                for (int y = minY; y <= maxY; y++) {
                    if (chunk.getBlockState(new BlockPos(x, y, z)).getBlock() != Blocks.BUBBLE_COLUMN) continue;
                    if (y < lowestY)  lowestY  = y;
                    if (y > highestY) highestY = y;
                }
                if (lowestY == Integer.MAX_VALUE) continue;

                int srcY = lowestY - 1, srcFloor = Math.max(srcY - 384, -64);
                while (srcY >= srcFloor && chunk.getBlockState(new BlockPos(x, srcY, z)).getBlock() == Blocks.BUBBLE_COLUMN)
                    srcY--;

                if (chunk.getBlockState(new BlockPos(x, srcY, z)).getBlock() != Blocks.SOUL_SAND)
                    continue;

                String key = x + "," + z;
                int fx = x, fz = z, flo = lowestY, fhi = highestY;
                yExtents.compute(key, (k, e) -> {
                    if (e == null) return new int[]{ fx, flo, fhi, fz };
                    e[1] = Math.min(e[1], flo);
                    e[2] = Math.max(e[2], fhi);
                    return e;
                });
            }
        }

        for (Map.Entry<String, int[]> entry : yExtents.entrySet()) {
            int[] e = entry.getValue();
            newLines.put(entry.getKey(), new Vec3[]{
                new Vec3(e[0] + 0.5, e[1],     e[3] + 0.5),
                new Vec3(e[0] + 0.5, e[2] + 1, e[3] + 0.5)
            });
        }
        columnLines.set(newLines);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        if (renderMode.get() == RenderMode.Default) {
            renderDefaultMode(event);
        } else {
            renderPearlsOnlyMode(event);
        }
    }

    private void renderDefaultMode(Render3DEvent event) {
        boolean     doBeam = columnsEnabled.get();
        CapPosition cap    = capPosition.get();
        boolean     doCap  = cap != CapPosition.NONE;
        if (!doBeam && !doCap) return;

        int r = range.get();
        Map<String, Vec3[]> lines = getColumnLines();
        
        Map<String, Vec3> activePearlPos = new HashMap<>();
        Set<Integer> activePearlIds = new HashSet<>();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getType() != EntityType.ENDER_PEARL) continue;
            if (mc.player.distanceTo(e) > r) continue;

            Vec3 pos = e.getPosition(com.example.addon.utils.MinecraftCompat.tickProgress(mc.getDeltaTracker(), true));
            int    px  = (int) Math.floor(e.getX());
            int    pz  = (int) Math.floor(e.getZ());
            String key = px + "," + pz;
            Vec3[] line = lines.get(key);

            if (line != null && pos.y >= line[0].y) {
                activePearlPos.put(key, pos);
                activePearlIds.add(e.getId());
            }
        }

        SettingColor core      = coreColor.get();
        SettingColor glow      = glowColor.get();
        double       halfCore  = coreWidth.get();
        double       spread    = glowSpread.get();
        int          layers    = glowLayers.get();
        int          baseAlpha = glowBaseAlpha.get();

        SettingColor capCol   = capColor.get();
        double       capHalf  = capSize.get();
        double       capThick = capThickness.get();
        ShapeMode    capMode  = capShapeMode.get();
        boolean      capBloom = capGlow.get();

        for (Map.Entry<String, Vec3[]> entry : lines.entrySet()) {
            Vec3 pearlPos = activePearlPos.get(entry.getKey());
            if (pearlPos == null) continue;

            Vec3[] line = entry.getValue();
            double cx = pearlPos.x;
            double cz = pearlPos.z;
            double botY = line[0].y;
            double topY = pearlPos.y;

            if (doBeam) {
                drawGlowBeam(event, cx, botY, cz, topY, core, glow, halfCore, spread, layers, baseAlpha);
            }

            if (doCap) {
                boolean db = (cap == CapPosition.BOTTOM || cap == CapPosition.BOTH);
                boolean dt = (cap == CapPosition.TOP    || cap == CapPosition.BOTH);
                if (db) {
                    drawCapBox(event, cx, botY, cz, capHalf, capThick, capCol, capMode, capBloom, spread, layers, baseAlpha);
                }
                if (dt) {
                    drawCapBox(event, cx, topY, cz, capHalf, capThick, capCol, capMode, capBloom, spread, layers, baseAlpha);
                }
            }
        }

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ThrownEnderpearl pearl)) continue;
            if (mc.player.distanceTo(e) > r) continue;
            if (!activePearlIds.contains(pearl.getId())) continue;

            boolean isOwn = pearl.getOwner() != null && pearl.getOwner().equals(mc.player);

            if (isOwn && highlightOwnPearl.get()) {
                GlowingRegistry.add(pearl.getId(), buildGlowArgb(ownPearlColor.get(), 3));
                trackedGlowIds.add(pearl.getId());
            } else if (!isOwn) {
                GlowingRegistry.add(pearl.getId(), buildGlowArgb(coreColor.get(), 3));
                trackedGlowIds.add(pearl.getId());
            }
        }

        for (Iterator<Integer> it = trackedGlowIds.iterator(); it.hasNext(); ) {
            int id = it.next();
            if (!activePearlIds.contains(id) || mc.level.getEntity(id) == null) {
                GlowingRegistry.remove(id);
                it.remove();
            }
        }
    }

    private void renderPearlsOnlyMode(Render3DEvent event) {
        int r = range.get();
        Set<Integer> activePearlIds = new HashSet<>();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ThrownEnderpearl pearl)) continue;
            if (mc.player.distanceTo(e) > r) continue;

            int pearlId = pearl.getId();
            activePearlIds.add(pearlId);
            boolean isOwn = pearl.getOwner() != null && pearl.getOwner().equals(mc.player);

            if (isOwn && highlightOwnPearl.get()) {
                if (poOwnOutlineEnabled.get()) {
                    GlowingRegistry.add(pearlId, buildGlowArgb(poOwnOutlineColor.get(), poOwnGlowStrength.get()));
                    trackedGlowIds.add(pearlId);
                } else {
                    GlowingRegistry.remove(pearlId);
                    trackedGlowIds.remove(pearlId);
                }
            } else {
                if (poOutlineEnabled.get()) {
                    GlowingRegistry.add(pearlId, buildGlowArgb(poOutlineColor.get(), poGlowStrength.get()));
                    trackedGlowIds.add(pearlId);
                } else {
                    GlowingRegistry.remove(pearlId);
                    trackedGlowIds.remove(pearlId);
                }
            }

            if (isInBubbleColumn(pearl)) {
                if (isOwn && highlightOwnPearl.get()) {
                    if (poOwnBeamEnabled.get()) {
                        renderPearlsOnlyBeam(event, pearl, poOwnBeamColor.get(),
                                poOwnBeamInnerRadius.get().floatValue(), poOwnBeamOuterRadius.get().floatValue(),
                                poOwnBeamHeight.get());
                    }
                } else {
                    if (poBeamEnabled.get()) {
                        renderPearlsOnlyBeam(event, pearl, poBeamColor.get(),
                                poBeamInnerRadius.get().floatValue(), poBeamOuterRadius.get().floatValue(),
                                poBeamHeight.get());
                    }
                }
            }
        }

        for (Iterator<Integer> it = trackedGlowIds.iterator(); it.hasNext(); ) {
            int id = it.next();
            if (!activePearlIds.contains(id) || mc.level.getEntity(id) == null) {
                GlowingRegistry.remove(id);
                it.remove();
            }
        }

        BEAM_POS_CACHE.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    private int buildGlowArgb(SettingColor c, int strength) {
        float factor = 1f + (strength - 1) * 0.15f;
        int r = Math.min(255, (int)(c.r * factor));
        int g = Math.min(255, (int)(c.g * factor));
        int b = Math.min(255, (int)(c.b * factor));
        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    private boolean isInBubbleColumn(ThrownEnderpearl pearl) {
        if (mc.level == null) return false;
        BlockPos pos = BlockPos.containing(com.example.addon.utils.MinecraftCompat.position(pearl));
        return mc.level.getBlockState(pos).is(Blocks.BUBBLE_COLUMN)
            || mc.level.getBlockState(pos.below()).is(Blocks.BUBBLE_COLUMN);
    }

    private Vec3 getOrCreateBeamPos(ThrownEnderpearl pearl) {
        return BEAM_POS_CACHE.computeIfAbsent(pearl.getId(), id -> {
            double x = pearl.getX();
            double y = pearl.getY();
            double z = pearl.getZ();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

            for (int i = 1; i < 256; i++) {
                pos.set((int) Math.floor(x), (int) (y - i), (int) Math.floor(z));
                BlockState state = mc.level.getBlockState(pos);

                if (!state.is(Blocks.WATER) && !state.is(Blocks.BUBBLE_COLUMN)) {
                    return new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                }
            }
            return new Vec3(Math.floor(x) + 0.5, y - 256, Math.floor(z) + 0.5);
        });
    }

    private void renderPearlsOnlyBeam(Render3DEvent event, ThrownEnderpearl pearl,
                                      SettingColor color, float innerRadius, float outerRadius, int height) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = com.example.addon.utils.MinecraftCompatCamera.position(camera);
        Vec3 base = getOrCreateBeamPos(pearl);

        com.example.addon.utils.MinecraftApiCompat.renderPearlBeam(
            mc, event, base, camPos, BEAM_TEXTURE, event.tickDelta,
            mc.level.getGameTime(), height, color.getPacked(), innerRadius, outerRadius
        );
    }

    private void drawGlowBeam(Render3DEvent event, double cx, double botY, double cz, double topY,
            SettingColor core, SettingColor glow, double halfCore,
            double spread, int layers, int baseAlpha) {
        for (int i = layers; i >= 1; i--) {
            double exp   = spread * i;
            int    alpha = Math.max(4, (int)(baseAlpha * (1.0 - (double)(i - 1) / layers)));
            event.renderer.box(new AABB(cx - halfCore - exp, botY, cz - halfCore - exp,
                                       cx + halfCore + exp, topY, cz + halfCore + exp),
                withAlpha(glow, alpha), withAlpha(glow, 0), ShapeMode.Sides, 0);
        }
        event.renderer.box(new AABB(cx - halfCore, botY, cz - halfCore,
                                   cx + halfCore, topY, cz + halfCore),
            withAlpha(core, 180), core, ShapeMode.Both, 0);
    }

    private void drawCapBox(Render3DEvent event, double cx, double y, double cz,
            double halfXZ, double halfY, SettingColor color, ShapeMode mode,
            boolean bloom, double spread, int layers, int baseAlpha) {
        double minY = y - halfY, maxY = y + halfY;
        if (bloom) {
            for (int i = layers; i >= 1; i--) {
                double exp   = spread * i;
                int    alpha = Math.max(4, (int)(baseAlpha * (1.0 - (double)(i - 1) / layers)));
                event.renderer.box(new AABB(cx - halfXZ - exp, minY, cz - halfXZ - exp,
                                           cx + halfXZ + exp, maxY, cz + halfXZ + exp),
                    withAlpha(color, alpha), withAlpha(color, 0), ShapeMode.Sides, 0);
            }
        }
        event.renderer.box(new AABB(cx - halfXZ, minY, cz - halfXZ,
                                   cx + halfXZ, maxY, cz + halfXZ),
            withAlpha(color, color.a), color, mode, 0);
    }

    private double xzDist(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private SettingColor withAlpha(SettingColor c, int alpha) {
        return new SettingColor(c.r, c.g, c.b, Math.min(255, Math.max(0, alpha)));
    }
}