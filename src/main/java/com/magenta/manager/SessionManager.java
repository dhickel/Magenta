package com.magenta.manager;

import com.magenta.session.AgentSession;
import com.magenta.session.SessionAlias;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic session coordinator: create, get, list, close sessions.
 * No terminal dependency. Framework-friendly.
 */
public class SessionManager implements AutoCloseable {

    private final ConcurrentHashMap<SessionAlias, AgentSession> sessions = new ConcurrentHashMap<>();

    public SessionManager() {
    }

    public void registerSession(AgentSession session) {
        if (sessions.containsKey(session.alias())) {
            throw new IllegalArgumentException("Session already exists: " + session.alias());
        }
        sessions.put(session.alias(), session);
    }

    public AgentSession getSession(SessionAlias alias) {
        return sessions.get(alias);
    }

    public Map<SessionAlias, AgentSession> allSessions() {
        return Map.copyOf(sessions);
    }

    public void removeSession(SessionAlias alias) {
        sessions.remove(alias);
    }

    @Override
    public void close() throws Exception {
        for (AgentSession session : sessions.values()) {
            session.close();
        }
        sessions.clear();
    }
}
