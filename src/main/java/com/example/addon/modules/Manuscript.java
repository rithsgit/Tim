package com.example.addon.modules;

import com.example.addon.Tim;
import com.example.addon.utils.MinecraftCompat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.util.Hand;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class Manuscript extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgContent = settings.createGroup("Content & Symbols");
    private final SettingGroup sgStyle   = settings.createGroup("Color & Scale");

    public enum MaxPages {
        Pages_1(1),
        Pages_5(5),
        Pages_10(10),
        Pages_25(25),
        Pages_50(50),
        Pages_75(75),
        Pages_100(100);

        private final int count;
        MaxPages(int count) { this.count = count; }
        public int getCount() { return count; }

        @Override
        public String toString() {
            return count + (count == 1 ? " Page" : " Pages");
        }
    }

    public enum ContentSource {
        CustomText,
        SymbolFlood,
        AsciiBanner,
        ExternalFile
    }

    public enum SymbolPreset {
        DenseUnicode("█▓▒░▀▄▌▐■□▲▼◆◇●○★☆"),
        CjkHeavy("龍鳳魑魅魍魎鬱爨癵驫麤龗龖Bi"),
        RunicGlyphs("ᚠᚢᚦᚨᚱᚲᚷᚹᚺᚾᛁᛃᛇᛈᛉᛊᛏᛒᛖᛗᛚᛜᛞᛟ"),
        MathAndBox("╬╫╪╩╨╧╦╥╤╣╢╡╠╟╞╝╜╛╚╙╘╗╖╕╔╓╒"),
        GlitchMatrix("฿₪₫€₭₮₯₰₱₲₳₴₵₶₷₸₹₺₻₼₽₾₿");

        private final String chars;
        SymbolPreset(String chars) { this.chars = chars; }
        public String getChars() { return chars; }
    }

    public enum ColorMode {
        None,
        CustomSingle,
        RandomPerPage,
        RandomPerLine,
        RandomPerCharacter
    }

    public enum BookColor {
        Black('0'),
        DarkBlue('1'),
        DarkGreen('2'),
        DarkAqua('3'),
        DarkRed('4'),
        DarkPurple('5'),
        Gold('6'),
        Gray('7'),
        DarkGray('8'),
        Blue('9'),
        Green('a'),
        Aqua('b'),
        Red('c'),
        LightPurple('d'),
        Yellow('e'),
        White('f');

        private final char code;
        BookColor(char code) { this.code = code; }
        public char getCode() { return code; }
    }

    public enum TextScale {
        Compact,
        Normal,
        LargeBold,
        WideFullwidth
    }

    // --- General Settings ---
    private final Setting<MaxPages> maxPages = sgGeneral.add(new EnumSetting.Builder<MaxPages>()
        .name("max-pages")
        .description("Maximum number of pages to fill before stopping at the title screen.")
        .defaultValue(MaxPages.Pages_50)
        .build()
    );

    private final Setting<Integer> charsPerPage = sgGeneral.add(new IntSetting.Builder()
        .name("chars-per-page")
        .description("Maximum characters per page (Vanilla book GUI fits ~256 chars, packet allows up to 1024).")
        .defaultValue(256)
        .min(32)
        .sliderMax(1024)
        .build()
    );

    private final Setting<Boolean> stopAtTitleScreen = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-at-title-screen")
        .description("Fills all pages and switches the open Book GUI directly to the 'Enter Book Title' signing screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> defaultTitlePrep = sgGeneral.add(new StringSetting.Builder()
        .name("pre-fill-title")
        .description("Pre-fills the title box on the signing screen without finalizing the signature.")
        .defaultValue("Tim Manuscript")
        .visible(stopAtTitleScreen::get)
        .build()
    );

    private final Setting<Boolean> autoTriggerOnOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("fill-on-book-open")
        .description("Automatically fills the book when you open a Writable Book GUI.")
        .defaultValue(true)
        .build()
    );

    // --- Content & Symbols Settings ---
    private final Setting<ContentSource> contentSource = sgContent.add(new EnumSetting.Builder<ContentSource>()
        .name("content-source")
        .description("Where to pull the manuscript text or symbols from.")
        .defaultValue(ContentSource.CustomText)
        .build()
    );

    private final Setting<String> customText = sgContent.add(new StringSetting.Builder()
        .name("custom-text")
        .description("Text to write across pages. Use \\n for line breaks and {page} for page number.")
        .defaultValue("Signed by Tim — Page {page}\n========================\n")
        .visible(() -> contentSource.get() == ContentSource.CustomText)
        .build()
    );

    private final Setting<Boolean> repeatCustomText = sgContent.add(new BoolSetting.Builder()
        .name("repeat-to-fill-pages")
        .description("Repeats the custom text across all selected max pages.")
        .defaultValue(true)
        .visible(() -> contentSource.get() == ContentSource.CustomText)
        .build()
    );

    private final Setting<SymbolPreset> symbolPreset = sgContent.add(new EnumSetting.Builder<SymbolPreset>()
        .name("symbol-preset")
        .description("High-density symbol palette to fill pages with.")
        .defaultValue(SymbolPreset.DenseUnicode)
        .visible(() -> contentSource.get() == ContentSource.SymbolFlood)
        .build()
    );

    private final Setting<String> customSymbols = sgContent.add(new StringSetting.Builder()
        .name("extra-symbols")
        .description("Optional additional symbols mixed into the Symbol Flood generator.")
        .defaultValue("")
        .visible(() -> contentSource.get() == ContentSource.SymbolFlood)
        .build()
    );

    private final Setting<String> fileName = sgContent.add(new StringSetting.Builder()
        .name("file-name")
        .description("Name of the UTF-8 .txt file inside config/meteor-client (e.g. manuscript.txt).")
        .defaultValue("manuscript.txt")
        .visible(() -> contentSource.get() == ContentSource.ExternalFile)
        .build()
    );

    // --- Color & Scale Settings ---
    private final Setting<ColorMode> colorMode = sgStyle.add(new EnumSetting.Builder<ColorMode>()
        .name("color-mode")
        .description("How color codes are applied to the book text.")
        .defaultValue(ColorMode.None)
        .build()
    );

    private final Setting<BookColor> customColor = sgStyle.add(new EnumSetting.Builder<BookColor>()
        .name("custom-color")
        .description("Color applied when Color Mode is set to CustomSingle.")
        .defaultValue(BookColor.DarkPurple)
        .visible(() -> colorMode.get() == ColorMode.CustomSingle)
        .build()
    );

    private final Setting<TextScale> textScale = sgStyle.add(new EnumSetting.Builder<TextScale>()
        .name("text-scale")
        .description("Adjusts visual glyph width and weight to simulate text size inside book pages.")
        .defaultValue(TextScale.Normal)
        .build()
    );

    private final Setting<Boolean> boldText = sgStyle.add(new BoolSetting.Builder()
        .name("bold")
        .description("Applies bold formatting (§l) to text.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> italicText = sgStyle.add(new BoolSetting.Builder()
        .name("italic")
        .description("Applies italic formatting (§o) to text.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> obfuscatedText = sgStyle.add(new BoolSetting.Builder()
        .name("obfuscated-matrix")
        .description("Applies scrambling matrix effect (§k) to text.")
        .defaultValue(false)
        .build()
    );

    private boolean hasFilledCurrentScreen = false;

    public Manuscript() {
        super(Tim.CATEGORY, "manuscript", "Fills writable books with custom text, symbols, and styling, with an optional signing screen.");
    }

    @Override
    public void onActivate() {
        hasFilledCurrentScreen = false;
        if (autoTriggerOnOpen.get() && mc.currentScreen instanceof BookEditScreen bookScreen) {
            executeBookFill(bookScreen);
            hasFilledCurrentScreen = true;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (ManuscriptScreenCompat.isBookWorkflowScreen(mc.currentScreen)) {
            if (mc.currentScreen instanceof BookEditScreen bookScreen && autoTriggerOnOpen.get() && !hasFilledCurrentScreen) {
                executeBookFill(bookScreen);
                hasFilledCurrentScreen = true;
            }
        } else {
            hasFilledCurrentScreen = false;
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WHorizontalList list = theme.horizontalList();
        WButton fillNow = list.add(theme.button("Fill Held Book Now")).expandX().widget();
        fillNow.action = () -> {
            if (mc.player == null) return;
            if (mc.currentScreen instanceof BookEditScreen bookScreen) {
                executeBookFill(bookScreen);
            } else {
                sendPacketDirectlyForHeldBook();
            }
        };
        return list;
    }

    public void executeBookFill(BookEditScreen screen) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        int slot = getWritableBookSlot();
        if (slot == -1) {
            ChatUtils.warning("You must be holding a Book and Quill.");
            return;
        }

        List<String> generatedPages = generatePages();
        if (generatedPages.isEmpty()) {
            ChatUtils.warning("Manuscript generated 0 pages.");
            return;
        }

        Hand hand = slot == 40 ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ManuscriptScreenCompat.update(
            screen,
            generatedPages,
            stopAtTitleScreen.get(),
            defaultTitlePrep.get(),
            hand
        );

        mc.getNetworkHandler().sendPacket(new BookUpdateC2SPacket(
            slot,
            generatedPages,
            Optional.empty()
        ));

        if (stopAtTitleScreen.get()) {
            ChatUtils.info("Manuscript filled %d/%d pages and opened the title screen.",
                generatedPages.size(), maxPages.get().getCount());
        } else {
            ChatUtils.info("Manuscript filled %d/%d pages (unsigned).",
                generatedPages.size(), maxPages.get().getCount());
        }
    }

    private void sendPacketDirectlyForHeldBook() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        int slot = getWritableBookSlot();
        if (slot == -1) {
            ChatUtils.warning("Hold a Book and Quill in your main hand or offhand first.");
            return;
        }

        List<String> generatedPages = generatePages();
        if (generatedPages.isEmpty()) {
            ChatUtils.warning("Manuscript generated 0 pages; no book update was sent.");
            return;
        }

        mc.getNetworkHandler().sendPacket(new BookUpdateC2SPacket(
            slot,
            generatedPages,
            Optional.empty()
        ));

        ChatUtils.info("Manuscript sent %d pages to held book (unsigned).", generatedPages.size());
    }

    private int getWritableBookSlot() {
        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.isOf(Items.WRITABLE_BOOK)) {
            return MinecraftCompat.selectedSlot(mc.player.getInventory());
        }
        ItemStack offHand = mc.player.getOffHandStack();
        if (offHand.isOf(Items.WRITABLE_BOOK)) {
            return 40; // Offhand slot index for BookUpdateC2SPacket
        }
        return -1;
    }

    private List<String> generatePages() {
        int targetPages = maxPages.get().getCount();
        int maxChars = charsPerPage.get();
        List<String> pages = new ArrayList<>(targetPages);

        switch (contentSource.get()) {
            case SymbolFlood -> {
                String palette = symbolPreset.get().getChars() + customSymbols.get();
                if (palette.isEmpty()) palette = SymbolPreset.DenseUnicode.getChars();

                for (int p = 1; p <= targetPages; p++) {
                    StringBuilder rawPage = new StringBuilder(maxChars);
                    for (int i = 0; i < maxChars; i++) {
                        if (i > 0 && i % 19 == 0) {
                            rawPage.append('\n');
                        } else {
                            char c = palette.charAt(ThreadLocalRandom.current().nextInt(palette.length()));
                            rawPage.append(c);
                        }
                    }
                    pages.add(applyScaleAndStyling(rawPage.toString(), p, maxChars));
                }
            }

            case AsciiBanner -> {
                String bannerTemplate =
                    "╔═════════════════╗\n" +
                    "║   TIM  ADDON    ║\n" +
                    "║   MANUSCRIPT    ║\n" +
                    "║    PAGE #{page}     ║\n" +
                    "╚═════════════════╝\n" +
                    "▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀\n" +
                    "▓▒░ TRAIL  HUNTER ░▒▓\n" +
                    "▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄▀▄";
                for (int p = 1; p <= targetPages; p++) {
                    String raw = bannerTemplate.replace("{page}", String.valueOf(p));
                    pages.add(applyScaleAndStyling(raw, p, maxChars));
                }
            }

            case ExternalFile -> {
                String fullContent = loadExternalFile();
                pages.addAll(splitStreamIntoPages(fullContent, targetPages, maxChars));
            }

            case CustomText -> {
                String base = customText.get().replace("\\n", "\n");
                if (repeatCustomText.get()) {
                    for (int p = 1; p <= targetPages; p++) {
                        String pageText = base.replace("{page}", String.valueOf(p));
                        pages.add(applyScaleAndStyling(pageText, p, maxChars));
                    }
                } else {
                    pages.addAll(splitStreamIntoPages(base, targetPages, maxChars));
                }
            }
        }

        return pages;
    }

    private List<String> splitStreamIntoPages(String rawStream, int targetPages, int maxChars) {
        List<String> result = new ArrayList<>();
        if (rawStream == null || rawStream.isEmpty()) return result;

        int index = 0;
        int pageNum = 1;
        while (index < rawStream.length() && pageNum <= targetPages) {
            int end = Math.min(index + maxChars, rawStream.length());
            String chunk = rawStream.substring(index, end).replace("{page}", String.valueOf(pageNum));
            String formatted = applyScaleAndStyling(chunk, pageNum, Integer.MAX_VALUE);
            while (formatted.length() > maxChars && end > index + 1) {
                int overflow = formatted.length() - maxChars;
                end = Math.max(index + 1, end - overflow);
                chunk = rawStream.substring(index, end).replace("{page}", String.valueOf(pageNum));
                formatted = applyScaleAndStyling(chunk, pageNum, Integer.MAX_VALUE);
            }

            // Try to break cleanly at a newline or space if near the end of the page
            if (end < rawStream.length()) {
                int lastSpace = rawStream.lastIndexOf(' ', end - 1);
                int lastNewline = rawStream.lastIndexOf('\n', end - 1);
                int breakPoint = Math.max(lastSpace, lastNewline);
                if (breakPoint > index + ((end - index) * 2 / 3)) {
                    end = breakPoint + 1;
                    chunk = rawStream.substring(index, end).replace("{page}", String.valueOf(pageNum));
                }
            }
            result.add(applyScaleAndStyling(chunk, pageNum, maxChars));
            index = end;
            pageNum++;
        }
        return result;
    }

    private String applyScaleAndStyling(String input, int pageNumber, int maxChars) {
        // 1. Apply Text Scale transformation (Fullwidth / SmallCaps / Compact)
        String scaled = transformScale(input, textScale.get());

        // 2. Apply Color & Formatting codes
        StringBuilder styled = new StringBuilder();
        String styleCodes = getFormattingSuffix();

        ColorMode mode = colorMode.get();
        if (mode == ColorMode.None) {
            if (!styleCodes.isEmpty()) {
                styled.append(styleCodes);
            }
            styled.append(scaled);
        } else if (mode == ColorMode.CustomSingle) {
            styled.append('§').append(customColor.get().getCode()).append(styleCodes).append(scaled);
        } else if (mode == ColorMode.RandomPerPage) {
            styled.append('§').append(randomColorCode()).append(styleCodes).append(scaled);
        } else if (mode == ColorMode.RandomPerLine) {
            String[] lines = scaled.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) styled.append('\n');
                styled.append('§').append(randomColorCode()).append(styleCodes).append(lines[i]);
            }
        } else if (mode == ColorMode.RandomPerCharacter) {
            for (int i = 0; i < scaled.length(); i++) {
                char c = scaled.charAt(i);
                if (c == '\n' || c == ' ') {
                    styled.append(c);
                } else {
                    styled.append('§').append(randomColorCode()).append(styleCodes).append(c);
                }
            }
        }

        // Ensure we don't exceed packet string limit
        String finalStr = styled.toString();
        if (finalStr.length() > maxChars) {
            finalStr = finalStr.substring(0, maxChars);
            if (Character.isHighSurrogate(finalStr.charAt(finalStr.length() - 1))
                || finalStr.endsWith("§")) {
                finalStr = finalStr.substring(0, finalStr.length() - 1);
            }
        }
        return finalStr;
    }

    private String getFormattingSuffix() {
        StringBuilder sb = new StringBuilder();
        if (textScale.get() == TextScale.LargeBold || boldText.get()) sb.append("§l");
        if (italicText.get()) sb.append("§o");
        if (obfuscatedText.get()) sb.append("§k");
        return sb.toString();
    }

    private char randomColorCode() {
        BookColor[] values = BookColor.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)].getCode();
    }

    /**
     * Converts standard ASCII characters into scaled Unicode equivalents
     * so text visually appears wider, larger, or more compact inside Minecraft's book font.
     */
    private String transformScale(String text, TextScale scale) {
        if (scale == TextScale.Normal) return text;

        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (scale == TextScale.WideFullwidth) {
                // Fullwidth Unicode range (renders 2x wider in Minecraft font)
                if (c >= '!' && c <= '~') {
                    out.append((char) (c - '!' + 0xFF01));
                } else if (c == ' ') {
                    out.append('\u3000');
                } else {
                    out.append(c);
                }
            } else if (scale == TextScale.LargeBold) {
                // Convert lowercase letters into bold Unicode Small-Caps
                String smallCaps = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ";
                if (c >= 'a' && c <= 'z') {
                    out.append(smallCaps.charAt(c - 'a'));
                } else {
                    out.append(c);
                }
            } else if (scale == TextScale.Compact) {
                // Replace double spaces and wide punctuation with tight equivalents
                if (c >= 'A' && c <= 'Z') {
                    out.append(Character.toLowerCase(c));
                } else {
                    out.append(c);
                }
            }
        }
        return out.toString();
    }

    private String loadExternalFile() {
        Path directory = FabricLoader.getInstance().getConfigDir()
            .resolve("meteor-client")
            .normalize();
        Path name;
        try {
            name = Path.of(fileName.get());
        } catch (InvalidPathException e) {
            ChatUtils.warning("Invalid Manuscript file name.");
            return "";
        }

        if (name.isAbsolute() || name.getRoot() != null || name.getNameCount() != 1
            || name.toString().isBlank() || name.toString().equals(".") || name.toString().equals("..")) {
            ChatUtils.warning("Manuscript file name must be a file inside config/meteor-client.");
            return "";
        }

        Path file = directory.resolve(name).normalize();
        if (!file.startsWith(directory)) {
            ChatUtils.warning("Manuscript file must be inside config/meteor-client.");
            return "";
        }
        if (!Files.isRegularFile(file)) {
            ChatUtils.warning("Manuscript file not found: " + file);
            return "";
        }
        try {
            Path realDirectory = directory.toRealPath();
            Path realFile = file.toRealPath();
            if (!realFile.startsWith(realDirectory)) {
                ChatUtils.warning("Manuscript file must resolve inside config/meteor-client.");
                return "";
            }
            return Files.readString(realFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ChatUtils.error("Failed to read manuscript file %s: %s", file, e.getMessage());
            return "";
        }
    }
}