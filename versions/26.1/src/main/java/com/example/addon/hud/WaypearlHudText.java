package com.example.addon.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

final class WaypearlHudText {
    private WaypearlHudText() {}

    static List<String> wrap(String text, double maxWidth, ToDoubleFunction<String> width) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = start;
            int lastSpace = -1;
            while (end < text.length()) {
                int next = end + Character.charCount(text.codePointAt(end));
                if (end > start && width.applyAsDouble(text.substring(start, next)) > maxWidth) break;
                if (Character.isWhitespace(text.codePointAt(end))) lastSpace = end;
                end = next;
            }
            if (end < text.length() && lastSpace > start) end = lastSpace;
            lines.add(text.substring(start, end));
            start = end;
            while (start < text.length() && Character.isWhitespace(text.codePointAt(start))) {
                start += Character.charCount(text.codePointAt(start));
            }
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }
}
