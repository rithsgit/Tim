package com.example.addon.hud;

import com.example.addon.Tim;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import java.util.concurrent.ThreadLocalRandom;

public class TimmyIconHud extends HudElement {
    public static final HudElementInfo<TimmyIconHud> INFO = new HudElementInfo<>(
        Tim.HUD_GROUP,
        "timmy-icon",
        "Timmy Icon",
        "Displays Timmy with adjustable appearance, blinking, angry expressions, and a shout image when his sound plays.",
        TimmyIconHud::new
    );

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("tim", "textures/hud/timmy.png");
    private static final Identifier SHOUT_ICON = Identifier.fromNamespaceAndPath("tim", "textures/hud/timmy-shout.png");
    private static final Identifier BLINK_ICON = Identifier.fromNamespaceAndPath("tim", "textures/hud/timmy-blink.png");
    private static final Identifier ANGRY_ONE_ICON = Identifier.fromNamespaceAndPath("tim", "textures/hud/timmy-angry-one.png");
    private static final Identifier ANGRY_TWO_ICON = Identifier.fromNamespaceAndPath("tim", "textures/hud/timmy-angry-two.png");
    private static final Identifier SOUND = Identifier.fromNamespaceAndPath("tim", "timmy");
    private static final SoundEvent SOUND_EVENT = SoundEvent.createVariableRangeEvent(SOUND);
    private static final Identifier SOUND_FILE = Identifier.fromNamespaceAndPath("tim", "sounds/timmy.ogg");
    private static final double BASE_HEIGHT = 64.0;
    private static final double ICON_ASPECT_RATIO = 56.0 / 109.0;
    private static final double SHOUT_ICON_ASPECT_RATIO = 56.0 / 83.0;
    private static final long SHOUT_DURATION_NANOS = 1_500_000_000L;

    private final SettingGroup sgAppearance = settings.createGroup("Appearance");
    private final SettingGroup sgBlink = settings.createGroup("Blink");
    private final SettingGroup sgAngry = settings.createGroup("Angry");
    private final SettingGroup sgSound = settings.createGroup("Sound");
    private final SettingGroup sgSoundSchedule = settings.createGroup("Sound Schedule");

    public enum SoundSchedule { RandomInterval, InGameTime, Sleep }
    public enum TimeUnit { Seconds, Minutes, Hours }
    public enum SleepUnit { Seconds, Minutes, Hours, InGameTicks }
    public enum AngryExpression {
        OneFinger("One finger"),
        TwoFingers("Two fingers"),
        Random("Random");

        private final String label;

