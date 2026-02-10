package com.magenta.session;

import com.magenta.Magenta;
import com.magenta.config.Config.AgentConfig;
import com.magenta.manager.SessionManager;
import com.magenta.io.QueuedIOManager;
import com.magenta.io.terminal.Command;
import com.magenta.io.terminal.CommandSet;
import com.magenta.io.terminal.TerminalIOManager;
import org.jline.reader.Candidate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * TerminalSession manages terminal focus and I/O routing for agent sessions.
 * Handles switching between agents while preserving state and enabling background execution.
 *
 * <p>I/O Routing Strategy:
 * <ul>
 *   <li>Focused session: Uses TerminalIOManager (real-time terminal I/O)</li>
 *   <li>Backgrounded sessions: Use QueuedIOManager (buffered output)</li>
 *   <li>On refocus: Drain queued output, reload context, redraw view</li>
 * </ul>
 */
public final class TerminalSession implements AutoCloseable {

    private final TerminalIOManager terminal;
    private final SessionManager sessionManager;
    private final Magenta magenta;
    private final Map<SessionAlias, QueuedIOManager> queuedIOs;
    private volatile SessionAlias focusedSession;

    public TerminalSession(TerminalIOManager terminal, SessionManager sessionManager, Magenta magenta) {
        this.terminal = terminal;
        this.sessionManager = sessionManager;
        this.magenta = magenta;
        this.queuedIOs = new ConcurrentHashMap<>();
        this.focusedSession = null;
    }

    // === Focus Management ===

    /**
     * Switch focus to the specified session.
     */
    public void switchTo(SessionAlias target) {
        // 1. Background current session
        if (focusedSession != null && !focusedSession.equals(target)) {
            AgentSession current = sessionManager.getSession(focusedSession);
            if (current != null) {
                // Flush context to database before backgrounding
                magenta.contextManager().flushContext(current.sessionId());

                QueuedIOManager queuedIO = queuedIOs.computeIfAbsent(
                    focusedSession,
                    k -> new QueuedIOManager()
                );
                current.attachIO(queuedIO);
                terminal.println("Backgrounded session: " + focusedSession.value(), 6);
            }
        }

        // 2. Focus target session
        AgentSession next = sessionManager.getSession(target);
        if (next == null) {
            throw new IllegalArgumentException("Session not found: " + target);
        }

        // Swap to terminal I/O
        next.attachIO(terminal.createProxy());

        // 3. Drain queued output
        QueuedIOManager queued = queuedIOs.get(target);
        if (queued != null) {
            for (String msg : queued.drainOutput()) {
                terminal.print(msg);
            }
        }

        // 4. Redraw view
        next.forceRedraw();

        focusedSession = target;
        terminal.println("Focused session: " + target.value(), 6);
    }

    /**
     * Register an existing session.
     */
    public void registerSession(AgentSession session) {
        sessionManager.registerSession(session);
        queuedIOs.put(session.alias(), new QueuedIOManager());

        // Register with AgentNetwork
        magenta.agentNetwork().registerAgent(session.sessionMeta());

        terminal.println("Registered session: " + session.alias().value(), 6);
    }

    /**
     * Create a new agent session.
     * Session starts in backgrounded state (uses QueuedIOManager).
     */
    public AgentSession createSession(SessionAlias alias, String agentName) {
        AgentConfig config = magenta.config().agents.get(agentName);
        if (config == null) {
            throw new IllegalArgumentException("Unknown agent config: " + agentName);
        }

        // Create queued IO for this session (starts backgrounded)
        QueuedIOManager queuedIO = new QueuedIOManager();
        queuedIOs.put(alias, queuedIO);

        // Create session with terminal commands injected
        AgentSession session = AgentSession.builder()
            .magenta(magenta)
            .alias(alias)
            .agent(config)
            .sessionId(SessionId.random())
            .messageHandler(new StreamingChat())
            .commands(terminalCommands())
            .ioManager(queuedIO)
            .build();

        sessionManager.registerSession(session);

        // Register with AgentNetwork
        magenta.agentNetwork().registerAgent(session.sessionMeta());

        terminal.println("Created session: " + alias.value(), 6);

        return session;
    }

    /**
     * Get the currently focused session.
     */
    public AgentSession focused() {
        return focusedSession != null ? sessionManager.getSession(focusedSession) : null;
    }

    /**
     * Get focused session alias.
     */
    public SessionAlias focusedAlias() {
        return focusedSession;
    }

    /**
     * Get the terminal IOManager.
     */
    public TerminalIOManager terminal() {
        return terminal;
    }

