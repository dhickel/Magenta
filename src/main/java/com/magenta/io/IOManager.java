package com.magenta.io;

import com.magenta.io.terminal.Command;
import com.magenta.io.terminal.CommandSet;
import com.magenta.security.SecurityFilter;

import java.util.Optional;

/**
 * Abstract base class for I/O management across different contexts (Terminal, Internal, etc.).
 * Provides common fields and default implementations, subclasses provide context-specific behavior.
 */
public abstract class IOManager implements AutoCloseable {

    protected InputPipe inputPipe;
    protected OutputPipe outputPipe;

    protected IOManager() {
    }

    // === Accessor Methods ===

    public InputPipe inputPipe() {
        return inputPipe;
    }

    public OutputPipe outputPipe() {
        return outputPipe;
    }

    // === Primary I/O ===

    /**
     * Read raw input and return as ReadResult.Input.
     */
    public ReadResult.Input read(String prompt) {
        String raw = inputPipe.take(prompt);
        return ReadResult.input(raw);
    }

    /**
     * Read input with composed security filtering and command detection.
     *
     * @param prompt The prompt to display
     * @param securityFilter Security filter to apply
     * @param commands Command set for parsing
     * @return ReadResult indicating input, command, or blocked
     */
    public ReadResult read(String prompt, SecurityFilter securityFilter, CommandSet commands) {
        // 1. Read raw input
        String raw = inputPipe.take(prompt);
        if (raw.isEmpty()) {
            return ReadResult.input(raw);
        }

        // 2. Apply security filter
        Optional<String> blocked = securityFilter.inputFilter().apply(raw, this);
        if (blocked.isPresent()) {
            return ReadResult.blocked(raw, blocked.get());
        }

        // 3. Check for command
        Optional<Command> cmd = commands.parse(raw);
        if (cmd.isPresent()) {
            return ReadResult.cmd(cmd.get(), raw);
        }

        // 4. Regular input
        return ReadResult.input(raw);
    }

    // === Output Methods ===

    /**
     * Print text (caller controls newlines).
     */
    public void print(String text) {
        outputPipe.get(text);
    }

    /**
     * Print text with newline.
     */
    public void println(String text) {
        outputPipe.println(text);
    }

    /**
     * Print colored text (caller controls newlines).
     * Default ignores color. Terminal implementations override.
     */
    public void print(String text, int colorCode) {
        print(text);
    }

    /**
     * Print colored text with newline.
     */
    public void println(String text, int colorCode) {
        print(text + "\n", colorCode);
    }

    /**
     * Print styled system message with newline.
     */
    public void printStyled(String text, OutputStyle style) {
        println(text);  // Default ignores style
    }

    /**
     * Print error message (convenience).
     */
    public void error(String text) {
        printStyled(text, OutputStyle.ERROR);
    }

    /**
     * Print info message (convenience).
     */
    public void info(String text) {
        printStyled(text, OutputStyle.INFO);
    }

    // === Configuration ===

    /**
     * Configure the input cursor/prompt.
     */
    public abstract void setCursor(String cursor, Integer cursorColor);

    /**
     * Create a response handler for streaming chat responses.
     */
    public abstract ResponseHandler createResponseHandler(Integer agentColor, int delayMs);

    @Override
    public void close() throws Exception {
        // Default no-op, subclasses can override
    }
}
