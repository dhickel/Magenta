package io.mindspice.magenta.ui.slash;

public sealed interface SlashCommandParseResult permits SlashCommandParseResult.NotCommand, SlashCommandParseResult.Parsed, SlashCommandParseResult.ParseError {

    record NotCommand() implements SlashCommandParseResult {}

    record Parsed(SlashCommandInvocation invocation) implements SlashCommandParseResult {}

    record ParseError(String message) implements SlashCommandParseResult {
        public ParseError {
            message = message == null ? "parse_error" : message;
        }
    }
}
