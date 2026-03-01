package io.mindspice.magenta.systems.session.compaction;

import io.mindspice.magenta.systems.session.SessionMessage;
import io.mindspice.magenta.systems.session.SessionTokenEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class SummarizeCompactionStrategy implements CompactionStrategy {

    private static final int SUMMARY_RECENT_MESSAGES = 6;

    private final Function<List<SessionMessage>, String> summarizer;
    private final CompactionStrategy fallback = new RollingWindowCompactionStrategy();

    public SummarizeCompactionStrategy(Function<List<SessionMessage>, String> summarizer) {
        this.summarizer = summarizer;
    }

    @Override
    public List<SessionMessage> run(UUID sessionId, List<SessionMessage> context, int targetTokens, String tokenizerEncoding) {
        if (context.size() <= SUMMARY_RECENT_MESSAGES + 1) {
            return fallback.run(sessionId, context, targetTokens, tokenizerEncoding);
        }

        SessionMessage system = null;
        int start = 0;
        if (context.getFirst() instanceof SessionMessage.SystemMsg sys) {
            system = sys;
            start = 1;
        }

        int summaryEnd = Math.max(start, context.size() - SUMMARY_RECENT_MESSAGES);
        if (summaryEnd <= start) {
            return fallback.run(sessionId, context, targetTokens, tokenizerEncoding);
        }

        List<SessionMessage> toSummarize = context.subList(start, summaryEnd);
        List<SessionMessage> recent = context.subList(summaryEnd, context.size());

        String summary = summarizer.apply(toSummarize);
        if (summary == null || summary.isBlank()) {
            return fallback.run(sessionId, context, targetTokens, tokenizerEncoding);
        }

        List<SessionMessage> output = new ArrayList<>();
        if (system != null) {
            output.add(system);
        }
        output.add(new SessionMessage.SummaryMsg(summary.trim(), "session:" + sessionId));
        output.addAll(recent);

        if (SessionTokenEstimator.estimate(output, tokenizerEncoding) > targetTokens) {
            return fallback.run(sessionId, output, targetTokens, tokenizerEncoding);
        }
        return output;
    }
}
