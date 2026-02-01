package com.magenta.context.manager;

import com.magenta.context.model.Context;
import com.magenta.context.model.ContextElement;
import com.magenta.context.policy.ContextLimits;
import com.magenta.context.policy.ContextPolicy;
import com.magenta.context.store.ContextRepository;
import com.magenta.session.SessionId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultContextManager extends ContextManager {
    private final ContextRepository repository;
    private final Map<SessionId, Context> activeContexts = new ConcurrentHashMap<>();
    private final ContextPolicy compactionPolicy;

    DefaultContextManager(ContextRepository repository, ContextPolicy compactionPolicy) {
        this.repository = repository;
        this.compactionPolicy = compactionPolicy;
    }

    @Override
    public Context loadContext(SessionId sessionId) {
        return activeContexts.computeIfAbsent(sessionId, id -> 
            repository.load(id.toString()).orElse(new Context(id.toString()))
        );
    }

    @Override
    public void saveContext(SessionId sessionId, Context context, ContextLimits limits) {
        compactionPolicy.apply(context, limits);
        activeContexts.put(sessionId, context);
        repository.save(sessionId.toString(), context);
    }

    @Override
    public void append(SessionId sessionId, ContextElement element, ContextLimits limits) {
        Context context = loadContext(sessionId);
        context.add(element);
        compactionPolicy.apply(context, limits);
        repository.save(sessionId.toString(), context);
    }

    @Override
    public Optional<Context> retrieveArchivedContext(String key) {
        return repository.load(key);
    }

    @Override
    public void archiveContext(String key, Context context) {
        repository.save(key, context);
    }
}