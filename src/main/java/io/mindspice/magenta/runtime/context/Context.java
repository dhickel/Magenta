package io.mindspice.magenta.runtime.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class Context {
    private final List<ContextElement> messages = new ArrayList<>();
    private final Instant createdAt = Instant.now();
    private volatile Instant updatedAt = createdAt;

    public synchronized void append(ContextElement message) {
        messages.add(message);
        updatedAt = Instant.now();
    }

    public synchronized void appendAll(List<ContextElement> messageList) {
        messages.addAll(messageList);
        updatedAt = Instant.now();
    }

    public synchronized void replaceAll(List<ContextElement> messageList) {
        messages.clear();
        messages.addAll(messageList);
        updatedAt = Instant.now();
    }

    public synchronized List<ContextElement> snapshot() {
        return List.copyOf(messages);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
