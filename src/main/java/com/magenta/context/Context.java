package com.magenta.context;

import com.magenta.session.SessionId;
import dev.langchain4j.data.message.ChatMessage;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Represents a conversation context containing multiple elements.
 * Mutable container, thread-safe.
 */
public class Context {
    private final List<ContextElement> elements;
    private final SessionId id;

    public Context(SessionId id) {
        this.id = id;
        this.elements = new CopyOnWriteArrayList<>();
    }

    public Context(SessionId id, List<ContextElement> elements) {
        this.id = id;
        this.elements = new CopyOnWriteArrayList<>(elements);
    }

    public SessionId getId() {
        return id;
    }

    public List<ContextElement> getElements() {
        return Collections.unmodifiableList(elements);
    }

    public void add(ContextElement element) {
        elements.add(element);
    }

    public void addAll(List<ContextElement> newElements) {
        elements.addAll(newElements);
    }
    
    public void setElements(List<ContextElement> newElements) {
        elements.clear();
        elements.addAll(newElements);
    }

    public List<ChatMessage> compile() {
        return elements.stream()
                .map(ContextElement::compile)
                .collect(Collectors.toList());
    }

    public int totalEstimatedTokens() {
        return elements.stream()
                .mapToInt(ContextElement::estimatedTokens)
                .sum();
    }
}