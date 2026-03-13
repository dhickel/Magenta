package io.mindspice.magenta.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
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

final class ComposerInput extends AbstractInteractableComponent<ComposerInput> {
    @FunctionalInterface
    interface SubmitHandler {
        boolean submit(String text);
    }

    private static final int DEFAULT_VISIBLE_ROWS = 4;

    private final SubmitHandler submitHandler;
    private final Runnable abortHandler;
    private String text = "";
    private int caretIndex = 0;
    private int preferredColumn = -1;
    private int scrollRow = 0;

    ComposerInput(SubmitHandler submitHandler, Runnable abortHandler) {
        this.submitHandler = Objects.requireNonNull(submitHandler, "submitHandler");
        this.abortHandler = Objects.requireNonNull(abortHandler, "abortHandler");
        setPreferredSize(new TerminalSize(1, DEFAULT_VISIBLE_ROWS));
    }

    String getText() {
        return text;
    }

    void setText(String text) {
        this.text = text == null ? "" : text;
        this.caretIndex = this.text.length();
        this.preferredColumn = -1;
        ensureCaretVisible();
        invalidate();
    }

    void refreshLayout() {
        ensureCaretVisible();
        invalidate();
    }

    @Override
    protected InteractableRenderer<ComposerInput> createDefaultRenderer() {
        return new Renderer();
    }

    @Override
    protected synchronized Interactable.Result handleKeyStroke(KeyStroke keyStroke) {
        if (isEnterCharacter(keyStroke)) {
            if (keyStroke.isCtrlDown()) {
                return Result.HANDLED;
            }
            if (submitHandler.submit(text)) {
                text = "";
                caretIndex = 0;
                scrollRow = 0;
                preferredColumn = -1;
                invalidate();
            }
            return Result.HANDLED;
        }

        if (keyStroke instanceof MouseAction mouseAction) {
            if (mouseAction.getActionType() == MouseActionType.CLICK_DOWN || mouseAction.getActionType() == MouseActionType.DRAG) {
                takeFocus();
                moveCaretToMouse(mouseAction);
                return Result.HANDLED;
            }
            if (mouseAction.getActionType() == MouseActionType.SCROLL_UP) {
                scrollRow = Math.max(0, scrollRow - 1);
                invalidate();
                return Result.HANDLED;
            }
            if (mouseAction.getActionType() == MouseActionType.SCROLL_DOWN) {
                scrollRow = scrollRow + 1;
                ensureCaretVisible();
                invalidate();
                return Result.HANDLED;
            }
            return Result.UNHANDLED;
        }

        if (keyStroke.getKeyType() == KeyType.Enter) {
            if (submitHandler.submit(text)) {
                text = "";
                caretIndex = 0;
                scrollRow = 0;
                preferredColumn = -1;
                invalidate();
            }
            return Result.HANDLED;
        }

        if (keyStroke.getKeyType() == KeyType.Character && keyStroke.isCtrlDown()) {
            Character character = keyStroke.getCharacter();
            if (character != null && (character == 'c' || character == 'C')) {
                abortHandler.run();
                return Result.HANDLED;
            }
            if (character != null && (character == 'n' || character == 'N')) {
                insert("\n");
                return Result.HANDLED;
            }
        }

        if (keyStroke.getKeyType() == KeyType.Tab && !keyStroke.isShiftDown()) {
            insert("    ");
            return Result.HANDLED;
        }

        if (keyStroke.getKeyType() == KeyType.ReverseTab) {
            insert("    ");
            return Result.HANDLED;
        }

        if (keyStroke.getKeyType() == KeyType.Character && !keyStroke.isCtrlDown() && !keyStroke.isAltDown()) {
            Character character = keyStroke.getCharacter();
            if (character != null) {
                insert(String.valueOf(character));
                return Result.HANDLED;
            }
        }

        return switch (keyStroke.getKeyType()) {
            case ArrowLeft -> {
                moveCaretHorizontal(-1);
                yield Result.HANDLED;
            }
            case ArrowRight -> {
                moveCaretHorizontal(1);
                yield Result.HANDLED;
            }
            case ArrowUp -> {
                moveCaretVertical(-1);
                yield Result.HANDLED;
            }
            case ArrowDown -> {
                moveCaretVertical(1);
                yield Result.HANDLED;
            }
            case Home -> {
                moveCaretToVisualBoundary(true);
                yield Result.HANDLED;
            }
            case End -> {
                moveCaretToVisualBoundary(false);
                yield Result.HANDLED;
            }
            case Backspace -> {
                deleteBackward();
                yield Result.HANDLED;
            }
            case Delete -> {
                deleteForward();
                yield Result.HANDLED;
            }
            case PageUp -> {
                scrollRow = Math.max(0, scrollRow - Math.max(1, getSize().getRows() - 1));
                ensureCaretVisible();
                invalidate();
                yield Result.HANDLED;
            }
            case PageDown -> {
                scrollRow = scrollRow + Math.max(1, getSize().getRows() - 1);
                ensureCaretVisible();
                invalidate();
                yield Result.HANDLED;
            }
            default -> super.handleKeyStroke(keyStroke);
        };
    }

