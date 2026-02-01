package com.magenta.io;

/**
 * Functional interface for reading input.
 */
@FunctionalInterface
public interface InputPipe {
    /**
     * Read input and return as Message.Input.
     */
    Message.Input read(String prompt);
}
