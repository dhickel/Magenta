package io.mindspice.magenta.runtime.session;

import java.util.UUID;

public final class SessionQueueFullException extends RuntimeException {
    private final UUID sessionId;
    private final int capacity;

    public SessionQueueFullException(UUID sessionId, int capacity) {
        super("queue_full: session input queue is full for session " + sessionId + " (capacity=" + capacity + ")");
        this.sessionId = sessionId;
        this.capacity = capacity;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public int capacity() {
        return capacity;
    }
}