    private boolean isEnterCharacter(KeyStroke keyStroke) {
        if (keyStroke.getKeyType() != KeyType.Character) {
            return false;
        }
        Character character = keyStroke.getCharacter();
        return character != null && (character == '\n' || character == '\r');
    }

    private void moveCaretToMouse(MouseAction mouseAction) {
        WrappedLayout layout = currentLayout();
        TerminalPosition local = mouseAction.getPosition().minus(getGlobalPosition());
        int row = Math.max(0, Math.min(layout.lines().size() - 1, scrollRow + local.getRow()));
        int column = Math.max(0, local.getColumn());
        caretIndex = layout.indexAt(row, column);
        preferredColumn = -1;
        ensureCaretVisible();
        invalidate();
    }

    private void insert(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        text = text.substring(0, caretIndex) + value + text.substring(caretIndex);
        caretIndex += value.length();
        preferredColumn = -1;
        ensureCaretVisible();
        invalidate();
    }

    private void deleteBackward() {
        if (caretIndex <= 0) {
            return;
        }
        text = text.substring(0, caretIndex - 1) + text.substring(caretIndex);
        caretIndex -= 1;
        preferredColumn = -1;
        ensureCaretVisible();
        invalidate();
    }

    private void deleteForward() {
        if (caretIndex >= text.length()) {
            return;
        }
        text = text.substring(0, caretIndex) + text.substring(caretIndex + 1);
        preferredColumn = -1;
        ensureCaretVisible();
        invalidate();
    }

    private void moveCaretHorizontal(int delta) {
        caretIndex = Math.max(0, Math.min(text.length(), caretIndex + delta));
        preferredColumn = -1;
        ensureCaretVisible();
        invalidate();
    }

    private void moveCaretVertical(int deltaRows) {
        WrappedLayout layout = currentLayout();
        CaretPosition position = layout.positionOf(caretIndex);
        int targetRow = Math.max(0, Math.min(layout.lines().size() - 1, position.row() + deltaRows));
        int targetColumn = preferredColumn >= 0 ? preferredColumn : position.column();
        caretIndex = layout.indexAt(targetRow, targetColumn);
        preferredColumn = targetColumn;
        ensureCaretVisible();
        invalidate();
    }

    private void moveCaretToVisualBoundary(boolean start) {
        WrappedLayout layout = currentLayout();
        CaretPosition position = layout.positionOf(caretIndex);
        caretIndex = start ? layout.lines().get(position.row()).startIndex() : layout.lines().get(position.row()).endIndex();
        preferredColumn = -1;
        ensureCaretVisible();
        invalidate();
    }

    private void ensureCaretVisible() {
        WrappedLayout layout = currentLayout();
        CaretPosition position = layout.positionOf(caretIndex);
        int visibleRows = Math.max(1, getSize().getRows());
        int maxScroll = Math.max(0, layout.lines().size() - visibleRows);
        if (position.row() < scrollRow) {
            scrollRow = position.row();
        } else if (position.row() >= scrollRow + visibleRows) {
            scrollRow = position.row() - visibleRows + 1;
        }
        scrollRow = Math.max(0, Math.min(maxScroll, scrollRow));
    }

    private WrappedLayout currentLayout() {
        return layoutFor(text, Math.max(1, getSize().getColumns()));
    }

