package com.magenta.io;

/**
 * Functional interface for reading input.
 * Returns raw string - timestamp added by IOManager when creating ReadResult.
 */
@FunctionalInterface
public interface InputPipe {
    /**
     * Read input from the source.
     * @param prompt The prompt to display (may be ignored by some implementations)
     * @return The raw input string
     */
    String take(String prompt);
}
