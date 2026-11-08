package com.example.addon.commands;

import com.example.addon.modules.PortalMaker;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class PortalMakerCommand extends Command {
    public PortalMakerCommand() {
        super("portal-maker", "Toggles the Portal Maker module.", "pm");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            Modules.get().get(PortalMaker.class).toggle();
            return SINGLE_SUCCESS;
        });
    }
}