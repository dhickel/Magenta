package io.mindspice.magenta2.chat;

public record ChatResponse(
    String conversationId,
    String model,
    String response
) {
}
