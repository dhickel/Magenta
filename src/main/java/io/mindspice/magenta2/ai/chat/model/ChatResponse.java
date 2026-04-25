package io.mindspice.magenta2.ai.chat.model;

import java.util.List;

public sealed interface ChatResponse {
    record MsgResponse(String conversationId, String model, String response, ContextUsage contextUsage) implements ChatResponse { }

    record CmdResponse(
            String conversationId,
            String model,
            String message,
            List<String> conversationIds,
            List<ChatMessage> history,
            ContextUsage contextUsage
    ) implements ChatResponse { }
}
