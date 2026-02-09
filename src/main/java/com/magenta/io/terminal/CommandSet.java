package com.magenta.io.terminal;

import com.magenta.io.IOManager;
import com.magenta.session.AgentSession;
import org.jline.reader.Candidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Composable command set interface.
 * Allows combining multiple command sets into a single unified set.
 *
 * Example:
 * {@code
 * CommandSet system = SystemCommands.commands();
 * CommandSet agent = agentCommands();
 * CommandSet combined = system.composedWith(agent);
 * }
 */
public interface CommandSet {

    /**
     * Get all commands in this set.
     */
    List<Command> commands();

    /**
     * Compose this command set with another.
     * Returns a new CommandSet that contains commands from both sets.
     *
     * @param other The command set to compose with
     * @return A new composed command set
     */
    default CommandSet composedWith(CommandSet other) {
        return new Composed(List.of(this, other));
    }

    // === Command operations ===

    /**
     * Parse raw input to find a matching command.
     * Returns the matching command, or an "unknown" command if no match found.
     * Returns empty if input is not a command (doesn't start with / or !).
     */
    default Optional<Command> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        if (!raw.startsWith("/") && !raw.startsWith("!")) {
            // Not a command
            return Optional.empty();
        }
        for (Command cmd : commands()) {
            if (cmd.matches(raw)) {
                return Optional.of(cmd);
            }
        }
        return Optional.of(unknown());
    }

    /**
     * Print help for all commands in this set.
     */
    default void printHelp(IOManager io) {
        io.print("Available commands:\n");
        for (Command cmd : commands()) {
            io.print("  /" + cmd.name() + " - " + cmd.description() + "\n");
        }
    }

    /**
     * Generate completions for command input.
     */
    default void complete(AgentSession session, String buffer, List<Candidate> candidates) {
        if (!buffer.startsWith("/")) {
            return;
        }

        String[] parts = buffer.split("\\s+", 2);
        String commandPart = parts[0];

        if (parts.length == 1 && !buffer.endsWith(" ")) {
            // Complete command names
            for (Command cmd : commands()) {
                String token = "/" + cmd.name();
                if (token.startsWith(commandPart)) {
                    candidates.add(new Candidate(
                        token,
                        token,
                        "commands",
                        cmd.description(),
                        null,
                        null,
                        true
                    ));
                }
            }
        } else {
            // Complete arguments for the matched command
            String name = commandPart.startsWith("/") ? commandPart.substring(1) : commandPart;
            Command target = null;
            for (Command cmd : commands()) {
                if (cmd.name().equals(name)) {
                    target = cmd;
                    break;
                }
            }
            if (target != null && session != null) {
                candidates.addAll(target.completions());
            }
        }
    }

    /**
     * Create an "unknown command" instance.
     */
    static Command unknown() {
        return Command.of(
            "unknown",
            "Unknown command",
            raw -> false,
            (session, raw) -> session.io().print("Unknown command: " + raw + "\n")
        );
    }

    // === Factory methods ===

    /**
     * Create a CommandSet from individual commands.
     */
    static CommandSet of(Command... commands) {
        return new Simple(List.of(commands));
    }

    /**
     * Create a CommandSet from a list of commands.
     */
    static CommandSet of(List<Command> commands) {
        return new Simple(commands != null ? commands : List.of());
    }

    /**
     * Create an empty CommandSet.
     */
    static CommandSet empty() {
        return new Simple(List.of());
    }

    // === Nested implementations ===

    /**
     * Simple command set backed by a list.
     */
    record Simple(List<Command> commands) implements CommandSet {
        public Simple {
            commands = List.copyOf(commands); // Defensive copy
        }
    }

    /**
     * Composed command set that combines multiple sets.
     * Flattens nested Composed sets to keep the structure shallow.
     */
    final class Composed implements CommandSet {
        private final List<CommandSet> sets;
        private final List<Command> cachedCommands;

        Composed(List<CommandSet> sets) {
            this.sets = List.copyOf(sets);
            this.cachedCommands = sets.stream()
                .flatMap(set -> set.commands().stream())
                .toList();
        }

        @Override
        public List<Command> commands() {
            return cachedCommands;
        }

        @Override
        public CommandSet composedWith(CommandSet other) {
            // Flatten if other is also Composed
            if (other instanceof Composed c) {
                List<CommandSet> newSets = new ArrayList<>(sets);
                newSets.addAll(c.sets);
                return new Composed(newSets);
            }
            List<CommandSet> newSets = new ArrayList<>(sets);
            newSets.add(other);
            return new Composed(newSets);
        }
    }
}
