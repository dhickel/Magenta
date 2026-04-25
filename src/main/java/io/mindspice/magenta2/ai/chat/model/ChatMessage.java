package io.mindspice.magenta2.ai.chat.model;

public record ChatMessage(
    String role,
    String text,
    String renderedHtml,
    String thinkingHtml
) {
}
