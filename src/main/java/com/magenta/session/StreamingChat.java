package com.magenta.session;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Streaming chat message handler.
 * Processes user messages through the agent's model with streaming responses.
 */
public class StreamingChat implements MessageHandler<AgentSession> {

    @Override
    public void processMessage(AgentSession session, String message) {
        if (message.isBlank()) { return; }
        // Get agent from session
        Agent agent = session.agent();

        // Add user message to conversation
        agent.addMessage(UserMessage.from(message));

        // Generate streaming response
        agent.model().generate(agent.conversationHistory(), session.responseHandler())
                .thenAccept(response -> agent.addMessage(AiMessage.from(response)))
                .join();
    }
}
