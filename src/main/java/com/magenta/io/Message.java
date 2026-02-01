package com.magenta.io;

import java.time.Instant;

/**
 * ADT for I/O messages with security filtering support.
 * Typed variants: Input, Output, System, Filtered.
 */
public sealed interface Message {

    /**
     * User input message.
     */
    record Input(String content, Instant timestamp) implements Message {
        public Input(String content) {
            this(content, Instant.now());
        }
    }

    /**
     * Agent output message.
     */
    record Output(String content, Integer colorCode) implements Message {
        public Output(String content) {
            this(content, null);
        }
    }

    /**
     * System message with style.
     */
    record System(String content, OutputStyle style) implements Message {
        public System(String content) {
            this(content, OutputStyle.INFO);
        }
    }

    /**
     * Filtered/blocked message with reason and type.
     */
    record Filtered(String original, String reason, FilterType type, Instant timestamp) implements Message {
        public Filtered(String original, String reason, FilterType type) {
            this(original, reason, type, Instant.now());
        }
    }

    enum FilterType { INPUT, OUTPUT, TOOL }

    // === Static Factory Methods ===

    /**
     * Create user input message.
     */
    static Message.Input input(String content) {
        return new Input(content);
    }

    /**
     * Create agent output message.
     */
    static Message.Output output(String content) {
        return new Output(content);
    }

    /**
     * Create agent output with color.
     */
    static Message.Output output(String content, int colorCode) {
        return new Output(content, colorCode);
    }

    /**
     * Create system message.
     */
    static Message.System system(String content) {
        return new System(content);
    }

    /**
     * Create system message with style.
     */
    static Message.System system(String content, OutputStyle style) {
        return new System(content, style);
    }

    /**
     * Create filtered/blocked message.
     */
    static Message.Filtered blocked(String original, String reason, FilterType type) {
        return new Filtered(original, reason, type);
    }

    /**
     * Convenience: wrap any string as Output (for simple cases).
     */
    static Message of(String content) {
        return new Output(content);
    }

    // === Extraction Methods ===

    /**
     * Extract content from any message type.
     */
    default String content() {
        return switch (this) {
            case Input(String content, var ts) -> content;
            case Output(String content, var color) -> content;
            case System(String content, var style) -> content;
            case Filtered(String orig, var reason, var type, var ts) -> orig;
        };
    }

    /**
     * Check if this message was filtered.
     */
    default boolean isFiltered() {
        return this instanceof Filtered;
    }

    /**
     * Get filter reason if filtered, otherwise null.
     */
    default String filterReason() {
        return this instanceof Filtered f ? f.reason() : null;
    }

    /**
     * Get filter type if filtered, otherwise null.
     */
    default FilterType filterType() {
        return this instanceof Filtered f ? f.type() : null;
    }
}
