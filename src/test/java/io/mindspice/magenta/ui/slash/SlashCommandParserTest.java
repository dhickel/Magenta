package io.mindspice.magenta.ui.slash;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlashCommandParserTest {

    private final SlashCommandParser parser = new SlashCommandParser();

    @Test
    void parsesSlashCommandWithQuotedArgs() {
        SlashCommandParseResult result = parser.parse("/event \"hello world\"");

        assertThat(result).isInstanceOf(SlashCommandParseResult.Parsed.class);
        SlashCommandInvocation invocation = ((SlashCommandParseResult.Parsed) result).invocation();
        assertThat(invocation.name()).isEqualTo("event");
        assertThat(invocation.args()).containsExactly("hello world");
    }

    @Test
    void parsesThreeArgWorkspaceWindowCommandAtLimit() {
        SlashCommandParseResult result = parser.parse("/window add file \"src/main/java\"");

        assertThat(result).isInstanceOf(SlashCommandParseResult.Parsed.class);
        SlashCommandInvocation invocation = ((SlashCommandParseResult.Parsed) result).invocation();
        assertThat(invocation.name()).isEqualTo("window");
        assertThat(invocation.args()).containsExactly("add", "file", "src/main/java");
    }

    @Test
    void rejectsMoreThanThreeArgs() {
        SlashCommandParseResult result = parser.parse("/x a b c d");

        assertThat(result).isInstanceOf(SlashCommandParseResult.ParseError.class);
        assertThat(((SlashCommandParseResult.ParseError) result).message()).contains("max 3");
    }

    @Test
    void nonSlashInputIsNotCommand() {
        SlashCommandParseResult result = parser.parse("hello");
        assertThat(result).isInstanceOf(SlashCommandParseResult.NotCommand.class);
    }

    @Test
    void unterminatedQuoteProducesParseError() {
        SlashCommandParseResult result = parser.parse("/workspace switch \"dashboard");

        assertThat(result).isInstanceOf(SlashCommandParseResult.ParseError.class);
        assertThat(((SlashCommandParseResult.ParseError) result).message()).contains("unterminated quote");
    }
}
