package com.magenta.session;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.reader.impl.DefaultParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MagentaCompleter command name completion.
 * Argument completion requires SessionManager and is tested via integration.
 */
class MagentaCompleterTest {

    private MagentaCompleter completer;

    @BeforeEach
    void setUp() {
        completer = new MagentaCompleter();
    }

    @Test
    void testSlashShowsAllCommands() {
        List<Candidate> candidates = complete("/");
        // Should contain all registered commands
        assertTrue(candidates.size() > 10, "Should have many command candidates");
        assertTrue(hasCandidate(candidates, "/exit"));
        assertTrue(hasCandidate(candidates, "/help"));
        assertTrue(hasCandidate(candidates, "/agent"));
        assertTrue(hasCandidate(candidates, "/view"));
        assertTrue(hasCandidate(candidates, "/dashboard"));
    }

    @Test
    void testAgPrefix() {
        List<Candidate> candidates = complete("/ag");
        assertTrue(hasCandidate(candidates, "/agent"));
        assertTrue(hasCandidate(candidates, "/agents"));
        assertFalse(hasCandidate(candidates, "/exit"));
    }

    @Test
    void testExPrefix() {
        List<Candidate> candidates = complete("/ex");
        assertTrue(hasCandidate(candidates, "/exit"));
        assertFalse(hasCandidate(candidates, "/agent"));
    }

    @Test
    void testSessionsPrefix() {
        List<Candidate> candidates = complete("/ses");
        assertTrue(hasCandidate(candidates, "/sessions"));
    }

    @Test
    void testViewPrefix() {
        List<Candidate> candidates = complete("/vi");
        assertTrue(hasCandidate(candidates, "/view"));
    }

    @Test
    void testConfigPrefixes() {
        List<Candidate> candidates = complete("/con");
        assertTrue(hasCandidate(candidates, "/config"));
        assertTrue(hasCandidate(candidates, "/context"));
    }

    @Test
    void testCfgAlias() {
        // /cfg is not a registered command prefix in the completer
        // Only /config is registered
        List<Candidate> candidates = complete("/cf");
        // Should not match anything since there's no /cf* in the commands map
        // Actually /config starts with /c, not /cf
        // Let me check - /config starts with /c, /cfg would need explicit entry
        // The COMMANDS map doesn't have /cfg, so it shouldn't appear
        assertFalse(hasCandidate(candidates, "/cfg"));
    }

    @Test
    void testNoCompletionForNonSlash() {
        List<Candidate> candidates = complete("hello");
        assertTrue(candidates.isEmpty(), "Non-slash input should not complete");
    }

    @Test
    void testNoCompletionForBangCommands() {
        List<Candidate> candidates = complete("!ls");
        assertTrue(candidates.isEmpty(), "Bang commands should not complete");
    }

    @Test
    void testDashboardPrefix() {
        List<Candidate> candidates = complete("/da");
        assertTrue(hasCandidate(candidates, "/dashboard"));
    }

    @Test
    void testNetworkPrefix() {
        List<Candidate> candidates = complete("/net");
        assertTrue(hasCandidate(candidates, "/network"));
    }

    @Test
    void testAllCandidatesHaveDescriptions() {
        List<Candidate> candidates = complete("/");
        for (Candidate c : candidates) {
            // All known commands should have non-null descriptions
            // (Unknown would have null, but we don't register Unknown)
            assertNotNull(c.descr(), "Command " + c.value() + " should have description");
        }
    }

    @Test
    void testCandidatesHaveGrouping() {
        List<Candidate> candidates = complete("/");
        for (Candidate c : candidates) {
            assertEquals("commands", c.group(),
                "Command " + c.value() + " should be in 'commands' group");
        }
    }

    @Test
    void testImplementsCompleterInterface() {
        assertTrue(completer instanceof Completer);
    }

    // === Helper ===

    private List<Candidate> complete(String input) {
        List<Candidate> candidates = new ArrayList<>();
        DefaultParser parser = new DefaultParser();
        ParsedLine parsedLine = parser.parse(input, input.length());
        completer.complete(null, parsedLine, candidates);
        return candidates;
    }

    private boolean hasCandidate(List<Candidate> candidates, String value) {
        return candidates.stream().anyMatch(c -> c.value().equals(value));
    }
}
