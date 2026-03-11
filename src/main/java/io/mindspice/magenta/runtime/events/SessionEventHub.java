package io.mindspice.magenta.runtime.events;

import io.mindspice.magenta.runtime.session.SessionHandle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SessionEventHub {

    private final Consumer<String> diagnosticsSink;
    private final ConcurrentMap<UUID, LinkedHashMap<UUID, EventListenerBinding<? extends SessionEvent>>> listenersBySession
            = new ConcurrentHashMap<>();

    public SessionEventHub(Consumer<String> diagnosticsSink) {
        this.diagnosticsSink = diagnosticsSink == null ? ignored -> {} : diagnosticsSink;
    }

    public <T extends SessionEvent> SessionEventListenerHandle on(
            SessionHandle handle,
            Class<T> eventType,
            Consumer<T> listener
    ) {
        return on(handle, eventType, ignored -> true, listener);
    }

    public <T extends SessionEvent> SessionEventListenerHandle on(
            SessionHandle handle,
            Class<T> eventType,
            Predicate<T> predicate,
            Consumer<T> listener
    ) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(listener, "listener");

        UUID listenerId = UUID.randomUUID();
        LinkedHashMap<UUID, EventListenerBinding<? extends SessionEvent>> bindings =
                listenersBySession.computeIfAbsent(handle.sessionId(), ignored -> new LinkedHashMap<>());
        synchronized (bindings) {
            bindings.put(listenerId, new EventListenerBinding<>(eventType, predicate, listener));
        }
        return new SessionEventListenerHandle(handle.sessionId(), listenerId);
    }

    public void off(SessionEventListenerHandle handle) {
        if (handle == null) {
            return;
        }
        LinkedHashMap<UUID, EventListenerBinding<? extends SessionEvent>> bindings = listenersBySession.get(handle.sessionId());
        if (bindings == null) {
            return;
        }
        synchronized (bindings) {
            bindings.remove(handle.listenerId());
            if (bindings.isEmpty()) {
                listenersBySession.remove(handle.sessionId());
            }
        }
    }

    public void pruneSession(SessionHandle handle) {
        if (handle == null) {
            return;
        }
        listenersBySession.remove(handle.sessionId());
    }

    public void emit(SessionEvent event) {
        if (event == null) {
            return;
        }
        LinkedHashMap<UUID, EventListenerBinding<? extends SessionEvent>> bindings =
                listenersBySession.get(event.sessionHandle().sessionId());
        if (bindings == null || bindings.isEmpty()) {
            return;
        }

        List<Map.Entry<UUID, EventListenerBinding<? extends SessionEvent>>> snapshot;
        synchronized (bindings) {
            snapshot = new ArrayList<>(bindings.entrySet());
        }

        for (Map.Entry<UUID, EventListenerBinding<? extends SessionEvent>> entry : snapshot) {
            EventListenerBinding<? extends SessionEvent> binding = entry.getValue();
            if (!binding.eventType().isInstance(event)) {
                continue;
            }
            dispatch(event, entry.getKey(), binding);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends SessionEvent> void dispatch(
            SessionEvent event,
            UUID listenerId,
            EventListenerBinding<T> binding
    ) {
        T typedEvent = (T) event;
        try {
            if (!binding.predicate().test(typedEvent)) {
                return;
            }
            binding.listener().accept(typedEvent);
        } catch (Throwable throwable) {
            diagnosticsSink.accept("session_event_listener_failure sessionId="
                    + event.sessionHandle().sessionId() + " listenerId=" + listenerId
                    + " eventType=" + event.getClass().getSimpleName()
                    + " error=" + throwable.getClass().getSimpleName());
        }
    }

    private record EventListenerBinding<T extends SessionEvent>(
            Class<T> eventType,
            Predicate<T> predicate,
            Consumer<T> listener
    ) {
    }
}
