package io.mindspice.magenta.ui.tui.chat;

import casciian.TEditor;
import casciian.TKeypress;
import casciian.TLabel;
import casciian.TPanel;
import casciian.TVScroller;
import casciian.TWindow;
import casciian.bits.CellAttributes;
import casciian.bits.Color;
import casciian.bits.ColorTheme;
import casciian.event.TKeypressEvent;
import io.mindspice.magenta.ui.TerminalUiConfig;
import casciian.event.TResizeEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ChatWindow extends TWindow {
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final int MIN_TRANSCRIPT_ROWS = 6;
    private static final int MIN_COMPOSER_ROWS = 3;
    private static final int MAX_TRANSCRIPT_BLOCKS = 800;

    private ChatController controller;
    private final TerminalUiConfig config;
    private final boolean showTimestamps;

    private final TPanel transcriptPanel;
    private final TPanel infoPanel;
    private final TPanel composerPanel;
    private final TranscriptView transcript;
    private final ComposerEditor composer;
    private final TVScroller composerScroller;
    private final TLabel statusLine;

    private final List<TranscriptBlock> blocks = new ArrayList<>();
    private String streamingTitle = null;
    private String streamingStyleKey = null;
    private String streamingText = "";

    public ChatWindow(
            casciian.TApplication application,
            String title,
            int width,
            int height,
            ChatController controller,
            TerminalUiConfig config
    ) {
        super(application, title, width, height, RESIZABLE);
        this.controller = Objects.requireNonNull(controller, "controller");
        this.config = Objects.requireNonNull(config, "config");
        this.showTimestamps = config.rendering().showTimestamps();

        this.transcriptPanel = addPanel(0, 0, Math.max(18, width - 2), Math.max(8, height - 8));
        this.transcriptPanel.setTitle("");
        this.transcript = new TranscriptView(
                transcriptPanel,
                1,
                1,
                Math.max(15, transcriptPanel.getWidth() - 3),
                Math.max(MIN_TRANSCRIPT_ROWS, transcriptPanel.getHeight() - 3)
        );
        this.transcript.getVerticalScroller().setVisible(true);
        this.transcript.getVerticalScroller().setBottomValue(1);

        this.infoPanel = addPanel(0, Math.max(1, height - 7), Math.max(18, width - 2), 1);
        this.infoPanel.setTitle("");

        this.composerPanel = addPanel(0, Math.max(1, height - 6), Math.max(18, width - 2), 4);
        this.composerPanel.setTitle("");
        this.transcriptPanel.setBorderStyle("single");
        this.composerPanel.setBorderStyle("single");
        this.composer = new ComposerEditor(
                composerPanel,
                1,
                1,
                Math.max(14, composerPanel.getWidth() - 4),
                Math.max(MIN_COMPOSER_ROWS, composerPanel.getHeight() - 3)
        );
        this.composer.setAutoWrap(true);
        this.composerScroller = new TVScroller(
                composerPanel,
                Math.max(2, composerPanel.getWidth() - 3),
                1,
                Math.max(MIN_COMPOSER_ROWS, composerPanel.getHeight() - 3)
        );
        this.composerScroller.setTopValue(0);
        this.composerScroller.setBottomValue(1);
        this.composerScroller.setValue(0);
        resetComposerViewport();

        this.statusLine = infoPanel.addLabel("", 0, 0);
        this.statusLine.setWidth(Math.max(12, width - 4));

        relayout();
    }

    @Override
    public void onResize(TResizeEvent event) {
        super.onResize(event);
        relayout();
    }

    public void setStatus(String status) {
        String text = status == null ? "" : status;
        statusLine.setLabel(compactSingleLine(text, Math.max(16, getWidth() - 4)));
    }

    public void setController(ChatController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public void setComposerEnabled(boolean enabled) {
        composer.setEnabled(enabled);
    }

    public void focusComposer() {
        composer.activate();
    }

    public void clearComposer() {
        composer.setText("");
        resetComposerViewport();
        refreshComposerScrollIndicator();
    }

    public void clearTranscript() {
        blocks.clear();
        streamingTitle = null;
        streamingStyleKey = null;
        streamingText = "";
        refreshTranscriptText();
    }

    public void appendBlock(String title, List<String> lines) {
        appendBlock(title, title, lines);
    }

    public void appendBlock(String title, String styleKey, List<String> lines) {
        TranscriptBlock block = new TranscriptBlock(title, styleKey, normalizeLines(lines));
        blocks.add(block);
        while (blocks.size() > MAX_TRANSCRIPT_BLOCKS) {
            blocks.remove(0);
        }
        refreshTranscriptText();
    }

    public void updateStreaming(String title, String content) {
        updateStreaming(title, title, content);
    }

    public void updateStreaming(String title, String styleKey, String content) {
        streamingTitle = title == null || title.isBlank() ? "assistant" : title;
        streamingStyleKey = styleKey == null || styleKey.isBlank() ? streamingTitle : styleKey;
        streamingText = content == null ? "" : content;
        refreshTranscriptText();
    }

    public void finishStreaming() {
        StreamingCommit commit = commitStreaming(streamingTitle, streamingStyleKey, streamingText);
        if (commit.block() == null) {
            return;
        }
        streamingTitle = commit.nextStreamingTitle();
        streamingStyleKey = commit.nextStreamingStyleKey();
        streamingText = commit.nextStreamingText();
        blocks.add(commit.block());
        while (blocks.size() > MAX_TRANSCRIPT_BLOCKS) {
            blocks.remove(0);
        }
        refreshTranscriptText();
    }

    private void relayout() {
        int width = Math.max(32, getWidth());
        int height = Math.max(12, getHeight());

        int clientWidth = Math.max(20, width - 2);
        int clientHeight = Math.max(10, height - 2);
        int composerRows = Math.max(MIN_COMPOSER_ROWS, Math.min(6, clientHeight / 4));
        int composerPanelHeight = composerRows + 2;
        int infoHeight = 1;
        int transcriptPanelHeight = Math.max(MIN_TRANSCRIPT_ROWS + 2, clientHeight - composerPanelHeight - infoHeight);

        transcriptPanel.setDimensions(0, 0, clientWidth, transcriptPanelHeight);
        transcript.setDimensions(1, 1,
                Math.max(15, transcriptPanel.getWidth() - 3),
                Math.max(MIN_TRANSCRIPT_ROWS, transcriptPanel.getHeight() - 3));
        transcript.syncLayout();

        int infoY = transcriptPanel.getY() + transcriptPanel.getHeight();
        infoPanel.setDimensions(0, infoY, clientWidth, infoHeight);
        statusLine.setX(0);
        statusLine.setY(0);
        statusLine.setWidth(Math.max(12, infoPanel.getWidth() - 2));

        int composerY = infoY + infoPanel.getHeight();
        composerPanel.setDimensions(0, composerY, clientWidth, composerPanelHeight);
        composer.setDimensions(1, 1,
                Math.max(14, composerPanel.getWidth() - 4),
                Math.max(MIN_COMPOSER_ROWS, composerPanel.getHeight() - 3));
        composerScroller.setX(Math.max(2, composerPanel.getWidth() - 3));
        composerScroller.setY(1);
        composerScroller.setHeight(Math.max(MIN_COMPOSER_ROWS, composerPanel.getHeight() - 3));

        refreshTranscriptText();
        refreshComposerScrollIndicator();
    }

    private void refreshTranscriptText() {
        int previousValue = transcript.getVerticalScroller().getValue();
        int previousBottom = Math.max(0, transcript.getVerticalScroller().getBottomValue());
        boolean stickToBottom = previousValue >= previousBottom;

        int contentWidth = Math.max(8, transcript.getWidth() - 1);
        List<RenderedLine> rendered = new ArrayList<>();
        for (TranscriptBlock block : blocks) {
            rendered.addAll(formatBlock(block, contentWidth));
            rendered.add(new RenderedLine("", blockStyle(block.styleKey())));
        }
        if (streamingTitle != null) {
            rendered.addAll(formatBlock(new TranscriptBlock(streamingTitle, streamingStyleKey, List.of(streamingText)), contentWidth));
        }
        transcript.setLines(rendered);
        if (stickToBottom) {
            transcript.getVerticalScroller().setValue(transcript.getVerticalScroller().getBottomValue());
            return;
        }
        transcript.getVerticalScroller().setValue(Math.min(previousValue,
                Math.max(0, transcript.getVerticalScroller().getBottomValue())));
    }

    private void refreshComposerScrollIndicator() {
        int lineCount = Math.max(1, composer.getLineCount());
        int visibleRows = Math.max(1, composer.getHeight());
        int visibleTop = Math.max(0, composer.getVisibleRowNumber() - 1);
        int bottomValue = Math.max(1, lineCount - visibleRows + 1);
        composerScroller.setTopValue(0);
        composerScroller.setBottomValue(bottomValue);
        composerScroller.setBigChange(visibleRows);
        composerScroller.setValue(Math.min(visibleTop, bottomValue));
    }

    private List<RenderedLine> formatBlock(TranscriptBlock block, int contentWidth) {
        BlockStyle style = blockStyle(block.styleKey());
        return formatBlockLines(block.title(), block.lines(), showTimestamps, Instant.now(), contentWidth).stream()
                .map(line -> new RenderedLine(line, style))
                .toList();
    }

    private List<String> normalizeLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream().map(value -> value == null ? "" : value).toList();
    }

    private String compactSingleLine(String value, int maxLen) {
        String single = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        int limit = Math.max(16, maxLen);
        if (single.length() <= limit) {
            return single;
        }
        return single.substring(0, limit - 3) + "...";
    }

    static List<String> formatBlockLines(
            String title,
            List<String> lines,
            boolean showTimestamps,
            Instant timestamp,
            int contentWidth
    ) {
        String safeTitle = title == null || title.isBlank() ? "event" : title;
        String header = showTimestamps
                ? "┌─ [" + TS_FORMAT.format(timestamp == null ? Instant.now() : timestamp) + "] " + safeTitle
                : "┌─ " + safeTitle;

        List<String> out = new ArrayList<>();
        out.add(header);
        if (lines == null || lines.isEmpty()) {
            out.add("│ ");
            return out;
        }
        for (String line : lines) {
            String safe = line == null ? "" : line;
            String[] physical = safe.split("\\R", -1);
            for (String value : physical) {
                out.addAll(wrapTranscriptLine("│ ", value, contentWidth));
            }
        }
        return out;
    }

    private static List<String> wrapTranscriptLine(String prefix, String text, int contentWidth) {
        int available = Math.max(1, contentWidth - prefix.length());
        String value = text == null ? "" : text;
        List<String> out = new ArrayList<>();
        if (value.isEmpty()) {
            out.add(prefix);
            return out;
        }
        int index = 0;
        while (index < value.length()) {
            int end = Math.min(value.length(), index + available);
            String chunk = value.substring(index, end);
            out.add(prefix + chunk);
            index = end;
        }
        return out;
    }

    static StreamingCommit commitStreaming(String title, String styleKey, String text) {
        if (title == null) {
            return new StreamingCommit(null, null, "", null);
        }
        return new StreamingCommit(
                null,
                null,
                "",
                new TranscriptBlock(title, styleKey, List.of(text == null ? "" : text))
        );
    }

    private void resetComposerViewport() {
        composer.setVisibleRowNumber(1);
        composer.setEditingRowNumber(1);
        composer.setEditingColumnNumber(1);
        composer.setCursorX(0);
        composer.setCursorY(0);
    }

    private BlockStyle blockStyle(String styleKey) {
        String normalized = styleKey == null ? "" : styleKey.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "assistant" -> BlockStyle.ASSISTANT;
            case "tool", "warn" -> BlockStyle.TOOL;
            case "error" -> BlockStyle.ERROR;
            case "user" -> BlockStyle.USER;
            case "security", "route", "context", "help", "session", "info" -> BlockStyle.INFO;
            default -> BlockStyle.DEFAULT;
        };
    }

    private final class ComposerEditor extends TEditor {
        private ComposerEditor(casciian.TWidget parent, int x, int y, int width, int height) {
            super(parent, "", x, y, width, height);
            setAutoWrap(false);
        }

        @Override
        public void onKeypress(TKeypressEvent event) {
            if (event == null) {
                return;
            }
            if (event.matchesKey(TKeypress.kbCtrlC)) {
                controller.requestAbort();
                return;
            }
            if (event.matchesKey(TKeypress.kbCtrlN)) {
                super.onKeypress(new TKeypressEvent(event.getBackend(), TKeypress.kbEnter));
                return;
            }
            if (event.matchesKey(TKeypress.kbEnter)) {
                String submitted = getText();
                if (controller.submitComposerText(submitted)) {
                    setText("");
                    resetComposerViewport();
                }
                refreshComposerScrollIndicator();
                return;
            }
            super.onKeypress(event);
            refreshComposerScrollIndicator();
        }
    }

    private final class TranscriptView extends casciian.TScrollable {
        private List<RenderedLine> lines = List.of();

        private TranscriptView(casciian.TWidget parent, int x, int y, int width, int height) {
            super(parent, x, y, width, height);
            this.vScroller = new TVScroller(this, Math.max(0, width - 1), 0, Math.max(1, height));
            this.vScroller.setTopValue(0);
            this.vScroller.setBottomValue(1);
            this.vScroller.setValue(0);
            syncLayout();
        }

        private void syncLayout() {
            vScroller.setX(Math.max(0, getWidth() - 1));
            vScroller.setY(0);
            vScroller.setHeight(Math.max(1, getHeight()));
            vScroller.setBigChange(Math.max(1, getHeight()));
        }

        private void setLines(List<RenderedLine> lines) {
            this.lines = lines == null ? List.of() : List.copyOf(lines);
            int bottomValue = Math.max(0, this.lines.size() - Math.max(1, getHeight()));
            vScroller.setTopValue(0);
            vScroller.setBottomValue(bottomValue);
            vScroller.setValue(Math.min(vScroller.getValue(), bottomValue));
        }

        @Override
        public void onMouseDown(casciian.event.TMouseEvent event) {
            if (event == null) {
                return;
            }
            if (event.isMouseWheelUp()) {
                vScroller.decrement();
                return;
            }
            if (event.isMouseWheelDown()) {
                vScroller.increment();
                return;
            }
            super.onMouseDown(event);
        }

        @Override
        public void onKeypress(TKeypressEvent event) {
            if (event == null) {
                return;
            }
            if (event.matchesKey(TKeypress.kbPgUp)) {
                vScroller.bigDecrement();
                return;
            }
            if (event.matchesKey(TKeypress.kbPgDn)) {
                vScroller.bigIncrement();
                return;
            }
            if (event.matchesKey(TKeypress.kbHome)) {
                vScroller.toTop();
                return;
            }
            if (event.matchesKey(TKeypress.kbEnd)) {
                vScroller.toBottom();
                return;
            }
            super.onKeypress(event);
        }

        @Override
        public void draw() {
            CellAttributes base = getTheme().getColor(ColorTheme.TTEXT);
            int contentWidth = Math.max(1, getWidth() - 1);
            int start = Math.max(0, vScroller.getValue());
            for (int row = 0; row < getHeight(); row++) {
                putStringXY(0, row, padRight("", contentWidth), base);
                int lineIndex = start + row;
                if (lineIndex >= lines.size()) {
                    continue;
                }
                RenderedLine line = lines.get(lineIndex);
                putStringXY(0, row, padRight(trimToWidth(line.text(), contentWidth), contentWidth), styleAttributes(line.style()));
            }
        }

        private String trimToWidth(String value, int width) {
            if (value == null || value.isEmpty()) {
                return "";
            }
            return value.length() <= width ? value : value.substring(0, width);
        }

        private String padRight(String value, int width) {
            String text = value == null ? "" : value;
            if (text.length() >= width) {
                return text;
            }
            return text + " ".repeat(width - text.length());
        }

        private CellAttributes styleAttributes(BlockStyle style) {
            CellAttributes attr = new CellAttributes(getTheme().getColor(ColorTheme.TTEXT));
            switch (style) {
                case USER -> attr.setTo(resolveRoleColor("magenta.transcript.user", Color.WHITE));
                case ASSISTANT -> attr.setTo(resolveRoleColor("magenta.transcript.assistant", Color.GREEN));
                case TOOL -> attr.setTo(resolveRoleColor("magenta.transcript.tool", Color.YELLOW));
                case ERROR -> attr.setTo(resolveRoleColor("magenta.transcript.error", Color.RED));
                case INFO -> attr.setTo(resolveRoleColor("magenta.transcript.info", Color.CYAN));
                case DEFAULT -> {
                }
            }
            return attr;
        }

        private CellAttributes resolveRoleColor(String key, Color fallback) {
            CellAttributes roleColor = getTheme().getColor(key);
            if (roleColor != null) {
                return roleColor;
            }
            CellAttributes fallbackColor = new CellAttributes(getTheme().getColor(ColorTheme.TTEXT));
            fallbackColor.setForeColor(fallback);
            return fallbackColor;
        }
    }

    static record TranscriptBlock(String title, String styleKey, List<String> lines) {
        TranscriptBlock {
            title = title == null ? "event" : title.trim().toLowerCase(Locale.ROOT);
            styleKey = styleKey == null ? title : styleKey.trim().toLowerCase(Locale.ROOT);
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    static record StreamingCommit(
            String nextStreamingTitle,
            String nextStreamingStyleKey,
            String nextStreamingText,
            TranscriptBlock block
    ) {}

    private record RenderedLine(String text, BlockStyle style) {}

    private enum BlockStyle {
        USER,
        ASSISTANT,
        TOOL,
        ERROR,
        INFO,
        DEFAULT
    }
}
