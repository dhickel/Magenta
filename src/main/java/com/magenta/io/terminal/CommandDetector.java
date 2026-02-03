package com.magenta.io.terminal;

import java.util.Optional;

/**
 * Functional interface for detecting commands from raw input.
 * Returns Optional<Command> if input is a command, empty otherwise.
 */
@FunctionalInterface
public interface CommandDetector {

    /**
     * Detect if the raw input is a command.
     * @param raw the raw input string
     * @return Optional<Command> if command detected, empty otherwise
     */
    Optional<Command> detect(String raw);

    /**
     * Default command detector using Command.tryParse().
     */
    static CommandDetector defaults() {
        return Command::tryParse;
    }
}
