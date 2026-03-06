package io.mindspice.magenta.ui;

import io.mindspice.magenta.ui.render.UiRenderBlock;
import io.mindspice.magenta.ui.render.UiRenderer;
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
        ToolOutputFormatter.FormattedToolCall formatted = formatter.formatCall(toolName, argumentsJson);
        renderer.renderBlock(new UiRenderBlock(formatted.title(), formatted.lines(), formatted.style()));
    }

    @Override
    public void printToolResult(String toolName, String content, boolean failed) {
        ToolOutputFormatter.FormattedToolResult formatted = formatter.formatResult(toolName, content);
        renderer.renderBlock(new UiRenderBlock(formatted.title(), formatted.lines(), formatted.style()));
    }

    @Override
    public void printStreamFallbackNotice(String reason) {
        renderer.printInfo("stream-fallback> " + reason);
    }
}
