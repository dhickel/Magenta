package io.mindspice.magenta.runtime.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.routing.OutputRoutingEvent;
import io.mindspice.magenta.runtime.session.Session;
import io.mindspice.magenta.runtime.session.SessionMessage;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class ModelRunner {

    private final OllamaClient ollamaClient;

    public ModelRunner(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    public String runTurn(Session session, int maxIterations) {
        return runTurn(session, maxIterations, false, event -> {}, () -> {});
    }

    public String runTurn(
            Session session,
            int maxIterations,
            boolean streamTokens,
            Consumer<OutputRoutingEvent> outputEmitter,
            Runnable beforeModelCallHook
    ) {
        boolean toolLoopActive = false;
        String latestText = "";
        Runnable safeBeforeModelCallHook = beforeModelCallHook == null ? () -> {} : beforeModelCallHook;
        Consumer<OutputRoutingEvent> safeOutputEmitter = outputEmitter == null ? ignored -> {} : outputEmitter;

        for (int i = 0; i < maxIterations; i++) {
            safeBeforeModelCallHook.run();
            ChatRequest request = ChatRequest.builder().messages(toChatMessages(session.context().snapshot())).build();
            RuntimeConfig.ModelConfig modelConfig = session.modelConfig();

            boolean useBlocking = session.sessionConfig().params().blockingOnly()
                    || toolLoopActive
                    || !streamTokens
                    || !modelConfig.supportsStreaming();
            ChatResponse response = useBlocking
                    ? ollamaClient.chatBlocking(modelConfig, request)
                    : ollamaClient.chatStreaming(
                            modelConfig,
                            request,
                            token -> safeOutputEmitter.accept(
                                    new OutputRoutingEvent.PartialToken(token, "model", Set.of("assistant"))
                            )
                    );

            AiMessage aiMessage = response.aiMessage();
            latestText = safeText(aiMessage.text());
            List<SessionMessage.ToolCall> toolCalls = toToolCalls(aiMessage.toolExecutionRequests());

            SessionMessage.AssistantMsg assistant = new SessionMessage.AssistantMsg(latestText, toolCalls);
            session.context().append(assistant);
            safeOutputEmitter.accept(new OutputRoutingEvent.MessageAppended(assistant, "session-context", Set.of("assistant")));
            safeOutputEmitter.accept(new OutputRoutingEvent.AssistantFinal(latestText, "model", Set.of("assistant")));

            if (toolCalls.isEmpty() || !session.sessionConfig().params().toolsEnabled()) {
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
                safeOutputEmitter.accept(new OutputRoutingEvent.MessageAppended(toolMessage, "session-context", Set.of("tool")));
                safeOutputEmitter.accept(new OutputRoutingEvent.ToolMessageAppended(toolMessage, "tool-bridge", Set.of("tool")));
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
