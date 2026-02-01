package com.magenta.session;

import com.magenta.context.manager.ContextManager;
import com.magenta.context.model.ContextElement;
import com.magenta.context.policy.ContextLimits;
import dev.langchain4j.data.message.*;

import java.util.List;

/**
 * Streaming chat message handler.
 * Processes user messages through the agent's model with streaming responses.
 */
public class StreamingChat implements MessageHandler<AgentSession> {

    @Override
    public void processMessage(AgentSession session, String message) {
        if (message.isBlank()) { return; }
        
        Agent agent = session.agent();
        SessionId sessionId = session.sessionId();
        ContextManager cm = ContextManager.getInstance();

        ContextLimits limits = new ContextLimits(
            agent.config().model().maxContext(),
            agent.config().model().compactThreshold()
        );

        // Ensure system prompt is set if context is empty
        // This check could also be moved to AgentSession init, but doing it here ensures it's checked on first message
        if (cm.loadContext(sessionId).getElements().isEmpty() && agent.config().systemPrompt() != null) {
            cm.append(sessionId, new ContextElement.System(agent.config().systemPrompt()), limits);
        }

        // Add user message to conversation
        cm.append(sessionId, new ContextElement.User(message), limits);

        // Get history for generation
        List<ChatMessage> history = cm.loadContext(sessionId).compile();

        // Generate streaming response
        agent.model().generate(history, session.responseHandler())
                .thenAccept(response -> cm.append(sessionId, new ContextElement.Assistant(response), limits))
                .join();
    }
}
