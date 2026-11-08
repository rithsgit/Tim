package com.example.addon.hud;

import com.example.addon.Tim;
import com.example.addon.modules.Waypearl;
import java.util.List;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class WaypearlMessengerHud extends HudElement {
    public static final HudElementInfo<WaypearlMessengerHud> INFO = new HudElementInfo<>(
        Tim.HUD_GROUP, "waypearl-messenger", "Shows the latest Waypearl messenger message.", WaypearlMessengerHud::new
    );

    public enum IconPosition { Left, Right, Above, Below }
    public enum Alignment { Left, Center, Right }

    private ItemStack pearlIcon = ItemStack.EMPTY;
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> textScale = HudAppearance.addTextScale(sgGeneral);
    private final Setting<Double> paddingScale = HudAppearance.addPaddingScale(sgGeneral);

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale").description("Scale of the HUD element.")
        .defaultValue(1.0).min(0.25).sliderRange(0.25, 4.0).build());
    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Draw a shadow behind text.")
        .defaultValue(true).build());
    private final Setting<Alignment> alignment = sgGeneral.add(new EnumSetting.Builder<Alignment>()
        .name("alignment").description("Align wrapped message text within the element.")
        .defaultValue(Alignment.Left).build());
    private final Setting<Double> messageWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("message-width").description("Maximum text width before wrapping, before scale is applied.")
        .defaultValue(240).min(32).sliderRange(32, 600).build());
    private final Setting<Boolean> showIcon = sgGeneral.add(new BoolSetting.Builder()
        .name("show-icon").description("Show the ender pearl icon.")
        .defaultValue(true).build());
    private final Setting<IconPosition> iconPosition = sgGeneral.add(new EnumSetting.Builder<IconPosition>()
        .name("icon-position").description("Where the icon appears relative to the message.")
        .defaultValue(IconPosition.Left).visible(showIcon::get).build());
    private final Setting<Double> iconScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("icon-scale").description("Scale of the ender pearl icon.")
        .defaultValue(1.0).min(0.25).sliderRange(0.25, 4.0).visible(showIcon::get).build());
    private final Setting<Double> iconGap = sgGeneral.add(new DoubleSetting.Builder()
        .name("icon-gap").description("Gap between the icon and the message.")
        .defaultValue(4.0).min(0).sliderRange(0, 16).visible(showIcon::get).build());
    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color").description("Color of the messenger text.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());
    private final Setting<Boolean> showBackground = sgGeneral.add(new BoolSetting.Builder()
        .name("background").description("Show a background behind the HUD.")
        .defaultValue(false).build());
    private final Setting<SettingColor> backgroundColor = sgGeneral.add(new ColorSetting.Builder()
        .name("background-color").defaultValue(new SettingColor(0, 0, 0, 80))
        .visible(showBackground::get).build());

    public WaypearlMessengerHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        Waypearl module = Modules.get().get(Waypearl.class);
        String text;
        if (module == null || !module.messengerAvailable()) {
            if (!isInEditor()) {
                setSize(0, 0);
                return;
            }
            text = "Waypearl: waiting for messages";
        } else {
            List<String> messages = module.messengerMessages();
            text = messages.isEmpty() ? "Waypearl: " + module.messengerStatus() : messages.get(messages.size() - 1);
        }

        double layoutScale = scale.get();
        double fontScale = HudAppearance.textScale(layoutScale, textScale.get());
        boolean shadow = textShadow.get();
        double lineHeight = renderer.textHeight(shadow, fontScale);
        double lineGap = 2 * layoutScale * paddingScale.get();
        double padX = 4 * layoutScale * paddingScale.get();
        double padY = 2 * layoutScale * paddingScale.get();
        List<String> lines = WaypearlHudText.wrap(text, messageWidth.get() * layoutScale * textScale.get(),
            value -> renderer.textWidth(value, shadow, fontScale));
        double textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, renderer.textWidth(line, shadow, fontScale));
        double textHeight = lineHeight * lines.size() + lineGap * (lines.size() - 1);

        boolean icon = showIcon.get();
        IconPosition position = iconPosition.get();
        boolean horizontal = position == IconPosition.Left || position == IconPosition.Right;
        double iconSize = icon ? 16 * iconScale.get() * layoutScale : 0;
        double gap = icon ? iconGap.get() * layoutScale * paddingScale.get() : 0;
        double contentWidth = icon ? (horizontal ? iconSize + gap + textWidth : Math.max(iconSize, textWidth)) : textWidth;
        double contentHeight = icon ? (horizontal ? Math.max(iconSize, textHeight) : iconSize + gap + textHeight) : textHeight;
        setSize(contentWidth + padX * 2, contentHeight + padY * 2);

        if (showBackground.get()) renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());
        double contentX = x + padX;
        double contentY = y + padY;
        double textX = contentX;
        double textY = contentY;
        if (icon && horizontal) {
            if (position == IconPosition.Left) textX += iconSize + gap;
            textY += (contentHeight - textHeight) / 2;
        } else if (icon) {
            textX += alignedOffset(contentWidth, textWidth, alignment.get());
            if (position == IconPosition.Above) textY += iconSize + gap;
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            double lineWidth = renderer.textWidth(line, shadow, fontScale);
            double lineX = textX + alignedOffset(textWidth, lineWidth, alignment.get());
            renderer.text(line, lineX, textY + i * (lineHeight + lineGap), textColor.get(), shadow, fontScale);
        }

        if (icon) {
            double iconX = contentX;
            double iconY = contentY;
            if (horizontal) {
                if (position == IconPosition.Right) iconX += textWidth + gap;
                iconY += (contentHeight - iconSize) / 2;
            } else {
                iconX += alignedOffset(contentWidth, iconSize, alignment.get());
                if (position == IconPosition.Below) iconY += textHeight + gap;
            }
            if (pearlIcon.isEmpty()) pearlIcon = new ItemStack(Items.ENDER_PEARL);
            renderer.item(pearlIcon, (int) iconX, (int) iconY, (float) (iconScale.get() * layoutScale), false);
        }
    }

    static double alignedOffset(double width, double itemWidth, Alignment alignment) {
        return switch (alignment) {
            case Left -> 0;
            case Center -> (width - itemWidth) / 2;
            case Right -> width - itemWidth;
        };
    }

}
