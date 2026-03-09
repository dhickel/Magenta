package io.mindspice.magenta.runtime.context.compaction;

import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.session.SessionTokenEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class SummarizeCompactionStrategy implements CompactionStrategy {

    private static final int SUMMARY_RECENT_MESSAGES = 6;

    private final Function<List<ContextElement>, String> summarizer;
    private final CompactionStrategy fallback = new RollingWindowCompactionStrategy();

    public SummarizeCompactionStrategy(Function<List<ContextElement>, String> summarizer) {
        this.summarizer = summarizer;
    }

    @Override
    public List<ContextElement> run(UUID sessionId, List<ContextElement> context, int targetTokens, String tokenizerEncoding) {
        if (context.size() <= SUMMARY_RECENT_MESSAGES + 1) {
            return fallback.run(sessionId, context, targetTokens, tokenizerEncoding);
        }

        List<ContextElement> leadingSystem = new ArrayList<>();
        int start = 0;
        while (start < context.size() && context.get(start) instanceof ContextElement.SystemMsg) {
            leadingSystem.add(context.get(start));
            start++;
        }

        int summaryEnd = Math.max(start, context.size() - SUMMARY_RECENT_MESSAGES);
        if (summaryEnd <= start) {
            return fallback.run(sessionId, context, targetTokens, tokenizerEncoding);
        }

        List<ContextElement> toSummarize = context.subList(start, summaryEnd);
        List<ContextElement> recent = context.subList(summaryEnd, context.size());

        String summary = summarizer.apply(toSummarize);
        if (summary == null || summary.isBlank()) {
            return fallback.run(sessionId, context, targetTokens, tokenizerEncoding);
        }

        List<ContextElement> output = new ArrayList<>();
        output.addAll(leadingSystem);
        output.add(new ContextElement.SummaryMsg(summary.trim(), "session:" + sessionId));
        output.addAll(recent);

        if (SessionTokenEstimator.estimate(output, tokenizerEncoding) > targetTokens) {
            return fallback.run(sessionId, output, targetTokens, tokenizerEncoding);
        }
        return output;
    }
}
