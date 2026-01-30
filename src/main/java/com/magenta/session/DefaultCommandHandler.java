package com.magenta.session;

import com.magenta.io.Command;
import com.magenta.io.IOManager;

/**
 * Default implementation of CommandHandler.
 */
public class DefaultCommandHandler implements CommandHandler {

    @Override
    public void handle(Session session, Command command) {
        var io = session.io();
        switch (command) {
            case Command.Exit() -> session.setExit(true);
            case Command.Help() -> printHelp(io);
            case Command.Clear() -> { /* handled by IOManager */ }
            case Command.History() -> io.println("History not yet implemented");
            case Command.Agent(String rawArg) -> switchAgent(session, rawArg);
            case Command.Sessions() -> listSessions(io);
            case Command.Agents() -> listAgents(io);
            case Command.Unknown(String raw) -> io.println("Unknown command: " + raw);
        }
    }

    private void printHelp(IOManager io) {
        io.println("Available commands:");
        io.println("  /exit, /quit, /q - Exit the session");
        io.println("  /help, /? - Show this help message");
        io.println("  /clear, /cls - Clear the screen");
        io.println("  /history - Show conversation history");
        io.println("  /agent <name> [alias] - Switch to a different agent/session");
        io.println("  /sessions - List active sessions");
        io.println("  /agents - List available agent configurations");
    }

    private void switchAgent(Session session, String rawArg) {
        try {
            SessionManager sm = SessionManager.getInstance();
            String[] parts = rawArg.trim().split("\\s+", 2);
            String configName = parts[0];
            String aliasStr = parts.length > 1 ? parts[1] : configName;
            SessionAlias alias = SessionAlias.of(aliasStr);

            // Check if alias exists (switch)
            if (sm.getSession(alias) != null) {
                sm.switchToSession(alias);
                return;
            }

            // Otherwise, create new session (using configName)
            sm.createSession(alias, configName);
            sm.switchToSession(alias);

        } catch (IllegalArgumentException e) {
            session.io().println("Error: " + e.getMessage(), 1); 
        } catch (IllegalStateException e) {
            session.io().println("Session switching not available: " + e.getMessage(), 3); 
        }
    }

    private void listSessions(IOManager io) {
        try {
            var sessions = SessionManager.getInstance().listActiveSessions();
            var current = SessionManager.getInstance().getCurrentSessionAlias();

            io.println("Active sessions:");
            if (sessions.isEmpty()) {
                io.println("  (none)");
            } else {
                for (String session : sessions) {
                    String marker = session.equals(current) ? " *" : "";
                    io.println("  " + session + marker);
                }
            }
        } catch (IllegalStateException e) {
            io.println("Session management not available: " + e.getMessage(), 3);
        }
    }

    private void listAgents(IOManager io) {
        try {
            var agents = SessionManager.getInstance().listAvailableAgents();
            var currentAlias = SessionManager.getInstance().getCurrentSessionAlias();

            io.println("Available agents (configs):");
            for (String agent : agents) {
                String marker = agent.equals(currentAlias) ? " (active)" : "";
                io.println("  " + agent + marker);
            }
        } catch (IllegalStateException e) {
            io.println("Session management not available: " + e.getMessage(), 3);
        }
    }
}