    static WrappedLayout layoutFor(String text, int width) {
        int safeWidth = Math.max(1, width);
        String value = text == null ? "" : text;
        List<WrappedLine> lines = new ArrayList<>();

        if (value.isEmpty()) {
            lines.add(new WrappedLine("", 0, 0));
            return new WrappedLayout(lines, value.length(), safeWidth);
        }

        int index = 0;
        while (index < value.length()) {
            int newlineIndex = value.indexOf('\n', index);
            int segmentEnd = newlineIndex >= 0 ? newlineIndex : value.length();
            if (segmentEnd == index) {
                lines.add(new WrappedLine("", index, index));
            } else {
                for (int cursor = index; cursor < segmentEnd; cursor += safeWidth) {
                    int chunkEnd = Math.min(segmentEnd, cursor + safeWidth);
                    lines.add(new WrappedLine(value.substring(cursor, chunkEnd), cursor, chunkEnd));
                }
            }
            if (newlineIndex < 0) {
                break;
            }
            index = newlineIndex + 1;
            if (index == value.length()) {
                lines.add(new WrappedLine("", value.length(), value.length()));
            }
        }

        if (lines.isEmpty()) {
            lines.add(new WrappedLine("", 0, 0));
        }
        return new WrappedLayout(lines, value.length(), safeWidth);
    }

    record WrappedLine(String text, int startIndex, int endIndex) {
        int length() {
            return text.length();
        }
    }

    record CaretPosition(int row, int column) {}

    static final class WrappedLayout {
        private final List<WrappedLine> lines;
        private final int textLength;
        private final int width;

        WrappedLayout(List<WrappedLine> lines, int textLength, int width) {
            this.lines = List.copyOf(lines);
            this.textLength = textLength;
            this.width = width;
        }

        List<WrappedLine> lines() {
            return lines;
        }

        CaretPosition positionOf(int caretIndex) {
            int clamped = Math.max(0, Math.min(textLength, caretIndex));
            for (int row = 0; row < lines.size(); row++) {
                WrappedLine line = lines.get(row);
                if (clamped >= line.startIndex() && clamped <= line.endIndex()) {
                    return new CaretPosition(row, clamped - line.startIndex());
                }
                if (row + 1 < lines.size()
                    && clamped == line.endIndex() + 1
                    && lines.get(row + 1).startIndex() == line.endIndex() + 1) {
                    return new CaretPosition(row + 1 < lines.size() ? row + 1 : row, 0);
                }
            }
            WrappedLine last = lines.getLast();
            return new CaretPosition(lines.size() - 1, Math.min(last.length(), Math.max(0, clamped - last.startIndex())));
        }

        int indexAt(int row, int column) {
            WrappedLine line = lines.get(Math.max(0, Math.min(lines.size() - 1, row)));
            int clampedColumn = Math.max(0, Math.min(line.length(), column));
            return Math.min(textLength, line.startIndex() + clampedColumn);
        }

        int width() {
            return width;
        }
    }

    private final class Renderer implements InteractableRenderer<ComposerInput> {
        @Override
        public TerminalPosition getCursorLocation(ComposerInput component) {
            component.ensureCaretVisible();
            CaretPosition position = component.currentLayout().positionOf(component.caretIndex);
            return new TerminalPosition(position.column(), Math.max(0, position.row() - component.scrollRow));
        }

        @Override
        public TerminalSize getPreferredSize(ComposerInput component) {
            return new TerminalSize(Math.max(1, component.currentLayout().width()), DEFAULT_VISIBLE_ROWS);
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, ComposerInput component) {
            component.ensureCaretVisible();
            ThemeDefinition definition = component.getThemeDefinition();
            ThemeStyle style = component.isFocused() ? definition.getActive() : definition.getNormal();
            graphics.applyThemeStyle(style);
            graphics.fill(' ');

            WrappedLayout layout = component.currentLayout();
            int visibleRows = Math.max(1, graphics.getSize().getRows());
            for (int row = 0; row < visibleRows; row++) {
                int sourceRow = component.scrollRow + row;
                if (sourceRow >= layout.lines().size()) {
                    break;
                }
                String lineText = layout.lines().get(sourceRow).text();
                if (!lineText.isEmpty()) {
                    graphics.putString(0, row, lineText);
                }
            }
        }
    }
}
