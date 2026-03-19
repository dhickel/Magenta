package io.mindspice.magenta.ui.tui.windows;

import casciian.TApplication;
import casciian.TText;
import casciian.event.TResizeEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

public final class EventViewerWindow extends WorkspaceTWindow {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final int maxLines;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final TText eventText;
    private final Runnable closeCallback;

    public EventViewerWindow(TApplication application, String title, int width, int height, int maxLines) {
        this(application, title, width, height, maxLines, () -> { });
    }

    public EventViewerWindow(
            TApplication application,
            String title,
            int width,
            int height,
            int maxLines,
            Runnable closeCallback
    ) {
        super(application, title, width, height);
        this.maxLines = Math.max(100, maxLines);
        this.eventText = addText("", 1, 1, Math.max(20, width - 2), Math.max(8, height - 2));
        this.closeCallback = closeCallback == null ? () -> { } : closeCallback;
        addButton("Clear", 1, 0, this::clear);
    }

    public void appendEvent(String source, List<String> messageLines) {
        String prefix = "[" + TIMESTAMP_FORMAT.format(Instant.now()) + "] " + normalizeSource(source) + ": ";
        if (messageLines == null || messageLines.isEmpty()) {
            appendLine(prefix);
            return;
        }
        appendLine(prefix + Objects.toString(messageLines.getFirst(), ""));
        for (int i = 1; i < messageLines.size(); i++) {
            appendLine("  " + Objects.toString(messageLines.get(i), ""));
        }
    }

    public void clear() {
        lines.clear();
        eventText.setText("");
    }

    @Override
    public void onResize(TResizeEvent event) {
        super.onResize(event);
        if (event.getType() != TResizeEvent.Type.WIDGET) {
            return;
        }
        eventText.onResize(new TResizeEvent(
                event.getBackend(),
                TResizeEvent.Type.WIDGET,
                Math.max(20, event.getWidth() - 2),
                Math.max(8, event.getHeight() - 2)
        ));
    }

    @Override
    protected void onClose() {
        super.onClose();
        closeCallback.run();
    }

    private void appendLine(String line) {
        lines.addLast(line == null ? "" : line);
        while (lines.size() > maxLines) {
            lines.removeFirst();
        }
        eventText.setText(String.join("\n", lines));
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "event";
        }
        return source.trim();
    }
}