    /**
     * Set the view for the focused session.
     */
    public void setView(TerminalView view) {
        AgentSession session = focused();
        if (session != null) {
            session.setView(view);
        }
    }

    /**
     * Run the main loop on the focused session.
     */
    public void run() {
        AgentSession session = focused();
        if (session == null) {
            throw new IllegalStateException("No focused session");
        }

        while (!session.shouldExit()) {
            try {
                session.runOnce();
            } catch (Exception e) {
                terminal.error("Error: " + e.getMessage());
                org.slf4j.LoggerFactory.getLogger(TerminalSession.class)
                    .error("Unhandled error in session loop", e);
            }
        }
    }

    // === Terminal-specific commands ===

    /**
     * Create the terminal-specific command set (agent switching, session listing).
     * These commands are injected into agent sessions created by TerminalSession.
     */
    public CommandSet terminalCommands() {
        return CommandSet.of(agentCmd(), sessionsCmd(), agentsCmd());
    }

    private Command agentCmd() {
        return Command.of("agent", "Switch to different agent",
            this::agentCompletions,
            raw -> slash(raw, "agent") && !argString(raw).isBlank(),
            (session, raw) -> switchAgent(session, argString(raw).split("\\s+")[0]));
    }

    private Command sessionsCmd() {
        return Command.of("sessions", "List active sessions", List.of(),
            raw -> slash(raw, "sessions"),
            (session, raw) -> printSessions(session.io()));
    }

    private Command agentsCmd() {
        return Command.of("agents", "List available agent configurations", List.of(),
            raw -> slash(raw, "agents"),
            (session, raw) -> printAgents(session.io()));
    }

    // === Command helpers ===

    private static boolean slash(String raw, String... names) {
        if (raw == null || !raw.startsWith("/")) return false;
        String cmd = raw.substring(1).trim();
        int idx = cmd.indexOf(' ');
        String name = idx < 0 ? cmd.toLowerCase() : cmd.substring(0, idx).toLowerCase();
        for (String n : names) {
            if (name.equals(n)) return true;
        }
        return false;
    }

    private static String argString(String raw) {
        if (raw == null || !raw.startsWith("/")) return "";
        String cmd = raw.substring(1).trim();
        int idx = cmd.indexOf(' ');
        return idx < 0 ? "" : cmd.substring(idx + 1).trim();
    }

    private List<Candidate> agentCompletions() {
        return magenta.config().agents.keySet().stream()
            .map(name -> new Candidate(name, name, "agents", "Switch to " + name, null, null, true))
            .toList();
    }

    private void switchAgent(Session session, String agentName) {
        try {
            var agentConfig = magenta.config().agents.get(agentName);
            if (agentConfig == null) {
                session.io().print("Error: Unknown agent: " + agentName + "\n");
                return;
            }

            SessionAlias alias = SessionAlias.of(agentName);
            if (sessionManager.getSession(alias) != null) {
                switchToSession(alias);
                return;
            }

            session.io().print("Creating session for: " + agentName + "\n");
            createSession(alias, agentName);
            switchToSession(alias);

        } catch (Exception e) {
            session.io().print("Error: " + e.getMessage() + "\n");
        }
    }

    public void switchToSession(SessionAlias alias) {
        AgentSession current = focused();
        AgentSession target = sessionManager.getSession(alias);

        if (target == null) {
            throw new IllegalArgumentException("Unknown session alias: " + alias);
        }

        if (current == target) {
            terminal.println("Already in session: " + alias.value(), 6);
            return;
        }

        switchTo(alias);
    }

    // === Display methods ===

    public void printSessions(com.magenta.io.IOManager io) {
        var allSessions = sessionManager.allSessions();
        String currentAlias = focusedSession != null ? focusedSession.value() : "unknown";

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
                    var cm = magenta.contextManager();
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
    }

    public void printAgents(com.magenta.io.IOManager io) {
        var agents = magenta.config().agents;
        String currentAlias = focusedSession != null ? focusedSession.value() : "unknown";

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
    }

    private static String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    // === Lifecycle ===

    @Override
    public void close() throws Exception {
        // Flush all contexts to database before shutdown
        magenta.contextManager().flushAll();

        // Unregister all sessions
        for (AgentSession session : sessionManager.allSessions().values()) {
            magenta.agentNetwork().unregisterAgent(session.sessionMeta());
            session.close();
        }

        // Close queued IOs
        for (QueuedIOManager io : queuedIOs.values()) {
            io.close();
        }

        queuedIOs.clear();
        terminal.close();
    }
}
