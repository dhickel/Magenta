package io.mindspice.magenta.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.graphics.ThemeStyle;
import com.googlecode.lanterna.gui2.AbstractInteractableComponent;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.InteractableRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class TranscriptView extends AbstractInteractableComponent<TranscriptView> {
    private static final int DEFAULT_VISIBLE_ROWS = 12;
    private static final int SCROLL_STEP = 3;

    private List<Block> blocks = List.of();
    private List<RenderedLine> renderedLines = List.of();
    private int topRow = 0;

    TranscriptView() {
        setPreferredSize(new TerminalSize(1, DEFAULT_VISIBLE_ROWS));
    }

    void setBlocks(List<Block> blocks) {
        ScrollAnchor anchor = currentAnchor();
        boolean pinnedToBottom = isPinnedToBottom();
        this.blocks = blocks == null ? List.of() : List.copyOf(blocks);
        rebuildRenderedLines(anchor, pinnedToBottom);
        invalidate();
    }

    void refreshLayout() {
        rebuildRenderedLines(currentAnchor(), isPinnedToBottom());
        invalidate();
    }

    void scrollBy(int delta) {
        if (delta == 0) {
            return;
        }
        topRow = clampTopRow(topRow + delta);
        invalidate();
    }

    int topRow() {
        return topRow;
    }

    boolean isPinnedToBottom() {
        int visibleRows = Math.max(1, getSize().getRows());
        return renderedLines.isEmpty() || topRow >= Math.max(0, renderedLines.size() - visibleRows);
    }

    @Override
    protected InteractableRenderer<TranscriptView> createDefaultRenderer() {
        return new Renderer();
    }

    @Override
    protected synchronized Interactable.Result handleKeyStroke(KeyStroke keyStroke) {
        if (keyStroke instanceof MouseAction mouseAction) {
            return handleMouse(mouseAction);
        }

        return switch (keyStroke.getKeyType()) {
            case ArrowUp -> {
                scrollBy(-1);
                yield Result.HANDLED;
            }
            case ArrowDown -> {
                scrollBy(1);
                yield Result.HANDLED;
            }
            case PageUp -> {
                scrollBy(-Math.max(1, getSize().getRows() - 1));
                yield Result.HANDLED;
            }
            case PageDown -> {
                scrollBy(Math.max(1, getSize().getRows() - 1));
                yield Result.HANDLED;
            }
            case Home -> {
                topRow = 0;
                invalidate();
                yield Result.HANDLED;
            }
            case End -> {
                topRow = clampTopRow(Integer.MAX_VALUE);
                invalidate();
                yield Result.HANDLED;
            }
            default -> super.handleKeyStroke(keyStroke);
        };
    }

    private Interactable.Result handleMouse(MouseAction mouseAction) {
        MouseActionType actionType = mouseAction.getActionType();
        if (actionType == MouseActionType.CLICK_DOWN) {
            takeFocus();
            if (hasScrollbar() && isScrollbarColumn(mouseAction.getPosition().minus(getGlobalPosition()).getColumn())) {
                moveThumbTo(mouseAction);
            }
            return Result.HANDLED;
        }
        if (actionType == MouseActionType.DRAG && hasScrollbar()
            && isScrollbarColumn(mouseAction.getPosition().minus(getGlobalPosition()).getColumn())) {
            moveThumbTo(mouseAction);
            return Result.HANDLED;
        }
        if (actionType == MouseActionType.SCROLL_UP) {
            scrollBy(-SCROLL_STEP);
            return Result.HANDLED;
        }
        if (actionType == MouseActionType.SCROLL_DOWN) {
            scrollBy(SCROLL_STEP);
            return Result.HANDLED;
        }
        return Result.UNHANDLED;
    }

    private void moveThumbTo(MouseAction mouseAction) {
        TerminalPosition local = mouseAction.getPosition().minus(getGlobalPosition());
        int visibleRows = Math.max(1, getSize().getRows());
        int maxTopRow = Math.max(0, renderedLines.size() - visibleRows);
        if (maxTopRow == 0) {
            topRow = 0;
            invalidate();
            return;
        }
        double ratio = visibleRows <= 1 ? 0d : (double) Math.max(0, Math.min(visibleRows - 1, local.getRow())) / (visibleRows - 1);
        topRow = clampTopRow((int) Math.round(ratio * maxTopRow));
        invalidate();
    }

    private boolean hasScrollbar() {
        return renderedLines.size() > Math.max(1, getSize().getRows()) && getSize().getColumns() > 2;
    }

    private boolean isScrollbarColumn(int localColumn) {
        return hasScrollbar() && localColumn >= getSize().getColumns() - 1;
    }

    private ScrollAnchor currentAnchor() {
        if (renderedLines.isEmpty() || topRow < 0 || topRow >= renderedLines.size()) {
            return null;
        }
        RenderedLine line = renderedLines.get(topRow);
        return new ScrollAnchor(line.blockId(), line.blockLineIndex());
    }

    private void rebuildRenderedLines(ScrollAnchor anchor, boolean pinnedToBottom) {
        List<RenderedLine> rebuilt = renderBlocks(blocks, getSize());
        renderedLines = rebuilt;
        int visibleRows = Math.max(1, getSize().getRows());
        if (pinnedToBottom) {
            topRow = Math.max(0, rebuilt.size() - visibleRows);
            return;
        }
        if (anchor != null) {
            for (int i = 0; i < rebuilt.size(); i++) {
                RenderedLine line = rebuilt.get(i);
                if (line.blockId() == anchor.blockId() && line.blockLineIndex() == anchor.blockLineIndex()) {
                    topRow = clampTopRow(i);
                    return;
                }
            }
        }
        topRow = clampTopRow(topRow);
    }

    private int clampTopRow(int candidate) {
        int visibleRows = Math.max(1, getSize().getRows());
        int maxTopRow = Math.max(0, renderedLines.size() - visibleRows);
        if (candidate < 0) {
            return 0;
        }
        return Math.min(candidate, maxTopRow);
    }

    static List<RenderedLine> renderBlocks(List<Block> blocks, TerminalSize size) {
        List<Block> safeBlocks = blocks == null ? List.of() : blocks;
        int columns = size == null ? 0 : size.getColumns();
        int rows = size == null ? 0 : size.getRows();
        int baseWidth = Math.max(4, columns <= 0 ? 80 : columns);
        int visibleRows = Math.max(1, rows <= 0 ? DEFAULT_VISIBLE_ROWS : rows);

        List<RenderedLine> withoutScrollbar = wrapBlocks(safeBlocks, baseWidth);
        if (withoutScrollbar.size() <= visibleRows || baseWidth <= 4) {
            return withoutScrollbar;
        }
        return wrapBlocks(safeBlocks, Math.max(4, baseWidth - 1));
    }

    private static List<RenderedLine> wrapBlocks(List<Block> blocks, int width) {
        List<RenderedLine> lines = new ArrayList<>();
        for (Block block : blocks) {
            String text = block == null || block.text() == null ? "" : block.text();
            String[] physicalLines = text.split("\\R", -1);
            int blockLineIndex = 0;
            for (String physicalLine : physicalLines) {
                List<String> wrapped = wrapPhysicalLine(physicalLine, width);
                for (String segment : wrapped) {
                    lines.add(new RenderedLine(block.id(), blockLineIndex, block.foreground(), block.background(), segment));
                    blockLineIndex++;
                }
            }
        }
        return lines.isEmpty()
                ? List.of(new RenderedLine(-1L, 0, TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT, ""))
                : List.copyOf(lines);
    }

    private static List<String> wrapPhysicalLine(String line, int width) {
        String safeLine = line == null ? "" : line;
        if (safeLine.isEmpty()) {
            return List.of("");
        }
        int dividerIndex = safeLine.indexOf("│ ");
        if (dividerIndex >= 0) {
            String prefix = safeLine.substring(0, dividerIndex + 2);
            String payload = safeLine.substring(dividerIndex + 2);
            int available = Math.max(4, width - prefix.length());
            List<String> wrapped = wrapHard(payload, available);
            if (wrapped.isEmpty()) {
                return List.of(prefix);
            }
            List<String> lines = new ArrayList<>(wrapped.size());
            for (String segment : wrapped) {
                lines.add(prefix + segment);
            }
            return lines;
        }
        return wrapHard(safeLine, width);
    }

    private static List<String> wrapHard(String text, int width) {
        int safeWidth = Math.max(4, width);
        List<String> base = TerminalTextUtils.getWordWrappedText(safeWidth, text == null ? "" : text);
        List<String> wrapped = new ArrayList<>();
        for (String line : base) {
            String safeLine = line == null ? "" : line;
            if (safeLine.length() <= safeWidth) {
                wrapped.add(safeLine);
                continue;
            }
            for (int i = 0; i < safeLine.length(); i += safeWidth) {
                wrapped.add(safeLine.substring(i, Math.min(safeLine.length(), i + safeWidth)));
            }
        }
        return wrapped.isEmpty() ? List.of("") : wrapped;
    }

    record Block(long id, TextColor foreground, TextColor background, String text) {
        Block {
            Objects.requireNonNull(foreground, "foreground");
            Objects.requireNonNull(background, "background");
            text = text == null ? "" : text;
        }
    }

    record RenderedLine(long blockId, int blockLineIndex, TextColor foreground, TextColor background, String text) {}

    private record ScrollAnchor(long blockId, int blockLineIndex) {}

    private final class Renderer implements InteractableRenderer<TranscriptView> {
        @Override
        public TerminalPosition getCursorLocation(TranscriptView component) {
            return null;
        }

        @Override
        public TerminalSize getPreferredSize(TranscriptView component) {
            return new TerminalSize(1, DEFAULT_VISIBLE_ROWS);
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, TranscriptView component) {
            ThemeDefinition definition = component.getThemeDefinition();
            ThemeStyle style = component.isFocused() ? definition.getActive() : definition.getNormal();
            graphics.applyThemeStyle(style);
            graphics.fill(' ');

            int visibleRows = Math.max(1, graphics.getSize().getRows());
            int contentWidth = Math.max(1, graphics.getSize().getColumns() - (component.hasScrollbar() ? 1 : 0));
            for (int row = 0; row < visibleRows; row++) {
                int sourceIndex = component.topRow + row;
                if (sourceIndex >= component.renderedLines.size()) {
                    break;
                }
                RenderedLine line = component.renderedLines.get(sourceIndex);
                graphics.setForegroundColor(line.foreground());
                graphics.setBackgroundColor(line.background());
                graphics.fillRectangle(new TerminalPosition(0, row), new TerminalSize(contentWidth, 1), ' ');
                if (!line.text().isEmpty()) {
                    graphics.putString(0, row, line.text());
                }
            }

            if (component.hasScrollbar()) {
                int barColumn = Math.max(0, graphics.getSize().getColumns() - 1);
                int maxTopRow = Math.max(1, component.renderedLines.size() - visibleRows);
                int thumbSize = Math.max(1, (int) Math.round((visibleRows * (double) visibleRows) / component.renderedLines.size()));
                thumbSize = Math.min(visibleRows, thumbSize);
                int thumbTop = maxTopRow <= 0
                        ? 0
                        : (int) Math.round((component.topRow / (double) maxTopRow) * Math.max(0, visibleRows - thumbSize));
                graphics.setForegroundColor(TextColor.ANSI.WHITE);
                graphics.setBackgroundColor(TextColor.ANSI.BLACK_BRIGHT);
                for (int row = 0; row < visibleRows; row++) {
                    boolean thumb = row >= thumbTop && row < thumbTop + thumbSize;
                    graphics.putString(barColumn, row, thumb ? "█" : "│");
                }
            }
        }
    }
}
