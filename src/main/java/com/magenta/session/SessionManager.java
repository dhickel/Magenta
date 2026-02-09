package com.magenta.session;

import com.magenta.config.ConfigManager;
import com.magenta.context.ContextManager;
import com.magenta.io.IOManager;
import com.magenta.io.terminal.TerminalIOManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SessionManager singleton manages session lifecycle and delegates focus management
 * to TerminalSession for I/O routing and background execution.
 */
public class SessionManager implements AutoCloseable {
    private static SessionManager instance;

    private final TerminalIOManager terminalIO;
    private final TerminalSession terminalSession;

    public static void initialize(TerminalIOManager terminalIO, Session initialSession) {
        if (instance != null) {
            throw new IllegalStateException("SessionManager already initialized");
        }
        instance = new SessionManager(terminalIO, initialSession);
    }

    public static SessionManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SessionManager not initialized - call initialize() first");
        }
        return instance;
    }

    private SessionManager(TerminalIOManager terminalIO, Session initialSession) {
        this.terminalIO = terminalIO;

        // Initialize TerminalSession for focus management
        TerminalSession.initialize(terminalIO);
        this.terminalSession = TerminalSession.getInstance();

        // Setup initial session
        if (initialSession instanceof AgentSession agentSession) {
            // Register and focus initial session
            terminalSession.registerSession(agentSession);
            terminalSession.switchTo(agentSession.alias());
        } else {
            throw new UnsupportedOperationException("No non-agent session support implemented");
            // Non-agent session (legacy support)
            //initialSession.attachIO(terminalIO.createProxy());
        }

        // Set up command completion
        terminalIO.setCompleter(new MagentaCompleter());
    }

    public AgentSession createSession(SessionAlias alias, String configName) {
        return terminalSession.createSession(alias, configName);
    }

    public AgentSession getSession(SessionAlias alias) {
        return terminalSession.getSession(alias);
    }

    public void switchToSession(SessionAlias alias) {
        AgentSession current = terminalSession.focused();
        AgentSession target = terminalSession.getSession(alias);

        if (target == null) {
            throw new IllegalArgumentException("Unknown session alias: " + alias);
        }

        if (current == target) {
            terminalIO.println("Already in session: " + alias.value(), 6);
            return;
        }

        terminalSession.switchTo(alias);
    }

    public List<String> listActiveSessions() {
        return terminalSession.allSessions().keySet().stream()
                .map(SessionAlias::value)
                .collect(Collectors.toList());
    }

    public List<String> listAvailableAgents() {
        return new ArrayList<>(ConfigManager.config().agents.keySet());
    }

    public String getCurrentSessionAlias() {
        SessionAlias alias = terminalSession.focusedAlias();
        return alias != null ? alias.value() : "unknown";
    }

    public void run() {
        AgentSession focused = terminalSession.focused();
        if (focused == null) { throw new IllegalStateException("No focused session"); }

        while (!focused.shouldExit()) {
            focused.runOnce();
        }
    }

    @Override
    public void close() throws Exception {
        terminalSession.close();
    }

    // === Display methods for sessions ===

    public void printSessions(IOManager io) {
        try {
            var allSessions = terminalSession.allSessions();
            var currentAlias = getCurrentSessionAlias();

            io.print("Active sessions:\n");
            io.print("─".repeat(80) + "\n");
            io.print(String.format("%-15s %-10s %-15s %-10s\n",
                "Name", "Messages", "Context Tokens", "Current"));
            io.print("─".repeat(80) + "\n");

            if (allSessions.isEmpty()) {
                io.print("(none)\n");
            } else {
                for (var entry : allSessions.entrySet()) {
                    SessionAlias alias = entry.getKey();
                    AgentSession session = entry.getValue();

                    int messageCount = 0;
                    int contextTokens = 0;

                    if (session != null) {
                        var cm = ContextManager.getInstance();
                        var context = cm.loadContext(session.sessionId());
                        messageCount = context.getElements().size();
                        contextTokens = context.totalEstimatedTokens();
                    }

                    String marker = alias.value().equals(currentAlias) ? "*" : "";

                    io.print(String.format("%-15s %-10d %-15s %-10s\n",
                        alias.value(),
                        messageCount,
                        contextTokens > 0 ? contextTokens + " tokens" : "N/A",
                        marker));
                }
            }
            io.print("─".repeat(80) + "\n");
        } catch (IllegalStateException e) {
            io.print("Session management not available: " + e.getMessage() + "\n");
        }
    }

    public void printAgents(IOManager io) {
        try {
            var config = ConfigManager.config();
            var agents = config.agents;
            var currentAlias = getCurrentSessionAlias();

            io.print("Available agents:\n");
            io.print("─".repeat(80) + "\n");
            io.print(String.format("%-15s %-15s %-8s %-12s\n",
                "Name", "Model", "Tools", "Security"));
            io.print("─".repeat(80) + "\n");

            for (var entry : agents.entrySet()) {
                var agent = entry.getValue();
                int toolCount = agent.tools() != null ? agent.tools().size() : 0;
                String marker = entry.getKey().equals(currentAlias) ? " *" : "";

                io.print(String.format("%-15s %-15s %-8d %-12s%s\n",
                    entry.getKey(),
                    truncate(agent.model().modelName(), 15),
                    toolCount,
                    "configured",
                    marker));
            }
            io.print("─".repeat(80) + "\n");
        } catch (IllegalStateException e) {
            io.print("Session management not available: " + e.getMessage() + "\n");
        }
    }

    private static String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}
