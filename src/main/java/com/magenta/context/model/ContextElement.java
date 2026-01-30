package com.magenta.context.model;

import dev.langchain4j.data.message.*;

import java.util.Collections;
import java.util.List;

/**
 * ADT representing an element of context.
 * Can be a message, a system prompt, a summary, etc.
 */
public sealed interface ContextElement permits
        ContextElement.System,
        ContextElement.User,
        ContextElement.Assistant,
        ContextElement.Tool,
        ContextElement.Summary {

    String content();
    ChatMessage compile();
    int estimatedTokens();

    record System(String content) implements ContextElement {
        @Override
        public ChatMessage compile() {
            return new SystemMessage(content);
        }

        @Override
        public int estimatedTokens() {
            return content.length() / 4;
        }
    }

    record User(String content) implements ContextElement {
        @Override
        public ChatMessage compile() {
            return new UserMessage(content);
        }

        @Override
        public int estimatedTokens() {
            return content.length() / 4;
        }
    }

    record Assistant(String content) implements ContextElement {
        @Override
        public ChatMessage compile() {
            return new AiMessage(content);
        }

        @Override
        public int estimatedTokens() {
            return content.length() / 4;
        }
    }

    record Tool(String toolName, String content) implements ContextElement {
        @Override
        public ChatMessage compile() {
            return new UserMessage("Tool '" + toolName + "' output: " + content);
        }

        @Override
        public int estimatedTokens() {
            return (toolName.length() + content.length()) / 4;
        }
    }

    /**
     * Represents a summary of a larger context.
     * @param summary The summary text.
     * @param originalContextKey The key/ID to lookup the original full context.
     * @param originalElements The list of elements that were summarized.
     */
    record Summary(String summary, String originalContextKey, List<ContextElement> originalElements) implements ContextElement {
        public Summary(String summary, String originalContextKey, List<ContextElement> originalElements) {
            this.summary = summary;
            this.originalContextKey = originalContextKey;
            this.originalElements = originalElements == null ? Collections.emptyList() : List.copyOf(originalElements);
        }
        
        // Convenience constructor for backward compatibility or when elements aren't available locally
        public Summary(String summary, String originalContextKey) {
            this(summary, originalContextKey, Collections.emptyList());
        }

        @Override
        public String content() {
            return summary;
        }

        @Override
        public ChatMessage compile() {
            return new SystemMessage("Context Summary: " + summary);
        }

        @Override
        public int estimatedTokens() {
            return summary.length() / 4;
        }
    }
}