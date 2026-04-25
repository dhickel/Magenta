package io.mindspice.magenta2.ai.chat.model;

public record ChatStreamEvent(
    String conversationId,
    String model,
    String text,
    String renderedHtml,
    String thinkingHtml,
    ContextUsage contextUsage,
    String message
) {
    public static ChatStreamEvent start(String conversationId, String model) {
        return new ChatStreamEvent(conversationId, model, null, null, null, null, null);
    }

    public static ChatStreamEvent message(String conversationId, String model, ChatMessage message) {
        return message(conversationId, model, message, null);
    }

    public static ChatStreamEvent message(
        String conversationId,
        String model,
        ChatMessage message,
        ContextUsage contextUsage
    ) {
        return new ChatStreamEvent(
            conversationId,
            model,
            message.text(),
            message.renderedHtml(),
            message.thinkingHtml(),
            contextUsage,
            null
        );
    }

    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent(null, null, null, null, null, null, message);
    }
}
