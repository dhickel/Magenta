package io.mindspice.magenta.runtime.context.compaction;

import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.session.SessionTokenEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RollingWindowCompactionStrategy implements CompactionStrategy {

    @Override
    public List<ContextElement> run(UUID sessionId, List<ContextElement> context, int targetTokens, String tokenizerEncoding) {
        if (context.isEmpty()) {
            return context;
        }

        ContextElement system = null;
        int start = 0;
        if (context.getFirst() instanceof ContextElement.SystemMsg sys) {
            system = sys;
            start = 1;
        }

        List<ContextElement> kept = new ArrayList<>();
        int tokenCount = system == null ? 0 : SessionTokenEstimator.estimateMessage(system, tokenizerEncoding);

        for (int i = context.size() - 1; i >= start; i--) {
            ContextElement message = context.get(i);
            int messageTokens = SessionTokenEstimator.estimateMessage(message, tokenizerEncoding);
            if (tokenCount + messageTokens > targetTokens) {
                break;
            }
            kept.addFirst(message);
            tokenCount += messageTokens;
        }

        List<ContextElement> output = new ArrayList<>();
        if (system != null) {
            output.add(system);
        }
        output.addAll(kept);
        return output;
    }
}
