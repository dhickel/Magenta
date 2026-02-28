package io.mindspice.magenta.systems.session;

import io.mindspice.magenta.systems.config.RuntimeConfig.ModelConfig;
import io.mindspice.magenta.systems.config.RuntimeConfig;
import io.mindspice.magenta.systems.session.compaction.CompactionStrategy;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class ContextManager {

    public Context newContext(String systemPrompt) {
        Context context = new Context();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            context.append(new SessionMessage.SystemMsg(systemPrompt));
        }
        return context;
    }

    public Context copyContext(Context source) {
        Context copy = new Context();
        copy.appendAll(source.snapshot());
        return copy;
    }

    public Context loadContext(Context sourceOrNull, String systemPrompt) {
        if (sourceOrNull == null) {
            return newContext(systemPrompt);
        }
        return copyContext(sourceOrNull);
    }

    public void storeContext(Context context) {
        Objects.requireNonNull(context, "context");
        // Persistence intentionally deferred for this slice.
    }

    public void compactIfNeeded(
            UUID sessionId,
            Context context,
            RuntimeConfig.ModelConfig modelConfig,
            Function<List<SessionMessage>, String> summarizer
    ) {
        List<SessionMessage> snapshot = context.snapshot();
        int tokens = SessionTokenEstimator.estimate(snapshot);
        if (tokens <= modelConfig.compactThreshold()) {
            return;
        }

        CompactionStrategy strategy = CompactionStrategy.forName(modelConfig.compactionStrategyOrDefault(), summarizer);
        List<SessionMessage> compacted = strategy.run(sessionId, snapshot, modelConfig.compactThreshold());
        context.replaceAll(compacted);
    }
}
