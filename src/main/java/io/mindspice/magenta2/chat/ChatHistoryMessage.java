package io.mindspice.magenta2.chat;

public record ChatHistoryMessage(
    String role,
    String text,
    String renderedHtml,
    String thinkingHtml
) {
}
