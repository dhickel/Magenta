package com.magenta.session;

import com.magenta.context.manager.ContextManager;
import com.magenta.context.model.Context;
import com.magenta.context.policy.ContextLimits;
import com.magenta.context.policy.ContextWindowManager;
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
            case Command.Context(String subCmd, String arg) -> handleContext(session, subCmd, arg);
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
        io.outputPipe().print("  /context [status|compact|clear|archive|load] - Manage conversation context\n");
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

    private void handleContext(Session session, String subCmd, String arg) {
        IOManager io = session.io();

        // Context commands only work with AgentSession
        if (!(session instanceof AgentSession agentSession)) {
            io.outputPipe().print("Context commands only available in agent sessions\n");
            return;
        }

        SessionId sessionId = agentSession.sessionId();
        ContextManager cm = ContextManager.getInstance();
        Context context = cm.loadContext(sessionId);

        ContextLimits limits = new ContextLimits(
            agentSession.agent().config().model().maxContext(),
            agentSession.agent().config().model().compactThreshold()
        );

        switch (subCmd) {
            case "status" -> showContextStatus(io, context, limits, cm);
            case "compact" -> compactContext(io, context, limits, cm);
            case "clear" -> clearContext(io, context);
            case "archive" -> archiveContext(io, context, arg, cm);
            case "load" -> loadContext(io, sessionId, arg, limits, cm);
            default -> io.outputPipe().print("Unknown context subcommand: " + subCmd +
                "\nAvailable: status, compact, clear, archive <key>, load <key>\n");
        }
    }

    private void showContextStatus(IOManager io, Context context, ContextLimits limits, ContextManager cm) {
        ContextWindowManager wm = cm.windowManager();
        if (wm == null) {
            io.outputPipe().print("Context statistics not available\n");
            return;
        }

        var stats = wm.getStats(context, limits);
        io.outputPipe().print("Context Status:\n");
        io.outputPipe().print("  " + stats.toSummary() + "\n");
    }

    private void compactContext(IOManager io, Context context, ContextLimits limits, ContextManager cm) {
        ContextWindowManager wm = cm.windowManager();
        if (wm == null) {
            io.outputPipe().print("Context compaction not available\n");
            return;
        }

        int beforeTokens = context.totalEstimatedTokens();
        int beforeElements = context.getElements().size();

        wm.forceCompact(context, limits);

        int afterTokens = context.totalEstimatedTokens();
        int afterElements = context.getElements().size();

        io.outputPipe().print(String.format(
            "Context compacted: %d → %d elements, %d → %d tokens (saved %d tokens)\n",
            beforeElements, afterElements,
            beforeTokens, afterTokens,
            beforeTokens - afterTokens
        ));
    }

    private void clearContext(IOManager io, Context context) {
        int elementCount = context.getElements().size();
        context.setElements(java.util.List.of());
        io.outputPipe().print("Context cleared. Removed " + elementCount + " elements.\n");
    }

    private void archiveContext(IOManager io, Context context, String key, ContextManager cm) {
        if (key == null || key.isBlank()) {
            io.outputPipe().print("Usage: /context archive <key>\n");
            return;
        }

        cm.archiveContext(key, context);
        io.outputPipe().print("Context archived with key: " + key + " (" +
            context.getElements().size() + " elements, " +
            context.totalEstimatedTokens() + " tokens)\n");
    }

    private void loadContext(IOManager io, SessionId sessionId, String key, ContextLimits limits, ContextManager cm) {
        if (key == null || key.isBlank()) {
            io.outputPipe().print("Usage: /context load <key>\n");
            return;
        }

        var archived = cm.retrieveArchivedContext(key);
        if (archived.isEmpty()) {
            io.outputPipe().print("No archived context found for key: " + key + "\n");
            return;
        }

        String summaryText = "Loaded context '" + key + "' with " +
            archived.get().getElements().size() + " elements.";

        var summary = new com.magenta.context.model.ContextElement.Summary(
            summaryText, key, archived.get().getElements()
        );

        cm.append(sessionId, summary, limits);
        io.outputPipe().print(summaryText + "\n");
    }
}