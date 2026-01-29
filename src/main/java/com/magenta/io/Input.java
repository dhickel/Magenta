package com.magenta.io;


public sealed interface Input  {

    record Cmd(Command command) implements Input {}

    record Msg(String text) implements Input {}


    static Input defaultParser(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Msg(""); // Empty message
        }

        // Try to parse as command
        return Command.tryParse(raw)
                .map(cmd -> (Input) new Cmd(cmd))
                .orElseGet(() -> new Msg(raw));
    }
}
