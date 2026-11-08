package com.example.addon.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.example.addon.Tim;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.Minecraft;

public class InfoAssistantHud extends HudElement {

    public static final HudElementInfo<InfoAssistantHud> INFO = new HudElementInfo<>(
        Tim.HUD_GROUP,
        "info-assistant",
        "Lists every module that has a keybind, grouped by category.",
        InfoAssistantHud::new
    );

    private static final Minecraft mc = Minecraft.getInstance();

    public enum FilterMode { All, Active_Only, Inactive_Only }
    public enum DescMode { Hidden, Always, On_Hover }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> textScale = HudAppearance.addTextScale(sgGeneral);
    private final Setting<Double> paddingScale = HudAppearance.addPaddingScale(sgGeneral);

    private final Setting<Keybind> toggleKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("toggle-key")
        .description("Press this key to show or hide Info Assistant.")
        .defaultValue(Keybind.none())
        .action(this::onToggleKey)
        .build()
    );

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

    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title")
        .description("Show the 'Info Assistant' header line.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showCategories = sgGeneral.add(new BoolSetting.Builder()
        .name("show-categories")
        .description("Show category group headers between module entries.")
        .defaultValue(true)
        .build()
    );

    private final Setting<DescMode> descMode = sgGeneral.add(new EnumSetting.Builder<DescMode>()
        .name("descriptions")
        .description("Show module descriptions permanently, on hover, or hide them.")
        .defaultValue(DescMode.On_Hover)
        .build()
    );

    private final Setting<FilterMode> filterMode = sgGeneral.add(new EnumSetting.Builder<FilterMode>()
        .name("filter-mode")
        .description("Filter modules by their active state.")
        .defaultValue(FilterMode.All)
        .build()
    );

    private final Setting<Integer> maxRows = sgGeneral.add(new IntSetting.Builder()
        .name("max-rows")
        .description("Maximum number of rows to display. 0 for unlimited.")
        .defaultValue(0)
        .min(0)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> huOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("hu-modules-only")
        .description("Only list Hunting Utilities modules; hide Meteor built-ins.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showBackground = sgGeneral.add(new BoolSetting.Builder()
        .name("background")
        .description("Draw a background panel behind the element.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
        .name("background-color")
        .defaultValue(new SettingColor(0, 0, 0, 100))
        .visible(showBackground::get)
        .build()
    );

    private final Setting<SettingColor> titleColor = sgGeneral.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Color of the 'Info Assistant' header.")
        .defaultValue(new SettingColor(255, 200, 50, 255))
        .build()
    );

    private final Setting<SettingColor> categoryColor = sgGeneral.add(new ColorSetting.Builder()
        .name("category-color")
        .description("Color of the category group headers.")
        .defaultValue(new SettingColor(140, 140, 140, 255))
        .visible(showCategories::get)
        .build()
    );

    private final Setting<SettingColor> moduleColor = sgGeneral.add(new ColorSetting.Builder()
        .name("module-color")
        .description("Color of the module name column.")
        .defaultValue(new SettingColor(210, 210, 210, 255))
        .build()
    );

    private final Setting<SettingColor> kbTagColor = sgGeneral.add(new ColorSetting.Builder()
        .name("kb-tag-color")
        .description("Color of the (KB) label.")
        .defaultValue(new SettingColor(120, 180, 255, 255))
        .build()
    );

    private final Setting<SettingColor> keyColor = sgGeneral.add(new ColorSetting.Builder()
        .name("key-color")
        .description("Color of the key name text.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> separatorColor = sgGeneral.add(new ColorSetting.Builder()
        .name("separator-color")
        .description("Color of the | separator between key and description.")
        .defaultValue(new SettingColor(70, 70, 70, 255))
        .build()
    );

    private final Setting<SettingColor> descriptionColor = sgGeneral.add(new ColorSetting.Builder()
        .name("description-color")
        .description("Color of the module description text.")
        .defaultValue(new SettingColor(155, 155, 155, 255))
        .build()
    );

    public enum Alignment { Left, Right }

    private final Setting<Alignment> alignment = sgGeneral.add(new EnumSetting.Builder<Alignment>()
        .name("alignment")
        .description("Align the element text left or right.")
        .defaultValue(Alignment.Left)
        .build()
    );

    private record Row(boolean isHeader, String label, String key, String description, Module module) {}

    private boolean visible = true;

    public InfoAssistantHud() {
        super(INFO);
    }

    private void onToggleKey() {
        visible = !visible;
    }

    @Override
    public void render(HudRenderer renderer) {
        if (!visible && !isInEditor()) {
            setSize(0, 0);
            return;
        }

        List<Row> rows = buildRows();

        if (rows.isEmpty() && !showTitle.get()) {
            setSize(0, 0);
            return;
        }

        final double s      = scale.get();
        final double padH   = (6 * s) * paddingScale.get();
        final double padV   = (4 * s) * paddingScale.get();
        final double rowGap = 2 * s;
        final double colGap = 8 * s;
        final boolean shadow = textShadow.get();
        final double lh     = renderer.textHeight(shadow, HudAppearance.textScale(s, textScale.get()));
        final boolean right = alignment.get() == Alignment.Right;
        final boolean showDescColumn = descMode.get() == DescMode.Always;

        double colAW   = 0;
        double colBW   = 0;
        double colCW   = 0;
        double catMaxW = 0;

        for (Row r : rows) {
            if (r.isHeader()) {
                catMaxW = Math.max(catMaxW, renderer.textWidth(r.label(), shadow, HudAppearance.textScale(s, textScale.get())));
            } else {
                colAW = Math.max(colAW, renderer.textWidth(r.label(), shadow, HudAppearance.textScale(s, textScale.get())));
                colBW = Math.max(colBW, renderer.textWidth("(KB) " + r.key(), shadow, HudAppearance.textScale(s, textScale.get())));
                if (showDescColumn)
                    colCW = Math.max(colCW, renderer.textWidth(r.description(), shadow, HudAppearance.textScale(s, textScale.get())));
            }
        }

        double sepW   = renderer.textWidth(" | ", shadow, HudAppearance.textScale(s, textScale.get()));
        double titleW = showTitle.get() ? renderer.textWidth("Info Assistant", shadow, HudAppearance.textScale(s, textScale.get())) : 0;

        double contentW = colAW + colGap + colBW + (showDescColumn ? sepW + colCW : 0);
        double innerW   = Math.max(Math.max(contentW, titleW), catMaxW);
        double totalW   = innerW + padH * 2;

        int    totalRowCount = rows.size() + (showTitle.get() ? 1 : 0);
        double totalH        = totalRowCount * lh + Math.max(0, totalRowCount - 1) * rowGap + padV * 2;

        if (showBackground.get())
            renderer.quad(x, y, totalW, totalH, backgroundColor.get());

        Row hoveredRow = null;
        double hoveredRowY = 0;

        if (descMode.get() == DescMode.On_Hover) {
            double mouseX = mc.mouseHandler.xpos() / (double) mc.getWindow().getGuiScale();
            double mouseY = mc.mouseHandler.ypos() / (double) mc.getWindow().getGuiScale();

            if (mouseX >= x && mouseX <= x + totalW && mouseY >= y && mouseY <= y + totalH) {
                int idx = 0;
                if (showTitle.get()) idx = 1;
                for (Row row : rows) {
                    double rowY = rowY(idx, y, padV, lh, rowGap);
                    if (mouseY >= rowY && mouseY <= rowY + lh) {
                        if (!row.isHeader() && !row.description().isEmpty()) {
                            hoveredRow = row;
                            hoveredRowY = rowY;
                        }
                        break;
                    }
                    idx++;
                }
            }
        }

        int idx = 0;

        if (showTitle.get()) {
            double rowY = rowY(idx, y, padV, lh, rowGap);
            double tw   = renderer.textWidth("Info Assistant", shadow, HudAppearance.textScale(s, textScale.get()));
            double tx   = right ? x + totalW - padH - tw : x + padH;
            renderer.text("Info Assistant", tx, rowY, titleColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
            idx++;
        }

        for (Row row : rows) {
            double rowY = rowY(idx, y, padV, lh, rowGap);

            if (row.isHeader()) {
                double hw = renderer.textWidth(row.label(), shadow, HudAppearance.textScale(s, textScale.get()));
                double hx = right ? x + totalW - padH - hw : x + padH;
                renderer.text(row.label(), hx, rowY, categoryColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));

            } else {
                String kbTag   = "(KB) ";
                double kbTagW  = renderer.textWidth(kbTag, shadow, HudAppearance.textScale(s, textScale.get()));
                double keyValW = renderer.textWidth(row.key(), shadow, HudAppearance.textScale(s, textScale.get()));
                double nameW   = renderer.textWidth(row.label(), shadow, HudAppearance.textScale(s, textScale.get()));

                if (right) {
                    double cx = x + totalW - padH;

                    if (showDescColumn) {
                        double dw = renderer.textWidth(row.description(), shadow, HudAppearance.textScale(s, textScale.get()));
                        cx -= dw;
                        renderer.text(row.description(), cx, rowY, descriptionColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
                        cx -= sepW;
                        renderer.text(" | ", cx, rowY, separatorColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
                    }

                    cx -= keyValW;
                    renderer.text(row.key(), cx, rowY, keyColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
                    cx -= kbTagW;
                    renderer.text(kbTag, cx, rowY, kbTagColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
                    cx -= colGap + nameW;
                    renderer.text(row.label(), cx, rowY, moduleColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));

                } else {
                    renderer.text(row.label(), x + padH, rowY, moduleColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));

                    double bx = x + padH + colAW + colGap;
                    renderer.text(kbTag, bx, rowY, kbTagColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
                    renderer.text(row.key(), bx + kbTagW, rowY, keyColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));

                    if (showDescColumn) {
                        double sepX = x + padH + colAW + colGap + colBW;
                        renderer.text(" | ", sepX, rowY, separatorColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
                        renderer.text(row.description(), sepX + sepW, rowY, descriptionColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
                    }
                }
            }

            idx++;
        }

        setSize(totalW, totalH);

        if (hoveredRow != null) {
            String desc = hoveredRow.description();
            double maxTipW = 200 * s;
            List<String> tipLines = wrapText(renderer, desc, maxTipW, s, shadow);
            
            double tipW = 0;
            for (String line : tipLines) tipW = Math.max(tipW, renderer.textWidth(line, shadow, HudAppearance.textScale(s, textScale.get())));
            tipW += padH * 2;
            
            double tipH = tipLines.size() * lh + (tipLines.size() - 1) * (2 * s) + padV * 2;

            double tipX = right ? x - tipW - 4 * s : x + totalW + 4 * s;
            if (right && tipX < 0) tipX = x + totalW + 4 * s;
            if (!right && tipX + tipW > mc.getWindow().getGuiScaledWidth()) tipX = x - tipW - 4 * s;

            double tipY = Math.max(0, Math.min(hoveredRowY, mc.getWindow().getGuiScaledHeight() - tipH));

            renderer.quad(tipX, tipY, tipW, tipH, backgroundColor.get());
            
            double textY = tipY + padV;
            for (String line : tipLines) {
                renderer.text(line, tipX + padH, textY, descriptionColor.get(), shadow, HudAppearance.textScale(s, textScale.get()));
                textY += lh + 2 * s;
            }
        }
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        List<Module> allModules = new ArrayList<>(Modules.get().getAll());

        allModules.sort(Comparator
            .comparingInt((Module m) -> m.category == Tim.CATEGORY ? 0 : 1)
            .thenComparing(m -> m.category.name)
            .thenComparing(m -> m.title)
        );

        String lastCategory = null;

        for (Module module : allModules) {
            if (huOnly.get() && module.category != Tim.CATEGORY) continue;
            if (!module.keybind.isSet()) continue;

            if (filterMode.get() == FilterMode.Active_Only && !module.isActive()) continue;
            if (filterMode.get() == FilterMode.Inactive_Only && module.isActive()) continue;

            String categoryName = module.category.name;

            if (showCategories.get() && !categoryName.equals(lastCategory)) {
                rows.add(new Row(true, "[" + categoryName + "]", null, null, null));
                lastCategory = categoryName;
            }

            String keyName = module.keybind.toString().toUpperCase();
            String desc = (module.description != null) ? module.description : "";

            if (descMode.get() == DescMode.Always && desc.length() > 55) {
                desc = desc.substring(0, 52) + "...";
            }

            rows.add(new Row(false, module.title, keyName, desc, module));
        }

        int limit = maxRows.get();
        if (limit > 0 && rows.size() > limit) {
            int extra = rows.size() - limit;
            List<Row> truncated = new ArrayList<>(rows.subList(0, limit));
            
            if (!truncated.isEmpty() && truncated.get(truncated.size() - 1).isHeader()) {
                truncated.remove(truncated.size() - 1);
                extra++;
            }
            
            truncated.add(new Row(true, "[... and " + extra + " more]", null, null, null));
            rows = truncated;
        }

        return rows;
    }

    private static double rowY(int idx, double baseY, double padV, double lh, double rowGap) {
        return baseY + padV + idx * (lh + rowGap);
    }

    private List<String> wrapText(HudRenderer renderer, String text, double maxWidth, double scale, boolean shadow) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (renderer.textWidth(testLine, shadow, HudAppearance.textScale(scale, textScale.get())) > maxWidth && !currentLine.isEmpty()) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }
}