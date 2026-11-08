package com.example.addon.gui;

import com.example.addon.modules.Waypearl;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;

public class WaypearlMessengerScreen extends WindowScreen {
    private final Waypearl waypearl;
    private final List<WLabel> messages = new ArrayList<>();
    private WLabel status;
    private WLabel fingerprint;
    private WLabel fingerprintEnd;

    public WaypearlMessengerScreen(GuiTheme theme, Waypearl waypearl) {
        super(theme, "Waypearl Messenger");
        this.waypearl = waypearl;
    }

    @Override
    public void initWidgets() {
        messages.clear();
        add(theme.label(waypearl.messengerRequester() ? "REQUESTER MESSENGER" : "ASSISTANT MESSENGER", true)).expandX();
        status = add(theme.label(" ")).expandX().widget();
        add(theme.label("CONVERSATION", true)).expandX();
        for (int i = 0; i < 8; i++) {
            messages.add(add(theme.label(" ")).expandX().widget());
        }
        WTextBox input = add(theme.textBox("", "Write a message...")).expandX().widget();
        WTable actions = add(theme.table()).expandX().widget();
        WButton send = actions.add(theme.button("Send Message")).expandX().widget();
        send.action = () -> {
            if (waypearl.sendMessenger(waypearl.messengerPeer(), input.get())) input.set("");
        };
        if (waypearl.messengerRequester()) {
            WButton pull = actions.add(theme.button("Request Pearl Pull")).expandX().widget();
            pull.action = waypearl::sendMessengerPull;
            WTable waiting = add(theme.table()).expandX().widget();
            WButton arm = waiting.add(theme.button("Arm Pull for Server Join")).expandX().widget();
            arm.action = () -> waypearl.sendArmedPull(false);
            WButton cancel = waiting.add(theme.button("Cancel Armed Pull")).expandX().widget();
            cancel.action = () -> waypearl.sendArmedPull(true);
            add(theme.label("Arm while offline, then join the assistant's server.")).expandX();
        }
        if (!waypearl.messengerPresets().isEmpty()) {
            add(theme.label("QUICK MESSAGES", true)).expandX();
            WTable presets = add(theme.table()).expandX().widget();
            int count = 0;
            for (String preset : waypearl.messengerPresets()) {
                if (count++ > 0) presets.row();
                WButton button = presets.add(theme.button(preset)).expandX().widget();
                button.action = () -> waypearl.sendMessenger(waypearl.messengerPeer(), preset);
            }
        }
        add(theme.label("CONNECTION SETUP", true)).expandX();
        fingerprint = add(theme.label(" ")).expandX().widget();
        fingerprintEnd = add(theme.label(" ")).expandX().widget();
        if (waypearl.messengerRequester()) {
            add(theme.label("Assistant SHA-256 fingerprint")).expandX();
            WTextBox pin = add(theme.textBox("")).expandX().widget();
            add(theme.label("Requester password")).expandX();
            WTextBox password = add(theme.textBox("", null, (text, c) -> true, PasswordRenderer.class)).expandX().widget();
            WButton connect = add(theme.button("Connect to local assistant")).expandX().widget();
            connect.action = () -> {
                waypearl.connectMessenger(pin.get().trim(), password.get());
                password.set("");
                refreshMessages();
            };
        } else {
            WButton copyFingerprint = add(theme.button("Copy assistant fingerprint")).expandX().widget();
            copyFingerprint.action = () -> {
                String pin = waypearl.messengerFingerprint();
                if (!pin.isEmpty()) Minecraft.getInstance().keyboardHandler.setClipboard(pin);
            };
            add(theme.label("Whitelisted requester name")).expandX();
            WTextBox name = add(theme.textBox("")).expandX().widget();
            add(theme.label("Requester password (16+ characters)")).expandX();
            WTextBox password = add(theme.textBox("", null, (text, c) -> true, PasswordRenderer.class)).expandX().widget();
            WButton enroll = add(theme.button("Save requester credentials")).expandX().widget();
            enroll.action = () -> {
                waypearl.enrollMessenger(name.get().trim(), password.get());
                password.set("");
                refreshMessages();
            };
            WButton host = add(theme.button("Start local assistant")).expandX().widget();
            host.action = () -> {
                waypearl.startMessengerHost();
                refreshMessages();
            };
        }
        WButton disconnect = add(theme.button("Disconnect")).expandX().widget();
        disconnect.action = () -> {
            waypearl.stopMessenger();
            refreshMessages();
        };
        refreshMessages();
    }

    public void refreshMessages() {
        status.set("Status: " + waypearl.messengerStatus());
        String pin = waypearl.messengerFingerprint();
        fingerprint.set(pin.isEmpty() ? "Fingerprint unavailable" : "SHA-256: " + pin.substring(0, 32));
        fingerprintEnd.set(pin.isEmpty() ? " " : pin.substring(32));
        List<String> history = waypearl.messengerMessages();
        List<String> lines = new ArrayList<>();
        for (String message : history) {
            for (int start = 0; start < message.length(); start += 48) {
                lines.add(message.substring(start, Math.min(start + 48, message.length())));
            }
        }
        int first = Math.max(0, lines.size() - messages.size());
        for (int i = 0; i < messages.size(); i++) {
            messages.get(i).set(first + i < lines.size() ? lines.get(first + i) : " ");
        }
    }

    public static class PasswordRenderer implements WTextBox.Renderer {
        @Override
        public void render(GuiRenderer renderer, double x, double y, String text, Color color) {
            renderer.text("*".repeat(text.length()), x, y, color, false);
        }
    }
}
