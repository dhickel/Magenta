package io.mindspice.magenta.ui.render;

import io.mindspice.magenta.ui.TerminalUiConfig;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UiRenderer {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final Terminal terminal;
    private final PrintWriter writer;
    private final TerminalUiConfig.Rendering rendering;

    public UiRenderer(Terminal terminal, TerminalUiConfig.Rendering rendering) {
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.writer = terminal.writer();
        this.rendering = Objects.requireNonNull(rendering, "rendering");
    }

    public synchronized void renderStatus(UiStatusBar status) {
        if (!rendering.showStatusBar()) {
            return;
        }
        int width = terminalWidth();
        printStyled(joinLeftRight(status.topLeft(), status.topRight(), width), UiStyle.MUTED, false);
        printStyled(joinLeftRight(status.bottomLeft(), status.bottomRight(), width), UiStyle.MUTED, false);
    }

    public synchronized void renderBlock(UiRenderBlock block) {
        if (!block.title().isBlank()) {
            printStyled(block.title(), block.style(), true);
        }
        for (String line : block.lines()) {
            printStyled(line, block.style(), false);
        }
    }

    public synchronized void renderTable(UiRenderTable table) {
        if (table.headers().isEmpty() && table.rows().isEmpty()) {
            return;
        }

        int columns = table.headers().isEmpty() ? table.rows().stream().mapToInt(List::size).max().orElse(0) : table.headers().size();
        if (columns == 0) {
            return;
        }

        int[] widths = new int[columns];
        for (int i = 0; i < table.headers().size(); i++) {
            widths[i] = Math.max(widths[i], safe(table.headers().get(i)).length());
        }
        for (List<String> row : table.rows()) {
            for (int i = 0; i < Math.min(row.size(), columns); i++) {
                widths[i] = Math.max(widths[i], safe(row.get(i)).length());
            }
        }

        if (!table.headers().isEmpty()) {
            printStyled(formatRow(table.headers(), widths), UiStyle.INFO, false);
            printStyled("-".repeat(Math.min(terminalWidth(), formatRow(table.headers(), widths).length())), UiStyle.MUTED, false);
        }

        for (List<String> row : table.rows()) {
            printStyled(formatRow(row, widths), UiStyle.DEFAULT, false);
        }
    }

    public synchronized void printSystem(String text) {
        printStyled(text, UiStyle.SYSTEM, false);
    }

    public synchronized void printInfo(String text) {
        printStyled(text, UiStyle.INFO, false);
    }

    public synchronized void printWarn(String text) {
        printStyled(text, UiStyle.WARN, false);
    }

    public synchronized void printError(String text) {
        printStyled(text, UiStyle.ERROR, false);
    }

    public synchronized void printUser(String text) {
        printStyled(text, UiStyle.USER, false);
    }

    public synchronized void printAssistant(String text) {
        printStyled(text, UiStyle.ASSISTANT, false);
    }

    public synchronized void printStreamToken(String token) {
        AttributedString attributed = attributed(token, UiStyle.ASSISTANT, false);
        attributed.print(terminal);
        writer.flush();
    }

    public synchronized void finishStreamLine() {
        writer.println();
        writer.flush();
    }

    private void printStyled(String text, UiStyle style, boolean padTitle) {
        String payload = prefixTimestamp(text);
        if (padTitle) {
            payload = "[" + payload + "]";
        }
        AttributedString attributed = attributed(payload, style, true);
        attributed.println(terminal);
        writer.flush();
    }

    private AttributedString attributed(String text, UiStyle style, boolean reset) {
        if (!rendering.colorEnabled()) {
            return new AttributedString(text);
        }

        AttributedStringBuilder builder = new AttributedStringBuilder();
        builder.style(styleFor(style));
        builder.append(text);
        if (reset) {
            builder.style(AttributedStyle.DEFAULT);
        }
        return builder.toAttributedString();
    }

    private AttributedStyle styleFor(UiStyle style) {
        return switch (style) {
            case SYSTEM -> AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA);
            case USER -> AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
            case ASSISTANT -> AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
            case INFO -> AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE);
            case WARN -> AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
            case ERROR -> AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
            case MUTED -> AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT);
            case DEFAULT -> AttributedStyle.DEFAULT;
        };
    }

    private String prefixTimestamp(String text) {
        if (!rendering.showTimestamps()) {
            return text;
        }
        return TS_FORMAT.format(Instant.now()) + " " + text;
    }

    private int terminalWidth() {
        int width = terminal.getWidth();
        return width <= 0 ? 120 : width;
    }

    private String joinLeftRight(String left, String right, int width) {
        String cleanLeft = safe(left);
        String cleanRight = safe(right);
        if (cleanLeft.length() + cleanRight.length() + 1 > width) {
            int rightMax = Math.min(cleanRight.length(), Math.max(0, width / 3));
            int leftMax = Math.max(0, width - rightMax - 1);
            cleanLeft = truncate(cleanLeft, leftMax);
            cleanRight = truncate(cleanRight, rightMax);
        }
        int spaces = Math.max(1, width - cleanLeft.length() - cleanRight.length());
        return cleanLeft + " ".repeat(spaces) + cleanRight;
    }

    private String formatRow(List<String> rawValues, int[] widths) {
        List<String> values = new ArrayList<>(widths.length);
        for (int i = 0; i < widths.length; i++) {
            String value = i < rawValues.size() ? safe(rawValues.get(i)) : "";
            values.add(padRight(value, widths[i]));
        }
        return String.join(" | ", values);
    }

    private String padRight(String input, int width) {
        String normalized = safe(input);
        if (normalized.length() >= width) {
            return normalized;
        }
        return normalized + " ".repeat(width - normalized.length());
    }

    private String truncate(String text, int max) {
        if (max <= 0) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        if (max < 4) {
            return text.substring(0, max);
        }
        return text.substring(0, max - 3) + "...";
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
