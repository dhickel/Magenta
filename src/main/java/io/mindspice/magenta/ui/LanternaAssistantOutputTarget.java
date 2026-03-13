package io.mindspice.magenta.ui;

import java.util.Objects;

public final class LanternaAssistantOutputTarget implements AssistantOutputTarget {

    private final TerminalUiRuntime runtime;
    private final ToolOutputFormatter formatter;
    private final StringBuilder streamingBuffer = new StringBuilder();

    public LanternaAssistantOutputTarget(TerminalUiRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.formatter = new ToolOutputFormatter();
    }

    @Override
    public synchronized void printAssistantToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        // first token includes assistant prefix from writer; preserve readable text in one final block
        streamingBuffer.append(token);
    }

    @Override
    public synchronized void finishAssistantStreamLine() {
        if (streamingBuffer.isEmpty()) {
            return;
        }
        runtime.appendAssistant(streamingBuffer.toString());
        streamingBuffer.setLength(0);
    }

    @Override
    public synchronized void printAssistantFinal(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        runtime.appendAssistant(text);
    }

    @Override
    public void printToolCall(String toolName, String argumentsJson) {
        // Internal detail; rendered tool results are operator-relevant signal.
    }

    @Override
    public void printToolResult(String toolName, String content, boolean failed) {
        ToolOutputFormatter.FormattedToolResult formatted = formatter.formatResult(toolName, content);
        String line = formatted.title();
        if (!formatted.lines().isEmpty()) {
            line += " | " + String.join(" | ", formatted.lines());
        }
        runtime.appendToolLine(line);
    }

    @Override
    public void printStreamFallbackNotice(String reason) {
        // Kept intentionally quiet to reduce transcript noise.
    }
}
