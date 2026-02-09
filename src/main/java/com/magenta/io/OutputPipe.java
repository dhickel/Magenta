package com.magenta.io;

/**
 * Functional interface for writing output.
 * Takes raw strings - styling handled by IOManager methods.
 */
@FunctionalInterface
public interface OutputPipe {
    /**
     * Get Output
     */
    void get(String text);

    /**
     * Print text with newline.
     */
    default void println(String text) {
        get(text + "\n");
    }
}