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
            case Command.History() -> io.outputPipe().print("History not yet implemented\n");
            case Command.Agent(String rawArg) -> switchAgent(session, rawArg);
            case Command.Sessions() -> listSessions(io);
            case Command.Agents() -> listAgents(io);
            case Command.Unknown(String raw) -> io.outputPipe().print("Unknown command: " + raw + "\n");
        }
    }

    private void printHelp(IOManager io) {
        io.outputPipe().print("Available commands:\n");
        io.outputPipe().print("  /exit, /quit, /q - Exit the session\n");
        io.outputPipe().print("  /help, /? - Show this help message\n");
        io.outputPipe().print("  /clear, /cls - Clear the screen\n");
        io.outputPipe().print("  /history - Show conversation history\n");
        io.outputPipe().print("  /agent <name> [alias] - Switch to a different agent/session\n");
        io.outputPipe().print("  /sessions - List active sessions\n");
        io.outputPipe().print("  /agents - List available agent configurations\n");
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
            session.io().outputPipe().print("Error: " + e.getMessage() + "\n");
        } catch (IllegalStateException e) {
            session.io().outputPipe().print("Session switching not available: " + e.getMessage() + "\n");
        }
    }

    private void listSessions(IOManager io) {
        try {
            var sessions = SessionManager.getInstance().listActiveSessions();
            var current = SessionManager.getInstance().getCurrentSessionAlias();

            io.outputPipe().print("Active sessions:\n");
            if (sessions.isEmpty()) {
                io.outputPipe().print("  (none)\n");
            } else {
                for (String session : sessions) {
                    String marker = session.equals(current) ? " *" : "";
                    io.outputPipe().print("  " + session + marker + "\n");
                }
            }
        } catch (IllegalStateException e) {
            io.outputPipe().print("Session management not available: " + e.getMessage() + "\n");
        }
    }

    private void listAgents(IOManager io) {
        try {
            var agents = SessionManager.getInstance().listAvailableAgents();
            var currentAlias = SessionManager.getInstance().getCurrentSessionAlias();

            io.outputPipe().print("Available agents (configs):\n");
            for (String agent : agents) {
                String marker = agent.equals(currentAlias) ? " (active)" : "";
                io.outputPipe().print("  " + agent + marker + "\n");
            }
        } catch (IllegalStateException e) {
            io.outputPipe().print("Session management not available: " + e.getMessage() + "\n");
        }
    }
}