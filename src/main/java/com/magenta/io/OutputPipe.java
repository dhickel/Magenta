package com.magenta.io;

/**
 * Functional interface for writing output messages.
 */
@FunctionalInterface
public interface OutputPipe {
    /**
     * Print a message (caller controls newlines).
     */
    void print(Message message);

    /**
     * Convenience: print a string (wraps in Message.output).
     */
    default void print(String text) {
        print(Message.output(text));
    }
}
