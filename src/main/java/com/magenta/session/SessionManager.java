package com.magenta.session;

import com.magenta.config.Config.AgentConfig;
import com.magenta.config.ConfigManager;
import com.magenta.io.Input;
import com.magenta.io.TerminalIOManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SessionManager singleton manages session lifecycle and IO ownership.
 */
public class SessionManager implements AutoCloseable {
    private static SessionManager instance;

    private final TerminalIOManager terminalIO;
    private Session currentSession;
    // Map of Alias -> Session
    private final Map<SessionAlias, AgentSession> sessions = new HashMap<>();

    public static void initialize(TerminalIOManager terminalIO, Session initialSession, SessionAlias initialSessionAlias) {
        if (instance != null) {
            throw new IllegalStateException("SessionManager already initialized");
        }
        instance = new SessionManager(terminalIO, initialSession, initialSessionAlias);
    }

    public static SessionManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SessionManager not initialized - call initialize() first");
        }
        return instance;
    }

    private SessionManager(TerminalIOManager terminalIO, Session initialSession, SessionAlias initialSessionAlias) {
        this.terminalIO = terminalIO;
        initialSession.attachIO(terminalIO);
        this.currentSession = initialSession;

        if (initialSession instanceof AgentSession agentSession) {
            sessions.put(initialSessionAlias, agentSession);
        }
    }

    public AgentSession createSession(SessionAlias alias, String configName) {
        if (sessions.containsKey(alias)) {
            throw new IllegalArgumentException("Session alias already exists: " + alias);
        }
        
        AgentConfig config = ConfigManager.config().agents.get(configName);
        if (config == null) {
            throw new IllegalArgumentException("Unknown agent config: " + configName);
        }

        AgentSession session = AgentSession.builder()
                .agent(config)
                .messageHandler(new StreamingChat())
                .commandHandler(new DefaultCommandHandler())
                .inputParser(Input::defaultParser)
                .ioManager(terminalIO)
                .sessionId(SessionId.random())
                .build();
        
        sessions.put(alias, session);
        return session;
    }

    public AgentSession getSession(SessionAlias alias) {
        return sessions.get(alias);
    }

    public void switchToSession(SessionAlias alias) {
        AgentSession newSession = sessions.get(alias);
        if (newSession == null) {
            throw new IllegalArgumentException("Unknown session alias: " + alias);
        }

        if (currentSession == newSession) {
            terminalIO.print("Already in session: " + alias + "\n", 6);
            return;
        }

        this.currentSession = newSession;
        terminalIO.print("Switched to session: " + alias + "\n", 6);
    }

    public List<String> listActiveSessions() {
        return sessions.keySet().stream()
                .map(SessionAlias::value)
                .collect(Collectors.toList());
    }

    public List<String> listAvailableAgents() {
        return new ArrayList<>(ConfigManager.config().agents.keySet());
    }

    public String getCurrentSessionAlias() {
        for (Map.Entry<SessionAlias, AgentSession> entry : sessions.entrySet()) {
            if (entry.getValue() == currentSession) {
                return entry.getKey().value(); // Return string value
            }
        }
        return "unknown";
    }

    public void run() {
        while (!currentSession.shouldExit()) {
            currentSession.runOnce();
        }
    }

    @Override
    public void close() throws Exception {
        for (AgentSession session : sessions.values()) {
            session.close();
        }
        terminalIO.close();
    }
}