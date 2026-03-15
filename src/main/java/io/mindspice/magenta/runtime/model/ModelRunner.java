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
import io.mindspice.magenta.runtime.tools.ToolPayloads;
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
    private static final int EMPTY_TURN_RECOVERY_ATTEMPTS = 1;
    private static final String EMPTY_TURN_CONTINUITY_PREFIX = "[continuity-check]";
    private static final String EMPTY_TURN_STOP_PREFIX = "[model-empty-turn-stop]";
    private static final String SEARCH_REPLACE_WARNING_PREFIX = "[search-replace-warning]";
    private static final int SEARCH_REPLACE_MISMATCH_WARNING_THRESHOLD = 2;

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
        int emptyTurnRecoveryAttemptsUsed = 0;
        String pendingLoopWarningSystemMessage = null;
        String pendingEmptyTurnSystemMessage = null;
        String pendingSearchReplaceSystemMessage = null;
        LastToolOutcome lastToolOutcome = LastToolOutcome.none();
        SearchReplaceMismatchTracker searchReplaceMismatchTracker = new SearchReplaceMismatchTracker();

        for (int i = 0; i < maxIterations; i++) {
            safeBeforeModelCallHook.run();
            List<ContextElement> requestContext = session.context().snapshot();
            requestContext = normalizeStateSystemOrdering(requestContext);
            if (pendingLoopWarningSystemMessage != null && !pendingLoopWarningSystemMessage.isBlank()) {
                requestContext = prependSystemMessage(requestContext, pendingLoopWarningSystemMessage);
                pendingLoopWarningSystemMessage = null;
            }
            if (pendingEmptyTurnSystemMessage != null && !pendingEmptyTurnSystemMessage.isBlank()) {
                requestContext = prependSystemMessage(requestContext, pendingEmptyTurnSystemMessage);
                pendingEmptyTurnSystemMessage = null;
            }
            if (pendingSearchReplaceSystemMessage != null && !pendingSearchReplaceSystemMessage.isBlank()) {
                requestContext = prependSystemMessage(requestContext, pendingSearchReplaceSystemMessage);
                pendingSearchReplaceSystemMessage = null;
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

            if (latestText.isBlank() && toolCalls.isEmpty()) {
                if (emptyTurnRecoveryAttemptsUsed < EMPTY_TURN_RECOVERY_ATTEMPTS) {
                    emptyTurnRecoveryAttemptsUsed++;
                    pendingEmptyTurnSystemMessage = emptyTurnContinuityMessage(
                            emptyTurnRecoveryAttemptsUsed,
                            EMPTY_TURN_RECOVERY_ATTEMPTS,
                            lastToolOutcome
                    );
                    toolLoopActive = true;
                    continue;
                }
                String stopText = emptyTurnStopText(emptyTurnRecoveryAttemptsUsed, EMPTY_TURN_RECOVERY_ATTEMPTS, lastToolOutcome);
                session.context().append(new ContextElement.AssistantMsg(stopText, List.of()));
                safeOutputEmitter.accept(new OutputRoutingEvent(handle, new SessionOutput.FinalOutput(stopText)));
                return stopText;
            }
            emptyTurnRecoveryAttemptsUsed = 0;

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
                                    recoveryAttempts,
                                    lastToolOutcome
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
                String contextContent = ToolPayloads.buildToolPreview(
                        toolResult.toolName(),
                        rawContent,
                        MAX_CONTEXT_TOOL_PAYLOAD_CHARS
                );
                boolean contentTruncated = !contextContent.equals(rawContent);
                ContextElement.ToolMsg toolMessage = new ContextElement.ToolMsg(
                        toolResult.toolCallId(),
                        toolResult.toolName(),
                        contextContent,
                        rawContent,
                        contentTruncated
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
                lastToolOutcome = captureToolOutcome(toolResult.toolName(), rawContent);
                SearchReplaceSignal searchReplaceSignal = captureSearchReplaceSignal(toolCall, rawContent);
                if (searchReplaceSignal.clearPath() != null && !searchReplaceSignal.clearPath().isBlank()) {
                    searchReplaceMismatchTracker.clear(searchReplaceSignal.clearPath());
                }
                if (searchReplaceSignal.mismatchPath() != null && !searchReplaceSignal.mismatchPath().isBlank()) {
                    SearchReplaceMismatchStatus mismatchStatus = searchReplaceMismatchTracker.recordMismatch(
                            searchReplaceSignal.mismatchPath(),
                            searchReplaceSignal.reason()
                    );
                    if (mismatchStatus.emitWarning()) {
                        pendingSearchReplaceSystemMessage = searchReplaceWarningMessage(
                                mismatchStatus.path(),
                                mismatchStatus.consecutiveCount(),
                                mismatchStatus.reason()
                        );
                    }
                }
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
                case ContextElement.SystemElement ignored -> "system";
                case ContextElement.UserMsg ignored -> "user";
                case ContextElement.AssistantMsg ignored -> "assistant";
                case ContextElement.ToolMsg ignored -> "tool";
                case ContextElement.SummaryMsg ignored -> "summary";
                case ContextElement.InboundMsg ignored -> "inbound";
            };
            input.append(role).append(": ").append(message.content()).append("\n");
        }

        List<ContextElement> summaryMessages = List.of(
                new ContextElement.SystemCoreMsg(systemPrompt),
                new ContextElement.UserMsg(input.toString())
        );

        ChatRequest request = ChatRequest.builder().messages(toChatMessages(summaryMessages)).build();
        ChatResponse response = ollamaClient.chatBlocking(modelConfig, request);
        return safeText(response.aiMessage().text());
    }

    private List<ContextElement> prependSystemMessage(List<ContextElement> snapshot, String message) {
        List<ContextElement> withSystemPrefix = new ArrayList<>(snapshot.size() + 1);
        withSystemPrefix.add(new ContextElement.SystemAgentMsg(message));
        withSystemPrefix.addAll(snapshot);
        return withSystemPrefix;
    }

    private List<ContextElement> normalizeStateSystemOrdering(List<ContextElement> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return List.of();
        }
        ContextElement.SystemStateMsg newestState = null;
        List<ContextElement> withoutState = new ArrayList<>(snapshot.size());
        for (ContextElement message : snapshot) {
            if (message instanceof ContextElement.SystemStateMsg systemMsg) {
                newestState = systemMsg;
                continue;
            }
            withoutState.add(message);
        }
        if (newestState == null) {
            return List.copyOf(withoutState);
        }
        int insertionIndex = 0;
        for (int i = 0; i < withoutState.size(); i++) {
            if (ContextElement.isSystemElement(withoutState.get(i))) {
                insertionIndex = i + 1;
            }
        }
        withoutState.add(insertionIndex, newestState);
        return List.copyOf(withoutState);
    }

    private List<ChatMessage> toChatMessages(List<ContextElement> context) {
        List<ChatMessage> output = new ArrayList<>();
        for (ContextElement message : context) {
            switch (message) {
                case ContextElement.SystemElement systemMsg -> output.add(SystemMessage.from(systemMsg.content()));
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
        return ToolPayloads.buildToolPreview("", safeText(content), MAX_CONTEXT_TOOL_PAYLOAD_CHARS);
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
            int recoveryAttemptsMax,
            LastToolOutcome lastToolOutcome
    ) {
        return "[tool-loop-warning] repeated_calls=" + repeatedCalls
               + "/" + windowSize
               + "; window_failures=" + windowFailures
               + "; recovery_attempt=" + recoveryAttempt
               + "/" + recoveryAttemptsMax
               + "; required_action=change_approach_or_return_defeat"
               + "; last_tool=" + compactToken(lastToolOutcome.toolName(), "none")
               + "; last_tool_status=" + compactToken(lastToolOutcome.status(), "unknown")
               + "; last_failure_code=" + compactToken(lastToolOutcome.failureCode(), "none")
               + "; recovery_hint=inspect_last_tool_payload_change_method_do_not_reuse_error_json_as_args";
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

    private String emptyTurnContinuityMessage(int attempt, int maxAttempts, LastToolOutcome lastToolOutcome) {
        return EMPTY_TURN_CONTINUITY_PREFIX
               + " empty assistant response received (attempt "
               + attempt + "/" + maxAttempts + "). "
               + "Context: last_tool=" + compactToken(lastToolOutcome.toolName(), "none")
               + ", last_tool_status=" + compactToken(lastToolOutcome.status(), "unknown")
               + ", last_failure_code=" + compactToken(lastToolOutcome.failureCode(), "none") + ". "
               + "Before stopping, run a completion self-check: "
               + "if the task is fully complete, provide the final artifact/status update to the user; "
               + "if work remains, identify the next concrete step and continue execution. "
               + "If using todos, verify todo focus/list before declaring completion. "
               + "If the previous tool failed, inspect its payload and switch strategy; never pass prior tool-result JSON as new tool arguments.";
    }

    private String emptyTurnStopText(int attemptsUsed, int attemptsMax, LastToolOutcome lastToolOutcome) {
        return EMPTY_TURN_STOP_PREFIX
               + " no assistant content after continuity retry (attempts="
               + attemptsUsed + "/" + attemptsMax + "). "
               + "Context: last_tool=" + compactToken(lastToolOutcome.toolName(), "none")
               + ", last_tool_status=" + compactToken(lastToolOutcome.status(), "unknown")
               + ", last_failure_code=" + compactToken(lastToolOutcome.failureCode(), "none") + ". "
               + "Return either a final artifact/status update or continue with concrete next actions.";
    }

    private SearchReplaceSignal captureSearchReplaceSignal(ContextElement.ToolCall toolCall, String rawContent) {
        if (toolCall == null || toolCall.name() == null) {
            return SearchReplaceSignal.none();
        }
        String toolName = toolCall.name().trim();
        String path = extractPath(toolCall.argumentsJson());
        if ("read_file".equals(toolName)) {
            if (toolResultFailed(rawContent) || path.isBlank()) {
                return SearchReplaceSignal.none();
            }
            return SearchReplaceSignal.clear(path);
        }
        if (!"search_replace".equals(toolName) || path.isBlank()) {
            return SearchReplaceSignal.none();
        }
        if (!toolResultFailed(rawContent)) {
            return SearchReplaceSignal.clear(path);
        }

        SearchReplaceFailure failure = parseSearchReplaceFailure(rawContent);
        if (!failure.mismatch()) {
            return SearchReplaceSignal.none();
        }
        return SearchReplaceSignal.mismatch(path, failure.reason());
    }

    private SearchReplaceFailure parseSearchReplaceFailure(String rawContent) {
        String payload = safeText(rawContent);
        if (payload.isBlank()) {
            return SearchReplaceFailure.none();
        }
        try {
            JsonNode root = MAPPER.readTree(payload);
            if (!"anchor_mismatch".equals(root.path("code").asText())) {
                return SearchReplaceFailure.none();
            }
            JsonNode conflicts = root.path("data").path("conflicts");
            String reason = "";
            if (conflicts.isArray() && !conflicts.isEmpty()) {
                reason = conflicts.get(0).path("reason").asText("");
            }
            return new SearchReplaceFailure(true, reason);
        } catch (Exception ignored) {
            return SearchReplaceFailure.none();
        }
    }

    private String extractPath(String argumentsJson) {
        String safe = argumentsJson == null ? "" : argumentsJson.trim();
        if (safe.isBlank()) {
            return "";
        }
        try {
            JsonNode args = MAPPER.readTree(safe);
            if (!args.isObject()) {
                return "";
            }
            return firstNonBlank(
                    args.path("path").asText(""),
                    args.path("filePath").asText(""),
                    args.path("targetPath").asText("")
            );
        } catch (Exception ignored) {
            return "";
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String searchReplaceWarningMessage(String path, int consecutiveCount, String reason) {
        return SEARCH_REPLACE_WARNING_PREFIX
               + " repeated_anchor_mismatch="
               + consecutiveCount
               + "/"
               + SEARCH_REPLACE_MISMATCH_WARNING_THRESHOLD
               + "; path="
               + compactToken(path, "unknown")
               + "; reason="
               + compactToken(firstNonBlank(reason, "anchor_mismatch"), "anchor_mismatch")
               + "; required_action=read_file_refresh_before_retry"
               + "; guidance=refresh_snapshot_and_inclusive_anchors_before_next_search_replace";
    }

    private LastToolOutcome captureToolOutcome(String toolName, String rawContent) {
        String normalizedTool = safeText(toolName).isBlank() ? "unknown" : toolName.trim();
        String payload = safeText(rawContent);
        if (payload.isBlank()) {
            return new LastToolOutcome(normalizedTool, "failed", "empty_payload", "");
        }
        try {
            JsonNode root = MAPPER.readTree(payload);
            String status = safeText(root.path("status").asText("failed"));
            String code = safeText(root.path("code").asText("unknown"));
            String message = safeText(root.path("message").asText(""));
            return new LastToolOutcome(normalizedTool, status, code, message);
        } catch (Exception ignored) {
            return new LastToolOutcome(normalizedTool, "failed", "invalid_payload", "");
        }
    }

    private String compactToken(String text, String fallback) {
        String value = safeText(text).trim();
        if (value.isBlank()) {
            value = safeText(fallback).trim();
        }
        if (value.isBlank()) {
            return "none";
        }
        return value.replaceAll("[\\s;=]+", "_");
    }

    private record SearchReplaceFailure(boolean mismatch, String reason) {
        private static SearchReplaceFailure none() {
            return new SearchReplaceFailure(false, "");
        }
    }

    private record SearchReplaceSignal(String clearPath, String mismatchPath, String reason) {
        private static SearchReplaceSignal none() {
            return new SearchReplaceSignal(null, null, "");
        }

        private static SearchReplaceSignal clear(String path) {
            return new SearchReplaceSignal(path, null, "");
        }

        private static SearchReplaceSignal mismatch(String path, String reason) {
            return new SearchReplaceSignal(null, path, reason == null ? "" : reason);
        }
    }

    private static final class SearchReplaceMismatchTracker {
        private final Map<String, Integer> consecutiveByPath = new HashMap<>();
        private final Map<String, Boolean> warnedByPath = new HashMap<>();

        private SearchReplaceMismatchStatus recordMismatch(String path, String reason) {
            int next = consecutiveByPath.getOrDefault(path, 0) + 1;
            consecutiveByPath.put(path, next);
            boolean warned = warnedByPath.getOrDefault(path, false);
            boolean emitWarning = next >= SEARCH_REPLACE_MISMATCH_WARNING_THRESHOLD && !warned;
            if (emitWarning) {
                warnedByPath.put(path, true);
            }
            return new SearchReplaceMismatchStatus(path, next, reason == null ? "" : reason, emitWarning);
        }

        private void clear(String path) {
            if (path == null || path.isBlank()) {
                return;
            }
            consecutiveByPath.remove(path);
            warnedByPath.remove(path);
        }
    }

    private record SearchReplaceMismatchStatus(String path, int consecutiveCount, String reason, boolean emitWarning) {
    }

    private record LastToolOutcome(String toolName, String status, String code, String message) {
        static LastToolOutcome none() {
            return new LastToolOutcome("none", "unknown", "none", "");
        }

        String failureCode() {
            return "failed".equalsIgnoreCase(status) ? code : "none";
        }
    }
}
