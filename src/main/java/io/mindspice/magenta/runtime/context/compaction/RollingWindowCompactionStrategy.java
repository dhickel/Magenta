package io.mindspice.magenta.runtime.context.compaction;

import io.mindspice.magenta.runtime.session.SessionMessage;
import io.mindspice.magenta.runtime.session.SessionTokenEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RollingWindowCompactionStrategy implements CompactionStrategy {

    @Override
    public List<SessionMessage> run(UUID sessionId, List<SessionMessage> context, int targetTokens, String tokenizerEncoding) {
        if (context.isEmpty()) {
            return context;
        }

        SessionMessage system = null;
        int start = 0;
        if (context.getFirst() instanceof SessionMessage.SystemMsg sys) {
            system = sys;
            start = 1;
        }

        List<SessionMessage> kept = new ArrayList<>();
        int tokenCount = system == null ? 0 : SessionTokenEstimator.estimateMessage(system, tokenizerEncoding);

        for (int i = context.size() - 1; i >= start; i--) {
            SessionMessage message = context.get(i);
            int messageTokens = SessionTokenEstimator.estimateMessage(message, tokenizerEncoding);
            if (tokenCount + messageTokens > targetTokens) {
                break;
            }
            kept.addFirst(message);
            tokenCount += messageTokens;
        }

        List<SessionMessage> output = new ArrayList<>();
        if (system != null) {
            output.add(system);
        }
        output.addAll(kept);
        return output;
    }
}
