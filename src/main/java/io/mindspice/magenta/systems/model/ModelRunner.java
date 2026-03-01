package io.mindspice.magenta.systems.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.mindspice.magenta.systems.config.RuntimeConfig.ModelConfig;
import io.mindspice.magenta.systems.config.RuntimeConfig;
import io.mindspice.magenta.systems.session.Session;
import io.mindspice.magenta.systems.session.SessionMessage;
import io.mindspice.magenta.systems.session.ToolRequest;
import io.mindspice.magenta.systems.session.ToolResult;

import java.util.ArrayList;
import java.util.List;

public final class ModelRunner {

    private final OllamaClient ollamaClient;

    public ModelRunner(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    public String runTurn(Session session, int maxIterations) {
        return runTurn(session, maxIterations, () -> {});
    }

    public String runTurn(Session session, int maxIterations, Runnable beforeModelCallHook) {
        boolean toolLoopActive = false;
        String latestText = "";
        Runnable safeBeforeModelCallHook = beforeModelCallHook == null ? () -> {} : beforeModelCallHook;

        for (int i = 0; i < maxIterations; i++) {
            safeBeforeModelCallHook.run();
            ChatRequest request = ChatRequest.builder().messages(toChatMessages(session.context().snapshot())).build();
            RuntimeConfig.ModelConfig modelConfig = session.modelConfig();

            boolean useBlocking = session.sessionConfig().blockingOnly() || toolLoopActive || !modelConfig.supportsStreaming();
            boolean streamingUsed = !useBlocking;
            ChatResponse response = useBlocking
                    ? ollamaClient.chatBlocking(modelConfig, request)
                    : ollamaClient.chatStreaming(
                            modelConfig,
                            request,
                            token -> {
                                session.sessionConfig().onTokenStreamHook().accept(token);
                                session.sessionConfig().emitStreamingResponse(token);
                            }
                    );

            AiMessage aiMessage = response.aiMessage();
            latestText = safeText(aiMessage.text());
            List<SessionMessage.ToolCall> toolCalls = toToolCalls(aiMessage.toolExecutionRequests());

            SessionMessage.AssistantMsg assistant = new SessionMessage.AssistantMsg(latestText, toolCalls);
            session.context().append(assistant);
            session.sessionConfig().emitMessageAppended(assistant);
            session.sessionConfig().emitFullResponse(latestText, streamingUsed);

            if (toolCalls.isEmpty() || !session.sessionConfig().toolsEnabled()) {
                return latestText;
            }

            for (SessionMessage.ToolCall toolCall : toolCalls) {
                ToolRequest toolRequest = new ToolRequest(session.sessionId().toString(), session.agentId(), toolCall);
                ToolResult toolResult = session.sessionConfig().toolBridge().apply(toolRequest);
                SessionMessage.ToolMsg toolMessage = new SessionMessage.ToolMsg(
                        toolResult.toolCallId(),
                        toolResult.toolName(),
                        safeText(toolResult.content())
                );
                session.context().append(toolMessage);
                session.sessionConfig().emitMessageAppended(toolMessage);
            }

            toolLoopActive = true;
        }

        return latestText;
    }

    public String summarize(RuntimeConfig.ModelConfig modelConfig, String systemPrompt, List<SessionMessage> messages) {
        StringBuilder input = new StringBuilder();
        input.append("Summarize the following conversation context. Return summary text only.\n\n");
        for (SessionMessage message : messages) {
            String role = switch (message) {
                case SessionMessage.SystemMsg ignored -> "system";
                case SessionMessage.UserMsg ignored -> "user";
                case SessionMessage.AssistantMsg ignored -> "assistant";
                case SessionMessage.ToolMsg ignored -> "tool";
                case SessionMessage.SummaryMsg ignored -> "summary";
                case SessionMessage.InboundMsg ignored -> "inbound";
            };
            input.append(role).append(": ").append(message.content()).append("\n");
        }

        List<SessionMessage> summaryMessages = List.of(
                new SessionMessage.SystemMsg(systemPrompt),
                new SessionMessage.UserMsg(input.toString())
        );

        ChatRequest request = ChatRequest.builder().messages(toChatMessages(summaryMessages)).build();
        ChatResponse response = ollamaClient.chatBlocking(modelConfig, request);
        return safeText(response.aiMessage().text());
    }

    private List<ChatMessage> toChatMessages(List<SessionMessage> context) {
        List<ChatMessage> output = new ArrayList<>();
        for (SessionMessage message : context) {
            switch (message) {
                case SessionMessage.SystemMsg systemMsg -> output.add(SystemMessage.from(systemMsg.content()));
                case SessionMessage.UserMsg userMsg -> output.add(UserMessage.from(userMsg.content()));
                case SessionMessage.InboundMsg inboundMsg -> output.add(UserMessage.from(inboundMsg.content()));
                case SessionMessage.AssistantMsg assistantMsg -> {
                    List<ToolExecutionRequest> requests = assistantMsg.toolCalls().stream()
                            .map(tc -> ToolExecutionRequest.builder().id(tc.id()).name(tc.name()).arguments(tc.argumentsJson()).build())
                            .toList();
                    if (requests.isEmpty()) {
                        output.add(AiMessage.from(safeText(assistantMsg.content())));
                    } else {
                        output.add(AiMessage.from(safeText(assistantMsg.content()), requests));
                    }
                }
                case SessionMessage.ToolMsg toolMsg -> output.add(ToolExecutionResultMessage.from(
                        toolMsg.toolCallId(),
                        toolMsg.toolName(),
                        safeText(toolMsg.content())
                ));
                case SessionMessage.SummaryMsg summaryMsg -> output.add(SystemMessage.from("Context Summary: " + summaryMsg.content()));
            }
        }
        return output;
    }

    private List<SessionMessage.ToolCall> toToolCalls(List<ToolExecutionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream().map(r -> new SessionMessage.ToolCall(r.id(), r.name(), r.arguments())).toList();
    }

    private String safeText(String text) {
        if (text == null || text.isBlank()) {
            return ".";
        }
        return text;
    }
}
