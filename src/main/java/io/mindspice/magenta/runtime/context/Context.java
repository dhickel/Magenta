package io.mindspice.magenta.runtime.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class Context {
    public sealed interface Mutation permits Mutation.Append, Mutation.AppendAll, Mutation.ReplaceAll {
        record Append(ContextElement message) implements Mutation {
        }

        record AppendAll(List<ContextElement> messages) implements Mutation {
            public AppendAll {
                messages = messages == null ? List.of() : List.copyOf(messages);
            }
        }

        record ReplaceAll(List<ContextElement> messages) implements Mutation {
            public ReplaceAll {
                messages = messages == null ? List.of() : List.copyOf(messages);
            }
        }
    }

    @FunctionalInterface
    public interface MutationListener {
        void onMutation(Mutation mutation);
    }

    private final List<ContextElement> messages = new ArrayList<>();
    private final Instant createdAt = Instant.now();
    private volatile MutationListener mutationListener = ignored -> {};
    private volatile Instant updatedAt = createdAt;

    public Context() {
    }

    public Context(MutationListener mutationListener) {
        this.mutationListener = mutationListener == null ? ignored -> {} : mutationListener;
    }

    public void setMutationListener(MutationListener mutationListener) {
        this.mutationListener = mutationListener == null ? ignored -> {} : mutationListener;
    }

    public void append(ContextElement message) {
        MutationListener listener;
        synchronized (this) {
            messages.add(message);
            updatedAt = Instant.now();
            listener = mutationListener;
        }
        listener.onMutation(new Mutation.Append(message));
    }

    public void appendAll(List<ContextElement> messageList) {
        List<ContextElement> safeList = messageList == null ? List.of() : List.copyOf(messageList);
        MutationListener listener;
        synchronized (this) {
            messages.addAll(safeList);
            updatedAt = Instant.now();
            listener = mutationListener;
        }
        listener.onMutation(new Mutation.AppendAll(safeList));
    }

    public void replaceAll(List<ContextElement> messageList) {
        List<ContextElement> safeList = messageList == null ? List.of() : List.copyOf(messageList);
        MutationListener listener;
        synchronized (this) {
            messages.clear();
            messages.addAll(safeList);
            updatedAt = Instant.now();
            listener = mutationListener;
        }
        listener.onMutation(new Mutation.ReplaceAll(safeList));
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
