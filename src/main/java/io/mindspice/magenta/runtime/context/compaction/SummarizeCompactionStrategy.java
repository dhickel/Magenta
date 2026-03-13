package io.mindspice.magenta.runtime.context.compaction;

import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.session.SessionTokenEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class SummarizeCompactionStrategy implements CompactionStrategy {

    private static final int MAX_RECENT_MESSAGES = 10;
    private static final int MIN_RECENT_MESSAGES = 2;
    private static final int KEEP_FULL_RECENT_MESSAGES = 2;
    private static final int TURN_ALIGNMENT_BACKTRACK_LIMIT = 6;
    private static final int HEAVY_TOOL_PAYLOAD_CHARS = 2_000;
    private static final int TOOL_PAYLOAD_COMPACT_CHARS = 1_200;
    private static final int TOOL_PAYLOAD_HARD_COMPACT_CHARS = 400;
    private static final double HEAVY_RECENT_HEADROOM_RATIO = 0.75;

    private final Function<List<ContextElement>, String> summarizer;
    private final CompactionStrategy fallback = new RollingWindowCompactionStrategy();

    public SummarizeCompactionStrategy(Function<List<ContextElement>, String> summarizer) {
        this.summarizer = summarizer;
    }

    @Override
    public CompactionResult run(UUID sessionId, List<ContextElement> context, int targetTokens, String tokenizerEncoding) {
        if (context.size() <= MAX_RECENT_MESSAGES + 1) {
            return fallback.run(sessionId, context, targetTokens, tokenizerEncoding);
        }

        List<ContextElement> leadingSystem = new ArrayList<>();
        int start = 0;
        while (start < context.size() && context.get(start) instanceof ContextElement.SystemMsg) {
            leadingSystem.add(context.get(start));
            start++;
        }

        int summaryEnd = resolveSummaryEnd(context, start);
        if (summaryEnd <= start) {
            return fallback.run(sessionId, context, targetTokens, tokenizerEncoding);
        }

        List<ContextElement> toSummarize = context.subList(start, summaryEnd);
        List<ContextElement> recent = new ArrayList<>(context.subList(summaryEnd, context.size()));

        String summary = summarizer.apply(toSummarize);
        if (summary == null || summary.isBlank()) {
            return fallback.run(sessionId, context, targetTokens, tokenizerEncoding);
        }

        List<ContextElement> effectiveRecent = compactOlderRecentToolPayloads(recent, TOOL_PAYLOAD_COMPACT_CHARS);
        boolean heavyRecentPayload = hasHeavyToolPayload(recent);
        int desiredTokens = heavyRecentPayload
                ? Math.max(1, (int) Math.floor(targetTokens * HEAVY_RECENT_HEADROOM_RATIO))
                : targetTokens;
        effectiveRecent = pruneOldestRecentUntilWithinBudget(
                leadingSystem,
                summary,
                effectiveRecent,
                desiredTokens,
                tokenizerEncoding,
                MIN_RECENT_MESSAGES,
                sessionId
        );

        List<ContextElement> output = buildOutput(leadingSystem, summary, effectiveRecent, sessionId);
        if (SessionTokenEstimator.estimate(output, tokenizerEncoding) > targetTokens) {
            effectiveRecent = compactOlderRecentToolPayloads(effectiveRecent, TOOL_PAYLOAD_HARD_COMPACT_CHARS);
            effectiveRecent = pruneOldestRecentUntilWithinBudget(
                    leadingSystem,
                    summary,
                    effectiveRecent,
                    targetTokens,
                    tokenizerEncoding,
                    1,
                    sessionId
            );
            output = buildOutput(leadingSystem, summary, effectiveRecent, sessionId);
        }

        if (SessionTokenEstimator.estimate(output, tokenizerEncoding) > targetTokens) {
            return fallback.run(sessionId, output, targetTokens, tokenizerEncoding);
        }
        return new CompactionResult(output, leadingSystem.size(), toSummarize.size(), effectiveRecent.size());
    }

    private int resolveSummaryEnd(List<ContextElement> context, int nonSystemStart) {
        int tentative = Math.max(nonSystemStart, context.size() - MAX_RECENT_MESSAGES);
        int boundary = tentative;
        int backtrackSteps = 0;
        while (boundary > nonSystemStart
                && !isTurnStart(context.get(boundary))
                && backtrackSteps < TURN_ALIGNMENT_BACKTRACK_LIMIT) {
            boundary--;
            backtrackSteps++;
        }
        if (boundary == nonSystemStart) {
            return tentative;
        }
        if (!isTurnStart(context.get(boundary))) {
            return tentative;
        }
        return boundary;
    }

    private List<ContextElement> buildOutput(
            List<ContextElement> leadingSystem,
            String summary,
            List<ContextElement> recent,
            UUID sessionId
    ) {
        List<ContextElement> output = new ArrayList<>(leadingSystem.size() + 1 + recent.size());
        output.addAll(leadingSystem);
        output.add(new ContextElement.SummaryMsg(summary.trim(), "session:" + sessionId));
        output.addAll(recent);
        return output;
    }

    private List<ContextElement> compactOlderRecentToolPayloads(List<ContextElement> recent, int maxChars) {
        if (recent == null || recent.isEmpty()) {
            return List.of();
        }

        int compactBoundary = Math.max(0, recent.size() - KEEP_FULL_RECENT_MESSAGES);
        List<ContextElement> output = new ArrayList<>(recent.size());
        for (int i = 0; i < recent.size(); i++) {
            ContextElement message = recent.get(i);
            if (i < compactBoundary && message instanceof ContextElement.ToolMsg toolMsg) {
                output.add(compactToolMessage(toolMsg, maxChars));
            } else {
                output.add(message);
            }
        }
        return output;
    }

    private ContextElement.ToolMsg compactToolMessage(ContextElement.ToolMsg message, int maxChars) {
        String content = message.content() == null ? "" : message.content();
        if (content.length() <= maxChars) {
            return message;
        }

        int markerReserve = 64;
        int headLength = Math.max(0, maxChars - markerReserve);
        String compacted = content.substring(0, headLength)
                + "...[truncated_for_compaction chars="
                + content.length()
                + "]";
        return new ContextElement.ToolMsg(message.toolCallId(), message.toolName(), compacted);
    }

    private List<ContextElement> pruneOldestRecentUntilWithinBudget(
            List<ContextElement> leadingSystem,
            String summary,
            List<ContextElement> recent,
            int budgetTokens,
            String tokenizerEncoding,
            int minRecentMessages,
            UUID sessionId
    ) {
        List<ContextElement> pruned = new ArrayList<>(recent);
        while (pruned.size() > minRecentMessages
                && SessionTokenEstimator.estimate(
                buildOutput(leadingSystem, summary, pruned, sessionId),
                tokenizerEncoding
        ) > budgetTokens) {
            pruned.remove(0);
        }
        return pruned;
    }

    private boolean hasHeavyToolPayload(List<ContextElement> messages) {
        for (ContextElement message : messages) {
            if (message instanceof ContextElement.ToolMsg toolMsg
                && toolMsg.content() != null
                && toolMsg.content().length() > HEAVY_TOOL_PAYLOAD_CHARS) {
                return true;
            }
        }
        return false;
    }

    private boolean isTurnStart(ContextElement message) {
        return message instanceof ContextElement.UserMsg
                || message instanceof ContextElement.InboundMsg;
    }
}
