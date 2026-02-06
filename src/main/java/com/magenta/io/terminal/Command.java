package com.magenta.io.terminal;

import com.magenta.config.ConfigManager;
import org.jline.reader.Candidate;

import java.util.List;
import java.util.Optional;


public sealed interface Command {

    /**
     * Provide completion candidates for this command's arguments.
     * Default returns no completions. Override for commands with completable args.
     */
    default CompletionProvider completionProvider() {
        return CompletionProvider.NONE;
    }

    // === Commands without argument completion ===

    record Exit() implements Command {}
    record Help() implements Command {}
    record Clear() implements Command {}
    record Sessions() implements Command {}
    record Agents() implements Command {}
    record ConfigShow() implements Command {}
    record ConfigReload() implements Command {}
    record Messages() implements Command {}
    record Network() implements Command {}
    record Bash(String command) implements Command {}
    record Message(String targetAgent, String message) implements Command {}
    record Delegate(String targetAgent, String taskTemplateKey) implements Command {}
    record Unknown(String raw) implements Command {}

    // === Commands with argument completion ===

    record Agent(String agentName) implements Command {
        @Override
        public CompletionProvider completionProvider() {
            return (session) -> ConfigManager.config()
                .agents
                .keySet()
                .stream()
                .map(name -> new Candidate(name, name, "agents",
                    "Switch to " + name, null, null, true))
                .toList();
        }
    }

    record History() implements Command {
        @Override
        public CompletionProvider completionProvider() {
            return (session) -> List.of(
                new Candidate("show", "show", "history", "Show last N messages", null, null, true),
                new Candidate("search", "search", "history", "Search history", null, null, true)
            );
        }
    }

    record HistoryShow(int limit) implements Command {}
    record HistorySearch(String query) implements Command {}

    record Context(String subCommand, String arg) implements Command {
        @Override
        public CompletionProvider completionProvider() {
            return (session) -> List.of(
                new Candidate("status", "status", "context", "Show context statistics", null, null, true),
                new Candidate("compact", "compact", "context", "Compact conversation history", null, null, true),
                new Candidate("clear", "clear", "context", "Clear all context", null, null, true),
                new Candidate("archive", "archive", "context", "Archive context with key", null, null, true),
                new Candidate("load", "load", "context", "Load archived context", null, null, true)
            );
        }
    }

    record WorkflowTask(String subCommand, String arg) implements Command {
        @Override
        public CompletionProvider completionProvider() {
            return (session) -> List.of(
                new Candidate("list", "list", "tasks", "List task templates", null, null, true),
                new Candidate("show", "show", "tasks", "Show task details", null, null, true),
                new Candidate("run", "run", "tasks", "Run a task template", null, null, true),
                new Candidate("clear", "clear", "tasks", "Clear active task", null, null, true),
                new Candidate("status", "status", "tasks", "Show active task status", null, null, true)
            );
        }
    }

    record ConfigShowSection(String section) implements Command {
        @Override
        public CompletionProvider completionProvider() {
            return (session) -> List.of(
                new Candidate("agents", "agents", "config", "Show agents config", null, null, true),
                new Candidate("models", "models", "config", "Show models config", null, null, true),
                new Candidate("endpoints", "endpoints", "config", "Show endpoints config", null, null, true),
                new Candidate("securities", "securities", "config", "Show security config", null, null, true),
                new Candidate("colors", "colors", "config", "Show colors config", null, null, true),
                new Candidate("tasks", "tasks", "config", "Show task templates", null, null, true)
            );
        }
    }

    record View(String viewName) implements Command {
        @Override
        public CompletionProvider completionProvider() {
            return (session) -> List.of(
                new Candidate("chat", "chat", "views", "Chat view", null, null, true),
                new Candidate("dashboard", "dashboard", "views", "Dashboard view", null, null, true)
            );
        }
    }

    record Dashboard() implements Command {}

    static Optional<Command> tryParse(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        // Check for bash command (! prefix)
        if (input.startsWith("!")) {
            String bashCmd = input.substring(1).trim();
            if (bashCmd.isEmpty()) {
                return Optional.of(new Unknown(input));
            }
            return Optional.of(new Bash(bashCmd));
        }

        // Check for internal command (/ prefix)
        if (!input.startsWith("/")) {
            return Optional.empty();
        }

        String cmd = input.substring(1).trim();
        String[] parts = cmd.split("\\s+", 3);
        String commandName = parts[0].toLowerCase();

        Command command = switch (commandName) {
            case "exit", "quit", "q" -> new Exit();
            case "help", "?" -> new Help();
            case "clear", "cls" -> new Clear();
            case "history" -> {
                if (parts.length == 1) {
                    yield new History();  // /history (default: show last 20)
                }

                String subCmd = parts[1].toLowerCase();
                if (subCmd.equals("search") && parts.length >= 3) {
                    yield new HistorySearch(parts[2]);  // /history search <query>
                } else if (subCmd.equals("show") && parts.length >= 3) {
                    try {
                        int limit = Integer.parseInt(parts[2]);
                        yield new HistoryShow(limit);  // /history show <n>
                    } catch (NumberFormatException e) {
                        yield new Unknown(input);
                    }
                } else {
                    yield new Unknown(input);
                }
            }
            case "agent" -> {
                if (parts.length < 2 || parts[1].isBlank()) {
                    yield new Unknown(input); // Missing agent name
                }
                yield new Agent(parts[1].trim());
            }
            case "sessions" -> new Sessions();
            case "agents" -> new Agents();
            case "context" -> {
                // /context <subcommand> [arg]
                String subCmd = parts.length > 1 ? parts[1].toLowerCase() : "status";
                String arg = parts.length > 2 ? parts[2].trim() : "";
                yield new Context(subCmd, arg);
            }
            case "task" -> {
                String subCmd = parts.length > 1 ? parts[1].toLowerCase() : "list";
                String arg = parts.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length)) : "";
                yield new WorkflowTask(subCmd, arg);
            }
            case "message", "msg" -> {
                // /message <agent> <text>
                if (parts.length < 3) {
                    yield new Unknown(input);
                }
                String target = parts[1];
                String msg = parts[2]; // parts[2] contains everything after the second space
                yield new Message(target, msg);
            }
            case "messages", "inbox" -> new Messages();
            case "delegate" -> {
                // /delegate <agent> <template>
                if (parts.length < 3) {
                    yield new Unknown(input);
                }
                yield new Delegate(parts[1], parts[2]);
            }
            case "network" -> new Network();
            case "config", "cfg" -> {
                if (parts.length == 1) {
                    yield new ConfigShow();  // /config (show summary)
                }

                String subCmd = parts[1].toLowerCase();
                if (subCmd.equals("show") && parts.length >= 3) {
                    yield new ConfigShowSection(parts[2]);  // /config show agents
                } else if (subCmd.equals("reload")) {
                    yield new ConfigReload();
                } else {
                    yield new Unknown(input);
                }
            }
            case "view" -> {
                if (parts.length < 2 || parts[1].isBlank()) {
                    yield new Unknown(input);
                }
                yield new View(parts[1].toLowerCase());
            }
            case "dashboard" -> new Dashboard();
            default -> new Unknown(input);
        };

        return Optional.of(command);
    }
}
