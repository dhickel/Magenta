package io.mindspice.magenta2.ai.chat.model;

public record ChatHistoryMessage(
    String role,
    String text,
    String renderedHtml,
    String thinkingHtml
) {
}
