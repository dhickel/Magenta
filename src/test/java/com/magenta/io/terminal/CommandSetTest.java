package com.magenta.io.terminal;

import com.magenta.Magenta;
import com.magenta.manager.AgentNetwork;
import com.magenta.config.Config;
import com.magenta.config.ConfigManager;
import com.magenta.manager.ContextManager;
import com.magenta.manager.SecurityManager;
import com.magenta.session.AgentSession;
import com.magenta.session.SessionAlias;
import com.magenta.session.SessionId;
import com.magenta.session.SystemCommands;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandSetTest {

    private Magenta createTestMagenta() throws Exception {
        Config config = ConfigManager.loadFromFile("config.json");
        return new Magenta(
            config,
            null,
            new ContextManager(null),
            new AgentNetwork(),
            new SecurityManager()
        );
    }

    @Test
    void testSimpleCommandSet() {
        Command cmd1 = Command.of("test", "Test command", raw -> raw.startsWith("/test"), (s, r) -> {});
        Command cmd2 = Command.of("demo", "Demo command", raw -> raw.startsWith("/demo"), (s, r) -> {});

        CommandSet set = CommandSet.of(cmd1, cmd2);

        assertEquals(2, set.commands().size());
        assertTrue(set.commands().contains(cmd1));
        assertTrue(set.commands().contains(cmd2));
    }

    @Test
    void testComposedCommandSet() {
        Command cmd1 = Command.of("test", "Test command", raw -> raw.startsWith("/test"), (s, r) -> {});
        Command cmd2 = Command.of("demo", "Demo command", raw -> raw.startsWith("/demo"), (s, r) -> {});

        CommandSet set1 = CommandSet.of(cmd1);
        CommandSet set2 = CommandSet.of(cmd2);

        CommandSet composed = set1.composedWith(set2);

        assertEquals(2, composed.commands().size());
        assertTrue(composed.commands().contains(cmd1));
        assertTrue(composed.commands().contains(cmd2));
    }

    @Test
    void testMultipleComposition() {
        Command cmd1 = Command.of("test", "Test", raw -> raw.startsWith("/test"), (s, r) -> {});
        Command cmd2 = Command.of("demo", "Demo", raw -> raw.startsWith("/demo"), (s, r) -> {});
        Command cmd3 = Command.of("foo", "Foo", raw -> raw.startsWith("/foo"), (s, r) -> {});

        CommandSet set1 = CommandSet.of(cmd1);
        CommandSet set2 = CommandSet.of(cmd2);
        CommandSet set3 = CommandSet.of(cmd3);

        // Chain composition
        CommandSet composed = set1
            .composedWith(set2)
            .composedWith(set3);

        assertEquals(3, composed.commands().size());
        assertTrue(composed.commands().contains(cmd1));
        assertTrue(composed.commands().contains(cmd2));
        assertTrue(composed.commands().contains(cmd3));
    }

    @Test
    void testSystemCommandsComposition() throws Exception {
        Magenta magenta = createTestMagenta();
        Command custom = Command.of("custom", "Custom command", raw -> raw.startsWith("/custom"), (s, r) -> {});

        CommandSet systemCommands = new SystemCommands(magenta).commands();
        CommandSet customSet = CommandSet.of(custom);

        CommandSet composed = systemCommands.composedWith(customSet);

        // Should have all system commands plus custom
        assertTrue(composed.commands().size() > 5); // System has several commands
        assertTrue(composed.commands().contains(custom));
    }

    @Test
    void testEmptyCommandSet() {
        CommandSet empty = CommandSet.empty();

        assertEquals(0, empty.commands().size());
    }

    @Test
    void testParse() {
        Command testCmd = Command.of("test", "Test", raw -> raw.startsWith("/test"), (s, r) -> {});
        CommandSet set = CommandSet.of(testCmd);

        var parsed = set.parse("/test arg");
        assertTrue(parsed.isPresent());
        assertEquals("test", parsed.get().name());
    }

    @Test
    void testComposedSetFlattening() {
        Command cmd1 = Command.of("test1", "Test 1", raw -> raw.startsWith("/test1"), (s, r) -> {});
        Command cmd2 = Command.of("test2", "Test 2", raw -> raw.startsWith("/test2"), (s, r) -> {});
        Command cmd3 = Command.of("test3", "Test 3", raw -> raw.startsWith("/test3"), (s, r) -> {});

        CommandSet set1 = CommandSet.of(cmd1);
        CommandSet set2 = CommandSet.of(cmd2);
        CommandSet composed1 = set1.composedWith(set2); // Composed of 2 sets

        CommandSet set3 = CommandSet.of(cmd3);
        CommandSet composed2 = composed1.composedWith(set3); // Should flatten

        // Verify all commands are present
        assertEquals(3, composed2.commands().size());
        assertTrue(composed2.commands().contains(cmd1));
        assertTrue(composed2.commands().contains(cmd2));
        assertTrue(composed2.commands().contains(cmd3));
    }

    @Test
    void testAgentSessionUsesComposedCommandSet() throws Exception {
        Magenta magenta = createTestMagenta();
        Config.AgentConfig agentConfig = magenta.config().agents.get("default");
        assertNotNull(agentConfig, "Default agent config should exist");

        AgentSession session = new AgentSession(
            magenta,
            SessionAlias.of("test"),
            agentConfig,
            SessionId.random()
        );

        // Session should have system commands composed with agent commands
        assertNotNull(session.commandSet());
        assertFalse(session.commands().isEmpty());

        // Should contain system commands
        List<Command> commands = session.commands();
        boolean hasExit = commands.stream().anyMatch(cmd -> cmd.name().equals("exit"));
        boolean hasHelp = commands.stream().anyMatch(cmd -> cmd.name().equals("help"));

        assertTrue(hasExit, "Should have exit command from SystemCommands");
        assertTrue(hasHelp, "Should have help command from SystemCommands");
    }
}
