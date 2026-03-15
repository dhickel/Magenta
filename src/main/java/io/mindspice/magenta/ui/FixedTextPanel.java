package io.mindspice.magenta.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.graphics.ThemeStyle;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class FixedTextPanel extends AbstractComponent<FixedTextPanel> {
    private final int preferredRows;
    private final TextColor foreground;
    private final TextColor background;
    private List<String> lines = List.of();

    FixedTextPanel(int preferredRows, TextColor foreground, TextColor background) {
        this.preferredRows = Math.max(1, preferredRows);
        this.foreground = Objects.requireNonNull(foreground, "foreground");
        this.background = Objects.requireNonNull(background, "background");
        setPreferredSize(new TerminalSize(1, this.preferredRows));
    }

    void setLines(List<String> lines) {
        this.lines = lines == null ? List.of() : List.copyOf(lines);
        invalidate();
    }

    @Override
    protected ComponentRenderer<FixedTextPanel> createDefaultRenderer() {
        return new Renderer();
    }

    private final class Renderer implements ComponentRenderer<FixedTextPanel> {
        @Override
        public TerminalSize getPreferredSize(FixedTextPanel component) {
            return new TerminalSize(1, component.preferredRows);
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, FixedTextPanel component) {
            ThemeDefinition definition = component.getThemeDefinition();
            ThemeStyle style = definition == null ? null : definition.getNormal();
            if (style != null) {
                graphics.applyThemeStyle(style);
            }
            graphics.setForegroundColor(component.foreground);
            graphics.setBackgroundColor(component.background);
            graphics.fill(' ');

            List<String> wrapped = wrap(component.lines, graphics.getSize().getColumns());
            int visibleRows = Math.max(1, graphics.getSize().getRows());
            for (int row = 0; row < Math.min(visibleRows, wrapped.size()); row++) {
                String line = wrapped.get(row);
                if (!line.isEmpty()) {
                    graphics.putString(0, row, line);
                }
            }
        }
    }

    private static List<String> wrap(List<String> source, int width) {
        int safeWidth = Math.max(1, width);
        List<String> wrapped = new ArrayList<>();
        for (String line : source == null ? List.<String>of() : source) {
            String safeLine = line == null ? "" : line;
            List<String> parts = TerminalTextUtils.getWordWrappedText(safeWidth, safeLine);
            if (parts.isEmpty()) {
                wrapped.add("");
            } else {
                wrapped.addAll(parts);
            }
        }
        return wrapped.isEmpty() ? List.of("") : List.copyOf(wrapped);
    }
}
