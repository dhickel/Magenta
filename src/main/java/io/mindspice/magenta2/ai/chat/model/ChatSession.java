package io.mindspice.magenta2.ai.chat.model;

public record ChatSession(
    String conversationId,
    String title,
    String titleJobStatus
) {
}
