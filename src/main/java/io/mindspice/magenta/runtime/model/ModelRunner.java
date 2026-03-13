package io.mindspice.magenta.runtime.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ModelRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final int MAX_CONTEXT_TOOL_PAYLOAD_CHARS = 2_000;

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
                List.of(),
                RuntimeConfig.ToolLoopGuardConfig.defaults()
        );
    }

    public String runTurn(
            Session session,
            SessionHandle handle,
            int maxIterations,
            boolean streamTokens,
            Consumer<OutputRoutingEvent> outputEmitter,
            Runnable beforeModelCallHook,
            List<ToolSpecification> toolSpecifications,
            RuntimeConfig.ToolLoopGuardConfig toolLoopGuardConfig
    ) {
        boolean toolLoopActive = false;
        String latestText = "";
        Runnable safeBeforeModelCallHook = beforeModelCallHook == null ? () -> {} : beforeModelCallHook;
        Consumer<OutputRoutingEvent> safeOutputEmitter = outputEmitter == null ? ignored -> {} : outputEmitter;
        List<ToolSpecification> safeToolSpecifications = toolSpecifications == null ? List.of() : List.copyOf(toolSpecifications);
        RuntimeConfig.ToolLoopGuardConfig safeToolLoopGuardConfig = toolLoopGuardConfig == null
                ? RuntimeConfig.ToolLoopGuardConfig.defaults()
                : toolLoopGuardConfig;
        Deque<String> recentSignatures = new ArrayDeque<>();
        Map<String, Integer> signatureCounts = new HashMap<>();
        Deque<Boolean> recentFailureFlags = new ArrayDeque<>();
        int recoveryAttemptsUsed = 0;
        String pendingLoopWarningSystemMessage = null;

        for (int i = 0; i < maxIterations; i++) {
            safeBeforeModelCallHook.run();
            List<ContextElement> requestContext = session.context().snapshot();
            if (pendingLoopWarningSystemMessage != null && !pendingLoopWarningSystemMessage.isBlank()) {
                requestContext = prependSystemMessage(requestContext, pendingLoopWarningSystemMessage);
                pendingLoopWarningSystemMessage = null;
            }
            ChatRequest.Builder requestBuilder = ChatRequest.builder()
                    .messages(toChatMessages(requestContext));
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

            boolean loopWarningIssuedThisIteration = false;
            boolean executedAnyToolThisIteration = false;
            for (ContextElement.ToolCall toolCall : toolCalls) {
                if (safeToolLoopGuardConfig.enabled()) {
                    String signature = toolCallSignature(toolCall);
                    String oldestIfWindowFull = recentSignatures.size() >= safeToolLoopGuardConfig.windowSize()
                            ? recentSignatures.peekFirst()
                            : null;
                    int nextCount = signatureCounts.getOrDefault(signature, 0) + 1;
                    int effectiveCountAfterWindowShift = signature.equals(oldestIfWindowFull)
                            ? nextCount - 1
                            : nextCount;
                    if (effectiveCountAfterWindowShift >= safeToolLoopGuardConfig.repeatThreshold()) {
                        int windowFailures = countWindowFailures(recentFailureFlags);
                        int recoveryAttempts = safeToolLoopGuardConfig.recoveryAttempts();
                        if (recoveryAttempts > 0 && recoveryAttemptsUsed < recoveryAttempts) {
                            recoveryAttemptsUsed++;
                            String warningMessage = loopWarningMessage(
                                    effectiveCountAfterWindowShift,
                                    safeToolLoopGuardConfig.windowSize(),
                                    windowFailures,
                                    recoveryAttemptsUsed,
                                    recoveryAttempts
                            );
                            safeOutputEmitter.accept(
                                    new OutputRoutingEvent(handle, new SessionOutput.FinalOutput(warningMessage))
                            );
                            pendingLoopWarningSystemMessage = warningMessage;
                            recentSignatures.clear();
                            signatureCounts.clear();
                            recentFailureFlags.clear();
                            loopWarningIssuedThisIteration = true;
                            break;
                        }
                        String stopText = loopDetectedText(
                                latestText,
                                safeToolLoopGuardConfig.repeatThreshold(),
                                safeToolLoopGuardConfig.windowSize(),
                                windowFailures,
                                recoveryAttemptsUsed,
                                recoveryAttempts
                        );
                        if (!stopText.equals(latestText)) {
                            session.context().append(new ContextElement.AssistantMsg(stopText, List.of()));
                            safeOutputEmitter.accept(new OutputRoutingEvent(handle, new SessionOutput.FinalOutput(stopText)));
                        }
                        return stopText;
                    }
                    recentSignatures.addLast(signature);
                    signatureCounts.put(signature, nextCount);
                    while (recentSignatures.size() > safeToolLoopGuardConfig.windowSize()) {
                        String oldest = recentSignatures.removeFirst();
                        int count = signatureCounts.getOrDefault(oldest, 0) - 1;
                        if (count <= 0) {
                            signatureCounts.remove(oldest);
                        } else {
                            signatureCounts.put(oldest, count);
                        }
                    }
                }

                safeOutputEmitter.accept(new OutputRoutingEvent(handle, new SessionOutput.ToolCallOutput(toolCall)));
                ToolRequest toolRequest = new ToolRequest(session.sessionId().toString(), session.agentId(), toolCall);
                ToolResult toolResult = session.sessionConfig().toolBridge().apply(toolRequest);
                executedAnyToolThisIteration = true;
                String rawContent = safeText(toolResult.content());
                String contextContent = truncateToolContentForContext(rawContent);
                ContextElement.ToolMsg toolMessage = new ContextElement.ToolMsg(
                        toolResult.toolCallId(),
                        toolResult.toolName(),
                        contextContent
                );
                session.context().append(toolMessage);
                safeOutputEmitter.accept(new OutputRoutingEvent(
                        handle,
                        new SessionOutput.ToolMessageOutput(new ContextElement.ToolMsg(
                                toolResult.toolCallId(),
                                toolResult.toolName(),
                                rawContent
                        ))
                ));
                if (safeToolLoopGuardConfig.enabled()) {
                    recentFailureFlags.addLast(toolResultFailed(rawContent));
                    while (recentFailureFlags.size() > safeToolLoopGuardConfig.windowSize()) {
                        recentFailureFlags.removeFirst();
                    }
                }
            }

            if (loopWarningIssuedThisIteration) {
                toolLoopActive = true;
                continue;
            }
            if (executedAnyToolThisIteration) {
                recoveryAttemptsUsed = 0;
            }
            toolLoopActive = true;
        }

        String cappedText = maxTurnsExhaustedText(latestText, maxIterations);
        if (!cappedText.equals(latestText)) {
            session.context().append(new ContextElement.AssistantMsg(cappedText, List.of()));
            safeOutputEmitter.accept(new OutputRoutingEvent(handle, new SessionOutput.FinalOutput(cappedText)));
        }
        return cappedText;
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

    private List<ContextElement> prependSystemMessage(List<ContextElement> snapshot, String message) {
        List<ContextElement> withSystemPrefix = new ArrayList<>(snapshot.size() + 1);
        withSystemPrefix.add(new ContextElement.SystemMsg(message));
        withSystemPrefix.addAll(snapshot);
        return withSystemPrefix;
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

    private String truncateToolContentForContext(String content) {
        String safe = safeText(content);
        if (safe.length() <= MAX_CONTEXT_TOOL_PAYLOAD_CHARS) {
            return safe;
        }
        int markerReserve = 72;
        int headLength = Math.max(0, MAX_CONTEXT_TOOL_PAYLOAD_CHARS - markerReserve);
        return safe.substring(0, headLength)
               + "...[truncated_for_context chars="
               + safe.length()
               + "]";
    }

    private String toolCallSignature(ContextElement.ToolCall toolCall) {
        String toolName = toolCall.name() == null ? "" : toolCall.name().trim();
        String canonicalArgs = canonicalizeArguments(toolCall.argumentsJson());
        return toolName + "|" + canonicalArgs;
    }

    private String canonicalizeArguments(String argumentsJson) {
        String safe = argumentsJson == null ? "" : argumentsJson.trim();
        if (safe.isEmpty()) {
            return "";
        }
        try {
            JsonNode parsed = MAPPER.readTree(safe);
            return canonicalizeNode(parsed);
        } catch (Exception ignored) {
            return safe.replaceAll("\\s+", " ");
        }
    }

    private String canonicalizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                if (i > 0) {
                    sb.append(",");
                }
                sb.append("\"").append(name).append("\":").append(canonicalizeNode(node.get(name)));
            }
            sb.append("}");
            return sb.toString();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(canonicalizeNode(node.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return node.toString();
    }

    private int countWindowFailures(Deque<Boolean> recentFailureFlags) {
        int failures = 0;
        for (Boolean failed : recentFailureFlags) {
            if (Boolean.TRUE.equals(failed)) {
                failures++;
            }
        }
        return failures;
    }

    private boolean toolResultFailed(String rawContent) {
        String payload = safeText(rawContent);
        if (payload.isBlank()) {
            return true;
        }
        try {
            JsonNode root = MAPPER.readTree(payload);
            return !"ok".equalsIgnoreCase(root.path("status").asText("failed"));
        } catch (Exception ignored) {
            return true;
        }
    }

    private String loopWarningMessage(
            int repeatedCalls,
            int windowSize,
            int windowFailures,
            int recoveryAttempt,
            int recoveryAttemptsMax
    ) {
        return "[tool-loop-warning] repeated_calls=" + repeatedCalls
               + "/" + windowSize
               + "; window_failures=" + windowFailures
               + "; recovery_attempt=" + recoveryAttempt
               + "/" + recoveryAttemptsMax
               + "; required_action=change_approach_or_return_defeat";
    }

    private String loopDetectedText(
            String latestText,
            int repeatThreshold,
            int windowSize,
            int windowFailures,
            int recoveryAttemptsUsed,
            int recoveryAttemptsMax
    ) {
        String reason = "[tool-loop-stop] repeated tool-call pattern detected (threshold="
                        + repeatThreshold + ", window=" + windowSize + ")";
        String detail = reason
                        + " failures_in_window=" + windowFailures
                        + " recovery_attempts=" + recoveryAttemptsUsed + "/" + recoveryAttemptsMax;
        if (recoveryAttemptsMax > 0) {
            detail = detail + " model did not change approach after warning(s)";
        }
        return latestText == null || latestText.isBlank() ? detail : latestText + "\n\n" + detail;
    }

    private String maxTurnsExhaustedText(String latestText, int maxIterations) {
        String reason = "[tool-loop-stop] maxTurns exhausted (" + maxIterations + ")";
        return latestText == null || latestText.isBlank() ? reason : latestText + "\n\n" + reason;
    }
}
