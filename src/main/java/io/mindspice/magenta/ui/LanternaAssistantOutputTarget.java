package io.mindspice.magenta.ui;

import java.util.Objects;

public final class LanternaAssistantOutputTarget implements AssistantOutputTarget {
    private static final java.util.regex.Pattern PREFIX_PATTERN = java.util.regex.Pattern.compile("^[^>\\r\\n]{1,64}>\\s*");

    private final TerminalUiRuntime runtime;
    private final ToolOutputFormatter formatter;

    public LanternaAssistantOutputTarget(TerminalUiRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.formatter = new ToolOutputFormatter();
    }

    @Override
    public synchronized void printAssistantToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        String sanitized = stripLeadingLabelPrefix(token);
        if (sanitized.isEmpty()) {
            return;
        }
        runtime.appendAssistantToken(sanitized);
    }

    @Override
    public synchronized void finishAssistantStreamLine() {
        runtime.finishAssistantStream();
    }

    @Override
    public synchronized void printAssistantFinal(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        runtime.appendAssistant(stripLeadingLabelPrefix(text));
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

    private String stripLeadingLabelPrefix(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return PREFIX_PATTERN.matcher(value).replaceFirst("");
    }
}
