package io.mindspice.magenta2.ai.chat.model;

import java.util.List;

public sealed interface ChatResponse {
    record MsgResponse(
        String conversationId,
        String model,
        String response,
        ContextUsage contextUsage,
        ChatPlanState planState
    ) implements ChatResponse { }

    record CmdResponse(
            String conversationId,
            String model,
            String message,
            List<String> conversationIds,
            List<ChatMessage> history,
            ContextUsage contextUsage,
            ChatPlanState planState
    ) implements ChatResponse { }
}
