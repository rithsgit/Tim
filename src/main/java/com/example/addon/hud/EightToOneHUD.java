package com.example.addon.hud;

import com.example.addon.Tim;
import com.example.addon.modules.EightToOne;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class EightToOneHUD extends HudElement {

    public static final HudElementInfo<EightToOneHUD> INFO = new HudElementInfo<>(
        Tim.HUD_GROUP,
        "eight-to-one",
        "Displays portals and respawn anchors in the area, and total portals created this session.",
        EightToOneHUD::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ── Layout ────────────────────────────────────────────────────────────────

    public enum Layout { Inline, Stacked, StackedIcons }

    private final Setting<Layout> layout = sgGeneral.add(new EnumSetting.Builder<Layout>()
        .name("layout")
        .description("Inline: single line with separator. Stacked: one row per stat. StackedIcons: stacked with item icons.")
        .defaultValue(Layout.Inline)
        .build()
    );

    // ── Feature toggles ───────────────────────────────────────────────────────

    private final Setting<Boolean> showPortalsInArea = sgGeneral.add(new BoolSetting.Builder()
        .name("show-portals-in-area")
        .description("Show the portals in area count.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showAnchorsInArea = sgGeneral.add(new BoolSetting.Builder()
        .name("show-anchors-in-area")
        .description("Show the respawn anchors in area count.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showPortalsCreated = sgGeneral.add(new BoolSetting.Builder()
        .name("show-portals-created")
        .description("Show the total portals created this session.")
        .defaultValue(true)
        .build()
    );

    // ── Visual settings ───────────────────────────────────────────────────────

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the HUD element.")
        .defaultValue(1.0)
        .min(0.25)
        .sliderRange(0.25, 4.0)
        .build()
    );

    public enum Alignment { Left, Center, Right }

    private final Setting<Alignment> alignment = sgGeneral.add(new EnumSetting.Builder<Alignment>()
        .name("alignment")
        .description("Align text within the element. Has no effect in Inline layout.")
        .defaultValue(Alignment.Left)
        .visible(() -> layout.get() != Layout.Inline)
        .build()
    );

    public enum LabelMode { Text, Icon, Both }

    private final Setting<LabelMode> labelMode = sgGeneral.add(new EnumSetting.Builder<LabelMode>()
        .name("label-mode")
        .description("Show the item label as text, icon, or both.")
        .defaultValue(LabelMode.Both)
        .visible(() -> layout.get() == Layout.StackedIcons || layout.get() == Layout.Inline)
        .build()
    );

    public enum IconPosition { Left, Right, Above, Below }

    private final Setting<IconPosition> iconPosition = sgGeneral.add(new EnumSetting.Builder<IconPosition>()
        .name("icon-position")
        .description("Where the icon appears relative to the text.")
        .defaultValue(IconPosition.Left)
        .visible(() -> (layout.get() == Layout.StackedIcons || layout.get() == Layout.Inline) && labelMode.get() != LabelMode.Text)
        .build()
    );

    private final Setting<Double> iconScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("icon-scale")
        .description("Scale of the item icons.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderRange(0.5, 4.0)
        .visible(() -> (layout.get() == Layout.StackedIcons || layout.get() == Layout.Inline) && labelMode.get() != LabelMode.Text)
        .build()
    );

    private final Setting<Double> iconGapSetting = sgGeneral.add(new DoubleSetting.Builder()
        .name("icon-gap")
        .description("Gap in pixels between icon and text.")
        .defaultValue(4.0)
        .min(0)
        .sliderRange(0, 16)
        .visible(() -> (layout.get() == Layout.StackedIcons || layout.get() == Layout.Inline) && labelMode.get() != LabelMode.Text)
        .build()
    );

    private final Setting<SettingColor> labelColor = sgGeneral.add(new ColorSetting.Builder()
        .name("label-color")
        .description("Color for labels.")
        .defaultValue(new SettingColor(170, 170, 170, 255))
        .build()
    );

    private final Setting<SettingColor> valueColor = sgGeneral.add(new ColorSetting.Builder()
        .name("value-color")
        .description("Color for values.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> separatorColor = sgGeneral.add(new ColorSetting.Builder()
        .name("separator-color")
        .description("Color of the | separator.")
        .defaultValue(new SettingColor(100, 100, 100, 255))
        .visible(() -> layout.get() == Layout.Inline)
        .build()
    );

    private final Setting<Boolean> showBackground = sgGeneral.add(new BoolSetting.Builder()
        .name("background")
        .description("Show a background behind the element.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
        .name("background-color")
        .defaultValue(new SettingColor(0, 0, 0, 80))
        .visible(showBackground::get)
        .build()
    );

    // ── Constructor ───────────────────────────────────────────────────────────

    public EightToOneHUD() { super(INFO); }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(HudRenderer renderer) {
        EightToOne tracker = Modules.get().get(EightToOne.class);
        if (tracker == null || !tracker.isActive()) { setSize(0, 0); return; }
        if (!showPortalsInArea.get() && !showAnchorsInArea.get() && !showPortalsCreated.get()) { setSize(0, 0); return; }

        switch (layout.get()) {
            case Inline       -> renderInline(renderer, tracker);
            case Stacked      -> renderStacked(renderer, tracker, false);
            case StackedIcons -> renderStacked(renderer, tracker, true);
        }
    }

    // ── Inline layout ─────────────────────────────────────────────────────────

    private void renderInline(HudRenderer renderer, EightToOne tracker) {
        double s          = scale.get();
        double padH       = 4 * s;
        double padV       = 2 * s;
        double lineHeight = renderer.textHeight(false, s);
        double sepW       = renderer.textWidth(" | ", false, s);
        double iconSz     = 16.0 * iconScale.get();
        double iconGap    = iconGapSetting.get() * s;

        LabelMode mode = labelMode.get();
        boolean showIcon = mode != LabelMode.Text;
        boolean showLabel = mode != LabelMode.Icon;
        IconPosition iconPos = iconPosition.get();
        double effIconGap = showIcon ? iconGap : 0;

        boolean showArea    = showPortalsInArea.get();
        boolean showAnchors = showAnchorsInArea.get();
        boolean showCreated = showPortalsCreated.get();

        // Build segments dynamically so separators always sit between active ones
        record Stat(String label, String value, ItemStack icon) {}
        java.util.List<Stat> segments = new java.util.ArrayList<>();
        if (showAnchors) segments.add(new Stat("Anchors: ",         String.valueOf(tracker.getTotalAnchors()), new ItemStack(Items.RESPAWN_ANCHOR)));
        if (showArea)    segments.add(new Stat("Portals in Area: ", String.valueOf(tracker.getTotalPortals()), new ItemStack(Items.OBSIDIAN)));
        if (showCreated) segments.add(new Stat("Portals Created: ", String.valueOf(tracker.getTotalCreated()), new ItemStack(Items.FLINT_AND_STEEL)));
        if (segments.isEmpty()) { setSize(0, 0); return; }

        double totalW = 0;
        double rowH = showIcon ? Math.max(lineHeight, iconSz) : lineHeight;

        for (int i = 0; i < segments.size(); i++) {
            Stat st = segments.get(i);
            double segmentW = 0;
            if (showLabel) segmentW += renderer.textWidth(st.label(), false, s);
            segmentW += renderer.textWidth(st.value(), false, s);
            if (showIcon) segmentW += iconSz + effIconGap;
            totalW += segmentW;
            if (i < segments.size() - 1) totalW += sepW;
        }

        setSize(totalW + padH * 2, rowH + padV * 2);

        if (showBackground.get())
            renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());

        double cx   = x + padH;
        double rowY = y + padV;

        for (int i = 0; i < segments.size(); i++) {
            Stat st = segments.get(i);
            if (showIcon && iconPos == IconPosition.Left) {
                renderer.item(st.icon(), (int) cx, (int) (rowY + (rowH - iconSz) / 2.0), iconScale.get().floatValue(), false);
                cx += iconSz + effIconGap;
            }
            if (showLabel) {
                renderer.text(st.label(), cx, rowY + (rowH - lineHeight) / 2.0, labelColor.get(), false, s);
                cx += renderer.textWidth(st.label(), false, s);
            }
            renderer.text(st.value(), cx, rowY + (rowH - lineHeight) / 2.0, valueColor.get(), false, s);
            cx += renderer.textWidth(st.value(), false, s);

            if (showIcon && iconPos != IconPosition.Left) {
                cx += effIconGap;
                renderer.item(st.icon(), (int) cx, (int) (rowY + (rowH - iconSz) / 2.0), iconScale.get().floatValue(), false);
                cx += iconSz;
            }
            if (i < segments.size() - 1) {
                renderer.text(" | ", cx, rowY + (rowH - lineHeight) / 2.0, separatorColor.get(), false, s);
                cx += sepW;
            }
        }
    }

    // ── Stacked layout (with optional icons) ─────────────────────────────────

    private void renderStacked(HudRenderer renderer, EightToOne tracker, boolean withIcons) {
        double s          = scale.get();
        double padH       = 4 * s;
        double padV       = 2 * s;
        double rowGap     = 2 * s;
        double lineHeight = renderer.textHeight(false, s);
        double iconSz     = withIcons ? 16.0 * iconScale.get() : 0;
        double iconGap    = withIcons ? iconGapSetting.get() * s : 0;

        LabelMode    mode         = withIcons ? labelMode.get() : LabelMode.Text;
        IconPosition iconPos      = withIcons ? iconPosition.get() : IconPosition.Left;
        boolean      showIcon     = withIcons && mode != LabelMode.Text;
        boolean      showText     = mode != LabelMode.Icon;
        boolean      iconVertical = showIcon && (iconPos == IconPosition.Above || iconPos == IconPosition.Below);
        double       effectiveIconGap = (showIcon && showText) ? iconGap : 0;

        double statRowH;
        if (!showIcon) {
            statRowH = lineHeight;
        } else if (iconVertical) {
            statRowH = iconSz + iconGap + lineHeight;
        } else {
            statRowH = Math.max(lineHeight, iconSz);
        }

        // ── Gather data ───────────────────────────────────────────────────────

        String    anchorLabel = null, anchorValue = null;
        ItemStack anchorIcon  = ItemStack.EMPTY;
        if (showAnchorsInArea.get()) {
            anchorLabel = showText ? "Anchors: " : "";
            anchorValue = String.valueOf(tracker.getTotalAnchors());
            anchorIcon  = showIcon ? new ItemStack(Items.RESPAWN_ANCHOR) : ItemStack.EMPTY;
        }

        String    areaLabel = null, areaValue = null;
        ItemStack areaIcon  = ItemStack.EMPTY;
        if (showPortalsInArea.get()) {
            areaLabel = showText ? "Portals in Area: " : "";
            areaValue = String.valueOf(tracker.getTotalPortals());
            areaIcon  = showIcon ? new ItemStack(Items.OBSIDIAN) : ItemStack.EMPTY;
        }

        String    createdLabel = null, createdValue = null;
        ItemStack createdIcon  = ItemStack.EMPTY;
        if (showPortalsCreated.get()) {
            createdLabel = showText ? "Portals Created: " : "";
            createdValue = String.valueOf(tracker.getTotalCreated());
            createdIcon  = showIcon ? new ItemStack(Items.FLINT_AND_STEEL) : ItemStack.EMPTY;
        }

        boolean hasArea    = areaLabel    != null;
        boolean hasAnchor  = anchorLabel  != null;
        boolean hasCreated = createdLabel != null;
        if (!hasArea && !hasAnchor && !hasCreated) { setSize(0, 0); return; }

        // ── Measure widths ────────────────────────────────────────────────────

        double anchorTextW  = hasAnchor  ? renderer.textWidth(anchorLabel,  false, s) + renderer.textWidth(anchorValue,  false, s) : 0;
        double areaTextW    = hasArea    ? renderer.textWidth(areaLabel,    false, s) + renderer.textWidth(areaValue,    false, s) : 0;
        double createdTextW = hasCreated ? renderer.textWidth(createdLabel, false, s) + renderer.textWidth(createdValue, false, s) : 0;

        double anchorW, areaW, createdW;
        if (!showIcon || iconVertical) {
            anchorW  = hasAnchor  ? (showIcon && !anchorIcon.isEmpty()  ? Math.max(iconSz, anchorTextW)  : anchorTextW)  : 0;
            areaW    = hasArea    ? (showIcon && !areaIcon.isEmpty()    ? Math.max(iconSz, areaTextW)    : areaTextW)    : 0;
            createdW = hasCreated ? (showIcon && !createdIcon.isEmpty() ? Math.max(iconSz, createdTextW) : createdTextW) : 0;
        } else {
            double anchorIconW  = (showIcon && !anchorIcon.isEmpty())  ? iconSz + effectiveIconGap : 0;
            double areaIconW    = (showIcon && !areaIcon.isEmpty())    ? iconSz + effectiveIconGap : 0;
            double createdIconW = (showIcon && !createdIcon.isEmpty()) ? iconSz + effectiveIconGap : 0;
            anchorW  = hasAnchor  ? anchorIconW  + anchorTextW  : 0;
            areaW    = hasArea    ? areaIconW    + areaTextW    : 0;
            createdW = hasCreated ? createdIconW + createdTextW : 0;
        }

        double contentW = Math.max(anchorW, Math.max(areaW, createdW));
        if (showIcon && !showText) contentW = Math.max(contentW, iconSz);
        double totalW = contentW + padH * 2;

        double totalH = padV;
        if (hasAnchor)  totalH += statRowH + rowGap;
        if (hasArea)    totalH += statRowH + rowGap;
        if (hasCreated) totalH += statRowH + rowGap;
        totalH -= rowGap;
        totalH += padV;

        // ── Draw ──────────────────────────────────────────────────────────────

        Alignment align       = alignment.get();
        boolean   rightAlign  = align == Alignment.Right;
        boolean   centerAlign = align == Alignment.Center;
        double    curY        = y + padV;

        if (hasAnchor) {
            drawStatRow(renderer, s, x, curY, totalW, padH, statRowH, lineHeight,
                rightAlign, centerAlign, anchorW, anchorTextW,
                anchorIcon, iconSz, effectiveIconGap, iconPos,
                anchorLabel, anchorValue, labelColor.get(), valueColor.get());
            curY += statRowH + rowGap;
        }
        if (hasArea) {
            drawStatRow(renderer, s, x, curY, totalW, padH, statRowH, lineHeight,
                rightAlign, centerAlign, areaW, areaTextW,
                areaIcon, iconSz, effectiveIconGap, iconPos,
                areaLabel, areaValue, labelColor.get(), valueColor.get());
            curY += statRowH + rowGap;
        }
        if (hasCreated) {
            drawStatRow(renderer, s, x, curY, totalW, padH, statRowH, lineHeight,
                rightAlign, centerAlign, createdW, createdTextW,
                createdIcon, iconSz, effectiveIconGap, iconPos,
                createdLabel, createdValue, labelColor.get(), valueColor.get());
        }

        setSize(totalW, totalH);
    }

    // ── Draw a stat row with configurable icon position ───────────────────────

    private void drawStatRow(HudRenderer renderer, double s,
                             double rx, double ry, double totalW, double padH,
                             double rowH, double lineHeight,
                             boolean rightAlign, boolean centerAlign,
                             double lineW, double textW,
                             ItemStack icon, double iconSz, double iconGap,
                             IconPosition iconPos,
                             String label, String value,
                             SettingColor lColor, SettingColor vColor) {

        boolean hasIcon = !icon.isEmpty();

        if (showBackground.get())
            renderer.quad(rx, ry - 1, totalW, rowH + 2, backgroundColor.get());

        if (!hasIcon || iconPos == IconPosition.Left || iconPos == IconPosition.Right) {
            double textY = ry + (rowH - lineHeight) / 2.0;
            double iconY = ry + (rowH - iconSz)     / 2.0;

            if (rightAlign) {
                double cx = rx + totalW - padH;
                if (iconPos == IconPosition.Right && hasIcon) {
                    renderer.item(icon, (int)(cx - iconSz), (int) iconY, iconScale.get().floatValue(), false);
                    cx -= iconSz + iconGap;
                }
                if (value != null && !value.isEmpty()) {
                    cx -= renderer.textWidth(value, false, s);
                    renderer.text(value, cx, textY, vColor, false, s);
                }
                if (label != null && !label.isEmpty()) {
                    cx -= renderer.textWidth(label, false, s);
                    renderer.text(label, cx, textY, lColor, false, s);
                }
                if (iconPos == IconPosition.Left && hasIcon) {
                    cx -= iconGap + iconSz;
                    renderer.item(icon, (int) cx, (int) iconY, iconScale.get().floatValue(), false);
                }
            } else {
                double cx = centerAlign ? rx + (totalW - lineW) / 2.0 : rx + padH;
                if (iconPos == IconPosition.Left && hasIcon) {
                    renderer.item(icon, (int) cx, (int) iconY, iconScale.get().floatValue(), false);
                    cx += iconSz + iconGap;
                }
                if (label != null && !label.isEmpty()) {
                    renderer.text(label, cx, textY, lColor, false, s);
                    cx += renderer.textWidth(label, false, s);
                }
                if (value != null && !value.isEmpty()) {
                    renderer.text(value, cx, textY, vColor, false, s);
                    cx += renderer.textWidth(value, false, s);
                }
                if (iconPos == IconPosition.Right && hasIcon) {
                    cx += iconGap;
                    renderer.item(icon, (int) cx, (int)(ry + (rowH - iconSz) / 2.0), iconScale.get().floatValue(), false);
                }
            }

        } else {
            double iconY, textY;
            if (iconPos == IconPosition.Above) {
                iconY = ry;
                textY = ry + iconSz + iconGap;
            } else {
                textY = ry;
                iconY = ry + lineHeight + iconGap;
            }

            double iconX;
            if (rightAlign) {
                iconX = rx + totalW - padH - iconSz;
            } else if (centerAlign) {
                iconX = rx + (totalW - iconSz) / 2.0;
            } else {
                iconX = rx + padH + (textW - iconSz) / 2.0;
                if (iconX < rx + padH) iconX = rx + padH;
            }
            if (hasIcon)
                renderer.item(icon, (int) iconX, (int) iconY, iconScale.get().floatValue(), false);

            if (rightAlign) {
                double cx = rx + totalW - padH;
                if (value != null && !value.isEmpty()) {
                    cx -= renderer.textWidth(value, false, s);
                    renderer.text(value, cx, textY, vColor, false, s);
                }
                if (label != null && !label.isEmpty()) {
                    cx -= renderer.textWidth(label, false, s);
                    renderer.text(label, cx, textY, lColor, false, s);
                }
            } else {
                double cx = centerAlign ? rx + (totalW - textW) / 2.0 : rx + padH;
                if (label != null && !label.isEmpty()) {
                    renderer.text(label, cx, textY, lColor, false, s);
                    cx += renderer.textWidth(label, false, s);
                }
                if (value != null && !value.isEmpty()) {
                    renderer.text(value, cx, textY, vColor, false, s);
                }
            }
        }
    }
}