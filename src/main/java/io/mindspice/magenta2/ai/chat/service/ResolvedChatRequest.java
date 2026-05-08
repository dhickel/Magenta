package io.mindspice.magenta2.ai.chat.service;

public record ResolvedChatRequest(
    String conversationId,
    String message,
    String model,
    boolean newConversation,
    boolean titleJobEligible
) {
    public ResolvedChatRequest(String conversationId, String message, String model) {
        this(conversationId, message, model, false, false);
    }

    public ResolvedChatRequest(String conversationId, String message, String model, boolean newConversation) {
        this(conversationId, message, model, newConversation, false);
    }

    public ResolvedChatRequest withoutTitleJob() {
        return new ResolvedChatRequest(conversationId, message, model, newConversation, false);
    }
}
