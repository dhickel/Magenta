package com.magenta.context;

import com.magenta.session.SessionId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for context lifecycle and persistence.
 * Handles loading, saving, compacting, and archiving conversation contexts.
 */
public class ContextManager {
    private static volatile ContextManager instance;

    private final Map<SessionId, Context> activeContexts = new ConcurrentHashMap<>();
    private final CompactionStrategy compactionStrategy;

    private ContextManager() {
        // Use Truncate strategy by default (simple, deterministic)
        // Can be upgraded to Summarize when model integration is ready
        this.compactionStrategy = new CompactionStrategy.Truncate();
    }

    public static ContextManager initialize() {
        return getInstance();
    }

    public static ContextManager getInstance() {
        if (instance == null) {
            synchronized (ContextManager.class) {
                if (instance == null) {
                    instance = new ContextManager();
                }
            }
        }
        return instance;
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    public Context loadContext(SessionId sessionId) {
        return activeContexts.computeIfAbsent(sessionId, id -> new Context(id.toString()));
    }

    public void saveContext(SessionId sessionId, Context context, ContextLimits limits) {
        compactIfNeeded(context, limits);
        activeContexts.put(sessionId, context);
    }

    public void append(SessionId sessionId, ContextElement element, ContextLimits limits) {
        Context context = loadContext(sessionId);
        context.add(element);
        compactIfNeeded(context, limits);
    }

    public Optional<Context> retrieveArchivedContext(String key) {
        return Optional.empty();
    }

    public void archiveContext(String key, Context context) {
    }

    // === Context window management (inlined from ContextWindowManager) ===

    /**
     * Check if compaction is needed based on current token usage.
     * Triggers when current usage exceeds maxContext.
     */
    public boolean shouldCompact(Context context, ContextLimits limits) {
        return context.totalEstimatedTokens() > limits.maxContext();
    }

    /**
     * Apply compaction if needed.
     * Checks if compaction is required and applies strategy if so.
     *
     * @return true if compaction was performed
     */
    public boolean compactIfNeeded(Context context, ContextLimits limits) {
        if (shouldCompact(context, limits)) {
            compactionStrategy.compact(context, limits);
            return true;
        }
        return false;
    }

    /**
     * Force compaction regardless of current token count.
     * Useful for manual compaction or emergency situations.
     */
    public void forceCompact(Context context, ContextLimits limits) {
        compactionStrategy.compact(context, limits);
    }

    /**
     * Calculate remaining tokens available in context window.
     *
     * @return Number of tokens available before hitting maxContext
     */
    public int remainingTokens(Context context, ContextLimits limits) {
        return Math.max(0, limits.maxContext() - context.totalEstimatedTokens());
    }

    /**
     * Calculate utilization ratio (0.0-1.0) of context window.
     *
     * @return Ratio of current tokens to max tokens
     */
    public double utilizationRatio(Context context, ContextLimits limits) {
        return (double) context.totalEstimatedTokens() / limits.maxContext();
    }

    /**
     * Get context statistics.
     */
    public ContextStats getStats(Context context, ContextLimits limits) {
        int currentTokens = context.totalEstimatedTokens();
        return new ContextStats(
            context.getElements().size(),
            currentTokens,
            limits.maxContext(),
            remainingTokens(context, limits),
            utilizationRatio(context, limits),
            shouldCompact(context, limits)
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
