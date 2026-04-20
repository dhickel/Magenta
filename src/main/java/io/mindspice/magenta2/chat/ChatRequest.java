package io.mindspice.magenta2.chat;

public record ChatRequest(
    String conversationId,
    String message,
    String model
) {
}
