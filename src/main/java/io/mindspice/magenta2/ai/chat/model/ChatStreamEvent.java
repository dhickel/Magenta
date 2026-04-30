package io.mindspice.magenta2.ai.chat.model;

public record ChatStreamEvent(
    String conversationId,
    String model,
    String text,
    String renderedHtml,
    String thinkingHtml,
    ChatToolActivity toolActivity,
    ContextUsage contextUsage,
    ChatPlanState planState,
    String message
) {
    public static ChatStreamEvent start(String conversationId, String model) {
        return new ChatStreamEvent(conversationId, model, null, null, null, null, null, null, null);
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
            message.toolActivity(),
            contextUsage,
            null,
            null
        );
    }

    public static ChatStreamEvent tool(String conversationId, String model, ChatMessage message) {
        return new ChatStreamEvent(
            conversationId,
            model,
            message.text(),
            message.renderedHtml(),
            message.thinkingHtml(),
            message.toolActivity(),
            null,
            null,
            null
        );
    }

    public static ChatStreamEvent error(String message) {
        return new ChatStreamEvent(null, null, null, null, null, null, null, null, message);
    }

    public ChatStreamEvent withPlanState(ChatPlanState planState) {
        return new ChatStreamEvent(
            conversationId,
            model,
            text,
            renderedHtml,
            thinkingHtml,
            toolActivity,
            contextUsage,
            planState,
            message
        );
    }
}
