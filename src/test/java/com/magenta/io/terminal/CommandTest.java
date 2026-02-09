package com.magenta.io.terminal;

import com.magenta.session.SystemCommands;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandTest {

    @Test
    void testParseExit() {
        assertSame(SystemCommands.EXIT, parse("/exit"));
        assertSame(SystemCommands.EXIT, parse("/quit"));
        assertSame(SystemCommands.EXIT, parse("/q"));
    }

    @Test
    void testParseHelp() {
        assertSame(SystemCommands.HELP, parse("/help"));
        assertSame(SystemCommands.HELP, parse("/?"));
    }

    @Test
    void testParseClear() {
        assertSame(SystemCommands.CLEAR, parse("/clear"));
        assertSame(SystemCommands.CLEAR, parse("/cls"));
    }

    @Test
    void testParseAgent() {
        assertSame(SystemCommands.AGENT, parse("/agent helpful"));
    }

    @Test
    void testParseAgentMissingName() {
        assertUnknown("/agent");
        assertUnknown("/agent   ");
    }

    @Test
    void testParseSessionsAgents() {
        assertSame(SystemCommands.SESSIONS, parse("/sessions"));
        assertSame(SystemCommands.AGENTS, parse("/agents"));
    }

    @Test
    void testParseContext() {
        assertSame(SystemCommands.CONTEXT, parse("/context status"));
        assertSame(SystemCommands.CONTEXT, parse("/context"));
        assertSame(SystemCommands.CONTEXT, parse("/context archive mykey"));
    }

    @Test
    void testParseBash() {
        assertSame(SystemCommands.BASH, parse("!ls -la"));
    }

    @Test
    void testParseBashEmpty() {
        assertUnknown("!");
    }

    @Test
    void testParseNetwork() {
        assertSame(SystemCommands.NETWORK, parse("/network"));
    }

    @Test
    void testParseView() {
        assertSame(SystemCommands.VIEW, parse("/view dashboard"));
        assertSame(SystemCommands.VIEW, parse("/view DASHBOARD"));
    }

    @Test
    void testParseViewMissingName() {
        assertUnknown("/view");
    }

    @Test
    void testParseDashboard() {
        assertSame(SystemCommands.DASHBOARD, parse("/dashboard"));
    }

    @Test
    void testParseUnknown() {
        assertUnknown("/foo");
        assertUnknown("/notacommand");
    }

    @Test
    void testParseNonCommand() {
        assertNull(parse("hello"));
        assertNull(parse(""));
        assertNull(parse("   "));
        assertNull(parse(null));
    }

    @Test
    void testCompletions() {
        assertTrue(SystemCommands.EXIT.completions().isEmpty());
        assertFalse(SystemCommands.VIEW.completions().isEmpty());
        assertFalse(SystemCommands.CONTEXT.completions().isEmpty());
        // AGENT completions require ConfigManager initialization, so test it separately
    }

    private Command parse(String input) {
        return SystemCommands.commands().parse(input).orElse(null);
    }

    private void assertUnknown(String input) {
        Command cmd = parse(input);
        assertNotNull(cmd, "Expected command for: " + input);
        assertEquals("unknown", cmd.name(), "Expected unknown for: " + input);
    }
}
