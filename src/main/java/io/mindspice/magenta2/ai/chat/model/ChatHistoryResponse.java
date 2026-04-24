package io.mindspice.magenta2.ai.chat.model;

import java.util.List;

public record ChatHistoryResponse(
    String conversationId,
    String model,
    List<ChatHistoryMessage> messages
) {
}
