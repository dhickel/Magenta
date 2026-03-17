package io.mindspice.magenta.ui.tui.chat;

import casciian.TEditor;
import casciian.TKeypress;
import casciian.TLabel;
import casciian.TPanel;
import casciian.TText;
import casciian.TWindow;
import casciian.event.TKeypressEvent;
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
    private final boolean showTimestamps;

    private final TPanel transcriptPanel;
    private final TPanel composerPanel;
    private final TText transcript;
    private final ComposerEditor composer;
    private final TLabel metadataLine;
    private final TLabel statusLine;

    private final List<TranscriptBlock> blocks = new ArrayList<>();
    private String streamingTitle = null;
    private String streamingText = "";

    public ChatWindow(
            casciian.TApplication application,
            String title,
            int width,
            int height,
            ChatController controller,
            boolean showTimestamps
    ) {
        super(application, title, width, height, RESIZABLE);
        this.controller = Objects.requireNonNull(controller, "controller");
        this.showTimestamps = showTimestamps;

        this.transcriptPanel = addPanel(1, 1, Math.max(18, width - 2), Math.max(8, height - 8));
        this.transcriptPanel.setTitle("Chat");
        this.transcript = transcriptPanel.addText("", 1, 1,
                Math.max(16, transcriptPanel.getWidth() - 2),
                Math.max(MIN_TRANSCRIPT_ROWS, transcriptPanel.getHeight() - 2));
        this.transcript.getVerticalScroller().setVisible(true);
        this.transcript.getVerticalScroller().setBottomValue(1);

        this.composerPanel = addPanel(1, Math.max(2, height - 6), Math.max(18, width - 2), 4);
        this.composerPanel.setTitle("Input");
        this.composer = new ComposerEditor(
                composerPanel,
                1,
                1,
                Math.max(16, composerPanel.getWidth() - 2),
                Math.max(MIN_COMPOSER_ROWS, composerPanel.getHeight() - 2)
        );

        this.metadataLine = addLabel("", 1, 1);
        this.statusLine = addLabel("", 1, Math.max(1, height - 1));
        this.metadataLine.setWidth(Math.max(12, width - 2));
        this.statusLine.setWidth(Math.max(12, width - 2));

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

    public void setMetadata(String metadata) {
        String text = metadata == null ? "" : metadata;
        metadataLine.setLabel(compactSingleLine(text, Math.max(16, getWidth() - 4)));
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
    }

    public void clearTranscript() {
        blocks.clear();
        streamingTitle = null;
        streamingText = "";
        refreshTranscriptText();
    }

    public void appendBlock(String title, List<String> lines) {
        TranscriptBlock block = new TranscriptBlock(title, normalizeLines(lines));
        blocks.add(block);
        while (blocks.size() > MAX_TRANSCRIPT_BLOCKS) {
            blocks.remove(0);
        }
        refreshTranscriptText();
    }

    public void updateStreaming(String title, String content) {
        streamingTitle = title == null || title.isBlank() ? "assistant" : title;
        streamingText = content == null ? "" : content;
        refreshTranscriptText();
    }

    public void finishStreaming() {
        if (streamingTitle == null) {
            return;
        }
        appendBlock(streamingTitle, List.of(streamingText));
        streamingTitle = null;
        streamingText = "";
    }

    private void relayout() {
        int width = Math.max(32, getWidth());
        int height = Math.max(12, getHeight());

        int innerWidth = Math.max(20, width - 2);
        int usableRows = Math.max(8, height - 6);
        int composerRows = Math.max(MIN_COMPOSER_ROWS, Math.min(6, usableRows / 4));
        int transcriptRows = Math.max(MIN_TRANSCRIPT_ROWS, usableRows - composerRows - 2);

        metadataLine.setX(1);
        metadataLine.setY(1);
        metadataLine.setWidth(innerWidth);

        transcriptPanel.setDimensions(1, 2, innerWidth, transcriptRows + 2);
        transcript.setDimensions(1, 1,
                Math.max(16, transcriptPanel.getWidth() - 2),
                Math.max(MIN_TRANSCRIPT_ROWS, transcriptPanel.getHeight() - 2));
        transcript.getVerticalScroller().setVisible(true);

        int statusY = transcriptPanel.getY() + transcriptPanel.getHeight();
        statusLine.setX(1);
        statusLine.setY(statusY);
        statusLine.setWidth(innerWidth);

        int composerY = statusY + 1;
        composerPanel.setDimensions(1, composerY, innerWidth, composerRows + 2);
        composer.setDimensions(1, 1,
                Math.max(16, composerPanel.getWidth() - 2),
                Math.max(MIN_COMPOSER_ROWS, composerPanel.getHeight() - 2));

        refreshTranscriptText();
    }

    private void refreshTranscriptText() {
        List<String> rendered = new ArrayList<>();
        for (TranscriptBlock block : blocks) {
            rendered.add(formatBlock(block.title(), block.lines()));
        }
        if (streamingTitle != null) {
            rendered.add(formatBlock(streamingTitle, List.of(streamingText)));
        }
        String renderedText = String.join("\n\n", rendered);
        transcript.setText(renderedText);
        int visualLines = renderedText.isBlank() ? 1 : renderedText.split("\\R", -1).length;
        transcript.getVerticalScroller().setTopValue(0);
        transcript.getVerticalScroller().setBottomValue(Math.max(1, visualLines));
        transcript.toBottom();
    }

    private String formatBlock(String title, List<String> lines) {
        String safeTitle = title == null || title.isBlank() ? "event" : title;
        String header = showTimestamps
                ? TS_FORMAT.format(Instant.now()) + " [" + safeTitle + "]"
                : "[" + safeTitle + "]";

        List<String> out = new ArrayList<>();
        out.add(header);
        if (lines.isEmpty()) {
            out.add("| ");
        } else {
            for (String line : lines) {
                String safe = line == null ? "" : line;
                String[] physical = safe.split("\\R", -1);
                for (String value : physical) {
                    out.add("| " + value);
                }
            }
        }
        return String.join("\n", out);
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
                }
                return;
            }
            super.onKeypress(event);
        }
    }

    private record TranscriptBlock(String title, List<String> lines) {
        private TranscriptBlock {
            title = title == null ? "event" : title.trim().toLowerCase(Locale.ROOT);
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }
}
