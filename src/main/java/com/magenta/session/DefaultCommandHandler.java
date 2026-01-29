package com.magenta.session;

import com.magenta.io.Command;

/**
 * Default implementation of CommandHandler.
 * Handles standard commands like /exit, /help, /clear, /history.
 * Also handles session management commands: /agent, /sessions, /agents.
 */
public class DefaultCommandHandler implements CommandHandler {

    @Override
    public void handle(Session session, Command command) {
        var io = session.io();
        switch (command) {
            case Command.Exit() -> session.setExit(true);
            case Command.Help() -> printHelp(io);
            case Command.Clear() -> {
                // Clear is handled by IOManager directly
            }
            case Command.History() -> io.println("History not yet implemented");
            case Command.Agent(String agentName) -> switchAgent(session, agentName);
            case Command.Sessions() -> listSessions(io);
            case Command.Agents() -> listAgents(io);
            case Command.Unknown(String raw) -> io.println("Unknown command: " + raw);
        }
    }

    private void printHelp(com.magenta.io.IOManager io) {
        io.println("Available commands:");
        io.println("  /exit, /quit, /q - Exit the session");
        io.println("  /help, /? - Show this help message");
        io.println("  /clear, /cls - Clear the screen");
        io.println("  /history - Show conversation history");
        io.println("  /agent <name> - Switch to a different agent");
        io.println("  /sessions - List active sessions");
        io.println("  /agents - List available agent configurations");
    }

    private void switchAgent(Session session, String agentName) {
        try {
            SessionManager.getInstance().switchToAgent(agentName);
        } catch (IllegalArgumentException e) {
            session.io().println("Error: " + e.getMessage(), 1); // Red error color
        } catch (IllegalStateException e) {
            session.io().println("Session switching not available: " + e.getMessage(), 3); // Yellow warning
        }
    }

    private void listSessions(com.magenta.io.IOManager io) {
        try {
            var sessions = SessionManager.getInstance().listActiveSessions();
            var current = SessionManager.getInstance().getCurrentSessionName();

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
            io.println("Session management not available: " + e.getMessage(), 3); // Yellow warning
        }
    }

    private void listAgents(com.magenta.io.IOManager io) {
        try {
            var agents = SessionManager.getInstance().listAvailableAgents();
            var current = SessionManager.getInstance().getCurrentSessionName();

            io.println("Available agents:");
            for (String agent : agents) {
                String marker = agent.equals(current) ? " (current)" : "";
                io.println("  " + agent + marker);
            }
        } catch (IllegalStateException e) {
            io.println("Session management not available: " + e.getMessage(), 3); // Yellow warning
        }
    }
}
