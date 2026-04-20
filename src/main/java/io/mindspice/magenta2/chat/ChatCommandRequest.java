package io.mindspice.magenta2.chat;

public record ChatCommandRequest(
    String conversationId,
    String command
) {
}
