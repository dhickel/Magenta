package io.mindspice.magenta.ui;

import io.mindspice.magenta.ui.render.UiRenderBlock;
import io.mindspice.magenta.ui.render.UiRenderer;

import java.util.List;
import java.util.Objects;

public final class TerminalAssistantOutputTarget implements AssistantOutputTarget {

    private final UiRenderer renderer;
    private final ToolOutputFormatter formatter;

    public TerminalAssistantOutputTarget(UiRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.formatter = new ToolOutputFormatter();
    }

    @Override
    public void printAssistantToken(String token) {
        renderer.printStreamToken(token);
    }

    @Override
    public void finishAssistantStreamLine() {
        renderer.finishStreamLine();
    }

    @Override
    public void printAssistantFinal(String text) {
        renderer.printAssistant(text);
    }

    @Override
    public void printToolCall(String toolName, String argumentsJson) {
        // Tool call events are internal trace details; terminal output only shows results.
    }

    @Override
    public void printToolResult(String toolName, String content, boolean failed) {
        ToolOutputFormatter.FormattedToolResult formatted = formatter.formatResult(toolName, content);
        renderer.renderBlock(new UiRenderBlock(
                "",
                List.of("  " + singleLine(formatted.title(), formatted.lines())),
                formatted.style()
        ));
    }

    @Override
    public void printStreamFallbackNotice(String reason) {
        // Fallback notices are intentionally suppressed in terminal output.
    }

    private String singleLine(String title, List<String> lines) {
        String base = title == null ? "" : title;
        if (lines == null || lines.isEmpty()) {
            return base;
        }
        String suffix = lines.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
        return suffix.isEmpty() ? base : base + " | " + suffix;
    }
}
