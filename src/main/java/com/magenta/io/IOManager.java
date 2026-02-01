package com.magenta.io;

import com.magenta.security.SecurityFilter;

/**
 * IOManager coordinates I/O for different contexts (Terminal, Internal, etc.).
 * Enforces all I/O through composable pipes with default methods.
 *
 * Extension Points (implementations provide):
 * - inputPipe(): Raw input reading
 * - outputPipe(): Raw output writing
 * - colorPipe(): Color formatting (or identity if not supported)
 * - securityFilter(): Security filtering
 *
 * All I/O methods are non-overridable defaults that compose the pipes.
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

    /**
     * Provides color formatting capability.
     */
    ColorPipe colorPipe();

    /**
     * Provides security filtering.
     */
    SecurityFilter securityFilter();

    /**
     * Sets the security filter.
     */
    void setSecurityFilter(SecurityFilter filter);

    // === Primary I/O (non-overridable, forced through pipes) ===

    /**
     * Read input with security filtering applied.
     * Retries on filtered input with warning message.
     */
    default String read(String prompt) {
        while (true) {
            Message.Input input = inputPipe().read(prompt);
            Message filtered = securityFilter().inputFilter().apply(input, this);

            if (filtered instanceof Message.Input validInput) {
                return validInput.content();
            } else if (filtered instanceof Message.Filtered f) {
                // Print warning and retry
                print(Message.system("[FILTERED] " + f.reason(), OutputStyle.ERROR));
                continue;  // Loop and ask again
            } else {
                // Unexpected message type - pass through
                return filtered.content();
            }
        }
    }

    /**
     * Print message (caller controls newlines).
     * Applies security filtering automatically.
     */
    default void print(Message message) {
        // Only filter Output messages
        Message toOutput = switch (message) {
            case Message.Output output -> securityFilter().outputFilter().apply(output);
            case Message.Filtered filtered -> filtered;  // Already filtered, pass through
            default -> message;  // Input/System pass through
        };

        // Handle filtered messages
        if (toOutput instanceof Message.Filtered f) {
            // Log but don't print
            java.lang.System.err.println("[SECURITY] Output filtered: " + f.reason());
            return;  // Don't print blocked content
        }

        // Print the message via pipe
        outputPipe().print(toOutput);
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
