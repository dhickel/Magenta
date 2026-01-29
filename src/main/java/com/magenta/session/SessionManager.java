package com.magenta.session;

import com.magenta.config.Config.AgentConfig;
import com.magenta.config.ConfigManager;
import com.magenta.io.Input;
import com.magenta.io.TerminalIOManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SessionManager singleton manages session lifecycle and IO ownership.
 *
 * Design:
 * - Owns the TerminalIOManager (single terminal for entire lifecycle)
 * - Creates and caches agent sessions on-demand
 * - Handles session switching without recreating terminal
 * - Each agent session maintains independent conversation history
 *
 * Usage:
 * <pre>
 * SessionManager.initialize(terminalIO, initialSession);
 * SessionManager.getInstance().run();
 * SessionManager.getInstance().close();
 * </pre>
 */
public class SessionManager implements AutoCloseable {
    private static SessionManager instance;

    private final TerminalIOManager terminalIO;
    private Session currentSession;
    private final Map<String, AgentSession> agentSessions = new HashMap<>();

    /**
     * Initialize the SessionManager singleton.
     * Must be called once at startup before getInstance().
     *
     * @param terminalIO The terminal IO manager (owned by SessionManager)
     * @param initialSession The initial session to start with
     * @throws IllegalStateException if already initialized
     */
    public static void initialize(TerminalIOManager terminalIO, Session initialSession) {
        if (instance != null) {
            throw new IllegalStateException("SessionManager already initialized");
        }
        instance = new SessionManager(terminalIO, initialSession);
    }

    /**
     * Get the SessionManager singleton instance.
     *
     * @return The SessionManager instance
     * @throws IllegalStateException if not initialized
     */
    public static SessionManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SessionManager not initialized - call initialize() first");
        }
        return instance;
    }

    private SessionManager(TerminalIOManager terminalIO, Session initialSession) {
        this.terminalIO = terminalIO;
        initialSession.attachIO(terminalIO);
        this.currentSession = initialSession;

        // Cache initial session if it's an AgentSession
        if (initialSession instanceof AgentSession agentSession) {
            String agentName = getAgentName(agentSession);
            if (agentName != null) {
                agentSessions.put(agentName, agentSession);
            }
        }
    }

    /**
     * Get or create an agent session by agent config name.
     * Sessions are cached - switching back to an agent reuses the same session with history.
     *
     * @param agentName The agent config name (key in config.json agents map)
     * @return The agent session (existing or newly created)
     * @throws IllegalArgumentException if agent name is unknown
     */
    public AgentSession getOrCreateAgentSession(String agentName) {
        return agentSessions.computeIfAbsent(agentName, name -> {
            AgentConfig config = ConfigManager.config().agents.get(name);
            if (config == null) {
                throw new IllegalArgumentException("Unknown agent: " + name);
            }

            // Build with terminalIO (builder requires it for SecurityFilter setup)
            AgentSession session = AgentSession.builder()
                    .agent(config)
                    .messageHandler(new StreamingChat())
                    .commandHandler(new DefaultCommandHandler())
                    .inputParser(Input::defaultParser)
                    .ioManager(terminalIO)
                    .build();

            return session;
        });
    }

    /**
     * Switch to a different agent session.
     * Creates the session if it doesn't exist, otherwise reuses existing session with history.
     *
     * @param agentName The agent config name to switch to
     * @throws IllegalArgumentException if agent name is unknown
     */
    public void switchToAgent(String agentName) {
        AgentSession newSession = getOrCreateAgentSession(agentName);

        // Don't print message if we're already on this session
        if (currentSession == newSession) {
            terminalIO.println("Already in session: " + agentName, 6); // Info color
            return;
        }

        // Optional: add lifecycle hooks in future
        // if (currentSession instanceof AgentSession old) old.onPause();
        // newSession.onResume();

        this.currentSession = newSession;
        terminalIO.println("Switched to agent: " + agentName, 6); // Info color
    }

    /**
     * List all active agent session names.
     *
     * @return List of agent names with active sessions
     */
    public List<String> listActiveSessions() {
        return new ArrayList<>(agentSessions.keySet());
    }

    /**
     * List all available agent configurations from config.
     *
     * @return List of all agent config names
     */
    public List<String> listAvailableAgents() {
        return new ArrayList<>(ConfigManager.config().agents.keySet());
    }

    /**
     * Get the current session name (agent name if AgentSession).
     *
     * @return Current session identifier or "unknown"
     */
    public String getCurrentSessionName() {
        if (currentSession instanceof AgentSession agentSession) {
            String name = getAgentName(agentSession);
            return name != null ? name : "unknown";
        }
        return "unknown";
    }

    /**
     * Main execution loop.
     * Runs until current session requests exit.
     */
    public void run() {
        while (!currentSession.shouldExit()) {
            currentSession.runOnce();
        }
    }

    @Override
    public void close() throws Exception {
        // Close all sessions (they don't own IO, so safe)
        for (AgentSession session : agentSessions.values()) {
            session.close();
        }
        // Close terminal IO (we own it)
        terminalIO.close();
    }

    /**
     * Helper to find agent config name for a given AgentSession.
     * Searches config.agents map to find matching instance.
     *
     * @param session The agent session
     * @return Agent config name or null if not found
     */
    private String getAgentName(AgentSession session) {
        // Find the agent name by comparing agent instances
        // This is a bit indirect but avoids storing redundant state
        AgentConfig sessionAgent = session.agent().config();
        for (Map.Entry<String, AgentConfig> entry : ConfigManager.config().agents.entrySet()) {
            if (entry.getValue() == sessionAgent) {
                return entry.getKey();
            }
        }
        return null;
    }
}
