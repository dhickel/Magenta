package io.mindspice.magenta.runtime.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
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
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ModelRunner {

    private final OllamaClient ollamaClient;

    public ModelRunner(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    public String runTurn(Session session, int maxIterations) {
        return runTurn(
                session,
                new SessionHandle(session.sessionId(), () -> true),
                maxIterations,
                false,
                event -> {},
                () -> {},
                List.of()
        );
    }

    public String runTurn(
            Session session,
            SessionHandle handle,
            int maxIterations,
            boolean streamTokens,
            Consumer<OutputRoutingEvent> outputEmitter,
            Runnable beforeModelCallHook,
            List<ToolSpecification> toolSpecifications
    ) {
        boolean toolLoopActive = false;
        String latestText = "";
        Runnable safeBeforeModelCallHook = beforeModelCallHook == null ? () -> {} : beforeModelCallHook;
        Consumer<OutputRoutingEvent> safeOutputEmitter = outputEmitter == null ? ignored -> {} : outputEmitter;
        List<ToolSpecification> safeToolSpecifications = toolSpecifications == null ? List.of() : List.copyOf(toolSpecifications);

        for (int i = 0; i < maxIterations; i++) {
            safeBeforeModelCallHook.run();
            ChatRequest.Builder requestBuilder = ChatRequest.builder()
                    .messages(toChatMessages(session.context().snapshot()));
            RuntimeConfig.ModelConfig modelConfig = session.modelConfig();
            if (session.sessionConfig().params().toolsEnabled()
                && modelConfig.supportsToolCalling()
                && !safeToolSpecifications.isEmpty()) {
                requestBuilder.toolSpecifications(safeToolSpecifications);
            }
            ChatRequest request = requestBuilder.build();

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
                                    new OutputRoutingEvent(handle, new SessionOutput.StreamedOutput(token))
                            )
                    );

            AiMessage aiMessage = response.aiMessage();
            latestText = safeText(aiMessage.text());
            List<ContextElement.ToolCall> toolCalls = toToolCalls(aiMessage.toolExecutionRequests());

            ContextElement.AssistantMsg assistant = new ContextElement.AssistantMsg(latestText, toolCalls);
            session.context().append(assistant);
            safeOutputEmitter.accept(new OutputRoutingEvent(handle, new SessionOutput.FinalOutput(latestText)));

            if (toolCalls.isEmpty() || !session.sessionConfig().params().toolsEnabled()) {
                return latestText;
            }

            for (ContextElement.ToolCall toolCall : toolCalls) {
                safeOutputEmitter.accept(new OutputRoutingEvent(handle, new SessionOutput.ToolCallOutput(toolCall)));
                ToolRequest toolRequest = new ToolRequest(session.sessionId().toString(), session.agentId(), toolCall);
                ToolResult toolResult = session.sessionConfig().toolBridge().apply(toolRequest);
                ContextElement.ToolMsg toolMessage = new ContextElement.ToolMsg(
                        toolResult.toolCallId(),
                        toolResult.toolName(),
                        safeText(toolResult.content())
                );
                session.context().append(toolMessage);
                safeOutputEmitter.accept(new OutputRoutingEvent(handle, new SessionOutput.ToolMessageOutput(toolMessage)));
            }

            toolLoopActive = true;
        }

        return latestText;
    }

    public String summarize(RuntimeConfig.ModelConfig modelConfig, String systemPrompt, List<ContextElement> messages) {
        StringBuilder input = new StringBuilder();
        input.append("Summarize the following conversation context. Return summary text only.\n\n");
        for (ContextElement message : messages) {
            String role = switch (message) {
                case ContextElement.SystemMsg ignored -> "system";
                case ContextElement.UserMsg ignored -> "user";
                case ContextElement.AssistantMsg ignored -> "assistant";
                case ContextElement.ToolMsg ignored -> "tool";
                case ContextElement.SummaryMsg ignored -> "summary";
                case ContextElement.InboundMsg ignored -> "inbound";
            };
            input.append(role).append(": ").append(message.content()).append("\n");
        }

        List<ContextElement> summaryMessages = List.of(
                new ContextElement.SystemMsg(systemPrompt),
                new ContextElement.UserMsg(input.toString())
        );

        ChatRequest request = ChatRequest.builder().messages(toChatMessages(summaryMessages)).build();
        ChatResponse response = ollamaClient.chatBlocking(modelConfig, request);
        return safeText(response.aiMessage().text());
    }

    private List<ChatMessage> toChatMessages(List<ContextElement> context) {
        List<ChatMessage> output = new ArrayList<>();
        for (ContextElement message : context) {
            switch (message) {
                case ContextElement.SystemMsg systemMsg -> output.add(SystemMessage.from(systemMsg.content()));
                case ContextElement.UserMsg userMsg -> output.add(UserMessage.from(userMsg.content()));
                case ContextElement.InboundMsg inboundMsg -> output.add(UserMessage.from(inboundMsg.content()));
                case ContextElement.AssistantMsg assistantMsg -> {
                    List<ToolExecutionRequest> requests = assistantMsg.toolCalls().stream()
                            .map(tc -> ToolExecutionRequest.builder().id(tc.id()).name(tc.name()).arguments(tc.argumentsJson()).build())
                            .toList();
                    if (requests.isEmpty()) {
                        output.add(AiMessage.from(safeText(assistantMsg.content())));
                    } else {
                        output.add(AiMessage.from(safeText(assistantMsg.content()), requests));
                    }
                }
                case ContextElement.ToolMsg toolMsg -> output.add(ToolExecutionResultMessage.from(
                        toolMsg.toolCallId(),
                        toolMsg.toolName(),
                        safeText(toolMsg.content())
                ));
                case ContextElement.SummaryMsg summaryMsg -> output.add(
                        UserMessage.from("[Context Summary]\n" + safeText(summaryMsg.content()))
                );
            }
        }
        return output;
    }

    private List<ContextElement.ToolCall> toToolCalls(List<ToolExecutionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream().map(r -> new ContextElement.ToolCall(r.id(), r.name(), r.arguments())).toList();
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
