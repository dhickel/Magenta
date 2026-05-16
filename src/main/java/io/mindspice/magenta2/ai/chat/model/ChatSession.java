package io.mindspice.magenta2.ai.chat.model;

public record ChatSession(
    String conversationId,
    String title,
    String titleJobStatus,
    boolean favorite,
    boolean archived,
    String updatedAt,
    ChatSessionOrigin origin,
    String agentId
) {
    public ChatSession(String conversationId, String title, String titleJobStatus) {
        this(conversationId, title, titleJobStatus, false, false, null, null, null);
    }

    public ChatSession(
        String conversationId,
        String title,
        String titleJobStatus,
        boolean favorite,
        boolean archived,
        String updatedAt
    ) {
        this(conversationId, title, titleJobStatus, favorite, archived, updatedAt, null, null);
    }
}
