package io.mindspice.magenta.ui.slash;

import java.util.ArrayList;
import java.util.List;

public final class SlashCommandParser {

    private static final int MAX_ARGS = 3;

    public SlashCommandParseResult parse(String line) {
        if (line == null) {
            return new SlashCommandParseResult.NotCommand();
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("/")) {
            return new SlashCommandParseResult.NotCommand();
        }

        String body = trimmed.substring(1).trim();
        if (body.isEmpty()) {
            return new SlashCommandParseResult.ParseError("empty command");
        }

        List<String> tokens = lex(body);
        if (tokens == null) {
            return new SlashCommandParseResult.ParseError("unterminated quote");
        }
        if (tokens.isEmpty()) {
            return new SlashCommandParseResult.ParseError("empty command");
        }

        String name = normalize(tokens.getFirst());
        List<String> args = tokens.subList(1, tokens.size());
        if (args.size() > MAX_ARGS) {
            return new SlashCommandParseResult.ParseError("too many args: max 3");
        }

        return new SlashCommandParseResult.Parsed(new SlashCommandInvocation(name, args));
    }

    private List<String> lex(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }

            if (Character.isWhitespace(c) && !inQuote) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(c);
        }

        if (inQuote) {
            return null;
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }

        return List.copyOf(tokens);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