        AngryExpression(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Setting<Double> iconScale = sgAppearance.add(new DoubleSetting.Builder()
        .name("icon-scale")
        .description("Scale of the icon.")
        .defaultValue(1.0)
        .min(0.25)
        .sliderRange(0.25, 4.0)
        .build()
    );

    private final Setting<Double> opacity = sgAppearance.add(new DoubleSetting.Builder()
        .name("opacity")
        .description("Opacity of the icon.")
        .defaultValue(100.0)
        .min(0.0)
        .sliderRange(0.0, 100.0)
        .build()
    );

    private final Setting<Double> paddingHorizontal = sgAppearance.add(new DoubleSetting.Builder()
        .name("padding-horizontal")
        .description("Horizontal space around the icon, in pixels.")
        .defaultValue(4.0)
        .min(0.0)
        .sliderRange(0.0, 32.0)
        .build()
    );

    private final Setting<Double> paddingVertical = sgAppearance.add(new DoubleSetting.Builder()
        .name("padding-vertical")
        .description("Vertical space around the icon, in pixels.")
        .defaultValue(4.0)
        .min(0.0)
        .sliderRange(0.0, 32.0)
        .build()
    );

    private final Setting<Boolean> showBackground = sgAppearance.add(new BoolSetting.Builder()
        .name("background")
        .description("Show a background behind the icon.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgAppearance.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color of the background.")
        .defaultValue(new SettingColor(0, 0, 0, 100))
        .visible(showBackground::get)
        .build()
    );

    private final Setting<Boolean> blink = sgBlink.add(new BoolSetting.Builder()
        .name("blink")
        .description("Periodically show the closed-eyes image when timmy-blink.png is available.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minBlinkInterval = sgBlink.add(new IntSetting.Builder()
        .name("minimum-interval")
        .description("Minimum seconds between blinks.")
        .defaultValue(3)
        .min(1)
        .max(60)
        .sliderRange(1, 60)
        .visible(blink::get)
        .build()
    );

    private final Setting<Integer> maxBlinkInterval = sgBlink.add(new IntSetting.Builder()
        .name("maximum-interval")
        .description("Maximum seconds between blinks.")
        .defaultValue(7)
        .min(1)
        .max(60)
        .sliderRange(1, 60)
        .visible(blink::get)
        .build()
    );

    private final Setting<Integer> blinkDuration = sgBlink.add(new IntSetting.Builder()
        .name("duration-ms")
        .description("How long the closed-eyes image is shown.")
        .defaultValue(150)
        .min(50)
        .max(1000)
        .sliderRange(50, 1000)
        .visible(blink::get)
        .build()
    );

    private final Setting<Boolean> angryExpressions = sgAngry.add(new BoolSetting.Builder()
        .name("angry-expressions")
        .description("Show an angry image occasionally or when damage interrupts Sleep Mode.")
        .defaultValue(true)
        .build()
    );

    private final Setting<AngryExpression> angryExpression = sgAngry.add(new EnumSetting.Builder<AngryExpression>()
        .name("expression")
        .description("Choose which angry image to show.")
        .defaultValue(AngryExpression.Random)
        .visible(angryExpressions::get)
        .build()
    );

    private final Setting<Integer> minAngryInterval = sgAngry.add(new IntSetting.Builder()
        .name("minimum-random-interval")
        .description("Minimum seconds between random angry expressions.")
        .defaultValue(60)
        .min(1)
        .max(3600)
        .sliderRange(10, 300)
        .visible(angryExpressions::get)
        .build()
    );

    private final Setting<Integer> maxAngryInterval = sgAngry.add(new IntSetting.Builder()
        .name("maximum-random-interval")
        .description("Maximum seconds between random angry expressions.")
        .defaultValue(180)
        .min(1)
        .max(3600)
        .sliderRange(10, 300)
        .visible(angryExpressions::get)
        .build()
    );

    private final Setting<Integer> angryDuration = sgAngry.add(new IntSetting.Builder()
        .name("duration-seconds")
        .description("How long an angry expression stays visible.")
        .defaultValue(2)
        .min(1)
        .max(30)
        .sliderRange(1, 10)
        .visible(angryExpressions::get)
        .build()
    );

    private final Setting<Boolean> playSound = sgSound.add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Enable sound playback using the selected schedule.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SoundSchedule> soundSchedule = sgSoundSchedule.add(new EnumSetting.Builder<SoundSchedule>()
        .name("schedule")
        .description("Choose randomized intervals, a daily in-game time, or sleep and play the sound on waking.")
        .defaultValue(SoundSchedule.RandomInterval)
        .visible(playSound::get)
        .build()
    );

    private final Setting<TimeUnit> intervalUnit = sgSoundSchedule.add(new EnumSetting.Builder<TimeUnit>()
        .name("interval-unit")
        .description("Unit used for the randomized interval range.")
        .defaultValue(TimeUnit.Minutes)
        .visible(() -> playSound.get() && soundSchedule.get() == SoundSchedule.RandomInterval)
        .build()
    );

    private final Setting<Integer> minInterval = sgSoundSchedule.add(new IntSetting.Builder()
        .name("minimum-interval")
        .description("Minimum randomized delay.")
        .defaultValue(5)
        .min(1)
        .max(60)
        .sliderRange(1, 60)
        .visible(() -> playSound.get() && soundSchedule.get() == SoundSchedule.RandomInterval)
        .build()
    );

    private final Setting<Integer> maxInterval = sgSoundSchedule.add(new IntSetting.Builder()
        .name("maximum-interval")
        .description("Maximum randomized delay.")
        .defaultValue(20)
        .min(1)
        .max(60)
        .sliderRange(1, 60)
        .visible(() -> playSound.get() && soundSchedule.get() == SoundSchedule.RandomInterval)
        .build()
    );

    private final Setting<Integer> inGameTime = sgSoundSchedule.add(new IntSetting.Builder()
        .name("in-game-time")
        .description("Time of day to play the sound, in Minecraft ticks (0-23999).")
        .defaultValue(12000)
        .min(0)
        .max(23999)
        .sliderRange(0, 23999)
        .visible(() -> playSound.get() && soundSchedule.get() == SoundSchedule.InGameTime)
        .build()
    );

    private final Setting<Integer> sleepDuration = sgSoundSchedule.add(new IntSetting.Builder()
        .name("sleep-duration")
        .description("How long Timmy sleeps before waking and playing the sound.")
        .defaultValue(10)
        .min(1)
        .max(24000)
        .sliderRange(1, 120)
        .visible(() -> playSound.get() && soundSchedule.get() == SoundSchedule.Sleep)
        .build()
    );

    private final Setting<SleepUnit> sleepUnit = sgSoundSchedule.add(new EnumSetting.Builder<SleepUnit>()
        .name("sleep-unit")
        .description("Choose real-time seconds, minutes, hours, or in-game ticks.")
        .defaultValue(SleepUnit.Minutes)
        .visible(() -> playSound.get() && soundSchedule.get() == SoundSchedule.Sleep)
        .build()
    );

    private final Setting<Double> soundVolume = sgSound.add(new DoubleSetting.Builder()
        .name("sound-volume")
        .description("Volume of the sound.")
        .defaultValue(0.5)
        .min(0.0)
        .sliderRange(0.0, 1.0)
        .visible(playSound::get)
        .build()
    );

    private long nextSoundNanos = -1;
    private long shoutingUntilNanos = -1;
    private long nextInGameTick = -1;
    private long lastInGameTick = -1;
    private boolean sleepWakePending;
    private boolean sleepScheduled;
    private Level scheduledWorld;
    private String scheduleSettings;
    private long nextBlinkNanos = -1;
    private long blinkUntilNanos = -1;
    private long nextAngryNanos = -1;
    private long angryUntilNanos = -1;
    private Identifier activeAngryIcon;
    private Level angryWorld;
    private int lastPlayerHurtTime;
    private float lastPlayerHealth;
    private float lastPlayerAbsorption;

    public TimmyIconHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean angry = updateAngryState();
        boolean eyesClosed = !angry && isBlinking();
        updateSoundSchedule(eyesClosed);
        boolean shouting = System.nanoTime() < shoutingUntilNanos
            && mc.getResourceManager().getResource(SHOUT_ICON).isPresent();

        if (mc.getResourceManager().getResource(ICON).isEmpty()) {
            if (isInEditor()) {
                String missing = "Missing timmy.png";
                setSize(renderer.textWidth(missing, true), renderer.textHeight(true));
                renderer.text(missing, x, y, Color.WHITE, true);
            } else {
                setSize(0, 0);
            }
            return;
        }

        double height = BASE_HEIGHT * iconScale.get();
        double width = height * (shouting ? SHOUT_ICON_ASPECT_RATIO : ICON_ASPECT_RATIO);
        double padH = paddingHorizontal.get();
        double padV = paddingVertical.get();
        double totalWidth = width + padH * 2;
        double totalHeight = height + padV * 2;
        setSize(totalWidth, totalHeight);

        if (showBackground.get()) {
            renderer.quad(x, y, totalWidth, totalHeight, backgroundColor.get());
        }

        int alpha = (int) Math.round(opacity.get() * 2.55);
        Identifier texture = shouting ? SHOUT_ICON : angry ? activeAngryIcon : eyesClosed ? BLINK_ICON : ICON;
        renderer.texture(texture, x + padH, y + padV, width, height, new Color(255, 255, 255, alpha));
    }

    private boolean updateAngryState() {
        if (mc.level == null || mc.player == null || !angryExpressions.get()
            || mc.getResourceManager().getResource(ANGRY_ONE_ICON).isEmpty()
            || mc.getResourceManager().getResource(ANGRY_TWO_ICON).isEmpty()) {
            resetAngryState();
            return false;
        }

        if (angryWorld != mc.level) {
            angryWorld = mc.level;
            nextAngryNanos = -1;
            angryUntilNanos = -1;
            lastPlayerHurtTime = mc.player.hurtTime;
            lastPlayerHealth = mc.player.getHealth();
            lastPlayerAbsorption = mc.player.getAbsorptionAmount();
        }

        long now = System.nanoTime();
        boolean damaged = mc.player.hurtTime > lastPlayerHurtTime
            || mc.player.getHealth() < lastPlayerHealth
            || mc.player.getAbsorptionAmount() < lastPlayerAbsorption;
        lastPlayerHurtTime = mc.player.hurtTime;
        lastPlayerHealth = mc.player.getHealth();
        lastPlayerAbsorption = mc.player.getAbsorptionAmount();

        if (damaged) {
            if (playSound.get() && soundSchedule.get() == SoundSchedule.Sleep && sleepScheduled) {
                sleepWakePending = true;
            }
            showAngryExpression(now);
        }

        if (nextAngryNanos < 0) {
            scheduleNextAngryExpression(now);
        } else if (now >= nextAngryNanos) {
            showAngryExpression(now);
            scheduleNextAngryExpression(now);
        }

        if (angryUntilNanos >= 0 && now >= angryUntilNanos) {
            angryUntilNanos = -1;
            activeAngryIcon = null;
        }

        return activeAngryIcon != null && now < angryUntilNanos;
    }

    private void showAngryExpression(long now) {
        activeAngryIcon = switch (angryExpression.get()) {
            case OneFinger -> ANGRY_ONE_ICON;
            case TwoFingers -> ANGRY_TWO_ICON;
            case Random -> ThreadLocalRandom.current().nextBoolean() ? ANGRY_ONE_ICON : ANGRY_TWO_ICON;
        };
        angryUntilNanos = now + angryDuration.get() * 1_000_000_000L;
    }

    private void scheduleNextAngryExpression(long now) {
        int lower = Math.min(minAngryInterval.get(), maxAngryInterval.get());
        int upper = Math.max(minAngryInterval.get(), maxAngryInterval.get());
        long seconds = ThreadLocalRandom.current().nextLong(lower, (long) upper + 1);
        nextAngryNanos = now + seconds * 1_000_000_000L;
    }

    private void resetAngryState() {
        angryWorld = null;
        nextAngryNanos = -1;
        angryUntilNanos = -1;
        activeAngryIcon = null;
        if (mc.player != null) {
            lastPlayerHurtTime = mc.player.hurtTime;
            lastPlayerHealth = mc.player.getHealth();
            lastPlayerAbsorption = mc.player.getAbsorptionAmount();
        }
    }

    private boolean isBlinking() {
        if (!blink.get() || mc.getResourceManager().getResource(BLINK_ICON).isEmpty()) {
            nextBlinkNanos = -1;
            blinkUntilNanos = -1;
            return false;
        }

        long now = System.nanoTime();
        if (nextBlinkNanos < 0) {
            scheduleNextBlink(now);
        } else if (now >= blinkUntilNanos && blinkUntilNanos >= 0) {
            blinkUntilNanos = -1;
            scheduleNextBlink(now);
        }

        if (blinkUntilNanos < 0 && now >= nextBlinkNanos) {
            blinkUntilNanos = now + blinkDuration.get() * 1_000_000L;
        }

        return blinkUntilNanos >= 0 && now < blinkUntilNanos;
    }

    private void scheduleNextBlink(long now) {
        int lower = Math.min(minBlinkInterval.get(), maxBlinkInterval.get());
        int upper = Math.max(minBlinkInterval.get(), maxBlinkInterval.get());
        long seconds = ThreadLocalRandom.current().nextLong(lower, (long) upper + 1);
        nextBlinkNanos = now + seconds * 1_000_000_000L;
    }

    private void updateSoundSchedule(boolean eyesClosed) {
        if (!playSound.get() || mc.player == null || mc.level == null
            || mc.getResourceManager().getResource(SOUND_FILE).isEmpty()) {
            resetSoundSchedule();
            return;
        }

        String currentSettings = soundSchedule.get() + ":" + intervalUnit.get() + ":"
            + minInterval.get() + ":" + maxInterval.get() + ":" + inGameTime.get()
            + ":" + sleepDuration.get() + ":" + sleepUnit.get();
        boolean reset = scheduledWorld != mc.level || !currentSettings.equals(scheduleSettings);
        if (reset) {
            scheduledWorld = mc.level;
            scheduleSettings = currentSettings;
            nextSoundNanos = -1;
            nextInGameTick = -1;
            lastInGameTick = -1;
            sleepWakePending = false;
            sleepScheduled = false;
        }

        if (soundSchedule.get() == SoundSchedule.RandomInterval) {
            long now = System.nanoTime();
            if (nextSoundNanos < 0) nextSoundNanos = now + randomIntervalNanos();
            if (now >= nextSoundNanos) {
                if (!eyesClosed) playTimmySound();
                nextSoundNanos = now + randomIntervalNanos();
            }
            return;
        }

        if (soundSchedule.get() == SoundSchedule.Sleep) {
            updateSleepSchedule(eyesClosed);
            return;
        }

        long worldTick = mc.level.getGameTime();
        if (nextInGameTick < 0 || worldTick < lastInGameTick) {
            long timeOfDay = Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
            long ticksUntilSound = Math.floorMod((long) inGameTime.get() - timeOfDay, 24000L);
            nextInGameTick = worldTick + ticksUntilSound;
        }
        lastInGameTick = worldTick;

        if (worldTick >= nextInGameTick) {
            if (!eyesClosed) playTimmySound();
            do {
                nextInGameTick += 24000L;
            } while (nextInGameTick <= worldTick);
        }
    }

    private void updateSleepSchedule(boolean eyesClosed) {
        boolean usesInGameTicks = sleepUnit.get() == SleepUnit.InGameTicks;
        long now = usesInGameTicks ? mc.level.getGameTime() : System.nanoTime();
        long duration = sleepDurationInSelectedUnits();
        if (!sleepScheduled) {
            nextInGameTick = usesInGameTicks ? now + duration : -1;
            nextSoundNanos = usesInGameTicks ? -1 : now + duration;
            lastInGameTick = usesInGameTicks ? now : -1;
            sleepScheduled = true;
        }

        if (usesInGameTicks && now < lastInGameTick) {
            nextInGameTick = now + duration;
            sleepWakePending = false;
        }
        if (usesInGameTicks) lastInGameTick = now;

        boolean sleepFinished = sleepWakePending || (usesInGameTicks
            ? now >= nextInGameTick
            : now >= nextSoundNanos);
        if (!sleepFinished) return;
        if (eyesClosed) {
            sleepWakePending = true;
            return;
        }

        playTimmySound();
        sleepWakePending = false;
        if (usesInGameTicks) {
            nextInGameTick = now + duration;
        } else {
            nextSoundNanos = now + duration;
        }
    }

    private long sleepDurationInSelectedUnits() {
        long amount = sleepDuration.get();
        return switch (sleepUnit.get()) {
            case Seconds -> amount * 1_000_000_000L;
            case Minutes -> amount * 60_000_000_000L;
            case Hours -> amount * 3_600_000_000_000L;
            case InGameTicks -> amount;
        };
    }

    private long randomIntervalNanos() {
        int lower = Math.min(minInterval.get(), maxInterval.get());
        int upper = Math.max(minInterval.get(), maxInterval.get());
        long interval = ThreadLocalRandom.current().nextLong(lower, (long) upper + 1);
        long secondsPerUnit = switch (intervalUnit.get()) {
            case Seconds -> 1L;
            case Minutes -> 60L;
            case Hours -> 3600L;
        };
        return interval * secondsPerUnit * 1_000_000_000L;
    }

    private void playTimmySound() {
        mc.getSoundManager().play(
            SimpleSoundInstance.forUI(SOUND_EVENT, 1.0f, soundVolume.get().floatValue())
        );
        shoutingUntilNanos = System.nanoTime() + SHOUT_DURATION_NANOS;
    }

    private void resetSoundSchedule() {
        scheduledWorld = null;
        scheduleSettings = null;
        nextSoundNanos = -1;
        nextInGameTick = -1;
        lastInGameTick = -1;
        sleepWakePending = false;
        sleepScheduled = false;
    }
}
