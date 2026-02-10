package com.magenta.session;

import com.magenta.Magenta;
import com.magenta.manager.AgentNetwork;
import com.magenta.context.Context;
import com.magenta.context.ContextLimits;
import com.magenta.manager.ContextManager;
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
 * System command definitions - framework-level commands that use Magenta services.
 * Terminal-specific commands (AGENT, SESSIONS, AGENTS) are injected separately
 * by TerminalSession via the session commands mechanism.
 */
public final class SystemCommands {

    private final Magenta magenta;

    public SystemCommands(Magenta magenta) {
        this.magenta = magenta;
    }

    // ===== Commands =====

    public CommandSet commands() {
        return CommandSet.of(
            exitCmd(), helpCmd(), clearCmd(), contextCmd(), networkCmd(),
            viewCmd(), dashboardCmd(), bashCmd()
        );
    }

    private Command exitCmd() {
        return command("exit", "Exit the session", List.of(),
            raw -> slash(raw, "exit", "quit", "q"),
            (session, raw) -> session.setExit(true));
    }

    private Command helpCmd() {
        // Capture commands() result lazily to avoid circular reference
        return command("help", "Show help message", List.of(),
            raw -> slash(raw, "help", "?"),
            (session, raw) -> {
                if (session instanceof AgentSession as) {
                    as.commandSet().printHelp(session.io());
                }
            });
    }

    private Command clearCmd() {
        return command("clear", "Clear the screen", List.of(),
            raw -> slash(raw, "clear", "cls"),
            (session, raw) -> { /* handled by IOManager */ });
    }

    private Command contextCmd() {
        return command("context", "Manage context (status, compact, clear, save, archive, load)",
            SystemCommands::contextCompletions,
            raw -> slash(raw, "context"),
            (session, raw) -> handleContext(session, arg(raw, 1, "status"), arg(raw, 2, "")));
    }

    private Command networkCmd() {
        return command("network", "View agent network status", List.of(),
            raw -> slash(raw, "network"),
            (session, raw) -> showNetwork(session.io()));
    }

    private Command viewCmd() {
        return command("view", "Switch terminal view (chat, dashboard)",
            SystemCommands::viewCompletions,
            raw -> slash(raw, "view") && !argString(raw).isBlank(),
            (session, raw) -> handleView(session, argString(raw)));
    }

    private Command dashboardCmd() {
        return command("dashboard", "Show dashboard view", List.of(),
            raw -> slash(raw, "dashboard"),
            (session, raw) -> handleView(session, "dashboard"));
    }

    private Command bashCmd() {
        return Command.of("bash", "Execute bash command with security filtering", List.of(),
            raw -> raw != null && raw.startsWith("!") && raw.length() > 1,
            (session, raw) -> executeBash(session, raw.substring(1).trim()));
    }

    // ===== Helpers =====

    private static Command command(
        String name, String description, List<Candidate> completions,
        Predicate<String> matcher, BiConsumer<Session, String> handler
    ) {
        return Command.of(name, description, completions, matcher, handler);
    }

    private static Command command(
        String name, String description, Supplier<List<Candidate>> completions,
        Predicate<String> matcher, BiConsumer<Session, String> handler
    ) {
        return Command.of(name, description, completions, matcher, handler);
    }

    private static boolean slash(String raw, String... names) {
        if (raw == null || !raw.startsWith("/")) return false;
        String cmd = cmdName(raw);
        for (String n : names) {
            if (cmd.equals(n)) return true;
        }
        return false;
    }

    private static String cmdName(String raw) {
        if (raw == null || !raw.startsWith("/")) return "";
        String cmd = raw.substring(1).trim();
        int idx = cmd.indexOf(' ');
        return idx < 0 ? cmd.toLowerCase() : cmd.substring(0, idx).toLowerCase();
    }

    private static String argString(String raw) {
        if (raw == null || !raw.startsWith("/")) return "";
        String cmd = raw.substring(1).trim();
        int idx = cmd.indexOf(' ');
        return idx < 0 ? "" : cmd.substring(idx + 1).trim();
    }

    private static String arg(String raw, int index, String defaultVal) {
        String[] parts = argString(raw).split("\\s+");
        return index < parts.length ? parts[index] : defaultVal;
    }

    // ===== Completions =====

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

    private void handleContext(Session session, String subCmd, String arg) {
        if (!(session instanceof AgentSession agentSession)) {
            session.io().print("Context commands only available in agent sessions\n");
            return;
        }

        IOManager io = session.io();
        SessionId sessionId = agentSession.sessionId();
        ContextManager cm = magenta.contextManager();
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
            AgentNetwork network = magenta.agentNetwork();
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
