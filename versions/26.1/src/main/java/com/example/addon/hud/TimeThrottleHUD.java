package com.example.addon.hud;

import com.example.addon.Tim;
import com.example.addon.modules.Timethrottle;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.Minecraft;

public class TimeThrottleHUD extends HudElement {

    public static final HudElementInfo<TimeThrottleHUD> INFO = new HudElementInfo<>(
        Tim.HUD_GROUP,
        "time-throttle",
        "Visualizes time throttle speeds, active focus mode, and overload states.",
        TimeThrottleHUD::new
    );

    private static final Minecraft mc = Minecraft.getInstance();

    public enum Alignment { Left, Center, Right }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> textScale = HudAppearance.addTextScale(sgGeneral);
    private final Setting<Double> paddingScale = HudAppearance.addPaddingScale(sgGeneral);
    private final SettingGroup sgColors  = settings.createGroup("Colors");

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale").defaultValue(1.0).min(0.5).sliderMax(2.5).build()
    );

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Enables a shadow/shader effect to make text pop more.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> compact = sgGeneral.add(new BoolSetting.Builder()
        .name("compact-mode")
        .description("Collapses display into a clean single-line bar.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Alignment> alignment = sgGeneral.add(new EnumSetting.Builder<Alignment>()
        .name("alignment").defaultValue(Alignment.Left).build()
    );

    private final Setting<Boolean> showFocusMode = sgGeneral.add(new BoolSetting.Builder()
        .name("show-focus").defaultValue(true).build()
    );

    private final Setting<Boolean> showChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("show-missing-chunks").defaultValue(true).build()
    );

    private final Setting<Boolean> showSource = sgGeneral.add(new BoolSetting.Builder()
        .name("show-source").defaultValue(true).build()
    );

    private final Setting<Boolean> showBar = sgGeneral.add(new BoolSetting.Builder()
        .name("show-bar").defaultValue(true).build()
    );

    private final Setting<Double> barHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("bar-height").defaultValue(4.0).min(2.0).sliderMax(10.0).visible(showBar::get).build()
    );

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").defaultValue(new SettingColor(180, 180, 180, 255)).build()
    );
    private final Setting<SettingColor> healthyColor = sgColors.add(new ColorSetting.Builder()
        .name("healthy-color").defaultValue(new SettingColor(80, 255, 80, 255)).build()
    );
    private final Setting<SettingColor> warningColor = sgColors.add(new ColorSetting.Builder()
        .name("warning-color").defaultValue(new SettingColor(255, 170, 0, 255)).build()
    );
    private final Setting<SettingColor> criticalColor = sgColors.add(new ColorSetting.Builder()
        .name("critical-color").defaultValue(new SettingColor(255, 65, 65, 255)).build()
    );
    private final Setting<SettingColor> barBgColor = sgColors.add(new ColorSetting.Builder()
        .name("bar-background").defaultValue(new SettingColor(35, 35, 35, 180)).visible(showBar::get).build()
    );

    public TimeThrottleHUD() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.options.keyPlayerList.isDown() && !isInEditor()) {
            setSize(0, 0);
            return;
        }

        Timethrottle module = Modules.get().get(Timethrottle.class);
        boolean active = module != null && module.isActive();

        if (!active) {
            if (isInEditor()) {
                renderContent(renderer, 1.0, "CHUNKS", "None", 0, false, false, Timethrottle.SafetyReason.NONE);
            } else {
                setSize(0, 0);
            }
            return;
        }

        renderContent(
            renderer,
            module.getCurrentSpeed(),
            module.getFocusMode().toString(),
            module.getActiveDominantSource(),
            module.getCachedUnloadedChunks(),
            module.isSafetyActive(),
            module.isOverloadTimeoutActive(),
            module.getLastSafetyReason()
        );
    }

    private void renderContent(HudRenderer renderer, double speed, String focus, String source, int chunks, boolean safety, boolean timeout, Timethrottle.SafetyReason reason) {
        double s = scale.get();
        double padH = (4 * s) * paddingScale.get();
        double padV = (2 * s) * paddingScale.get();
        double gap = 2 * s;
        boolean shadow = textShadow.get();
        double lineHeight = renderer.textHeight(shadow, HudAppearance.textScale(s, textScale.get()));

        SettingColor valColor;
        if (safety) valColor = warningColor.get();
        else if (speed > 0.85) valColor = healthyColor.get();
        else if (speed > 0.50) valColor = warningColor.get();
        else valColor = criticalColor.get();

        String statusBadge = "";
        if (safety) {
            statusBadge = reason == Timethrottle.SafetyReason.HAZARD ? " [HAZARD]" : " [COMBAT]";
        } else if (timeout) {
            statusBadge = " [TIMEOUT]";
        }

        if (compact.get()) {
            StringBuilder sb = new StringBuilder("TT: ");
            sb.append(String.format("%.0f%%", speed * 100.0));
            sb.append(statusBadge);
            if (showFocusMode.get()) sb.append(" [").append(focus).append("]");
            if (showChunks.get()) sb.append(" (").append(chunks).append("ch)");

            String line = sb.toString();
            double w = renderer.textWidth(line, shadow, HudAppearance.textScale(s, textScale.get()));
            setSize(w + padH * 2, lineHeight + padV * 2);
            renderer.text(line, x + padH, y + padV, valColor, shadow, HudAppearance.textScale(s, textScale.get()));
            return;
        }

        String line1Label = "Speed: ";
        String line1Val = String.format("%.0f%%", speed * 100.0) + statusBadge;

        String line2 = "";
        if (showFocusMode.get() || showSource.get()) {
            StringBuilder sb = new StringBuilder();
            if (showFocusMode.get()) sb.append("[").append(focus).append("] ");
            if (showSource.get()) sb.append("Src: ").append(source);
            line2 = sb.toString().trim();
        }

        String line3 = showChunks.get() ? ("Unloaded Chunks: " + chunks) : "";

        double l1W = renderer.textWidth(line1Label, shadow, HudAppearance.textScale(s, textScale.get())) + renderer.textWidth(line1Val, shadow, HudAppearance.textScale(s, textScale.get()));
        double l2W = !line2.isEmpty() ? renderer.textWidth(line2, shadow, HudAppearance.textScale(s, textScale.get())) : 0;
        double l3W = !line3.isEmpty() ? renderer.textWidth(line3, shadow, HudAppearance.textScale(s, textScale.get())) : 0;

        double maxContentW = Math.max(l1W, Math.max(l2W, l3W));
        double barW = maxContentW;
        double barH = showBar.get() ? barHeight.get() * s : 0;

        int lines = 1 + (!line2.isEmpty() ? 1 : 0) + (!line3.isEmpty() ? 1 : 0);
        double totalTextH = (lines * lineHeight) + ((lines - 1) * gap);
        double totalH = totalTextH + padV * 2 + (showBar.get() ? barH + gap : 0);
        double totalW = maxContentW + padH * 2;

        setSize(totalW, totalH);

        double curY = y + padV;
        Alignment align = alignment.get();

        double l1X = getAlignedX(align, x, padH, totalW, l1W);
        renderer.text(line1Label, l1X, curY, labelColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
        renderer.text(line1Val, l1X + renderer.textWidth(line1Label, shadow, HudAppearance.textScale(s, textScale.get())), curY, valColor, shadow, HudAppearance.textScale(s, textScale.get()));
        curY += lineHeight + gap;

        if (!line2.isEmpty()) {
            double l2X = getAlignedX(align, x, padH, totalW, l2W);
            renderer.text(line2, l2X, curY, labelColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
            curY += lineHeight + gap;
        }

        if (!line3.isEmpty()) {
            double l3X = getAlignedX(align, x, padH, totalW, l3W);
            renderer.text(line3, l3X, curY, labelColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
            curY += lineHeight + gap;
        }

        if (showBar.get()) {
            double bX = getAlignedX(align, x, padH, totalW, barW);
            renderer.quad(bX, curY, barW, barH, barBgColor.get());
            double progress = Math.max(0.0, Math.min(1.0, speed));
            if (progress > 0) {
                renderer.quad(bX, curY, barW * progress, barH, valColor);
            }
        }
    }

    private double getAlignedX(Alignment align, double baseX, double padH, double totalW, double contentW) {
        return switch (align) {
            case Left   -> baseX + padH;
            case Right  -> baseX + totalW - padH - contentW;
            case Center -> baseX + (totalW - contentW) / 2.0;
        };
    }
}