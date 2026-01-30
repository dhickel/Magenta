package com.magenta.context.manager;

import com.magenta.context.model.Context;
import com.magenta.context.model.ContextElement;
import com.magenta.context.policy.ContextPolicy;
import com.magenta.context.store.ContextRepository;
import com.magenta.session.SessionId;

import java.util.Optional;

public abstract class ContextManager {
    private static ContextManager instance;

    public static void initialize(ContextRepository repository, ContextPolicy policy) {
        if (instance != null) {
            throw new IllegalStateException("ContextManager already initialized");
        }
        instance = new DefaultContextManager(repository, policy);
    }

    public static ContextManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ContextManager not initialized");
        }
        return instance;
    }

    public abstract Context loadContext(SessionId sessionId);
    public abstract void saveContext(SessionId sessionId, Context context);
    public abstract void append(SessionId sessionId, ContextElement element);
    public abstract Optional<Context> retrieveArchivedContext(String key);
    public abstract void archiveContext(String key, Context context);
}
