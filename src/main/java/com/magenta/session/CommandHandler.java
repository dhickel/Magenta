package com.magenta.session;

import com.magenta.io.Command;

/**
 * Functional interface for handling control flow commands.
 * Focuses on non-model interactions like /exit, /help, /clear, etc.
 * Can be used as a lambda for simple cases:
 * <pre>
 * CommandHandler handler = (session, command) -> {
 *     // custom command logic
 * };
 * </pre>
 */
@FunctionalInterface
public interface CommandHandler {

    void handle(Session session, Command command);
}
