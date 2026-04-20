package io.mindspice.magenta2.chat;

import java.util.List;

public record ChatHistoryResponse(
    String conversationId,
    String model,
    List<ChatHistoryMessage> messages
) {
}
