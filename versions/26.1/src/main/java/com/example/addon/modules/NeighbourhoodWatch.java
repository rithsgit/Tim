package com.example.addon.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.example.addon.Tim;
import com.example.addon.hud.LastSeenPlayerHud;
import com.example.addon.utils.GlowingRegistry;
import com.example.addon.utils.MinecraftCompat;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class NeighbourhoodWatch extends Module {

    public enum PlayerStatus { Friend, Enemy, Proxy, Other }

    public enum TabEvent   { Join, Leave, Both }
    public enum TabFilter  { Friends, Enemies, Proxies, Others, All }
    public enum FilterMode { Censor, AutoIgnore }

    public enum HighlightMode {
        Wireframe("Wireframe"),
        Spectral("Spectral");

        private final String title;
        HighlightMode(String title) { this.title = title; }

        @Override public String toString() { return title; }
    }

    public enum DangerSound {
        Off(null),
        WardenRoar(SoundEvents.WARDEN_ROAR),
        DragonGrowl(SoundEvents.ENDER_DRAGON_GROWL),
        RavagerRoar(SoundEvents.RAVAGER_ROAR),
        EndermanStare(SoundEvents.ENDERMAN_STARE),
        ElderGuardianCurse(SoundEvents.ELDER_GUARDIAN_CURSE),
        WitherDeath(SoundEvents.WITHER_DEATH);

        public final SoundEvent event;
        DangerSound(SoundEvent event) { this.event = event; }
    }

    private final SettingGroup sgSafety      = settings.createGroup("Safety");
    private final SettingGroup sgMsgControl  = settings.createGroup("Message Control");
    private final SettingGroup sgTracking    = settings.createGroup("Player Tracking");
    private final SettingGroup sgFriends     = settings.createGroup("Friends & Enemies");
    private final SettingGroup sgTabList     = settings.createGroup("Tab List Monitoring");

    private final Setting<Boolean> disconnectOnPlayer = sgSafety.add(new BoolSetting.Builder()
        .name("disconnect-on-player")
        .description("Disconnects when another player is detected nearby.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> playerDetectionRange = sgSafety.add(new IntSetting.Builder()
        .name("player-detection-range")
        .description("Distance within which a player triggers a disconnect.")
        .defaultValue(32).min(1).sliderMax(128)
        .visible(disconnectOnPlayer::get)
        .build()
    );

    private final Setting<Boolean> ignoreFriendsOnDisconnect = sgSafety.add(new BoolSetting.Builder()
        .name("ignore-friends-on-disconnect")
        .description("Does not disconnect if the nearby player is a friend.")
        .defaultValue(true)
        .visible(disconnectOnPlayer::get)
        .build()
    );

    private final Setting<Boolean> ignoreProxiesOnDisconnect = sgSafety.add(new BoolSetting.Builder()
        .name("ignore-proxies-on-disconnect")
        .description("Does not disconnect if the nearby player is a proxy.")
        .defaultValue(true)
        .visible(disconnectOnPlayer::get)
        .build()
    );

    private final Setting<FilterMode> filterMode = sgMsgControl.add(new EnumSetting.Builder<FilterMode>()
        .name("mode")
        .description("Censor: replaces matched keywords with XXXX. AutoIgnore: runs /ignorehard on the sender.")
        .defaultValue(FilterMode.Censor)
        .build()
    );

    private final Setting<List<String>> ignoreKeywords = sgMsgControl.add(new StringListSetting.Builder()
        .name("keywords")
        .description("Words to act on. Censor mode redacts them; AutoIgnore mode silences the sender. Case-insensitive.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<Boolean> trackPlayers = sgTracking.add(new BoolSetting.Builder()
        .name("track-players")
        .description("Highlights and notifies when players enter visual range.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> trackRange = sgTracking.add(new IntSetting.Builder()
        .name("track-range")
        .description("Distance within which players are tracked.")
        .defaultValue(128).min(1).sliderMax(256)
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<TabFilter> trackFilter = sgTracking.add(new EnumSetting.Builder<TabFilter>()
        .name("track-filter")
        .description("Which player category to highlight and notify for.")
        .defaultValue(TabFilter.Enemies)
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<Boolean> alertNotifications = sgTracking.add(new BoolSetting.Builder()
        .name("alert-notifications")
        .description("Sends chat messages, action bar overlays, and totem pop alerts.")
        .defaultValue(true).visible(trackPlayers::get)
        .build()
    );

    private final Setting<String> customMessage = sgTracking.add(new StringSetting.Builder()
        .name("custom-message")
        .description("Notification message. Use {player} for name and {status} for relation.")
        .defaultValue("Warning: {status} {player} is in visual range!")
        .visible(() -> trackPlayers.get() && alertNotifications.get())
        .build()
    );

    private final Setting<DangerSound> dangerSound = sgTracking.add(new EnumSetting.Builder<DangerSound>()
        .name("danger-sound")
        .description("Sound played when a matching player enters visual range. Off = silent.")
        .defaultValue(DangerSound.Off)
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<Double> soundVolume = sgTracking.add(new DoubleSetting.Builder()
        .name("sound-volume")
        .description("Volume of the danger sound.")
        .defaultValue(1.0).min(0.1).sliderMax(2.5)
        .visible(() -> trackPlayers.get() && dangerSound.get() != DangerSound.Off)
        .build()
    );

    private final Setting<Double> soundPitch = sgTracking.add(new DoubleSetting.Builder()
        .name("sound-pitch")
        .description("Pitch of the danger sound. 1.0 = normal.")
        .defaultValue(1.0).min(0.5).sliderMax(2.0)
        .visible(() -> trackPlayers.get() && dangerSound.get() != DangerSound.Off)
        .build()
    );

    private final Setting<HighlightMode> highlightMode = sgTracking.add(new EnumSetting.Builder<HighlightMode>()
        .name("highlight-mode")
        .description("Wireframe draws custom geometry. Spectral uses custom colors via vanilla glow pipeline.")
        .defaultValue(HighlightMode.Wireframe)
        .visible(trackPlayers::get)
        .build()
    );

    private final Setting<Double> outlineScale = sgTracking.add(new DoubleSetting.Builder()
        .name("outline-scale")
        .description("Scale of the wireframe outline (Wireframe mode only). 1.0 = exact model size.")
        .defaultValue(1.02).min(1.0).sliderMax(2.0)
        .visible(() -> trackPlayers.get() && highlightMode.get() == HighlightMode.Wireframe)
        .build()
    );

    private final Setting<List<String>> friends = sgFriends.add(new StringListSetting.Builder()
        .name("friends").description("Players treated as friends. Case-insensitive.")
        .defaultValue(List.of()).onChanged(l -> updateFriendEnemySets())
        .visible(this::isFriendCategoryVisible)
        .build()
    );

    private final Setting<SettingColor> friendColor = sgFriends.add(new ColorSetting.Builder()
        .name("friend-color").description("Highlight color for friends.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .visible(() -> trackPlayers.get() && isFriendCategoryVisible())
        .build()
    );

    private final Setting<List<String>> enemies = sgFriends.add(new StringListSetting.Builder()
        .name("enemies").description("Players treated as enemies. Case-insensitive.")
        .defaultValue(List.of()).onChanged(l -> updateFriendEnemySets())
        .visible(this::isEnemyCategoryVisible)
        .build()
    );

    private final Setting<SettingColor> enemyColor = sgFriends.add(new ColorSetting.Builder()
        .name("enemy-color").description("Highlight color for enemies.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(() -> trackPlayers.get() && isEnemyCategoryVisible())
        .build()
    );

    private final Setting<List<String>> proxies = sgFriends.add(new StringListSetting.Builder()
        .name("proxies").description("Players treated as proxies. Case-insensitive.")
        .defaultValue(List.of()).onChanged(l -> updateFriendEnemySets())
        .visible(this::isProxyCategoryVisible)
        .build()
    );

    private final Setting<SettingColor> proxyColor = sgFriends.add(new ColorSetting.Builder()
        .name("proxy-color").description("Highlight color for proxies.")
        .defaultValue(new SettingColor(255, 140, 0, 255))
        .visible(() -> trackPlayers.get() && isProxyCategoryVisible())
        .build()
    );

    private final Setting<SettingColor> otherColor = sgFriends.add(new ColorSetting.Builder()
        .name("other-color").description("Highlight color for unknown players.")
        .defaultValue(new SettingColor(139, 0, 0, 255))
        .visible(() -> trackPlayers.get() && isOtherCategoryVisible())
        .build()
    );

    private final Setting<TabEvent> tabEvent = sgTabList.add(new EnumSetting.Builder<TabEvent>()
        .name("event")
        .description("Which tab-list event to notify on.")
        .defaultValue(TabEvent.Both)
        .build()
    );

    private final Setting<TabFilter> tabFilter = sgTabList.add(new EnumSetting.Builder<TabFilter>()
        .name("notify-for")
        .description("Which player category triggers a notification.")
        .defaultValue(TabFilter.All)
        .build()
    );

    private final Set<Integer> notifiedPlayers    = new HashSet<>();
    private final Set<Integer> activelyOutlined   = new HashSet<>();
    private final Set<String>  ignoredThisSession = new HashSet<>();
    private final Set<String>  playersInTab       = new HashSet<>();
    private final Map<UUID, String> playerNamesInTab = new HashMap<>();
    private final Set<String>  friendSet          = new HashSet<>();
    private final Set<String>  enemySet           = new HashSet<>();
    private final Set<String>  proxySet           = new HashSet<>();
    private final Map<String, Integer> totemPops  = new HashMap<>();
    private final Map<Integer, Integer> spectralColors = new HashMap<>();

    private boolean anyPlayerNearby = false;
    private int actionBarTicks = 0;
    private Component actionBarMessage = null;

    private record StyledTextSegment(String content, Style style) {}

    public NeighbourhoodWatch() {
        super(Tim.CATEGORY, "neighbourhood-watch",
            "Manages player tracking, safety, server monitoring, and keyword alerts.");
    }

    @Override
    public void onActivate() {
        resetState();
        updateFriendEnemySets();
        updatePlayersInTab();
    }

    @Override
    public void onDeactivate() {
        clearAllOutlines();
        resetState();
        anyPlayerNearby = false;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        clearAllOutlines();
        resetState();
        anyPlayerNearby = false;
        updatePlayersInTab();
    }

    private void updatePlayersInTab() {
        if (mc.player == null || mc.player.connection == null) return;
        mc.player.connection.getOnlinePlayers().forEach(entry -> {
            String name = MinecraftCompat.profileName(entry.getProfile());
            if (name != null && !name.isEmpty()) {
                playersInTab.add(name);
                playerNamesInTab.put(MinecraftCompat.profileId(entry.getProfile()), name);
            }
        });
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        if (tickDisconnectOnPlayer()) return;
        tickPlayerTracking();
        tickOutlineShader();
        tickActionBar();
    }

    private void tickActionBar() {
        if (actionBarTicks > 0 && actionBarMessage != null) {
            mc.gui.setOverlayMessage(actionBarMessage, false);
            actionBarTicks--;
            if (actionBarTicks == 0) {
                actionBarMessage = null;
            }
        }
    }

    private void tickOutlineShader() {
        if (!trackPlayers.get()) {
            clearAllOutlines();
            return;
        }

        boolean spectral = highlightMode.get() == HighlightMode.Spectral;
        Set<Integer> newlyActive = new HashSet<>();
        Map<Integer, Integer> newSpectralColors = new HashMap<>();

        for (Player player : mc.level.players()) {
            if (isLocalPlayer(player) || player.isSpectator()) continue;
            if (mc.player.distanceTo(player) > trackRange.get()) continue;

            String       name   = player.getName().getString();
            PlayerStatus status = getPlayerStatusPublic(name);

            boolean shouldHighlight = trackFilter.get() == TabFilter.All || switch (status) {
                case Friend -> trackFilter.get() == TabFilter.Friends;
                case Enemy  -> trackFilter.get() == TabFilter.Enemies;
                case Proxy  -> trackFilter.get() == TabFilter.Proxies;
                case Other  -> trackFilter.get() == TabFilter.Others;
            };
            if (!shouldHighlight) continue;

            if (spectral) {
                SettingColor color = switch (status) {
                    case Friend -> friendColor.get();
                    case Enemy  -> enemyColor.get();
                    case Proxy  -> proxyColor.get();
                    case Other  -> otherColor.get();
                };
                newSpectralColors.put(player.getId(), toArgb(color));
            }

            newlyActive.add(player.getId());
        }

        for (int id : activelyOutlined) {
            if (!newlyActive.contains(id) || !spectral) {
                GlowingRegistry.remove(id);
            }
        }

        spectralColors.clear();
        spectralColors.putAll(newSpectralColors);

        if (spectral) {
            for (Map.Entry<Integer, Integer> entry : spectralColors.entrySet()) {
                GlowingRegistry.add(entry.getKey(), entry.getValue());
            }
        }

        activelyOutlined.clear();
        activelyOutlined.addAll(newlyActive);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        if (trackPlayers.get() && highlightMode.get() == HighlightMode.Spectral) {
            for (Map.Entry<Integer, Integer> entry : spectralColors.entrySet()) {
                GlowingRegistry.add(entry.getKey(), entry.getValue());
            }
        }

        if (trackPlayers.get() && highlightMode.get() == HighlightMode.Wireframe) {
            for (Player player : mc.level.players()) {
                if (isLocalPlayer(player)) continue;
                if (!activelyOutlined.contains(player.getId())) continue;

                String       name   = player.getName().getString();
                PlayerStatus status = getPlayerStatusPublic(name);
                SettingColor color  = switch (status) {
                    case Friend -> friendColor.get();
                    case Enemy  -> enemyColor.get();
                    case Proxy  -> proxyColor.get();
                    case Other  -> otherColor.get();
                };

                WireframeEntityRenderer.render(
                    event, player, outlineScale.get(),
                    withAlpha(color, 0), color, ShapeMode.Lines
                );
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.level != null && event.packet instanceof ClientboundEntityEventPacket statusPacket) {
            if (statusPacket.getEventId() == 35) {
                Entity entity = statusPacket.getEntity(mc.level);
                if (entity instanceof Player player && !isLocalPlayer(player)) {
                    handleTotemPop(player);
                }
            }
        }

        if (event.packet instanceof ClientboundPlayerInfoRemovePacket packet) {
            for (UUID profileId : packet.profileIds()) {
                String name = playerNamesInTab.remove(profileId);
                if (name != null && playersInTab.remove(name)) handleTabListChange(name, "left");
            }
            return;
        }

        if (!(event.packet instanceof ClientboundPlayerInfoUpdatePacket packet)) return;

        for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
            if (entry.profile() == null) continue;
            String name = MinecraftCompat.profileName(entry.profile());
            if (name == null || name.isEmpty()) continue;
            UUID profileId = MinecraftCompat.profileId(entry.profile());

            if (packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
                playerNamesInTab.put(profileId, name);
                if (playersInTab.add(name)) {
                    handleTabListChange(name, "joined");
                }
            }
        }
    }

    @EventHandler
    private void onReceiveMessage(meteordevelopment.meteorclient.events.game.ReceiveMessageEvent event) {
        if (mc.player == null || mc.player.connection == null) return;
        if (ignoreKeywords.get().isEmpty()) return;

        if (filterMode.get() == FilterMode.AutoIgnore) {
            parseMessageForAutoIgnore(event.getMessage().getString());
        } else {
            Component original = event.getMessage();
            Component censored = censorText(original);
            if (!censored.getString().equals(original.getString())) event.setMessage(censored);
        }
    }

    private Component censorText(Component text) {
        List<StyledTextSegment> segments = new ArrayList<>();
        StringBuilder plainText = new StringBuilder();
        text.visit((style, content) -> {
            segments.add(new StyledTextSegment(content, style));
            plainText.append(content);
            return Optional.empty();
        }, Style.EMPTY);

        String censored = censorMessage(plainText.toString());
        if (censored == null) return text;

        MutableComponent result = Component.empty();
        int offset = 0;
        for (StyledTextSegment segment : segments) {
            int end = offset + segment.content().length();
            result.append(Component.literal(censored.substring(offset, end)).setStyle(segment.style()));
            offset = end;
        }
        return result;
    }

    private boolean tickDisconnectOnPlayer() {
        if (!disconnectOnPlayer.get()) return false;

        for (Player player : mc.level.players()) {
            if (isLocalPlayer(player) || player.isCreative() || player.isSpectator()) continue;
            if (ignoreFriendsOnDisconnect.get()  && isFriend(player.getName().getString())) continue;
            if (ignoreProxiesOnDisconnect.get()  && isProxy(player.getName().getString()))  continue;
            if (mc.player.distanceTo(player) <= playerDetectionRange.get()) {
                disconnect("[NeighbourhoodWatch] Player detected: " + player.getName().getString());
                return true;
            }
        }
        return false;
    }

    private void tickPlayerTracking() {
        if (!trackPlayers.get()) {
            anyPlayerNearby = false;
            notifiedPlayers.clear();
            return;
        }

        anyPlayerNearby = false;
        Set<Integer> playersInVisualRangeThisTick = new HashSet<>();
        Set<Integer> eligiblePlayersThisTick = new HashSet<>();

        for (Player player : mc.level.players()) {
            if (isLocalPlayer(player) || player.isSpectator()) continue;
            if (mc.player.distanceTo(player) > trackRange.get()) continue;

            anyPlayerNearby = true;
            playersInVisualRangeThisTick.add(player.getId());

            String       name   = player.getName().getString();
            PlayerStatus status = getPlayerStatusPublic(name);

            boolean isNewlySpotted = !notifiedPlayers.contains(player.getId());

            if (isNewlySpotted) {
                Hud hudSystem = Systems.get(Hud.class);
                if (hudSystem != null) {
                    for (HudElement element : hudSystem) {
                        if (element instanceof LastSeenPlayerHud lastSeenHud) {
                            lastSeenHud.updateLastSeen(player);
                            break;
                        }
                    }
                }
            }

            boolean shouldNotify = trackFilter.get() == TabFilter.All || switch (status) {
                case Friend -> trackFilter.get() == TabFilter.Friends;
                case Enemy  -> trackFilter.get() == TabFilter.Enemies;
                case Proxy  -> trackFilter.get() == TabFilter.Proxies;
                case Other  -> trackFilter.get() == TabFilter.Others;
            };
            if (!shouldNotify) continue;

            eligiblePlayersThisTick.add(player.getId());
            if (isNewlySpotted) {
                String statusStr = status.name().toLowerCase();
                String msg = customMessage.get()
                    .replace("{player}", name)
                    .replace("{status}", statusStr);

                if (alertNotifications.get()) {
                    info(msg);

                    actionBarMessage = Component.literal(msg).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                    actionBarTicks = 40 + (int)(Math.random() * 61);
                }

                DangerSound sound = dangerSound.get();
                if (sound != DangerSound.Off && sound.event != null) {
                    mc.player.playSound(
                        sound.event,
                        soundVolume.get().floatValue(),
                        soundPitch.get().floatValue()
                    );
                }
            }
        }

        for (Integer id : notifiedPlayers) {
            if (!playersInVisualRangeThisTick.contains(id)) {
                Player player = (Player) mc.level.getEntity(id);
                if (player != null) {
                    String name = player.getName().getString();
                    PlayerStatus status = getPlayerStatusPublic(name);

                    boolean shouldNotify = trackFilter.get() == TabFilter.All || switch (status) {
                        case Friend -> trackFilter.get() == TabFilter.Friends;
                        case Enemy  -> trackFilter.get() == TabFilter.Enemies;
                        case Proxy  -> trackFilter.get() == TabFilter.Proxies;
                        case Other  -> trackFilter.get() == TabFilter.Others;
                    };

                    if (shouldNotify && alertNotifications.get()) {
                        info("§e%s has left visual range.", name);

                        actionBarMessage = Component.literal(name + " left visual range!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
                        actionBarTicks = 40 + (int)(Math.random() * 61);
                    }
                }
            }
        }

        notifiedPlayers.clear();
        notifiedPlayers.addAll(eligiblePlayersThisTick);
    }

    private void handleTotemPop(Player player) {
        if (!alertNotifications.get()) return;

        String name = player.getName().getString();
        int pops = totemPops.getOrDefault(name, 0) + 1;
        totemPops.put(name, pops);

        String popMsg = name + " just popped a totem! (" + pops + ")";

        info("§d" + popMsg);

        actionBarMessage = Component.literal(popMsg).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        actionBarTicks = 40 + (int)(Math.random() * 61);
    }

    private void handleTabListChange(String playerName, String action) {
        PlayerStatus status = getPlayerStatusPublic(playerName);

        if (tabEvent.get() != TabEvent.Both) {
            TabEvent eventType = action.equals("joined") ? TabEvent.Join : TabEvent.Leave;
            if (tabEvent.get() != eventType) return;
        }

        boolean shouldNotify = tabFilter.get() == TabFilter.All || switch (status) {
            case Friend -> tabFilter.get() == TabFilter.Friends;
            case Enemy  -> tabFilter.get() == TabFilter.Enemies;
            case Proxy  -> tabFilter.get() == TabFilter.Proxies;
            case Other  -> tabFilter.get() == TabFilter.Others;
        };
        if (!shouldNotify) return;

        String label = switch (status) {
            case Friend -> "§aFriend";
            case Enemy  -> "§cEnemy";
            case Proxy  -> "§6Proxy";
            case Other  -> "Player";
        };
        info("%s %s has %s the server.", label, playerName, action);
    }

    private String[] parseSenderAndBody(String rawMessage) {
        if (rawMessage.startsWith("<")) {
            int close = rawMessage.indexOf('>');
            if (close < 1) return null;
            return new String[]{ rawMessage.substring(1, close).trim(),
                                 rawMessage.substring(close + 1).trim() };
        }
        int colon = rawMessage.indexOf(':');
        if (colon < 1 || colon >= 20) return null;
        String name = rawMessage.substring(0, colon);
        if (name.contains(" ")) return null;
        return new String[]{ name.trim(), rawMessage.substring(colon + 1).trim() };
    }

    private String findKeyword(String body) {
        String search = body.toLowerCase();
        for (String kw : ignoreKeywords.get()) {
            if (kw.isBlank()) continue;
            if (search.contains(kw.toLowerCase())) return kw;
        }
        return null;
    }

    private String censorMessage(String rawMessage) {
        String  working = rawMessage;
        boolean changed = false;
        for (String kw : ignoreKeywords.get()) {
            if (kw.isBlank()) continue;
            String replacement = "X".repeat(kw.length());
            String replaced = working.replaceAll("(?i)" + java.util.regex.Pattern.quote(kw), replacement);
            if (!replaced.equals(working)) { working = replaced; changed = true; }
        }
        return changed ? working : null;
    }

    private void parseMessageForAutoIgnore(String rawMessage) {
        String[] parts = parseSenderAndBody(rawMessage);
        if (parts == null) return;
        String sender = parts[0], messageBody = parts[1];

        if (sender.equalsIgnoreCase(mc.player.getName().getString())) return;
        if (isFriend(sender) || isProxy(sender)) return;
        if (ignoredThisSession.contains(sender.toLowerCase())) return;
        if (findKeyword(messageBody) == null) return;

        mc.player.connection.sendCommand("ignorehard " + sender);
        ignoredThisSession.add(sender.toLowerCase());
        info("Auto-ignored %s (keyword match).", sender);
    }

    private void clearAllOutlines() {
        for (int id : activelyOutlined) {
            GlowingRegistry.remove(id);
        }
        if (mc.player != null) {
            GlowingRegistry.remove(mc.player.getId());
        }
        activelyOutlined.clear();
        spectralColors.clear();
    }

    private void resetState() {
        notifiedPlayers.clear();
        ignoredThisSession.clear();
        playersInTab.clear();
        playerNamesInTab.clear();
        actionBarTicks = 0;
        actionBarMessage = null;
        totemPops.clear();
    }

    private void updateFriendEnemySets() {
        friendSet.clear();
        for (String name : friends.get()) friendSet.add(name.toLowerCase());
        enemySet.clear();
        for (String name : enemies.get()) enemySet.add(name.toLowerCase());
        proxySet.clear();
        for (String name : proxies.get()) proxySet.add(name.toLowerCase());
    }

    private void disconnect(String reason) {
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.getConnection().disconnect(Component.literal(reason));
        }
        this.toggle();
    }

    private boolean isLocalPlayer(Player player) {
        if (player == null || mc.player == null) return false;
        if (player == mc.player) return true;
        if (player.getId() == mc.player.getId()) return true;

        if (player.getUUID() != null && mc.player.getUUID() != null) {
            if (player.getUUID().equals(mc.player.getUUID())) return true;
        }

        if (player.getGameProfile() != null && mc.player.getGameProfile() != null) {
            String name1 = MinecraftCompat.profileName(player.getGameProfile());
            String name2 = MinecraftCompat.profileName(mc.player.getGameProfile());
            if (name1 != null && name2 != null && name1.equalsIgnoreCase(name2)) return true;
        }

        return false;
    }

    private SettingColor withAlpha(SettingColor color, int alpha) {
        return new SettingColor(color.r, color.g, color.b, Math.min(255, Math.max(0, alpha)));
    }

    private static int toArgb(SettingColor color) {
        return (color.a << 24) | (color.r << 16) | (color.g << 8) | color.b;
    }

    private boolean isFriendCategoryVisible() {
        return trackFilter.get() == TabFilter.Friends || trackFilter.get() == TabFilter.All
            || tabFilter.get()   == TabFilter.Friends || tabFilter.get()   == TabFilter.All;
    }

    private boolean isEnemyCategoryVisible() {
        return trackFilter.get() == TabFilter.Enemies || trackFilter.get() == TabFilter.All
            || tabFilter.get()   == TabFilter.Enemies || tabFilter.get()   == TabFilter.All;
    }

    private boolean isProxyCategoryVisible() {
        return trackFilter.get() == TabFilter.Proxies || trackFilter.get() == TabFilter.All
            || tabFilter.get()   == TabFilter.Proxies || tabFilter.get()   == TabFilter.All;
    }

    private boolean isOtherCategoryVisible() {
        return trackFilter.get() == TabFilter.Others || trackFilter.get() == TabFilter.All
            || tabFilter.get()   == TabFilter.Others  || tabFilter.get()   == TabFilter.All;
    }

    public boolean isFriend(String name) { return name != null && friendSet.contains(name.toLowerCase()); }
    public boolean isEnemy(String name)  { return name != null && enemySet.contains(name.toLowerCase()); }
    public boolean isProxy(String name)  { return name != null && proxySet.contains(name.toLowerCase()); }

    public PlayerStatus getPlayerStatusPublic(String name) {
        if (isFriend(name)) return PlayerStatus.Friend;
        if (isEnemy(name))  return PlayerStatus.Enemy;
        if (isProxy(name))  return PlayerStatus.Proxy;
        return PlayerStatus.Other;
    }

    public boolean isDisconnectOnPlayerArmed() {
        return disconnectOnPlayer.get();
    }

    public boolean isAnyPlayerNearby() {
        return anyPlayerNearby;
    }
}