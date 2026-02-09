package com.magenta.session;

import com.magenta.agent.AgentNetwork;
import com.magenta.config.ConfigManager;
import com.magenta.context.Context;
import com.magenta.context.ContextLimits;
import com.magenta.context.ContextManager;
import com.magenta.io.IOManager;
import com.magenta.io.terminal.BashExecutor;
import com.magenta.io.terminal.Command;
import com.magenta.io.terminal.CommandSet;
import com.magenta.io.terminal.StatusBar;
import org.jline.reader.Candidate;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * System command definitions - simple and focused.
 */
public final class SystemCommands {

    private static final SystemCommands INSTANCE = new SystemCommands();

    private SystemCommands() {
    }

    // ===== Commands =====

    public static final Command EXIT = command(
        "exit",
        "Exit the session",
        List.of(),
        raw -> slash(raw, "exit", "quit", "q"),
        (session, raw) -> session.setExit(true)
    );

    public static final Command HELP = command(
        "help",
        "Show help message",
        List.of(),
        raw -> slash(raw, "help", "?"),
        (session, raw) -> commands().printHelp(session.io())
    );

    public static final Command CLEAR = command(
        "clear",
        "Clear the screen",
        List.of(),
        raw -> slash(raw, "clear", "cls"),
        (session, raw) -> { /* handled by IOManager */ }
    );

    public static final Command AGENT = command(
        "agent",
        "Switch to different agent",
        SystemCommands::agentCompletions,
        raw -> slash(raw, "agent") && !argString(raw).isBlank(),
        (session, raw) -> INSTANCE.switchAgent(session, argString(raw).split("\\s+")[0])
    );

    public static final Command SESSIONS = command(
        "sessions",
        "List active sessions",
        List.of(),
        raw -> slash(raw, "sessions"),
        (session, raw) -> SessionManager.getInstance().printSessions(session.io())
    );

    public static final Command AGENTS = command(
        "agents",
        "List available agent configurations",
        List.of(),
        raw -> slash(raw, "agents"),
        (session, raw) -> SessionManager.getInstance().printAgents(session.io())
    );

    public static final Command CONTEXT = command(
        "context",
        "Manage context (status, compact, clear, save, archive, load)",
        SystemCommands::contextCompletions,
        raw -> slash(raw, "context"),
        (session, raw) -> INSTANCE.handleContext(session, arg(raw, 1, "status"), arg(raw, 2, ""))
    );

    public static final Command NETWORK = command(
        "network",
        "View agent network status",
        List.of(),
        raw -> slash(raw, "network"),
        (session, raw) -> INSTANCE.showNetwork(session.io())
    );

    public static final Command VIEW = command(
        "view",
        "Switch terminal view (chat, dashboard)",
        SystemCommands::viewCompletions,
        raw -> slash(raw, "view") && !argString(raw).isBlank(),
        (session, raw) -> INSTANCE.handleView(session, argString(raw))
    );

    public static final Command DASHBOARD = command(
        "dashboard",
        "Show dashboard view",
        List.of(),
        raw -> slash(raw, "dashboard"),
        (session, raw) -> INSTANCE.handleView(session, "dashboard")
    );

    public static final Command BASH = Command.of(
        "bash",
        "Execute bash command with security filtering",
        List.of(),
        raw -> raw != null && raw.startsWith("!") && raw.length() > 1,
        (session, raw) -> INSTANCE.executeBash(session, raw.substring(1).trim())
    );

    /**
     * Get the system command set.
     */
    public static CommandSet commands() {
        return CommandSet.of(
            EXIT, HELP, CLEAR, AGENT, SESSIONS, AGENTS, CONTEXT, NETWORK, VIEW, DASHBOARD, BASH
        );
    }

    // ===== Helpers =====

    private static Command command(
        String name,
        String description,
        List<Candidate> completions,
        Predicate<String> matcher,
        BiConsumer<Session, String> handler
    ) {
        return Command.of(name, description, completions, matcher, handler);
    }

    private static Command command(
        String name,
        String description,
        Supplier<List<Candidate>> completions,
        Predicate<String> matcher,
        BiConsumer<Session, String> handler
    ) {
        return Command.of(name, description, completions, matcher, handler);
    }

    // Simple matching: /name or /name1 or /name2 or ...
    private static boolean slash(String raw, String... names) {
        if (raw == null || !raw.startsWith("/")) return false;
        String cmd = cmdName(raw);
        for (String n : names) {
            if (cmd.equals(n)) return true;
        }
        return false;
    }

    // Extract command name: /foo bar -> foo
    private static String cmdName(String raw) {
        if (raw == null || !raw.startsWith("/")) return "";
        String cmd = raw.substring(1).trim();
        int idx = cmd.indexOf(' ');
        return idx < 0 ? cmd.toLowerCase() : cmd.substring(0, idx).toLowerCase();
    }

    // Extract all args after command: /foo bar baz -> "bar baz"
    private static String argString(String raw) {
        if (raw == null || !raw.startsWith("/")) return "";
        String cmd = raw.substring(1).trim();
        int idx = cmd.indexOf(' ');
        return idx < 0 ? "" : cmd.substring(idx + 1).trim();
    }

    // Extract specific arg by index: arg(raw, 1) = first arg
    private static String arg(String raw, int index, String defaultVal) {
        String[] parts = argString(raw).split("\\s+");
        return index < parts.length ? parts[index] : defaultVal;
    }

