package com.magenta.io;

import com.magenta.io.terminal.Command;

import java.time.LocalDateTime;

/**
 * Result of IOManager.read() - the single ADT for input processing.
 * Three outcomes: valid input, detected command, or blocked by security.
 */
public sealed interface ReadResult {

    /** Valid user input (passed security, not a command). */
    record Input(String content, LocalDateTime timestamp) implements ReadResult {}

    /** Detected command (passed security, matched command syntax). */
    record Cmd(Command command, String raw, LocalDateTime timestamp) implements ReadResult {}

    /** Blocked by security filter. */
    record Blocked(String original, String reason, LocalDateTime timestamp) implements ReadResult {}

    // === Factory methods ===

    static Input input(String content) {
        return new Input(content, LocalDateTime.now());
    }

    static Cmd cmd(Command command, String raw) {
        return new Cmd(command, raw, LocalDateTime.now());
    }

    static Blocked blocked(String original, String reason) {
        return new Blocked(original, reason, LocalDateTime.now());
    }

    // === Common accessors ===

    default String content() {
        return switch (this) {
            case Input i -> i.content();
            case Cmd c -> c.raw();
            case Blocked b -> b.original();
        };
    }

    default LocalDateTime timestamp() {
        return switch (this) {
            case Input i -> i.timestamp();
            case Cmd c -> c.timestamp();
            case Blocked b -> b.timestamp();
        };
    }

    default boolean isBlocked() {
        return this instanceof Blocked;
    }

    default boolean isCommand() {
        return this instanceof Cmd;
    }

    default boolean isInput() {
        return this instanceof Input;
    }
}
