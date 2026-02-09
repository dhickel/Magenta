package com.magenta.session;

import com.magenta.agent.AgentNetwork;
import com.magenta.config.Config.AgentConfig;
import com.magenta.config.ConfigManager;
import com.magenta.context.ContextManager;
import com.magenta.io.QueuedIOManager;
import com.magenta.io.terminal.TerminalIOManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TerminalSession singleton manages terminal focus and I/O routing for agent sessions.
 * Handles switching between agents while preserving state and enabling background execution.
 *
 * <p>I/O Routing Strategy:
 * <ul>
 *   <li>Focused session: Uses TerminalIOManager (real-time terminal I/O)</li>
 *   <li>Backgrounded sessions: Use QueuedIOManager (buffered output)</li>
 *   <li>On refocus: Drain queued output, reload context, redraw view</li>
 * </ul>
 *
 * <p>Phase 1: Core infrastructure (synchronous)
 * <p>Phase 2: Context integration
 * <p>Phase 3: Background execution with virtual threads (TODO)
 */
public final class TerminalSession implements AutoCloseable {

    private static TerminalSession instance;

    private final TerminalIOManager terminal;
    private final Map<SessionAlias, AgentSession> sessions;
    private final Map<SessionAlias, QueuedIOManager> queuedIOs;
    private SessionAlias focusedSession;

    // TODO Phase 3: ExecutorService for background execution
    // private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private TerminalSession(TerminalIOManager terminal) {
        this.terminal = terminal;
        this.sessions = new ConcurrentHashMap<>();
        this.queuedIOs = new ConcurrentHashMap<>();
        this.focusedSession = null;
    }

    // === Singleton Pattern ===

    public static void initialize(TerminalIOManager terminal) {
        if (instance != null) {
            throw new IllegalStateException("TerminalSession already initialized");
        }
        instance = new TerminalSession(terminal);
    }

    public static TerminalSession getInstance() {
        if (instance == null) {
            throw new IllegalStateException("TerminalSession not initialized - call initialize() first");
        }
        return instance;
    }

    // === Focus Management ===

    /**
     * Switch focus to the specified session.
     * <ol>
     *   <li>Background current session (swap to QueuedIOManager)</li>
     *   <li>Focus target session (swap to TerminalIOManager)</li>
     *   <li>Drain queued output and display</li>
     *   <li>Reload context from ContextManager (Phase 2)</li>
     *   <li>Redraw view with current state</li>
     * </ol>
     *
     * @param target Session alias to focus
     */
    public void switchTo(SessionAlias target) {
        // 1. Background current session
        if (focusedSession != null && !focusedSession.equals(target)) {
            AgentSession current = sessions.get(focusedSession);
            if (current != null) {
                // Flush context to database before backgrounding
                if (ContextManager.isInitialized()) {
                    ContextManager.getInstance().flushContext(current.sessionId());
                }

                QueuedIOManager queuedIO = queuedIOs.computeIfAbsent(
                    focusedSession,
                    k -> new QueuedIOManager()
                );
                current.attachIO(queuedIO);
                terminal.println("Backgrounded session: " + focusedSession.value(), 6);
            }
        }

        // 2. Focus target session
        AgentSession next = sessions.get(target);
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

        // 4. Context is automatically loaded from ContextManager on each message
        // No explicit reload needed - StreamingChat.processMessage() loads context fresh each time
        // Optional enhancement: Display recent conversation history here for user context

        // 5. Redraw view
        next.forceRedraw();

        focusedSession = target;
        terminal.println("Focused session: " + target.value(), 6);
    }

    /**
     * Register an existing session.
     * Useful for initial session setup.
     *
     * @param session Existing session to register
     */
    public void registerSession(AgentSession session) {
        if (sessions.containsKey(session.alias())) {
            throw new IllegalArgumentException("Session already exists: " + session.alias());
        }

        sessions.put(session.alias(), session);
        queuedIOs.put(session.alias(), new QueuedIOManager());

        // Register with AgentNetwork
        AgentNetwork.getInstance().registerAgent(session.sessionMeta());

        terminal.println("Registered session: " + session.alias().value(), 6);
    }

    /**
     * Create a new agent session.
     * Session starts in backgrounded state (uses QueuedIOManager).
     *
     * @param alias Session alias
     * @param agentName Agent configuration name
     * @return Created session
     */
    public AgentSession createSession(SessionAlias alias, String agentName) {
        if (sessions.containsKey(alias)) {
            throw new IllegalArgumentException("Session already exists: " + alias);
        }

        AgentConfig config = ConfigManager.config().agents.get(agentName);
        if (config == null) {
            throw new IllegalArgumentException("Unknown agent config: " + agentName);
        }

        // Create queued IO for this session (starts backgrounded)
        QueuedIOManager queuedIO = new QueuedIOManager();
        queuedIOs.put(alias, queuedIO);

        // Create session
        AgentSession session = AgentSession.builder()
            .alias(alias)
            .agent(config)
            .sessionId(SessionId.random())
            .messageHandler(new StreamingChat())
            .ioManager(queuedIO)
            .build();

        sessions.put(alias, session);

        // Register with AgentNetwork
        AgentNetwork.getInstance().registerAgent(session.sessionMeta());

        // TODO Phase 3: Start background execution
        // executor.submit(() -> {
        //     while (!session.shouldExit()) {
        //         session.runOnce();
        //     }
        // });

        terminal.println("Created session: " + alias.value(), 6);

        return session;
    }

    // === Accessors ===

    /**
     * Get the currently focused session.
     */
    public AgentSession focused() {
        return focusedSession != null ? sessions.get(focusedSession) : null;
    }

    /**
     * Get focused session alias.
     */
    public SessionAlias focusedAlias() {
        return focusedSession;
    }

    /**
     * Get a session by alias.
     */
    public AgentSession getSession(SessionAlias alias) {
        return sessions.get(alias);
    }

    /**
     * Get all session aliases.
     */
    public Map<SessionAlias, AgentSession> allSessions() {
        return Map.copyOf(sessions);
    }

    /**
     * Get the terminal IOManager.
     */
    public TerminalIOManager terminal() {
        return terminal;
    }

    // === View Management ===

    /**
     * Set the view for the focused session.
     */
    public void setView(TerminalView view) {
        AgentSession session = focused();
        if (session != null) {
            session.setView(view);
        }
    }

    // === Lifecycle ===

    @Override
    public void close() throws Exception {
        // TODO Phase 3: Shutdown executor
        // executor.shutdown();
        // if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        //     executor.shutdownNow();
        // }

        // Flush all contexts to database before shutdown
        if (ContextManager.isInitialized()) {
            ContextManager.getInstance().flushAll();
        }

        // Unregister all sessions
        for (AgentSession session : sessions.values()) {
            AgentNetwork.getInstance().unregisterAgent(session.sessionMeta());
            session.close();
        }

        // Close queued IOs
        for (QueuedIOManager io : queuedIOs.values()) {
            io.close();
        }

        sessions.clear();
        queuedIOs.clear();
        terminal.close();
    }
}