    // ===== Completions =====

    private static List<Candidate> agentCompletions() {
        return ConfigManager.config()
            .agents
            .keySet()
            .stream()
            .map(name -> new Candidate(name, name, "agents", "Switch to " + name, null, null, true))
            .toList();
    }

    private static List<Candidate> contextCompletions() {
        return List.of(
            new Candidate("status", "status", "context", "Show context statistics", null, null, true),
            new Candidate("compact", "compact", "context", "Compact conversation history", null, null, true),
            new Candidate("clear", "clear", "context", "Clear all context", null, null, true),
            new Candidate("save", "save", "context", "Save context to database immediately", null, null, true),
            new Candidate("archive", "archive", "context", "Archive context with key", null, null, true),
            new Candidate("load", "load", "context", "Load archived context", null, null, true)
        );
    }

    private static List<Candidate> viewCompletions() {
        return List.of(
            new Candidate("chat", "chat", "views", "Chat view", null, null, true),
            new Candidate("dashboard", "dashboard", "views", "Dashboard view", null, null, true)
        );
    }

    // ===== Handlers =====

    private void switchAgent(Session session, String agentName) {
        try {
            SessionManager sm = SessionManager.getInstance();
            var config = ConfigManager.config();
            var agentConfig = config.agents.get(agentName);
            if (agentConfig == null) {
                session.io().print("Error: Unknown agent: " + agentName + "\n");
                return;
            }

            // Check if already exists
            SessionAlias alias = SessionAlias.of(agentName);
            if (sm.getSession(alias) != null) {
                sm.switchToSession(alias);
                return;
            }

            // Create and switch
            session.io().print("Creating session for: " + agentName + "\n");
            sm.createSession(alias, agentName);
            sm.switchToSession(alias);

        } catch (Exception e) {
            session.io().print("Error: " + e.getMessage() + "\n");
        }
    }

    private void handleContext(Session session, String subCmd, String arg) {
        if (!(session instanceof AgentSession agentSession)) {
            session.io().print("Context commands only available in agent sessions\n");
            return;
        }

        IOManager io = session.io();
        SessionId sessionId = agentSession.sessionId();
        ContextManager cm = ContextManager.getInstance();
        Context context = cm.loadContext(sessionId);
        ContextLimits limits = agentSession.contextLimits();

        switch (subCmd) {
            case "status" -> cm.printStatus(io, context, limits);
            case "compact" -> cm.printCompact(io, context, limits);
            case "clear" -> cm.printClear(io, context);
            case "save" -> {
                cm.flushContext(sessionId);
                io.print("Context saved to database (" +
                    context.getElements().size() + " elements, " +
                    context.totalEstimatedTokens() + " tokens)\n");
            }
            case "archive" -> cm.printArchive(io, context, arg);
            case "load" -> cm.printLoad(io, sessionId, arg, limits);
            default -> io.print("Unknown context subcommand: " + subCmd + "\n");
        }
    }

    private void handleView(Session session, String viewName) {
        if (!(session instanceof AgentSession agentSession)) {
            session.io().print("Views only available in agent sessions\n");
            return;
        }

        IOManager io = session.io();
        TerminalView view = switch (viewName.toLowerCase()) {
            case "chat" -> new TerminalView.Chat();
            case "dashboard" -> createDashboard();
            default -> {
                io.print("Unknown view: " + viewName + "\n");
                yield null;
            }
        };

        if (view != null) {
            agentSession.setView(view);
        }
    }

    private TerminalView createDashboard() {
        return TerminalView.builder()
            .header(ViewComponent.title("=== Magenta Dashboard ==="))
            .header(ViewComponent.blank())
            .content(new TerminalView.Dashboard())
            .footer(ViewComponent.separator())
            .footer(ViewComponent.styled(
                "Commands: /view chat | /exit-dashboard | /help",
                org.jline.utils.AttributedStyle.DEFAULT.faint()
            ))
            .statusBar(StatusBar::aligned, TerminalView.StatusPosition.BOTTOM_RIGHT)
            .build();
    }

    private void executeBash(Session session, String command) {
        if (!(session instanceof AgentSession agentSession)) {
            session.io().print("Bash commands only available in agent sessions\n");
            return;
        }

        IOManager io = session.io();
        BashExecutor.BashResult result = BashExecutor.execute(command);
        io.print("Exit Code: " + result.exitCode() + "\n");
        io.print("Output:\n" + result.output() + "\n");
        if (!result.error().isEmpty()) {
            io.print("Error: " + result.error() + "\n");
        }
    }

    private void showNetwork(IOManager io) {
        try {
            AgentNetwork network = AgentNetwork.getInstance();
            var agents = network.listRegisteredAgents();

            if (agents.isEmpty()) {
                io.print("No agents in network.\n");
                return;
            }

            io.print("Agent Network Status:\n");
            for (SessionMeta meta : agents) {
                String alias = meta.sessionAlias().value();
                int msgCount = network.getMessageCount(alias);
                String msgInfo = msgCount > 0 ? " (" + msgCount + " messages)" : "";
                io.print("  " + alias + msgInfo + "\n");
            }
        } catch (Exception e) {
            io.print("Error: " + e.getMessage() + "\n");
        }
    }
}
