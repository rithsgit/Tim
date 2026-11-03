package com.example.addon.hud;

import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;

final class HudAppearance {
    private HudAppearance() {
    }

    static Setting<Double> addTextScale(SettingGroup group) {
        return group.add(new DoubleSetting.Builder()
            .name("text-scale")
            .description("Scales text independently from the overall HUD layout scale.")
            .defaultValue(1.0)
            .min(0.25)
            .sliderRange(0.25, 4.0)
            .build()
        );
    }

    static Setting<Double> addPaddingScale(SettingGroup group) {
        return group.add(new DoubleSetting.Builder()
            .name("padding-scale")
            .description("Scales internal HUD padding independently from the overall layout scale.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderRange(0.0, 3.0)
            .build()
        );
    }

    static double textScale(double scale, double multiplier) {
        return (scale == -1 ? Hud.get().getTextScale() : scale) * multiplier;
    }
}
