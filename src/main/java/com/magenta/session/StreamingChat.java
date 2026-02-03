package com.magenta.session;

import com.magenta.context.ContextManager;
import com.magenta.context.Context;
import com.magenta.context.ContextElement;
import com.magenta.context.ContextLimits;
import com.magenta.task.TaskWorkflow;
import dev.langchain4j.data.message.*;

import java.util.List;

/**
 * Streaming chat message handler.
 * Processes user messages through the agent's model with streaming responses.
 * Manages context compaction to stay within token limits.
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

        Context context = cm.loadContext(sessionId);

        // Ensure system prompt is set if context is empty
        if (context.getElements().isEmpty() && agent.config().systemPrompt() != null) {
            String systemPrompt = agent.config().systemPrompt();

            // Compose with task prompt if workflow task is active
            TaskWorkflow task = session.currentWorkflowTask();
            if (task != null) {
                systemPrompt = systemPrompt + "\n\n## Current Task\n" + task.getResolvedTaskPrompt();
            }

            cm.append(sessionId, new ContextElement.System(systemPrompt), limits);
            context = cm.loadContext(sessionId); // Reload after append
        }

        // Add user message to conversation
        cm.append(sessionId, new ContextElement.User(message), limits);

        // Reload context after appending (may have been compacted)
        context = cm.loadContext(sessionId);

        // CRITICAL: Check if compaction needed BEFORE calling model
        // This ensures we don't send too many tokens to the model
        if (cm.shouldCompact(context, limits)) {
            cm.forceCompact(context, limits);
        }

        // Get history for generation (after potential compaction)
        List<ChatMessage> history = context.compile();

        // Generate streaming response
        agent.model().generate(history, session.responseHandler())
                .thenAccept(response -> cm.append(sessionId, new ContextElement.Agent(response), limits))
                .join();
    }
}

