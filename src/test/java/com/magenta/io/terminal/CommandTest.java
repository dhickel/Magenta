package com.magenta.io.terminal;

import com.magenta.Magenta;
import com.magenta.manager.AgentNetwork;
import com.magenta.config.Config;
import com.magenta.config.ConfigManager;
import com.magenta.manager.ContextManager;
import com.magenta.manager.SecurityManager;
import com.magenta.session.SystemCommands;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandTest {

    private static CommandSet commandSet;

    @BeforeAll
    static void setUp() throws Exception {
        Config config = ConfigManager.loadFromFile("config.json");
        Magenta magenta = new Magenta(
            config,
            null,
            new ContextManager(null),
            new AgentNetwork(),
            new SecurityManager()
        );
        commandSet = new SystemCommands(magenta).commands();
    }

    @Test
    void testParseExit() {
        assertCommand("exit", "/exit");
        assertCommand("exit", "/quit");
        assertCommand("exit", "/q");
    }

    @Test
    void testParseHelp() {
        assertCommand("help", "/help");
        assertCommand("help", "/?");
    }

    @Test
    void testParseClear() {
        assertCommand("clear", "/clear");
        assertCommand("clear", "/cls");
    }

    @Test
    void testParseContext() {
        assertCommand("context", "/context status");
        assertCommand("context", "/context");
        assertCommand("context", "/context archive mykey");
    }

    @Test
    void testParseBash() {
        assertCommand("bash", "!ls -la");
    }

    @Test
    void testParseBashEmpty() {
        assertUnknown("!");
    }

    @Test
    void testParseNetwork() {
        assertCommand("network", "/network");
    }

    @Test
    void testParseView() {
        assertCommand("view", "/view dashboard");
        assertCommand("view", "/view DASHBOARD");
    }

    @Test
    void testParseViewMissingName() {
        assertUnknown("/view");
    }

    @Test
    void testParseDashboard() {
        assertCommand("dashboard", "/dashboard");
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
        Command exit = findCommand("exit");
        Command view = findCommand("view");
        Command context = findCommand("context");

        assertNotNull(exit);
        assertNotNull(view);
        assertNotNull(context);

        assertTrue(exit.completions().isEmpty());
        assertFalse(view.completions().isEmpty());
        assertFalse(context.completions().isEmpty());
    }

    private Command parse(String input) {
        return commandSet.parse(input).orElse(null);
    }

    private Command findCommand(String name) {
        return commandSet.commands().stream()
            .filter(c -> c.name().equals(name))
            .findFirst()
            .orElse(null);
    }

    private void assertCommand(String expectedName, String input) {
        Command cmd = parse(input);
        assertNotNull(cmd, "Expected command for: " + input);
        assertEquals(expectedName, cmd.name(), "Expected " + expectedName + " for: " + input);
    }

    private void assertUnknown(String input) {
        Command cmd = parse(input);
        assertNotNull(cmd, "Expected command for: " + input);
        assertEquals("unknown", cmd.name(), "Expected unknown for: " + input);
    }
}
