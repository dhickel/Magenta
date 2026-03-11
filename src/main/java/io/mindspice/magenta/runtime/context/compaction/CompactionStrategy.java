package io.mindspice.magenta.runtime.context.compaction;

import io.mindspice.magenta.runtime.context.ContextElement;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public interface CompactionStrategy {
    CompactionResult run(UUID sessionId, List<ContextElement> context, int targetTokens, String tokenizerEncoding);

    record CompactionResult(
            List<ContextElement> messages,
            int protectedSystemCount,
            int summarizedCount,
            int preservedRecentCount
    ) {
        public CompactionResult {
            messages = messages == null ? List.of() : List.copyOf(messages);
            protectedSystemCount = Math.max(0, protectedSystemCount);
            summarizedCount = Math.max(0, summarizedCount);
            preservedRecentCount = Math.max(0, preservedRecentCount);
        }
    }

    static CompactionStrategy forName(String name, Function<List<ContextElement>, String> summarizer) {
        if ("summarize".equalsIgnoreCase(name)) {
            return new SummarizeCompactionStrategy(summarizer);
        }
        return new RollingWindowCompactionStrategy();
    }
}
