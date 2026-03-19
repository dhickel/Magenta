package io.mindspice.magenta.ui.tui.windows;

import casciian.TApplication;
import casciian.TLabel;
import casciian.TText;
import casciian.event.TResizeEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DocumentViewerWindow extends WorkspaceTWindow {
    private static final int DEFAULT_MAX_LINES = 400;
    private static final int DEFAULT_MAX_CHARS = 40_000;

    private final int maxLines;
    private final int maxChars;
    private final Path workspaceRoot;

    private final TLabel status;
    private final TText viewer;

    public DocumentViewerWindow(
            TApplication application,
            String title,
            int width,
            int height,
            Path workspaceRoot,
            int maxLines,
            int maxChars
    ) {
        super(application, title, width, height);
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.maxLines = maxLines <= 0 ? DEFAULT_MAX_LINES : maxLines;
        this.maxChars = maxChars <= 0 ? DEFAULT_MAX_CHARS : maxChars;

        this.status = addLabel("read-only", 1, 1);
        this.viewer = addText("", 1, 2, Math.max(24, width - 2), Math.max(8, height - 3));
    }

    public void openDocument(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            status.setLabel("read-only | no file configured");
            viewer.setText("No document path is configured for this window.");
            return;
        }

        Path documentPath = resolvePath(configuredPath);
        if (!Files.isRegularFile(documentPath)) {
            status.setLabel("read-only | file missing");
            viewer.setText("Document not found:\n" + documentPath);
            return;
        }

        String content = readBounded(documentPath);
        viewer.setText(content);
        status.setLabel("read-only | " + workspaceRelative(documentPath));
    }

    @Override
    public void onResize(TResizeEvent event) {
        super.onResize(event);
        if (event.getType() != TResizeEvent.Type.WIDGET) {
            return;
        }
        int width = Math.max(24, event.getWidth() - 2);
        int height = Math.max(8, event.getHeight() - 3);
        viewer.onResize(new TResizeEvent(event.getBackend(), TResizeEvent.Type.WIDGET, width, height));
    }

    private Path resolvePath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return workspaceRoot.resolve(path).normalize();
    }

    private String readBounded(Path path) {
        StringBuilder builder = new StringBuilder();
        int lineCount = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (lineCount > maxLines) {
                    builder.append("\n... [truncated: max lines reached]");
                    break;
                }
                if (builder.length() + line.length() + 1 > maxChars) {
                    builder.append("\n... [truncated: max chars reached]");
                    break;
                }
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(line);
            }
        } catch (IOException e) {
            return "Failed to read document:\n" + path + "\n" + e.getMessage();
        }

        if (builder.isEmpty()) {
            return "[empty file]";
        }
        return builder.toString();
    }

    private String workspaceRelative(Path path) {
        try {
            return workspaceRoot.relativize(path).toString();
        } catch (Exception ignored) {
            return path.toString();
        }
    }
}
