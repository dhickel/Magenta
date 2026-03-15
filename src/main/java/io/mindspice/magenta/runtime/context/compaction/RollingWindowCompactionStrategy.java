package io.mindspice.magenta.runtime.context.compaction;

import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.session.SessionTokenEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RollingWindowCompactionStrategy implements CompactionStrategy {

    @Override
    public CompactionResult run(UUID sessionId, List<ContextElement> context, int targetTokens, String tokenizerEncoding) {
        if (context.isEmpty()) {
            return new CompactionResult(List.of(), 0, 0, 0);
        }

        List<ContextElement> leadingSystem = new ArrayList<>();
        int start = 0;
        while (start < context.size() && ContextElement.isPromptSystemElement(context.get(start))) {
            leadingSystem.add(context.get(start));
            start++;
        }

        List<ContextElement> kept = new ArrayList<>();
        int tokenCount = leadingSystem.stream()
                .mapToInt(message -> SessionTokenEstimator.estimateMessage(message, tokenizerEncoding))
                .sum();

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
        output.addAll(leadingSystem);
        output.addAll(kept);
        return new CompactionResult(output, leadingSystem.size(), 0, kept.size());
    }
}
