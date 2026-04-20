package io.mindspice.magenta2.chat;

import java.util.List;

public record ChatCommandResponse(
    String conversationId,
    String model,
    String message,
    List<String> conversationIds,
    List<ChatHistoryMessage> history
) {
}
