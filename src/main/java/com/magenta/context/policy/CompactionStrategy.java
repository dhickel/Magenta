package com.magenta.context.policy;

import com.magenta.context.model.Context;
import com.magenta.context.model.ContextElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Sealed ADT for context compaction strategies.
 * Each strategy handles reducing context size when limits are approached or exceeded.
 */
public sealed interface CompactionStrategy permits
        CompactionStrategy.Truncate,
        CompactionStrategy.SlidingWindow,
        CompactionStrategy.Summarize {

    /**
     * Compact the context according to the strategy.
     * Modifies context in-place via setElements().
     */
    void compact(Context context, ContextLimits limits);

    /**
     * Truncate strategy - removes oldest messages first, preserving system prompt.
     * Simple and deterministic, good fallback when summarization unavailable.
     */
    final class Truncate implements CompactionStrategy {
        @Override
        public void compact(Context context, ContextLimits limits) {
            int currentTokens = context.totalEstimatedTokens();
            int targetTokens = calculateTargetTokens(currentTokens, limits);

            if (currentTokens <= targetTokens) {
                return; // No compaction needed
            }

            List<ContextElement> elements = new ArrayList<>(context.getElements());
            List<ContextElement> kept = new ArrayList<>();
            ContextElement systemElement = null;

            // Preserve system prompt if present
            if (!elements.isEmpty() && elements.get(0) instanceof ContextElement.System) {
                systemElement = elements.get(0);
            }

            int retainedTokens = (systemElement != null) ? systemElement.estimatedTokens() : 0;

            // Keep most recent messages that fit within target
            for (int i = elements.size() - 1; i >= 0; i--) {
                ContextElement e = elements.get(i);
                if (e == systemElement) continue;

                int cost = e.estimatedTokens();
                if (retainedTokens + cost <= targetTokens) {
                    kept.add(0, e);
                    retainedTokens += cost;
                } else {
                    break;
                }
            }

            List<ContextElement> newElements = new ArrayList<>();
            if (systemElement != null) {
                newElements.add(systemElement);
            }
            newElements.addAll(kept);

            context.setElements(newElements);
        }
    }

    /**
     * Sliding window strategy - keeps system prompt + most recent messages,
     * drops messages in the middle. More aggressive than truncate.
     */
    final class SlidingWindow implements CompactionStrategy {
        private final int recentMessagesToKeep;

        public SlidingWindow(int recentMessagesToKeep) {
            this.recentMessagesToKeep = recentMessagesToKeep;
        }

        public SlidingWindow() {
            this(10); // Default: keep last 10 messages
        }

        @Override
        public void compact(Context context, ContextLimits limits) {
            int currentTokens = context.totalEstimatedTokens();
            int targetTokens = calculateTargetTokens(currentTokens, limits);

            if (currentTokens <= targetTokens) {
                return; // No compaction needed
            }

            List<ContextElement> elements = new ArrayList<>(context.getElements());
            List<ContextElement> newElements = new ArrayList<>();

            // Keep system prompt
            ContextElement systemElement = null;
            if (!elements.isEmpty() && elements.get(0) instanceof ContextElement.System) {
                systemElement = elements.get(0);
                newElements.add(systemElement);
            }

            // Keep most recent N messages
            int startIndex = Math.max((systemElement != null ? 1 : 0),
                                     elements.size() - recentMessagesToKeep);

            for (int i = startIndex; i < elements.size(); i++) {
                newElements.add(elements.get(i));
            }

            // If still too large, fall back to truncate
            int estimatedTokens = newElements.stream()
                    .mapToInt(ContextElement::estimatedTokens)
                    .sum();

            if (estimatedTokens > targetTokens) {
                // Use truncate strategy as fallback
                new Truncate().compact(context, limits);
            } else {
                context.setElements(newElements);
            }
        }
    }

    /**
     * Summarization strategy - uses AI model to create summaries of older context.
     * Preserves information while reducing tokens. Most sophisticated strategy.
     *
     * NOTE: Implementation stubbed - requires ChatModel integration.
     * For alpha, this falls back to Truncate until summarization is wired up.
     */
    final class Summarize implements CompactionStrategy {
        private final CompactionStrategy fallback;

        public Summarize() {
            this.fallback = new Truncate();
        }

        @Override
        public void compact(Context context, ContextLimits limits) {
            // TODO: Implement actual summarization when ChatModel integration ready
            // For now, use truncate as fallback
            //
            // Future implementation:
            // 1. Identify oldest 50% of messages (excluding system)
            // 2. Send to model with summarization prompt
            // 3. Create ContextElement.Summary with result
            // 4. Replace old messages with summary
            // 5. Keep system + summary + recent messages

            fallback.compact(context, limits);
        }
    }

    /**
     * Calculate target token count for compaction.
     * Triggers at compactThreshold, aims to reduce to that level.
     */
    private static int calculateTargetTokens(int currentTokens, ContextLimits limits) {
        if (currentTokens > limits.compactThreshold()) {
            return limits.compactThreshold();
        } else if (currentTokens > limits.maxContext()) {
            return limits.maxContext();
        }
        return currentTokens;
    }
}
