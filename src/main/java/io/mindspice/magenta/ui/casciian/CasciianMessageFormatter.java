package io.mindspice.magenta.ui.casciian;

import java.util.ArrayList;
import java.util.List;

public final class CasciianMessageFormatter {

    private CasciianMessageFormatter() {
    }

    public static String block(String role, String text, int width) {
        int boundedWidth = Math.max(12, width);
        List<String> lines = new ArrayList<>();
        lines.add(" " + safe(role) + " ");
        lines.addAll(wordWrap(safe(text), Math.max(1, boundedWidth - 2)));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            out.append(padRight(lines.get(i), boundedWidth));
            if (i + 1 < lines.size()) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    static List<String> wordWrap(String text, int width) {
        int boundedWidth = Math.max(1, width);
        String normalized = safe(text).replace('\t', ' ');
        String[] sourceLines = normalized.split("\\R", -1);
        List<String> wrapped = new ArrayList<>();
        for (String source : sourceLines) {
            if (source.isEmpty()) {
                wrapped.add("");
                continue;
            }
            String remaining = source;
            while (remaining.length() > boundedWidth) {
                int split = findSplit(remaining, boundedWidth);
                wrapped.add(remaining.substring(0, split).stripTrailing());
                remaining = remaining.substring(split).stripLeading();
            }
            wrapped.add(remaining);
        }
        return wrapped;
    }

    private static int findSplit(String text, int width) {
        int split = Math.min(width, text.length());
        int lastSpace = text.lastIndexOf(' ', split);
        if (lastSpace <= 0) {
            return split;
        }
        return lastSpace;
    }

    private static String padRight(String value, int width) {
        if (value.length() >= width) {
            return value.substring(0, width);
        }
        return value + " ".repeat(width - value.length());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
