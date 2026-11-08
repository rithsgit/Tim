package com.example.addon.hud;

import com.example.addon.modules.PortalMaker;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public class PortalMakerHud extends HudElement {
    public static final HudElementInfo<PortalMakerHud> INFO = new HudElementInfo<>(
        null, "Portal Maker HUD",
        "portal-maker",
        "Displays the progress of the Portal Maker module.",
        PortalMakerHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Enables a shadow/shader effect to make text pop more.")
        .defaultValue(true)
        .build()
    );

    public PortalMakerHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        PortalMaker module = Modules.get().get(PortalMaker.class);
        if (module == null || !module.isActive()) return;

        if (module.portalFramePositions.isEmpty()) return;

        int total = module.portalFramePositions.size();
        int placed = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (BlockPos pos : module.portalFramePositions) {
                if (mc.level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
                    placed++;
                }
            }
        }

        String text = "Portal: " + placed + "/" + total;
        boolean shadow = textShadow.get();

        setSize(renderer.textWidth(text, shadow), renderer.textHeight(shadow));

        renderer.quad(x, y, getWidth(), getHeight(), new Color(0, 0, 0, 150));
        renderer.text(text, x, y, Color.MAGENTA, shadow);
    }
}