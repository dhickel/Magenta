package com.magenta.session;

import com.magenta.io.terminal.Command;
import com.magenta.io.terminal.CompletionProvider;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Context-aware command completer for Magenta.
 * Delegates to Command.completionProvider() for command-specific completions.
 * Stateless - queries SessionManager for current session dynamically.
 */
public class MagentaCompleter implements Completer {

    // Prototype commands for each slash command (used for completion lookup)
    private static final Map<String, Command> COMMANDS;

    static {
        var map = new LinkedHashMap<String, Command>();
        map.put("/exit", new Command.Exit());
        map.put("/help", new Command.Help());
        map.put("/clear", new Command.Clear());
        map.put("/history", new Command.History());
        map.put("/agent", new Command.Agent(""));
        map.put("/sessions", new Command.Sessions());
        map.put("/agents", new Command.Agents());
        map.put("/context", new Command.Context("", ""));
        map.put("/task", new Command.WorkflowTask("", ""));
        map.put("/config", new Command.ConfigShow());
        map.put("/message", new Command.Message("", ""));
        map.put("/messages", new Command.Messages());
        map.put("/delegate", new Command.Delegate("", ""));
        map.put("/network", new Command.Network());
        map.put("/view", new Command.View(""));
        map.put("/dashboard", new Command.Dashboard());
        COMMANDS = Map.copyOf(map);
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.line();

        // Only complete slash commands
        if (!buffer.startsWith("/")) {
            return;
        }

        String[] parts = buffer.split("\\s+", 2);
        String commandPart = parts[0];

        if (parts.length == 1 && !buffer.endsWith(" ")) {
            // Complete command name (user is still typing the command)
            completeCommandName(commandPart, candidates);
        } else {
            // Complete command arguments (command name is complete, user typing args)
            completeCommandArgs(commandPart, candidates);
        }

        // Add view-specific completions
        addViewSpecificCandidates(buffer, candidates);
    }

    private void completeCommandName(String prefix, List<Candidate> candidates) {
        for (var entry : COMMANDS.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                candidates.add(new Candidate(
                    entry.getKey(),
                    entry.getKey(),
                    "commands",
                    descriptionOf(entry.getValue()),
                    null,
                    null,
                    true
                ));
            }
        }
    }

    private void completeCommandArgs(String command, List<Candidate> candidates) {
        // Special case: /config show <section> needs ConfigShowSection's provider
        if (command.equals("/config")) {
            CompletionProvider provider = new Command.ConfigShowSection("").completionProvider();
            AgentSession session = currentSession();
            if (session != null) {
                candidates.addAll(provider.provide(session));
            }
            // Also add "reload" as a subcommand
            candidates.add(new Candidate("show", "show", "config", "Show config section", null, null, true));
            candidates.add(new Candidate("reload", "reload", "config", "Reload configuration", null, null, true));
            return;
        }

        Command cmd = COMMANDS.get(command);
        if (cmd == null) return;

        CompletionProvider provider = cmd.completionProvider();
        AgentSession session = currentSession();
        if (session != null) {
            candidates.addAll(provider.provide(session));
        }
    }

    private void addViewSpecificCandidates(String buffer, List<Candidate> candidates) {
        AgentSession session = currentSession();
        if (session == null) return;

        // Dashboard-specific commands
        if (session.currentView() instanceof TerminalView.Dashboard) {
            if ("/exit-dashboard".startsWith(buffer)) {
                candidates.add(new Candidate(
                    "/exit-dashboard", "/exit-dashboard", "view",
                    "Return to chat view", null, null, true
                ));
            }
        }

        // Table-specific commands
        if (session.currentView() instanceof TerminalView.Table<?>) {
            if ("/exit-table".startsWith(buffer)) {
                candidates.add(new Candidate(
                    "/exit-table", "/exit-table", "view",
                    "Return to chat view", null, null, true
                ));
            }
        }
    }

    private AgentSession currentSession() {
        try {
            var sm = SessionManager.getInstance();
            String alias = sm.getCurrentSessionAlias();
            return sm.getSession(SessionAlias.of(alias));
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private static String descriptionOf(Command cmd) {
        return switch (cmd) {
            case Command.Exit() -> "Exit the session";
            case Command.Help() -> "Show help message";
            case Command.Clear() -> "Clear screen";
            case Command.History() -> "View conversation history";
            case Command.HistoryShow(var l) -> "Show last N messages";
            case Command.HistorySearch(var q) -> "Search history";
            case Command.Agent(var n) -> "Switch to different agent";
            case Command.Sessions() -> "List active sessions";
            case Command.Agents() -> "List available agents";
            case Command.Context(var s, var a) -> "Manage conversation context";
            case Command.WorkflowTask(var s, var a) -> "Manage workflow tasks";
            case Command.ConfigShow() -> "View configuration";
            case Command.ConfigShowSection(var s) -> "View config section";
            case Command.ConfigReload() -> "Reload configuration";
            case Command.Bash(var c) -> "Execute bash command";
            case Command.Message(var t, var m) -> "Send message to agent";
            case Command.Messages() -> "Check messages";
            case Command.Delegate(var t, var k) -> "Delegate task to agent";
            case Command.Network() -> "View agent network";
            case Command.View(var v) -> "Switch terminal view";
            case Command.Dashboard() -> "Show dashboard view";
            case Command.Unknown(var r) -> null;
        };
    }
}
