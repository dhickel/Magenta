package io.mindspice.magenta.runtime.context.compaction;

import io.mindspice.magenta.runtime.session.SessionMessage;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public interface CompactionStrategy {
    List<SessionMessage> run(UUID sessionId, List<SessionMessage> context, int targetTokens, String tokenizerEncoding);

    static CompactionStrategy forName(String name, Function<List<SessionMessage>, String> summarizer) {
        if ("summarize".equalsIgnoreCase(name)) {
            return new SummarizeCompactionStrategy(summarizer);
        }
        return new RollingWindowCompactionStrategy();
    }
}
