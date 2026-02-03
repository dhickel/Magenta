package com.magenta.io.terminal;

import java.util.Optional;


public sealed interface Command {

    record Exit() implements Command {}
    record Help() implements Command {}
    record Clear() implements Command {}
    record History() implements Command {}
    record HistoryShow(int limit) implements Command {}
    record HistorySearch(String query) implements Command {}
    record Agent(String agentName) implements Command {}
    record Sessions() implements Command {}
    record Agents() implements Command {}
    record Context(String subCommand, String arg) implements Command {}
    record WorkflowTask(String subCommand, String arg) implements Command {}
    record ConfigShow() implements Command {}
    record ConfigShowSection(String section) implements Command {}
    record ConfigReload() implements Command {}
    record Bash(String command) implements Command {}
    record Message(String targetAgent, String message) implements Command {}
    record Messages() implements Command {}
    record Delegate(String targetAgent, String taskTemplateKey) implements Command {}
    record Network() implements Command {}
    record Unknown(String raw) implements Command {}


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
            default -> new Unknown(input);
        };

        return Optional.of(command);
    }
}
