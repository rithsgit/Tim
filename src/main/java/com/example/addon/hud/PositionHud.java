package com.example.addon.hud;

import com.example.addon.Tim;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PositionHud extends HudElement {

    public static final HudElementInfo<PositionHud> INFO = new HudElementInfo<>(
        Tim.HUD_GROUP,
        "position",
        "Displays your current coordinates, dimension, biome, and Nether/Overworld equivalents.",
        PositionHud::new
    );

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> textScale = HudAppearance.addTextScale(sgGeneral);
    private final Setting<Double> paddingScale = HudAppearance.addPaddingScale(sgGeneral);

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Enables a shadow/shader effect to make text pop more.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Scale of the HUD element.")
        .defaultValue(1.0)
        .min(0.25)
        .sliderRange(0.25, 4.0)
        .build()
    );

    public enum Alignment { Left, Center, Right }
    public enum CoordVisibility { Visible, Censored, Hidden }

    private final Setting<Alignment> alignment = sgGeneral.add(new EnumSetting.Builder<Alignment>()
        .name("alignment")
        .description("Align text to the left, center, or right.")
        .defaultValue(Alignment.Left)
        .build()
    );

    private final Setting<SettingColor> labelColor = sgGeneral.add(new ColorSetting.Builder()
        .name("label-color")
        .defaultValue(new SettingColor(170, 170, 170, 255))
        .build()
    );

    private final Setting<SettingColor> valueColor = sgGeneral.add(new ColorSetting.Builder()
        .name("value-color")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> separatorColor = sgGeneral.add(new ColorSetting.Builder()
        .name("separator-color")
        .description("Color of the | separators.")
        .defaultValue(new SettingColor(100, 100, 100, 255))
        .build()
    );

    private final Setting<SettingColor> netherLabelColor = sgGeneral.add(new ColorSetting.Builder()
        .name("nether-label-color")
        .description("Color of the Nether/Overworld coordinate labels.")
        .defaultValue(new SettingColor(200, 80, 80, 255))
        .build()
    );

    private final Setting<Boolean> showBackground = sgGeneral.add(new BoolSetting.Builder()
        .name("background")
        .description("Show a per-line background highlight.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
        .name("background-color")
        .defaultValue(new SettingColor(0, 0, 0, 80))
        .visible(showBackground::get)
        .build()
    );

    private final Setting<CoordVisibility> coordVisibility = sgGeneral.add(new EnumSetting.Builder<CoordVisibility>()
        .name("coord-visibility")
        .description("Controls how coordinates are displayed.")
        .defaultValue(CoordVisibility.Visible)
        .build()
    );

    private final Setting<Boolean> showDimension = sgGeneral.add(new BoolSetting.Builder()
        .name("show-dimension")
        .description("Show the current dimension.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showBiome = sgGeneral.add(new BoolSetting.Builder()
        .name("show-biome")
        .description("Show the biome you are currently standing in.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showNether = sgGeneral.add(new BoolSetting.Builder()
        .name("show-nether-coords")
        .description("Show the Nether/Overworld equivalent coordinates on a second line.")
        .defaultValue(true)
        .build()
    );

    public PositionHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null) { setSize(0, 0); return; }

        double s          = scale.get();
        double padH       = (4 * s) * paddingScale.get();
        double padV       = (2 * s) * paddingScale.get();
        double rowGap     = 2 * s;
        boolean shadow    = textShadow.get();
        double lineHeight = renderer.textHeight(shadow, HudAppearance.textScale(s, textScale.get()));
        double sepW       = renderer.textWidth(" | ", shadow, HudAppearance.textScale(s, textScale.get()));
        Alignment align   = alignment.get();
        boolean rightAlign  = align == Alignment.Right;
        boolean centerAlign = align == Alignment.Center;

        BlockPos pos = mc.player.getBlockPos();
        int bx = pos.getX(), by = pos.getY(), bz = pos.getZ();

        boolean inNether = mc.world != null && mc.world.getRegistryKey() == World.NETHER;
        boolean inEnd    = mc.world != null && mc.world.getRegistryKey() == World.END;

        String dimLabel = null, dimValue = null;
        if (showDimension.get() && mc.world != null) {
            dimLabel = "Dim: ";
            dimValue = inNether ? "Nether" : inEnd ? "End" : "Overworld";
        }

        String biomeLabel = null, biomeValue = null;
        if (showBiome.get() && mc.world != null) {
            biomeLabel = "Biome: ";
            biomeValue = mc.world.getBiome(pos).getKey()
                .map(RegistryKey::getValue)
                .map(id -> {
                    String[] parts = id.getPath().split("_");
                    StringBuilder sb = new StringBuilder();
                    for (String p : parts) {
                        if (!sb.isEmpty()) sb.append(' ');
                        sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
                    }
                    return sb.toString();
                })
                .orElse("Unknown");
        }

        boolean coordsVisible = coordVisibility.get() != CoordVisibility.Hidden;
        boolean censored = coordVisibility.get() == CoordVisibility.Censored;
        String xLabel = "X: ", xVal = censored ? "XXXX" : String.valueOf(bx);
        String yLabel = "Y: ", yVal = censored ? "XXXX" : String.valueOf(by);
        String zLabel = "Z: ", zVal = censored ? "XXXX" : String.valueOf(bz);

        String netherLineLabel = null;
        String nxVal = null, nzVal = null;
        boolean hasLineNether = showNether.get() && !inEnd;

        if (hasLineNether) {
            if (inNether) {
                netherLineLabel = "OW: ";
                nxVal = String.valueOf(bx * 8);
                nzVal = censored ? "XXXX" : String.valueOf(bz * 8);
            } else {
                netherLineLabel = "Nether: ";
                nxVal = censored ? "XXXX" : String.valueOf((int) Math.floor(bx / 8.0));
                nzVal = censored ? "XXXX" : String.valueOf((int) Math.floor(bz / 8.0));
            }
        }

        String endNote = null;
        if (showNether.get() && inEnd) {
            endNote = "No portal conversion in The End";
        }

        String nxLabel = "X: ", nzLabel = "Z: ";

        double dimW = dimLabel != null
            ? renderer.textWidth(dimLabel, shadow, HudAppearance.textScale(s, textScale.get())) + renderer.textWidth(dimValue, shadow, HudAppearance.textScale(s, textScale.get())) : 0;

        double biomeW = biomeLabel != null
            ? renderer.textWidth(biomeLabel, shadow, HudAppearance.textScale(s, textScale.get())) + renderer.textWidth(biomeValue, shadow, HudAppearance.textScale(s, textScale.get())) : 0;

        double coordW = renderer.textWidth(xLabel, shadow, HudAppearance.textScale(s, textScale.get())) + renderer.textWidth(xVal, shadow, HudAppearance.textScale(s, textScale.get()))
                      + sepW
                      + renderer.textWidth(yLabel, shadow, HudAppearance.textScale(s, textScale.get())) + renderer.textWidth(yVal, shadow, HudAppearance.textScale(s, textScale.get()))
                      + sepW
                      + renderer.textWidth(zLabel, shadow, HudAppearance.textScale(s, textScale.get())) + renderer.textWidth(zVal, shadow, HudAppearance.textScale(s, textScale.get()));

        double netherW = hasLineNether
            ? renderer.textWidth(netherLineLabel, shadow, HudAppearance.textScale(s, textScale.get()))
              + renderer.textWidth(nxLabel, shadow, HudAppearance.textScale(s, textScale.get())) + renderer.textWidth(nxVal, shadow, HudAppearance.textScale(s, textScale.get()))
              + sepW
              + renderer.textWidth(nzLabel, shadow, HudAppearance.textScale(s, textScale.get())) + renderer.textWidth(nzVal, shadow, HudAppearance.textScale(s, textScale.get()))
            : 0;

        double endNoteW = endNote != null
            ? renderer.textWidth(endNote, shadow, HudAppearance.textScale(s, textScale.get())) : 0;

        boolean hasCoords   = coordsVisible;
        boolean hasDim      = dimLabel    != null;
        boolean hasBiome    = biomeLabel  != null;
        boolean hasEndNote  = endNote     != null;

        double maxW   = Math.max(coordW, Math.max(dimW, Math.max(biomeW, Math.max(netherW, endNoteW))));
        double totalW = maxW + padH * 2;

        int lineCount = (hasCoords ? 1 : 0)
            + (hasDim     ? 1 : 0)
            + (hasBiome   ? 1 : 0)
            + (hasLineNether ? 1 : 0)
            + (hasEndNote ? 1 : 0);

        double totalH = lineCount * lineHeight + (lineCount - 1) * rowGap + padV * 2;

        int lineIdx = 0;

        if (hasDim) {
            lineIdx = drawLabelValue(renderer, s, padH, padV, rowGap, lineHeight, shadow,
                rightAlign, centerAlign, totalW, dimW, lineIdx,
                dimLabel, dimValue, labelColor.get(), valueColor.get());
        }

        if (hasBiome) {
            lineIdx = drawLabelValue(renderer, s, padH, padV, rowGap, lineHeight, shadow,
                rightAlign, centerAlign, totalW, biomeW, lineIdx,
                biomeLabel, biomeValue, labelColor.get(), valueColor.get());
        }

        if (hasCoords) {
            double rowY     = y + padV + lineIdx * (lineHeight + rowGap);
            double lineBoxW = coordW + padH * 2;
            if (showBackground.get()) {
                double bgX = rightAlign ? x + totalW - lineBoxW : centerAlign ? x + (totalW - lineBoxW) / 2.0 : x;
                renderer.quad(bgX, rowY - 1, lineBoxW, lineHeight + 2, backgroundColor.get());
            }
            if (rightAlign) {
                double cx = x + totalW - padH;
                double zw = renderer.textWidth(zVal,   shadow, HudAppearance.textScale(s, textScale.get())); cx -= zw; renderer.text(zVal,   cx, rowY, valueColor.get(),     shadow, HudAppearance.textScale(s, textScale.get()));
                double zlw= renderer.textWidth(zLabel, shadow, HudAppearance.textScale(s, textScale.get())); cx -= zlw;renderer.text(zLabel, cx, rowY, labelColor.get(),     shadow, HudAppearance.textScale(s, textScale.get()));
                cx -= sepW; renderer.text(" | ", cx, rowY, separatorColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
                double yw = renderer.textWidth(yVal,   shadow, HudAppearance.textScale(s, textScale.get())); cx -= yw; renderer.text(yVal,   cx, rowY, valueColor.get(),     shadow, HudAppearance.textScale(s, textScale.get()));
                double ylw= renderer.textWidth(yLabel, shadow, HudAppearance.textScale(s, textScale.get())); cx -= ylw;renderer.text(yLabel, cx, rowY, labelColor.get(),     shadow, HudAppearance.textScale(s, textScale.get()));
                cx -= sepW; renderer.text(" | ", cx, rowY, separatorColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
                double xw = renderer.textWidth(xVal,   shadow, HudAppearance.textScale(s, textScale.get())); cx -= xw; renderer.text(xVal,   cx, rowY, valueColor.get(),     shadow, HudAppearance.textScale(s, textScale.get()));
                double xlw= renderer.textWidth(xLabel, shadow, HudAppearance.textScale(s, textScale.get())); cx -= xlw;renderer.text(xLabel, cx, rowY, labelColor.get(),     shadow, HudAppearance.textScale(s, textScale.get()));
            } else {
                double cx = centerAlign ? x + (totalW - coordW) / 2.0 : x + padH;
                renderer.text(xLabel, cx, rowY, labelColor.get(),     shadow, HudAppearance.textScale(s, textScale.get())); cx += renderer.textWidth(xLabel, shadow, HudAppearance.textScale(s, textScale.get()));
                renderer.text(xVal,   cx, rowY, valueColor.get(),     shadow, HudAppearance.textScale(s, textScale.get())); cx += renderer.textWidth(xVal,   shadow, HudAppearance.textScale(s, textScale.get()));
                renderer.text(" | ",  cx, rowY, separatorColor.get(), shadow, HudAppearance.textScale(s, textScale.get())); cx += sepW;
                renderer.text(yLabel, cx, rowY, labelColor.get(),     shadow, HudAppearance.textScale(s, textScale.get())); cx += renderer.textWidth(yLabel, shadow, HudAppearance.textScale(s, textScale.get()));
                renderer.text(yVal,   cx, rowY, valueColor.get(),     shadow, HudAppearance.textScale(s, textScale.get())); cx += renderer.textWidth(yVal,   shadow, HudAppearance.textScale(s, textScale.get()));
                renderer.text(" | ",  cx, rowY, separatorColor.get(), shadow, HudAppearance.textScale(s, textScale.get())); cx += sepW;
                renderer.text(zLabel, cx, rowY, labelColor.get(),     shadow, HudAppearance.textScale(s, textScale.get())); cx += renderer.textWidth(zLabel, shadow, HudAppearance.textScale(s, textScale.get()));
                renderer.text(zVal,   cx, rowY, valueColor.get(),     shadow, HudAppearance.textScale(s, textScale.get()));
            }
            lineIdx++;
        }

        if (hasLineNether) {
            double rowY     = y + padV + lineIdx * (lineHeight + rowGap);
            double lineBoxW = netherW + padH * 2;
            if (showBackground.get()) {
                double bgX = rightAlign ? x + totalW - lineBoxW : centerAlign ? x + (totalW - lineBoxW) / 2.0 : x;
                renderer.quad(bgX, rowY - 1, lineBoxW, lineHeight + 2, backgroundColor.get());
            }
            if (rightAlign) {
                double cx = x + totalW - padH;
                double nzw = renderer.textWidth(nzVal,   shadow, HudAppearance.textScale(s, textScale.get())); cx -= nzw; renderer.text(nzVal,         cx, rowY, valueColor.get(),      shadow, HudAppearance.textScale(s, textScale.get()));
                double nzlw= renderer.textWidth(nzLabel, shadow, HudAppearance.textScale(s, textScale.get())); cx -= nzlw;renderer.text(nzLabel,       cx, rowY, labelColor.get(),      shadow, HudAppearance.textScale(s, textScale.get()));
                cx -= sepW; renderer.text(" | ", cx, rowY, separatorColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
                double nxw = renderer.textWidth(nxVal,   shadow, HudAppearance.textScale(s, textScale.get())); cx -= nxw; renderer.text(nxVal,         cx, rowY, valueColor.get(),      shadow, HudAppearance.textScale(s, textScale.get()));
                double nxlw= renderer.textWidth(nxLabel, shadow, HudAppearance.textScale(s, textScale.get())); cx -= nxlw;renderer.text(nxLabel,       cx, rowY, labelColor.get(),      shadow, HudAppearance.textScale(s, textScale.get()));
                double nlw = renderer.textWidth(netherLineLabel, shadow, HudAppearance.textScale(s, textScale.get())); cx -= nlw; renderer.text(netherLineLabel, cx, rowY, netherLabelColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
            } else {
                double cx = centerAlign ? x + (totalW - netherW) / 2.0 : x + padH;
                renderer.text(netherLineLabel, cx, rowY, netherLabelColor.get(), shadow, HudAppearance.textScale(s, textScale.get())); cx += renderer.textWidth(netherLineLabel, shadow, HudAppearance.textScale(s, textScale.get()));
                renderer.text(nxLabel, cx, rowY, labelColor.get(),     shadow, HudAppearance.textScale(s, textScale.get())); cx += renderer.textWidth(nxLabel, shadow, HudAppearance.textScale(s, textScale.get()));
                renderer.text(nxVal,   cx, rowY, valueColor.get(),     shadow, HudAppearance.textScale(s, textScale.get())); cx += renderer.textWidth(nxVal,   shadow, HudAppearance.textScale(s, textScale.get()));
                renderer.text(" | ",   cx, rowY, separatorColor.get(), shadow, HudAppearance.textScale(s, textScale.get())); cx += sepW;
                renderer.text(nzLabel, cx, rowY, labelColor.get(),     shadow, HudAppearance.textScale(s, textScale.get())); cx += renderer.textWidth(nzLabel, shadow, HudAppearance.textScale(s, textScale.get()));
                renderer.text(nzVal,   cx, rowY, valueColor.get(),     shadow, HudAppearance.textScale(s, textScale.get()));
            }
            lineIdx++;
        }

        if (hasEndNote) {
            lineIdx = drawLabelValue(renderer, s, padH, padV, rowGap, lineHeight, shadow,
                rightAlign, centerAlign, totalW, endNoteW, lineIdx,
                endNote, "", labelColor.get(), valueColor.get());
        }

        setSize(totalW, totalH);
    }

    private int drawLabelValue(HudRenderer renderer, double s,
                               double padH, double padV, double rowGap, double lineHeight, boolean shadow,
                               boolean rightAlign, boolean centerAlign,
                               double totalW, double lineW, int lineIdx,
                               String label, String value,
                               SettingColor lColor, SettingColor vColor) {
        double rowY     = y + padV + lineIdx * (lineHeight + rowGap);
        double lineBoxW = lineW + padH * 2;
        if (showBackground.get()) {
            double bgX = rightAlign  ? x + totalW - lineBoxW
                       : centerAlign ? x + (totalW - lineBoxW) / 2.0
                       : x;
            renderer.quad(bgX, rowY - 1, lineBoxW, lineHeight + 2, backgroundColor.get());
        }
        if (rightAlign) {
            double vw = renderer.textWidth(value, shadow, HudAppearance.textScale(s, textScale.get()));
            double lw = renderer.textWidth(label, shadow, HudAppearance.textScale(s, textScale.get()));
            double vx = x + totalW - padH - vw;
            renderer.text(label, vx - lw, rowY, lColor, shadow, HudAppearance.textScale(s, textScale.get()));
            renderer.text(value, vx,       rowY, vColor, shadow, HudAppearance.textScale(s, textScale.get()));
        } else {
            double cx = centerAlign ? x + (totalW - lineW) / 2.0 : x + padH;
            renderer.text(label, cx, rowY, lColor, shadow, HudAppearance.textScale(s, textScale.get()));
            cx += renderer.textWidth(label, shadow, HudAppearance.textScale(s, textScale.get()));
            renderer.text(value, cx, rowY, vColor, shadow, HudAppearance.textScale(s, textScale.get()));
        }
        return lineIdx + 1;
    }
}