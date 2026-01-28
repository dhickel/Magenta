package com.magenta.io;

public sealed interface Command {

    record Exit() implements Command {}
    record Help() implements Command {}
    record Clear() implements Command {}
    record History() implements Command {}
    record Unknown(String raw) implements Command {}

    static Command parse(String input) {
        if (input == null || !input.startsWith("/")) {
            return null;
        }

        String cmd = input.substring(1).trim().toLowerCase();
        String[] parts = cmd.split("\\s+", 2);

        return switch (parts[0]) {
            case "exit", "quit", "q" -> new Exit();
            case "help", "?" -> new Help();
            case "clear", "cls" -> new Clear();
            case "history" -> new History();
            default -> new Unknown(input);
        };
    }
}
