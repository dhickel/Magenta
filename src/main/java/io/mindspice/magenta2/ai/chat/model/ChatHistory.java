package io.mindspice.magenta2.ai.chat.model;

import java.util.List;

public record ChatHistory(
    String conversationId,
    String model,
    List<ChatMessage> messages,
    ContextUsage contextUsage,
    ChatPlanState planState
) {
}
