package com.magenta.context.policy;

import com.magenta.context.model.Context;

/**
 * Manages context window and token tracking.
 * Determines when compaction is needed and triggers appropriate strategy.
 */
public class ContextWindowManager {
    private final CompactionStrategy strategy;
    private final double compactionTriggerRatio;

    /**
     * Create manager with specific strategy and trigger ratio.
     *
     * @param strategy Strategy to use for compaction
     * @param compactionTriggerRatio Ratio (0.0-1.0) of maxContext to trigger compaction
     */
    public ContextWindowManager(CompactionStrategy strategy, double compactionTriggerRatio) {
        if (compactionTriggerRatio < 0.0 || compactionTriggerRatio > 1.0) {
            throw new IllegalArgumentException("Trigger ratio must be between 0.0 and 1.0");
        }
        this.strategy = strategy;
        this.compactionTriggerRatio = compactionTriggerRatio;
    }

    /**
     * Create manager with default settings (Truncate strategy, 80% trigger).
     */
    public ContextWindowManager() {
        this(new CompactionStrategy.Truncate(), 0.8);
    }

    /**
     * Create manager with specified strategy, default 80% trigger.
     */
    public ContextWindowManager(CompactionStrategy strategy) {
        this(strategy, 0.8);
    }

    /**
     * Check if compaction is needed based on current token usage.
     *
     * @param context Current context
     * @param limits Token limits
     * @return true if compaction should be triggered
     */
    public boolean shouldCompact(Context context, ContextLimits limits) {
        int currentTokens = context.totalEstimatedTokens();
        int threshold = (int) (limits.maxContext() * compactionTriggerRatio);
        return currentTokens > threshold;
    }

    /**
     * Apply compaction if needed.
     * Checks if compaction is required and applies strategy if so.
     *
     * @param context Context to potentially compact
     * @param limits Token limits
     * @return true if compaction was performed
     */
    public boolean compactIfNeeded(Context context, ContextLimits limits) {
        if (shouldCompact(context, limits)) {
            strategy.compact(context, limits);
            return true;
        }
        return false;
    }

    /**
     * Force compaction regardless of current token count.
     * Useful for manual compaction or emergency situations.
     *
     * @param context Context to compact
     * @param limits Token limits
     */
    public void forceCompact(Context context, ContextLimits limits) {
        strategy.compact(context, limits);
    }

    /**
     * Calculate remaining tokens available in context window.
     *
     * @param context Current context
     * @param limits Token limits
     * @return Number of tokens available before hitting maxContext
     */
    public int remainingTokens(Context context, ContextLimits limits) {
        int currentTokens = context.totalEstimatedTokens();
        return Math.max(0, limits.maxContext() - currentTokens);
    }

    /**
     * Calculate utilization ratio (0.0-1.0) of context window.
     *
     * @param context Current context
     * @param limits Token limits
     * @return Ratio of current tokens to max tokens
     */
    public double utilizationRatio(Context context, ContextLimits limits) {
        int currentTokens = context.totalEstimatedTokens();
        return (double) currentTokens / limits.maxContext();
    }

    /**
     * Get context statistics.
     *
     * @param context Current context
     * @param limits Token limits
     * @return Statistics object
     */
    public ContextStats getStats(Context context, ContextLimits limits) {
        int currentTokens = context.totalEstimatedTokens();
        int remaining = remainingTokens(context, limits);
        double utilization = utilizationRatio(context, limits);
        boolean needsCompaction = shouldCompact(context, limits);

        return new ContextStats(
            context.getElements().size(),
            currentTokens,
            limits.maxContext(),
            remaining,
            utilization,
            needsCompaction
        );
    }

    /**
     * Statistics about current context state.
     */
    public record ContextStats(
        int elementCount,
        int currentTokens,
        int maxTokens,
        int remainingTokens,
        double utilizationRatio,
        boolean needsCompaction
    ) {
        public String toSummary() {
            return String.format(
                "Elements: %d | Tokens: %d/%d (%.1f%%) | Remaining: %d | Needs compaction: %s",
                elementCount,
                currentTokens,
                maxTokens,
                utilizationRatio * 100,
                remainingTokens,
                needsCompaction ? "YES" : "no"
            );
        }
    }
}
