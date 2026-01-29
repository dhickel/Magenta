package com.magenta.io;

import java.util.Optional;


public sealed interface Command {

    record Exit() implements Command {}
    record Help() implements Command {}
    record Clear() implements Command {}
    record History() implements Command {}
    record Agent(String agentName) implements Command {}
    record Sessions() implements Command {}
    record Agents() implements Command {}
    record Unknown(String raw) implements Command {}


    static Optional<Command> tryParse(String input) {
        if (input == null || input.isBlank() || !input.startsWith("/")) {
            return Optional.empty();
        }

        String cmd = input.substring(1).trim();
        String[] parts = cmd.split("\\s+", 2);
        String commandName = parts[0].toLowerCase();

        Command command = switch (commandName) {
            case "exit", "quit", "q" -> new Exit();
            case "help", "?" -> new Help();
            case "clear", "cls" -> new Clear();
            case "history" -> new History();
            case "agent" -> {
                if (parts.length < 2 || parts[1].isBlank()) {
                    yield new Unknown(input); // Missing agent name
                }
                yield new Agent(parts[1].trim());
            }
            case "sessions" -> new Sessions();
            case "agents" -> new Agents();
            default -> new Unknown(input);
        };

        return Optional.of(command);
    }
}
