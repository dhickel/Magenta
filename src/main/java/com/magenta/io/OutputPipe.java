package com.magenta.io;

/**
 * Functional interface for writing output.
 * Takes raw strings - styling handled by IOManager methods.
 */
@FunctionalInterface
public interface OutputPipe {
    /**
     * Print text (caller controls newlines).
     */
    void print(String text);

    /**
     * Print text with newline.
     */
    default void println(String text) {
        print(text + "\n");
    }
}
