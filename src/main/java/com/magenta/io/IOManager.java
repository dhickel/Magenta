package com.magenta.io;

import java.util.function.Function;

/**
 * IOManager coordinates I/O for different contexts (Terminal, Internal, etc.).
 * Pure I/O layer - no security filtering (that's Session's responsibility).
 *
 * Extension Points (implementations provide):
 * - inputPipe(): Raw input reading
 * - outputPipe(): Raw output writing
 *
 * Provides functional composition for message transformations.
 */
public interface IOManager extends AutoCloseable {

    // === Extension Points (implementations must provide) ===

    /**
     * Provides the raw input pipe.
     */
    InputPipe inputPipe();

    /**
     * Provides the raw output pipe.
     */
    OutputPipe outputPipe();

    // === Primary I/O ===

    /**
     * Read raw input string.
     * Simple delegation to inputPipe, no parsing or filtering.
     */
    default String read(String prompt) {
        return inputPipe().read(prompt).content();
    }

    /**
     * Print message with functional composition.
     * Applies transformers in order (e.g., security filter, then coloring).
     */
    default void print(Message message, Function<Message, Message>... transformers) {
        Message current = message;
        for (Function<Message, Message> transformer : transformers) {
            current = transformer.apply(current);

            // Stop if filtered
            if (current instanceof Message.Filtered f) {
                java.lang.System.err.println("[SECURITY] Output filtered: " + f.reason());
                return;
            }
        }

        // Print via pipe
        outputPipe().print(current);
    }

    /**
     * Print message without transformations.
     */
    default void print(Message message) {
        outputPipe().print(message);
    }

    /**
     * Print text (caller controls newlines).
     * Convenience method - wraps in Message.output().
     */
    default void print(String text) {
        print(Message.output(text));
    }

    /**
     * Print colored text (caller controls newlines).
     * Convenience method - wraps in Message.output() with color.
     */
    default void print(String text, int colorCode) {
        print(Message.output(text, colorCode));
    }

    // === Configuration ===

    /**
     * Configure the input cursor/prompt.
     */
    void setCursor(String cursor, Integer cursorColor);

    /**
     * Create a response handler for streaming chat responses.
     */
    ResponseHandler createResponseHandler(Integer agentColor, int delayMs);

    @Override
    default void close() throws Exception {
        // Default no-op, implementations can override
    }
}
