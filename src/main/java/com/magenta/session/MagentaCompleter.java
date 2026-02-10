package com.magenta.session;

import com.magenta.io.terminal.CommandSet;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

/**
 * Context-aware command completer for Magenta.
 * Delegates to Command completion providers for command-specific completions.
 *
 * When no TerminalSession is provided, falls back to empty completions
 * for argument-level completion (command name completion always works).
 */
public class MagentaCompleter implements Completer {

    private volatile TerminalSession terminalSession;

    public MagentaCompleter() {
    }

    public MagentaCompleter(TerminalSession terminalSession) {
        this.terminalSession = terminalSession;
    }

    public void setTerminalSession(TerminalSession terminalSession) {
        this.terminalSession = terminalSession;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();
        AgentSession session = currentSession();
        CommandSet commandSet = session != null
            ? session.commandSet()
            : CommandSet.empty();
        commandSet.complete(session, buffer, candidates);

        // Add view-specific completions
        addViewSpecificCandidates(buffer, candidates);
    }

    private void addViewSpecificCandidates(String buffer, List<Candidate> candidates) {
        AgentSession session = currentSession();
        if (session == null) return;

        // Dashboard-specific commands
        if (session.currentView() instanceof TerminalView.Dashboard) {
            if ("/exit-dashboard".startsWith(buffer)) {
                candidates.add(new Candidate(
                    "/exit-dashboard", "/exit-dashboard", "view",
                    "Return to chat view", null, null, true
                ));
            }
        }

        // Table-specific commands
        if (session.currentView() instanceof TerminalView.Table<?>) {
            if ("/exit-table".startsWith(buffer)) {
                candidates.add(new Candidate(
                    "/exit-table", "/exit-table", "view",
                    "Return to chat view", null, null, true
                ));
            }
        }
    }

    private AgentSession currentSession() {
        var ts = terminalSession;
        if (ts == null) return null;
        return ts.focused();
    }

}
