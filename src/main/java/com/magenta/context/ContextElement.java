package com.magenta.context;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.Collections;
import java.util.List;

/**
 * ADT representing an element of context.
 * Can be a message, a system prompt, a summary, etc.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ContextElement.System.class, name = "System"),
        @JsonSubTypes.Type(value = ContextElement.User.class, name = "User"),
        @JsonSubTypes.Type(value = ContextElement.Agent.class, name = "Agent"),
        @JsonSubTypes.Type(value = ContextElement.Tool.class, name = "Tool"),
        @JsonSubTypes.Type(value = ContextElement.Summary.class, name = "Summary")
})
public sealed interface ContextElement {
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
            return Tokenizer.estimateWithOverhead(content);
        }
    }

    record User(String content) implements ContextElement {
        @Override
        public ChatMessage compile() {
            return new UserMessage(content);
        }

        @Override
        public int estimatedTokens() {
            return Tokenizer.estimateWithOverhead(content);
        }
    }

    record Agent(String content) implements ContextElement {
        @Override
        public ChatMessage compile() {
            return new AiMessage(content);
        }

        @Override
        public int estimatedTokens() {
            return Tokenizer.estimateWithOverhead(content);
        }
    }

    record Tool(String toolName, String content) implements ContextElement {
        @Override
        public ChatMessage compile() {
            return new UserMessage("Tool '" + toolName + "' output: " + content);
        }

        @Override
        public int estimatedTokens() {
            // Include the formatted output in token count
            String fullOutput = "Tool '" + toolName + "' output: " + content;
            return Tokenizer.estimateWithOverhead(fullOutput);
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
            // Include "Context Summary: " prefix in token count
            return Tokenizer.estimateWithOverhead("Context Summary: " + summary);
        }
    }
}