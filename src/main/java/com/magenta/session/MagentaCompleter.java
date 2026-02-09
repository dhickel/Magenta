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
 * Stateless - queries SessionManager for current session dynamically.
 */
public class MagentaCompleter implements Completer {

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();
        AgentSession session = currentSession();
        CommandSet commandSet = session != null
            ? session.commandSet()
            : SystemCommands.commands();
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
        try {
            var sm = SessionManager.getInstance();
            String alias = sm.getCurrentSessionAlias();
            return sm.getSession(SessionAlias.of(alias));
        } catch (IllegalStateException e) {
            return null;
        }
    }

}
