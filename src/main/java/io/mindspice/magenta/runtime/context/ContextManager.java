package io.mindspice.magenta.runtime.context;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.compaction.CompactionStrategy;
import io.mindspice.magenta.runtime.session.SessionTokenEstimator;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class ContextManager {

    public Context newContext(String systemPrompt) {
        Context context = new Context();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            context.append(new ContextElement.SystemMsg(systemPrompt));
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
            Function<List<ContextElement>, String> summarizer
    ) {
        List<ContextElement> snapshot = context.snapshot();
        String tokenizerEncoding = modelConfig.tokenizerEncodingOrDefault();
        int tokens = SessionTokenEstimator.estimate(snapshot, tokenizerEncoding);
        if (tokens <= modelConfig.compactThreshold()) {
            return;
        }

        CompactionStrategy strategy = CompactionStrategy.forName(modelConfig.compactionStrategyOrDefault(), summarizer);
        List<ContextElement> compacted = strategy.run(sessionId, snapshot, modelConfig.compactThreshold(), tokenizerEncoding);
        context.replaceAll(compacted);
    }
}
