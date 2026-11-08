package com.example.addon.modules;

import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;

public abstract class MouseAwareModule extends Module {
    protected MouseAwareModule(Category category, String name, String description) {
        super(category, name, description);
    }

    @EventHandler
    private void onMouseButtonEvent(MouseButtonEvent event) {
        if (onMouseButton(event.button, event.action)) event.cancel();
    }

    protected abstract boolean onMouseButton(int button, KeyAction action);
}
