package io.mindspice.magenta.ui.casciian;

import io.mindspice.magenta.ui.AssistantOutputTarget;
import io.mindspice.magenta.ui.ToolOutputFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class CasciianAssistantOutputTarget implements AssistantOutputTarget {
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[^>\\r\\n]{1,64}>\\s*");

    private final CasciianTerminalUiRuntime runtime;
    private final ToolOutputFormatter formatter;

    public CasciianAssistantOutputTarget(CasciianTerminalUiRuntime runtime) {
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
        // Internal detail; only rendered tool results are operator-relevant.
    }

    @Override
    public void printToolResult(String toolName, String content, boolean failed) {
        ToolOutputFormatter.FormattedToolResult formatted = formatter.formatResult(toolName, content);
        runtime.appendToolLine(renderToolText(formatted.title(), formatted.lines()));
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

    private String renderToolText(String title, List<String> lines) {
        String base = title == null ? "" : title;
        if (lines == null || lines.isEmpty()) {
            return base;
        }
        List<String> nonBlank = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                nonBlank.add(trimmed);
            }
        }
        if (nonBlank.isEmpty()) {
            return base;
        }
        StringBuilder out = new StringBuilder(base).append(" | ").append(nonBlank.getFirst());
        for (int i = 1; i < nonBlank.size(); i++) {
            out.append('\n').append("  ").append(nonBlank.get(i));
        }
        return out.toString();
    }
}
