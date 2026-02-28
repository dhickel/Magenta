package io.mindspice.magenta.systems.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class Context {
    private final List<SessionMessage> messages = new ArrayList<>();
    private final Instant createdAt = Instant.now();
    private volatile Instant updatedAt = createdAt;

    public synchronized void append(SessionMessage message) {
        messages.add(message);
        updatedAt = Instant.now();
    }

    public synchronized void appendAll(List<SessionMessage> messageList) {
        messages.addAll(messageList);
        updatedAt = Instant.now();
    }

    public synchronized void replaceAll(List<SessionMessage> messageList) {
        messages.clear();
        messages.addAll(messageList);
        updatedAt = Instant.now();
    }

    public synchronized List<SessionMessage> snapshot() {
        return List.copyOf(messages);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
