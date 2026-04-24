package io.mindspice.magenta2.ai.chat.model;

public record ChatStreamEvent(
    String conversationId,
    String model,
    String text,
    String renderedHtml,
    String thinkingHtml,
    String message
) {
    public static ChatStreamEvent start(String conversationId, String model) {
        return new ChatStreamEvent(conversationId, model, null, null, null, null);
    }

    public static ChatStreamEvent message(String conversationId, String model, ChatHistoryMessage message) {
        return new ChatStreamEvent(
            conversationId,
            model,
            message.text(),
            message.renderedHtml(),
            message.thinkingHtml(),
            null
        );
    }

    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent(null, null, null, null, null, message);
    }
}
