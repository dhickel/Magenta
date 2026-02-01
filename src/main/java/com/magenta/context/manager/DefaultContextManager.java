package com.magenta.context.manager;

import com.magenta.context.model.Context;
import com.magenta.context.model.ContextElement;
import com.magenta.context.policy.ContextLimits;
import com.magenta.context.policy.ContextPolicy;
import com.magenta.context.policy.ContextWindowManager;
import com.magenta.context.policy.CompactionStrategy;
import com.magenta.context.store.ContextRepository;
import com.magenta.session.SessionId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultContextManager extends ContextManager {
    private final ContextRepository repository;
    private final Map<SessionId, Context> activeContexts = new ConcurrentHashMap<>();
    private final ContextPolicy legacyPolicy; // Keep for backward compatibility
    private final ContextWindowManager windowManager;

    DefaultContextManager(ContextRepository repository, ContextPolicy legacyPolicy) {
        this.repository = repository;
        this.legacyPolicy = legacyPolicy;
        // Use Truncate strategy by default (simple, deterministic)
        // Can be upgraded to Summarize when model integration is ready
        this.windowManager = new ContextWindowManager(new CompactionStrategy.Truncate());
    }

    @Override
    public Context loadContext(SessionId sessionId) {
        return activeContexts.computeIfAbsent(sessionId, id ->
            repository.load(id.toString()).orElse(new Context(id.toString()))
        );
    }

    @Override
    public void saveContext(SessionId sessionId, Context context, ContextLimits limits) {
        windowManager.compactIfNeeded(context, limits);
        activeContexts.put(sessionId, context);
        // Repository save stubbed - will implement persistence later
        // repository.save(sessionId.toString(), context);
    }

    @Override
    public void append(SessionId sessionId, ContextElement element, ContextLimits limits) {
        Context context = loadContext(sessionId);
        context.add(element);
        windowManager.compactIfNeeded(context, limits);
        // Repository save stubbed - will implement persistence later
        // repository.save(sessionId.toString(), context);
    }

    @Override
    public Optional<Context> retrieveArchivedContext(String key) {
        // Repository load stubbed - will implement persistence later
        // return repository.load(key);
        return Optional.empty();
    }

    @Override
    public void archiveContext(String key, Context context) {
        // Repository save stubbed - will implement persistence later
        // repository.save(key, context);
    }

    /**
     * Get the context window manager for direct access to stats/compaction.
     */
    public ContextWindowManager windowManager() {
        return windowManager;
    }
}