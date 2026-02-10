package com.magenta.session;

import com.magenta.Magenta;
import com.magenta.manager.AgentNetwork;
import com.magenta.config.Config;
import com.magenta.config.ConfigManager;
import com.magenta.manager.ContextManager;
import com.magenta.io.terminal.Command;
import com.magenta.io.terminal.CommandSet;
import com.magenta.manager.SecurityManager;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.ParsedLine;
import org.jline.reader.impl.DefaultParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MagentaCompleter.
 * Without a TerminalSession, the completer returns empty completions.
 * Command completion logic is tested via CommandSet directly.
 */
class MagentaCompleterTest {

    @Test
    void testImplementsCompleterInterface() {
        MagentaCompleter completer = new MagentaCompleter();
        assertTrue(completer instanceof Completer);
    }

    @Test
    void testNoCompletionWithoutSession() {
        MagentaCompleter completer = new MagentaCompleter();
        List<Candidate> candidates = complete(completer, "/");
        assertTrue(candidates.isEmpty(), "Should return empty without a session");
    }

    @Test
    void testNoCompletionForNonSlash() {
        MagentaCompleter completer = new MagentaCompleter();
        List<Candidate> candidates = complete(completer, "hello");
        assertTrue(candidates.isEmpty(), "Non-slash input should not complete");
    }

    @Test
    void testNoCompletionForBangCommands() {
        MagentaCompleter completer = new MagentaCompleter();
        List<Candidate> candidates = complete(completer, "!ls");
        assertTrue(candidates.isEmpty(), "Bang commands should not complete");
    }

    // === CommandSet completion tests (testing the actual completion logic) ===

    @Test
    void testCommandSetSlashShowsAllCommands() throws Exception {
        CommandSet commandSet = createTestCommandSet();
        List<Candidate> candidates = completeViaCommandSet(commandSet, "/");

        assertTrue(candidates.size() > 5, "Should have many command candidates");
        assertTrue(hasCandidate(candidates, "/exit"));
        assertTrue(hasCandidate(candidates, "/help"));
        assertTrue(hasCandidate(candidates, "/view"));
        assertTrue(hasCandidate(candidates, "/dashboard"));
    }

    @Test
    void testCommandSetExPrefix() throws Exception {
        CommandSet commandSet = createTestCommandSet();
        List<Candidate> candidates = completeViaCommandSet(commandSet, "/ex");

        assertTrue(hasCandidate(candidates, "/exit"));
        assertFalse(hasCandidate(candidates, "/help"));
    }

    @Test
    void testCommandSetViewPrefix() throws Exception {
        CommandSet commandSet = createTestCommandSet();
        List<Candidate> candidates = completeViaCommandSet(commandSet, "/vi");

        assertTrue(hasCandidate(candidates, "/view"));
    }

    @Test
    void testCommandSetContextPrefix() throws Exception {
        CommandSet commandSet = createTestCommandSet();
        List<Candidate> candidates = completeViaCommandSet(commandSet, "/con");

        assertTrue(hasCandidate(candidates, "/context"));
    }

    @Test
    void testCommandSetClPrefix() throws Exception {
        CommandSet commandSet = createTestCommandSet();
        List<Candidate> candidates = completeViaCommandSet(commandSet, "/cl");

        assertTrue(hasCandidate(candidates, "/clear"));
    }

    @Test
    void testCommandSetDashboardPrefix() throws Exception {
        CommandSet commandSet = createTestCommandSet();
        List<Candidate> candidates = completeViaCommandSet(commandSet, "/da");

        assertTrue(hasCandidate(candidates, "/dashboard"));
    }

    @Test
    void testCommandSetNetworkPrefix() throws Exception {
        CommandSet commandSet = createTestCommandSet();
        List<Candidate> candidates = completeViaCommandSet(commandSet, "/net");

        assertTrue(hasCandidate(candidates, "/network"));
    }

    @Test
    void testCommandSetNoCompletionForNonSlash() throws Exception {
        CommandSet commandSet = createTestCommandSet();
        List<Candidate> candidates = completeViaCommandSet(commandSet, "hello");

        assertTrue(candidates.isEmpty(), "Non-slash input should not complete");
    }

    @Test
    void testAllCandidatesHaveDescriptions() throws Exception {
        CommandSet commandSet = createTestCommandSet();
        List<Candidate> candidates = completeViaCommandSet(commandSet, "/");

        for (Candidate c : candidates) {
            assertNotNull(c.descr(), "Command " + c.value() + " should have description");
        }
    }

    @Test
    void testCandidatesHaveGrouping() throws Exception {
        CommandSet commandSet = createTestCommandSet();
        List<Candidate> candidates = completeViaCommandSet(commandSet, "/");

        for (Candidate c : candidates) {
            assertEquals("commands", c.group(),
                "Command " + c.value() + " should be in 'commands' group");
        }
    }

    // === Helpers ===

    private CommandSet createTestCommandSet() throws Exception {
        Config config = ConfigManager.loadFromFile("config.json");
        Magenta magenta = new Magenta(
            config,
            null,
            new ContextManager(null),
            new AgentNetwork(),
            new SecurityManager()
        );
        return new SystemCommands(magenta).commands();
    }

    private List<Candidate> complete(MagentaCompleter completer, String input) {
        List<Candidate> candidates = new ArrayList<>();
        DefaultParser parser = new DefaultParser();
        ParsedLine parsedLine = parser.parse(input, input.length());
        completer.complete(null, parsedLine, candidates);
        return candidates;
    }

    private List<Candidate> completeViaCommandSet(CommandSet commandSet, String input) {
        List<Candidate> candidates = new ArrayList<>();
        commandSet.complete(null, input, candidates);
        return candidates;
    }

    private boolean hasCandidate(List<Candidate> candidates, String value) {
        return candidates.stream().anyMatch(c -> c.value().equals(value));
    }
}
