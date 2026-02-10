package com.magenta.session;

import com.magenta.context.Context;
import com.magenta.context.ContextElement;
import com.magenta.context.ContextLimits;
import com.magenta.manager.ContextManager;
import com.magenta.io.ResponseHandler;
import com.magenta.security.SecurityFilter;
import com.magenta.task.TaskWorkflow;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Streaming chat message handler.
 * Processes user messages through the agent's model with streaming responses.
 * Manages context compaction and tool execution loops.
 *
 * Uses blocking model for tool execution rounds (OllamaStreamingChatModel doesn't
 * support tools), and streaming model for plain text responses.
 */
public class StreamingChat implements MessageHandler<AgentSession> {

    private static final Logger logger = LoggerFactory.getLogger(StreamingChat.class);
    private static final int MAX_TOOL_ITERATIONS = 10;

    @Override
    public void processMessage(AgentSession session, String message) {
        if (message.isBlank()) { return; }

        Agent agent = session.agent();
        SessionId sessionId = session.sessionId();
        ContextManager cm = session.magenta().contextManager();
        ContextLimits limits = session.contextLimits();

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
            context = cm.loadContext(sessionId);
        }

        // Add user message to conversation
        cm.append(sessionId, new ContextElement.User(message), limits);

        // Reload context after appending (may have been compacted)
        context = cm.loadContext(sessionId);

        // CRITICAL: Check if compaction needed BEFORE calling model
        if (cm.shouldCompact(context, limits)) {
            cm.forceCompact(context, limits);
        }

        // Tool execution loop
        StreamingChatLanguageModel streamingModel = agent.model();
        ChatLanguageModel blockingModel = agent.blockingModel();
        List<ToolSpecification> toolSpecs = agent.toolSpecs();
        Map<String, ToolExecutor> toolExecutors = agent.toolExecutors();
        SecurityFilter securityFilter = session.securityFilter();
        ResponseHandler handler = session.responseHandler();
        boolean hasTools = toolSpecs != null && !toolSpecs.isEmpty();

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            List<ChatMessage> history = cm.loadContext(sessionId).compile();

            try {
                if (hasTools) {
                    // Use blocking model for tool-capable generation
                    Response<AiMessage> response = blockingModel.generate(history, toolSpecs);
                    AiMessage aiMessage = response.content();

                    if (aiMessage.hasToolExecutionRequests()) {
                        // Add the AI's tool request message to context
                        cm.append(sessionId, new ContextElement.Agent(aiMessage.text() != null ? aiMessage.text() : ""), limits);

                        // Execute each tool
                        for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                            String result = executeTool(toolRequest, toolExecutors, securityFilter, session);
                            cm.append(sessionId, new ContextElement.Tool(toolRequest.name(), result), limits);
                            logger.debug("Tool '{}' executed, result length: {}", toolRequest.name(), result.length());
                        }

                        // Loop to let model process tool results
                        continue;
                    }

                    // Text response from blocking model — write to handler for display
                    String text = aiMessage.text() != null ? aiMessage.text() : "";
                    handler.write(text);
                    handler.complete();
                    cm.append(sessionId, new ContextElement.Agent(text), limits);
                    return;
                }

                // No tools — use streaming model
                Response<AiMessage> response = generateStreaming(streamingModel, history, handler);
                AiMessage aiMessage = response.content();
                String text = aiMessage.text() != null ? aiMessage.text() : handler.getBuffer();
                cm.append(sessionId, new ContextElement.Agent(text), limits);
                return;
            } catch (Exception e) {
                Throwable cause = unwrapCause(e);
                if (isConnectionError(cause)) {
                    logger.warn("Connection error during model call: {}", cause.getMessage());
                    handler.error(cause);
                    session.io().error("Connection lost: " + cause.getMessage());
                    return;
                }
                // Non-connection errors — rethrow
                throw e;
            }
        }

        // Max iterations reached
        logger.warn("Tool execution loop hit max iterations ({})", MAX_TOOL_ITERATIONS);
        session.io().error("Tool execution limit reached. The agent may be stuck in a loop.");
    }

    /**
     * Generate a streaming response (no tools), blocking until complete.
     */
    private Response<AiMessage> generateStreaming(
            StreamingChatLanguageModel model,
            List<ChatMessage> messages,
            ResponseHandler handler
    ) {
        CompletableFuture<Response<AiMessage>> future = new CompletableFuture<>();

        StreamingResponseHandler<AiMessage> streamHandler = new StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) {
                handler.write(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                handler.complete();
                future.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                handler.error(error);
                future.completeExceptionally(error);
            }
        };

        model.generate(messages, streamHandler);
        return future.join();
    }

    /**
     * Unwrap nested exception causes to find the root.
     */
    private Throwable unwrapCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * Check if the root cause is a connection/network error.
     */
    private boolean isConnectionError(Throwable cause) {
        return cause instanceof java.net.ConnectException
            || cause instanceof java.net.SocketException
            || cause instanceof java.net.SocketTimeoutException
            || cause instanceof java.io.EOFException
            || (cause instanceof java.io.IOException
                && cause.getMessage() != null
                && cause.getMessage().contains("unexpected end of stream"));
    }

    /**
     * Execute a single tool request with security filtering.
     */
    private String executeTool(
            ToolExecutionRequest request,
            Map<String, ToolExecutor> executors,
            SecurityFilter securityFilter,
            AgentSession session
    ) {
        // Check security filter
        Optional<String> blocked = securityFilter.toolFilter().apply(request, session.io());
        if (blocked.isPresent()) {
            logger.info("Tool '{}' blocked by security: {}", request.name(), blocked.get());
            return "Error: Tool blocked by security policy - " + blocked.get();
        }

        // Find and execute
        ToolExecutor executor = executors.get(request.name());
        if (executor == null) {
            logger.warn("Unknown tool requested: {}", request.name());
            return "Error: Unknown tool '" + request.name() + "'";
        }

        try {
            return executor.execute(request, null);
        } catch (Exception e) {
            logger.error("Tool '{}' execution failed: {}", request.name(), e.getMessage());
            return "Error executing tool: " + e.getMessage();
        }
    }
}
