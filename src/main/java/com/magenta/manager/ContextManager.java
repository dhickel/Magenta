package com.magenta.manager;

import com.magenta.context.CompactionStrategy;
import com.magenta.context.Context;
import com.magenta.context.ContextElement;
import com.magenta.context.ContextLimits;
import com.magenta.io.IOManager;
import com.magenta.persistence.Database;
import com.magenta.session.SessionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for context lifecycle and persistence.
 * Handles loading, saving, compacting, and archiving conversation contexts.
 *
 * <p>Architecture:
 * <ul>
 *   <li>Write-through cache: Database is source of truth, memory is cache</li>
 *   <li>Active sessions stay in memory for performance</li>
 *   <li>On cache miss, load from database</li>
 *   <li>On modification, mark dirty and flush to database automatically</li>
 *   <li>Background flush: Every 30 seconds, dirty contexts are persisted</li>
 *   <li>Explicit flush: On session switch, shutdown, or manual save command</li>
 *   <li>On database error, log warning and continue with cache</li>
 * </ul>
 *
 * <p>Auto-save Strategy (Hybrid):
 * <ul>
 *   <li>Background flush every 30s for safety (max 30s data loss on crash)</li>
 *   <li>Explicit save at natural boundaries (session switch, exit)</li>
 *   <li>Shutdown hook to flush all dirty contexts on JVM exit</li>
 *   <li>Manual save via {@link #flushDirtyContexts()} or {@link #flushAll()}</li>
 * </ul>
 */
public class ContextManager {
    private static final Logger logger = LoggerFactory.getLogger(ContextManager.class);

    private final Map<SessionId, Context> activeContexts = new ConcurrentHashMap<>();
    private final Set<SessionId> dirtyContexts = ConcurrentHashMap.newKeySet();
    private final Map<SessionId, Integer> lastSavedSequence = new ConcurrentHashMap<>();
    private final CompactionStrategy compactionStrategy;
    private final Database database;

    /**
     * Create a ContextManager with optional database for persistence.
     *
     * @param database Database instance (nullable for no-persistence mode)
     */
    public ContextManager(Database database) {
        this.compactionStrategy = new CompactionStrategy.Truncate();
        this.database = database;

        if (database == null) {
            logger.warn("Database is null - persistence will be disabled");
        }
    }

    // === Auto-save Methods ===

    /**
     * Mark a context as dirty (modified in memory but not yet persisted).
     * Will be flushed on next background flush cycle.
     */
    private void markDirty(SessionId sessionId) {
        dirtyContexts.add(sessionId);
        logger.trace("Marked context as dirty: {}", sessionId);
    }

    /**
     * Flush all dirty contexts to database.
     * Called automatically by background scheduler and on shutdown.
     * Only saves elements that haven't been persisted yet (append-only).
     */
    public void flushDirtyContexts() {
        if (database == null || dirtyContexts.isEmpty()) {
            return;
        }

        int flushedContexts = 0;
        int flushedElements = 0;

        for (SessionId sessionId : dirtyContexts) {
            Context context = activeContexts.get(sessionId);
            if (context != null) {
                int saved = flushContextInternal(sessionId, context);
                if (saved > 0) {
                    flushedContexts++;
                    flushedElements += saved;
                }
            }
        }

        dirtyContexts.clear();

        if (flushedContexts > 0) {
            logger.debug("Flushed {} dirty contexts ({} new elements) to database",
                        flushedContexts, flushedElements);
        }
    }

    /**
     * Internal helper to flush only new elements for a context.
     * Returns the number of elements flushed.
     */
    private int flushContextInternal(SessionId sessionId, Context context) {
        int lastSaved = lastSavedSequence.getOrDefault(sessionId, -1);
        List<ContextElement> allElements = context.getElements();
        int currentSize = allElements.size();

        // Nothing new to save
        if (currentSize <= lastSaved + 1) {
            return 0;
        }

        // Get only new elements since last save
        List<ContextElement> newElements = allElements.subList(lastSaved + 1, currentSize);

        // Append new elements starting at the next sequence number
        database.appendElements(sessionId, newElements, lastSaved + 1);

        // Update last saved sequence
        lastSavedSequence.put(sessionId, currentSize - 1);

        return newElements.size();
    }

    /**
     * Flush all active contexts to database, regardless of dirty status.
     * Use this for explicit save-all operations (e.g., on shutdown, session switch).
     * Only saves new elements that haven't been persisted yet.
     */
    public void flushAll() {
        if (database == null) {
            return;
        }

        int flushedContexts = 0;
        int flushedElements = 0;

        for (Map.Entry<SessionId, Context> entry : activeContexts.entrySet()) {
            int saved = flushContextInternal(entry.getKey(), entry.getValue());
            if (saved > 0) {
                flushedContexts++;
                flushedElements += saved;
            }
        }

        dirtyContexts.clear();

        if (flushedContexts > 0) {
            logger.info("Flushed all contexts: {} contexts, {} new elements",
                       flushedContexts, flushedElements);
        }
    }

    /**
     * Flush a specific session's context to database immediately.
     * Useful for explicit save points (e.g., session switch).
     * Only saves new elements that haven't been persisted yet.
     */
    public void flushContext(SessionId sessionId) {
        if (database == null) {
            return;
        }

        Context context = activeContexts.get(sessionId);
        if (context != null) {
            int saved = flushContextInternal(sessionId, context);
            dirtyContexts.remove(sessionId);
            if (saved > 0) {
                logger.debug("Flushed context to database: {} ({} new elements)", sessionId, saved);
            }
        }
    }


    /**
     * Load context from cache or database.
     * Write-through cache pattern: Check cache first, then database.
     */
    public Context loadContext(SessionId sessionId) {
        // Check cache first
        Context cached = activeContexts.get(sessionId);
        if (cached != null) {
            return cached;
        }

        // Cache miss - try database
        if (database != null) {
            Optional<Context> fromDb = database.load(sessionId);
            if (fromDb.isPresent()) {
                Context context = fromDb.get();
                activeContexts.put(sessionId, context);
                // Track that all loaded elements are already persisted
                int elementCount = context.getElements().size();
                if (elementCount > 0) {
                    lastSavedSequence.put(sessionId, elementCount - 1);
                }
                logger.debug("Loaded context from database: {} ({} elements)", sessionId, elementCount);
                return context;
            }
        }

        // Not in cache or database - create new
        Context context = new Context(sessionId);
        activeContexts.put(sessionId, context);
        return context;
    }

    /**
     * Save context to cache and mark dirty for background flush.
     * Use {@link #flushContext(SessionId)} for immediate persistence.
     */
    public void saveContext(SessionId sessionId, Context context, ContextLimits limits) {
        compactIfNeeded(context, limits);
        activeContexts.put(sessionId, context);
        markDirty(sessionId);
    }

    /**
     * Append element to context and mark dirty for background flush.
     * Context will be persisted on next flush cycle (every 30s).
     */
    public void append(SessionId sessionId, ContextElement element, ContextLimits limits) {
        Context context = loadContext(sessionId);
        context.add(element);
        compactIfNeeded(context, limits);
        markDirty(sessionId);
    }

    /**
     * Retrieve context by session ID (treat as "archived" lookup).
     */
    public Optional<Context> retrieveArchivedContext(String key) {
        if (database == null) {
            return Optional.empty();
        }

        // Try to parse as SessionId
        try {
            SessionId sessionId = SessionId.of(key);
            return database.load(sessionId);
        } catch (IllegalArgumentException e) {
            logger.debug("Could not parse key as SessionId: {}", key);
            return Optional.empty();
        }
    }

    /**
     * Archive context (saves all elements to database).
     * This is a full save operation that ensures all elements are persisted.
     */
    public void archiveContext(String key, Context context) {
        if (database == null) {
            logger.warn("Cannot archive context - database not initialized");
            return;
        }

        // Save all elements to database
        SessionId sessionId = context.getId();
        List<ContextElement> elements = context.getElements();

        if (!elements.isEmpty()) {
            database.appendElements(sessionId, elements, 0);
            lastSavedSequence.put(sessionId, elements.size() - 1);
            logger.debug("Archived context: {} ({} elements)", sessionId, elements.size());
        }
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

    // === Display methods for context operations ===

    public void printStatus(IOManager io, Context context, ContextLimits limits) {
        var stats = getStats(context, limits);
        io.print("Context Status:\n");
        io.print("  " + stats.toSummary() + "\n");
    }

    public void printCompact(IOManager io, Context context, ContextLimits limits) {
        int beforeTokens = context.totalEstimatedTokens();
        int beforeElements = context.getElements().size();

        forceCompact(context, limits);
        markDirty(context.getId());

        int afterTokens = context.totalEstimatedTokens();
        int afterElements = context.getElements().size();

        io.print(String.format(
            "Context compacted: %d → %d elements, %d → %d tokens (saved %d tokens)\n",
            beforeElements, afterElements,
            beforeTokens, afterTokens,
            beforeTokens - afterTokens
        ));
    }

    public void printClear(IOManager io, Context context) {
        int elementCount = context.getElements().size();
        context.setElements(List.of());
        markDirty(context.getId());
        io.print("Context cleared. Removed " + elementCount + " elements.\n");
    }

    public void printArchive(IOManager io, Context context, String key) {
        if (key == null || key.isBlank()) {
            io.print("Usage: /context archive <key>\n");
            return;
        }

        archiveContext(key, context);
        io.print("Context archived with key: " + key + " (" +
                            context.getElements().size() + " elements, " +
                            context.totalEstimatedTokens() + " tokens)\n");
    }

    public void printLoad(IOManager io, SessionId sessionId, String key, ContextLimits limits) {
        if (key == null || key.isBlank()) {
            io.print("Usage: /context load <key>\n");
            return;
        }

        var archived = retrieveArchivedContext(key);
        if (archived.isEmpty()) {
            io.print("No archived context found for key: " + key + "\n");
            return;
        }

        String summaryText = "Loaded context '" + key + "' with " +
            archived.get().getElements().size() + " elements.";

        var summary = new ContextElement.Summary(
            summaryText, key, archived.get().getElements()
        );

        append(sessionId, summary, limits);
        io.print(summaryText + "\n");
    }

    public void printHistory(IOManager io, Context context, int limit) {
        List<ContextElement> elements = context.getElements();

        if (elements.isEmpty()) {
            io.print("No conversation history\n");
            return;
        }

        int size = elements.size();
        int start = Math.max(0, size - limit);
        io.print("Recent conversation history (last " + (size - start) + " messages):\n");
        io.print("─".repeat(60) + "\n");

        for (int i = start; i < size; i++) {
            ContextElement element = elements.get(i);
            String role = ContextElement.roleName(element);

            String content = element.content();
            String preview = content.length() > 100 ? content.substring(0, 97) + "..." : content;
            io.print(String.format("[%d] %s: %s\n", i + 1, role, preview));
        }
        io.print("─".repeat(60) + "\n");
    }

    public void printSearchHistory(IOManager io, Context context, String query) {
        List<ContextElement> elements = context.getElements();

        String lowerQuery = query.toLowerCase();
        java.util.List<Integer> matches = new java.util.ArrayList<>();

        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).content().toLowerCase().contains(lowerQuery)) {
                matches.add(i);
            }
        }

        if (matches.isEmpty()) {
            io.print("No matches found for: " + query + "\n");
            return;
        }

        io.print("Found " + matches.size() + " matches for '" + query + "':\n");
        io.print("─".repeat(60) + "\n");

        for (int idx : matches) {
            ContextElement element = elements.get(idx);
            String role = ContextElement.roleName(element);
            io.print(String.format("[%d] %s: %s\n", idx + 1, role, element.content()));
            io.print("─".repeat(60) + "\n");
        }
    }

    // === Session Management ===

    /**
     * Evict a session from memory cache.
     * Context remains in database and can be reloaded later.
     */
    public void evictFromCache(SessionId sessionId) {
        Context removed = activeContexts.remove(sessionId);
        lastSavedSequence.remove(sessionId);
        dirtyContexts.remove(sessionId);
        if (removed != null) {
            logger.debug("Evicted session from cache: {} ({} elements)", sessionId, removed.getElements().size());
        }
    }

    /**
     * Delete a session from both cache and database.
     * This is permanent and cannot be undone.
     */
    public void deleteSession(SessionId sessionId) {
        activeContexts.remove(sessionId);
        lastSavedSequence.remove(sessionId);
        dirtyContexts.remove(sessionId);

        if (database != null) {
            database.delete(sessionId);
            logger.debug("Deleted session: {}", sessionId);
        }
    }

    /**
     * List all sessions in the database.
     */
    public List<SessionId> listSessions() {
        if (database == null) {
            return List.of();
        }
        return database.listSessions();
    }
}
