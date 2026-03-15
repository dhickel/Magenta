package io.mindspice.magenta.runtime.context;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.compaction.CompactionStrategy;
import io.mindspice.magenta.runtime.context.compaction.RollingWindowCompactionStrategy;
import io.mindspice.magenta.runtime.persistence.CommonCommandResults;
import io.mindspice.magenta.runtime.persistence.SessionContextCommand;
import io.mindspice.magenta.runtime.persistence.SessionContextResult;
import io.mindspice.magenta.runtime.session.Session;
import io.mindspice.magenta.runtime.session.SessionTokenEstimator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class ContextManager {

    private static final int MIN_COMPACTION_TOKEN_REDUCTION = 128;
    private static final double MIN_COMPACTION_RATIO_REDUCTION = 0.05;

    private final Function<SessionContextCommand, SessionContextResult> contextBridge;

    public ContextManager() {
        this(null);
    }

    public ContextManager(Function<SessionContextCommand, SessionContextResult> contextBridge) {
        this.contextBridge = contextBridge;
    }

    public Context newContext(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return new Context();
        }
        return newContext(List.of(new ContextElement.SystemCoreMsg(systemPrompt)));
    }

    public Context newContext(List<? extends ContextElement.SystemElement> systemMessages) {
        Context context = new Context();
        List<? extends ContextElement.SystemElement> safe = systemMessages == null ? List.of() : List.copyOf(systemMessages);
        for (ContextElement.SystemElement message : safe) {
            if (message != null && !message.content().isBlank()) {
                context.append(message);
            }
        }
        return context;
    }

    public Context copyContext(Context source) {
        Context copy = new Context();
        copy.appendAll(source.snapshot());
        return copy;
    }

    public Context loadContext(Context sourceOrNull, String systemPrompt) {
        return loadContext(
                UUID.randomUUID(),
                sourceOrNull,
                systemPrompt == null ? List.of() : List.of(new ContextElement.SystemCoreMsg(systemPrompt))
        );
    }

    public Context loadContext(
            UUID sessionId,
            Context sourceOrNull,
            List<? extends ContextElement.SystemElement> systemMessages
    ) {
        Context context;
        if (sourceOrNull != null) {
            context = copyContext(sourceOrNull);
        } else {
            context = loadPersistedOrNew(sessionId, systemMessages);
        }

        attachMutationListener(sessionId, context);
        return context;
    }

    public void initializeSessionPersistence(Session session) {
        Objects.requireNonNull(session, "session");
        if (contextBridge == null) {
            return;
        }

        List<ContextElement> snapshot = session.context().snapshot();
        SessionContextResult result = contextBridge.apply(new SessionContextCommand.InitializeSession(
                session.sessionId().toString(),
                session.agentId(),
                session.alias(),
                countLeadingSystemPrompts(snapshot),
                snapshot
        ));
        ensureSuccess(result, "Failed to initialize session persistence");
    }

    public int countLeadingSystemPrompts(List<ContextElement> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (ContextElement message : messages) {
            if (ContextElement.isPromptSystemElement(message)) {
                count++;
                continue;
            }
            if (ContextElement.isStateSystemElement(message)) {
                continue;
            }
            break;
        }
        return count;
    }

    public void upsertStateSystemMessage(Context context, String stateJson) {
        Objects.requireNonNull(context, "context");
        context.upsertStateSystemMessage(stateJson);
    }

    public void storeContext(Context context) {
        Objects.requireNonNull(context, "context");
        // Persistence is mutation-driven through session-scoped context listeners.
    }

    public Optional<CompactionOutcome> compactIfNeeded(
            UUID sessionId,
            Context context,
            RuntimeConfig.ModelConfig modelConfig,
            Function<List<ContextElement>, String> summarizer
    ) {
        List<ContextElement> snapshot = context.snapshot();
        StateExtraction stateExtraction = extractStateMessage(snapshot);
        List<ContextElement> compactableSnapshot = stateExtraction.withoutStateMessages();
        int originalSystemCount = countLeadingSystemPrompts(compactableSnapshot);
        List<ContextElement> originalLeadingSystem = originalSystemCount == 0
                ? List.of()
                : List.copyOf(compactableSnapshot.subList(0, originalSystemCount));
        String tokenizerEncoding = modelConfig.tokenizerEncodingOrDefault();
        int preTokens = SessionTokenEstimator.estimate(snapshot, tokenizerEncoding);
        int compactThreshold = modelConfig.compactThreshold();
        if (preTokens <= compactThreshold) {
            return Optional.empty();
        }

        Function<List<ContextElement>, String> effectiveSummarizer = messages -> {
            String summary;
            try {
                summary = summarizer.apply(messages);
            } catch (Exception ignored) {
                summary = "";
            }
            if (!"summarize".equalsIgnoreCase(modelConfig.compactionStrategyOrDefault())) {
                return summary;
            }
            if (summary == null || summary.isBlank()) {
                summary = buildDeterministicCompactionFallback(messages);
            }
            return summary;
        };

        CompactionStrategy strategy = CompactionStrategy.forName(modelConfig.compactionStrategyOrDefault(), effectiveSummarizer);
        CompactionStrategy.CompactionResult compactedResult = strategy.run(
                sessionId,
                compactableSnapshot,
                compactThreshold,
                tokenizerEncoding
        );
        List<ContextElement> repaired = withPreservedLeadingSystemPrompts(
                originalLeadingSystem,
                compactedResult.messages()
        );
        List<ContextElement> compacted = withStateMessage(repaired, stateExtraction.stateMessageOrNull());
        if (snapshot.equals(compacted)) {
            return Optional.empty();
        }

        int postTokens = SessionTokenEstimator.estimate(compacted, tokenizerEncoding);
        int tokenDelta = preTokens - postTokens;
        double reductionRatio = preTokens <= 0 ? 0.0 : (double) tokenDelta / (double) preTokens;
        if (tokenDelta < MIN_COMPACTION_TOKEN_REDUCTION || reductionRatio < MIN_COMPACTION_RATIO_REDUCTION) {
            return Optional.empty();
        }

        int preMessages = snapshot.size();
        context.replaceAll(compacted);
        int postMessages = compacted.size();
        return Optional.of(new CompactionOutcome(
                preTokens,
                postTokens,
                preMessages,
                postMessages,
                compactThreshold,
                modelConfig.compactionStrategyOrDefault(),
                countLeadingSystemPrompts(compacted),
                compactedResult.summarizedCount(),
                compactedResult.preservedRecentCount()
        ));
    }

    public Optional<CompactionOutcome> enforceMaxContext(
            UUID sessionId,
            Context context,
            RuntimeConfig.ModelConfig modelConfig
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(modelConfig, "modelConfig");

        List<ContextElement> snapshot = context.snapshot();
        StateExtraction stateExtraction = extractStateMessage(snapshot);
        List<ContextElement> compactableSnapshot = stateExtraction.withoutStateMessages();
        int originalSystemCount = countLeadingSystemPrompts(compactableSnapshot);
        List<ContextElement> originalLeadingSystem = originalSystemCount == 0
                ? List.of()
                : List.copyOf(compactableSnapshot.subList(0, originalSystemCount));
        String tokenizerEncoding = modelConfig.tokenizerEncodingOrDefault();
        int preTokens = SessionTokenEstimator.estimate(snapshot, tokenizerEncoding);
        int maxContext = modelConfig.maxContext();
        if (preTokens <= maxContext) {
            return Optional.empty();
        }

        CompactionStrategy.CompactionResult compactedResult = new RollingWindowCompactionStrategy().run(
                sessionId,
                compactableSnapshot,
                maxContext,
                tokenizerEncoding
        );
        List<ContextElement> repaired = withPreservedLeadingSystemPrompts(
                originalLeadingSystem,
                compactedResult.messages()
        );
        List<ContextElement> compacted = withStateMessage(repaired, stateExtraction.stateMessageOrNull());
        if (snapshot.equals(compacted)) {
            return Optional.empty();
        }

        context.replaceAll(compacted);
        int postTokens = SessionTokenEstimator.estimate(compacted, tokenizerEncoding);
        return Optional.of(new CompactionOutcome(
                preTokens,
                postTokens,
                snapshot.size(),
                compacted.size(),
                maxContext,
                "max_context_guard",
                countLeadingSystemPrompts(compacted),
                compactedResult.summarizedCount(),
                compactedResult.preservedRecentCount()
        ));
    }

    public record CompactionOutcome(
            int tokensBefore,
            int tokensAfter,
            int messagesBefore,
            int messagesAfter,
            int compactThreshold,
            String strategy,
            int protectedSystemCount,
            int summarizedCount,
            int preservedRecentCount
    ) {
        public CompactionOutcome {
            strategy = strategy == null ? "" : strategy;
            protectedSystemCount = Math.max(0, protectedSystemCount);
            summarizedCount = Math.max(0, summarizedCount);
            preservedRecentCount = Math.max(0, preservedRecentCount);
        }
    }

    private Context loadPersistedOrNew(UUID sessionId, List<? extends ContextElement.SystemElement> systemMessages) {
        if (contextBridge == null || sessionId == null) {
            return newContext(systemMessages);
        }

        SessionContextResult loaded = contextBridge.apply(new SessionContextCommand.LoadActiveContext(sessionId.toString()));
        if (loaded instanceof SessionContextResult.ActiveContextLoaded active && !active.messages().isEmpty()) {
            Context context = new Context();
            context.appendAll(active.messages());
            return context;
        }

        if (loaded instanceof CommonCommandResults.Failure) {
            return newContext(systemMessages);
        }

        return newContext(systemMessages);
    }

    private void attachMutationListener(UUID sessionId, Context context) {
        if (sessionId == null || contextBridge == null) {
            return;
        }

        context.setMutationListener(mutation -> {
            SessionContextResult result = switch (mutation) {
                case Context.Mutation.Append append -> contextBridge.apply(new SessionContextCommand.AppendMessage(
                        sessionId.toString(),
                        append.message()
                ));
                case Context.Mutation.AppendAll appendAll -> contextBridge.apply(new SessionContextCommand.AppendMessages(
                        sessionId.toString(),
                        appendAll.messages()
                ));
                case Context.Mutation.ReplaceAll replaceAll -> contextBridge.apply(new SessionContextCommand.ReplaceActiveContext(
                        sessionId.toString(),
                        replaceAll.messages(),
                        countLeadingSystemPrompts(replaceAll.messages())
                ));
                case Context.Mutation.UpsertStateSystemMessage upsert -> contextBridge.apply(
                        new SessionContextCommand.UpsertStateSystemMessage(sessionId.toString(), upsert.stateJson())
                );
            };
            ensureSuccess(result, "Failed to persist context mutation");
        });
    }

    private void ensureSuccess(SessionContextResult result, String prefix) {
        if (result instanceof CommonCommandResults.Failure failure) {
            throw new IllegalStateException(prefix + ": " + failure.code() + " - " + failure.message());
        }
    }

    private List<ContextElement> withPreservedLeadingSystemPrompts(
            List<ContextElement> originalLeadingSystem,
            List<ContextElement> candidate
    ) {
        List<ContextElement> compacted = candidate == null ? List.of() : List.copyOf(candidate);
        if (originalLeadingSystem == null || originalLeadingSystem.isEmpty()) {
            return compacted;
        }
        if (compacted.size() >= originalLeadingSystem.size()
            && compacted.subList(0, originalLeadingSystem.size()).equals(originalLeadingSystem)) {
            return compacted;
        }

        int candidateLeadingSystemCount = countLeadingSystemPrompts(compacted);
        List<ContextElement> tail = compacted.subList(candidateLeadingSystemCount, compacted.size());
        ArrayList<ContextElement> repaired = new ArrayList<>(originalLeadingSystem.size() + tail.size());
        repaired.addAll(originalLeadingSystem);
        repaired.addAll(tail);
        return List.copyOf(repaired);
    }

    private StateExtraction extractStateMessage(List<ContextElement> messages) {
        if (messages == null || messages.isEmpty()) {
            return new StateExtraction(null, List.of());
        }
        ContextElement.SystemStateMsg newestState = null;
        for (ContextElement message : messages) {
            if (isStateSystemMessage(message)) {
                newestState = (ContextElement.SystemStateMsg) message;
            }
        }
        if (newestState == null) {
            return new StateExtraction(null, List.copyOf(messages));
        }

        List<ContextElement> without = new ArrayList<>(messages.size());
        for (ContextElement message : messages) {
            if (isStateSystemMessage(message)) {
                continue;
            }
            without.add(message);
        }
        return new StateExtraction(newestState, List.copyOf(without));
    }

    private List<ContextElement> withStateMessage(List<ContextElement> messages, ContextElement.SystemStateMsg stateMessage) {
        if (stateMessage == null) {
            return messages == null ? List.of() : List.copyOf(messages);
        }
        List<ContextElement> safe = messages == null ? List.of() : List.copyOf(messages);
        ArrayList<ContextElement> rebuilt = new ArrayList<>(safe.size() + 1);
        for (ContextElement message : safe) {
            if (isStateSystemMessage(message)) {
                continue;
            }
            rebuilt.add(message);
        }
        int insertionIndex = 0;
        for (int i = 0; i < rebuilt.size(); i++) {
            if (ContextElement.isSystemElement(rebuilt.get(i))) {
                insertionIndex = i + 1;
            }
        }
        rebuilt.add(insertionIndex, stateMessage);
        return List.copyOf(rebuilt);
    }

    private boolean isStateSystemMessage(ContextElement message) {
        return ContextElement.isStateSystemElement(message);
    }

    private String buildDeterministicCompactionFallback(List<ContextElement> messages) {
        if (messages == null || messages.isEmpty()) {
            return """
                    Objective: Continue current task.
                    Completed: Compaction fallback summary generated from empty context.
                    Pending: Resume normal execution flow.
                    Errors: summary_model_empty.
                    Next: Read current TODO state before more tool mutations.
                    """.trim();
        }

        int userCount = 0;
        int assistantCount = 0;
        int toolCount = 0;
        String lastUser = "";
        String lastAssistant = "";
        Map<String, Integer> toolSignatureCounts = new HashMap<>();

        for (ContextElement message : messages) {
            switch (message) {
                case ContextElement.UserMsg userMsg -> {
                    userCount++;
                    if (userMsg.content() != null && !userMsg.content().isBlank()) {
                        lastUser = compactText(userMsg.content(), 180);
                    }
                }
                case ContextElement.InboundMsg inboundMsg -> {
                    userCount++;
                    if (inboundMsg.content() != null && !inboundMsg.content().isBlank()) {
                        lastUser = compactText(inboundMsg.content(), 180);
                    }
                }
                case ContextElement.AssistantMsg assistantMsg -> {
                    assistantCount++;
                    if (assistantMsg.content() != null && !assistantMsg.content().isBlank()) {
                        lastAssistant = compactText(assistantMsg.content(), 180);
                    }
                }
                case ContextElement.ToolMsg toolMsg -> {
                    toolCount++;
                    String signature = compactText(toolMsg.toolName() + "|" + compactText(toolMsg.content(), 120), 200);
                    toolSignatureCounts.merge(signature, 1, Integer::sum);
                }
                default -> { }
            }
        }

        String objective = lastUser.isBlank()
                ? (lastAssistant.isBlank() ? "Continue current task." : "Continue from latest assistant state: " + lastAssistant)
                : "Follow latest user intent: " + lastUser;

        String repeatWarning = "";
        String repeatedSignature = "";
        int repeatedCount = 0;
        for (Map.Entry<String, Integer> entry : toolSignatureCounts.entrySet()) {
            if (entry.getValue() > repeatedCount) {
                repeatedCount = entry.getValue();
                repeatedSignature = entry.getKey();
            }
        }
        if (repeatedCount >= 3) {
            repeatWarning = " repetitive_tool_pattern count=" + repeatedCount + " signature=" + compactText(repeatedSignature, 120) + ".";
        }

        return (
                "Objective: " + objective + "\n"
                + "Completed: Condensed " + messages.size() + " messages (user=" + userCount
                + ", assistant=" + assistantCount + ", tool=" + toolCount + ").\n"
                + "Pending: Resume active workflow from state snapshot and current TODO focus.\n"
                + "Errors: summary_model_empty." + repeatWarning + "\n"
                + "Next: Refresh TODO state, then continue only with non-duplicate tool actions."
        ).trim();
    }

    private String compactText(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        if (maxChars <= 3) {
            return normalized.substring(0, Math.max(maxChars, 0));
        }
        return normalized.substring(0, maxChars - 3) + "...";
    }

    private record StateExtraction(
            ContextElement.SystemStateMsg stateMessageOrNull,
            List<ContextElement> withoutStateMessages
    ) {
        private StateExtraction {
            withoutStateMessages = withoutStateMessages == null ? List.of() : List.copyOf(withoutStateMessages);
        }
    }
}
