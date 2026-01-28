# Pluggable Commands (Planned Feature)

**Status:** Design Phase
**Target Version:** 2.0

## Overview

Pluggable commands enable context-specific command registration, allowing different components (agents, context managers, networks) to expose their own slash commands dynamically.

## Vision

```
Terminal Context:
  /exit, /help, /clear, /history (built-in)
  /context status, /context summarize (from ContextManager)
  /agents list, /agents switch (from AgentNetwork)
  /task add, /task list (from TodoService)

Internal Context:
  /status, /ping (agent-to-agent commands)

Network Context:
  /broadcast, /discover (network-specific)
```

## Current State

Commands are currently hardcoded in `Command.parse()`:

```java
public static Command parse(String input) {
    return switch (input.trim()) {
        case "/exit", "/quit" -> new Exit();
        case "/help" -> new Help();
        case "/clear" -> new Clear();
        case "/history" -> new History();
        default -> input.startsWith("/")
            ? new Unknown(input)
            : new Message(input);
    };
}
```

**Limitations:**
- No way to add commands without modifying `Command.java`
- Commands not context-aware
- No command discovery or help generation

## Planned Design

### 1. CommandRegistry

**Purpose:** Central registry for pluggable commands

```java
// Planned API (subject to change)
public class CommandRegistry {
    private final Map<String, CommandHandler> handlers;
    private final Map<String, CommandMetadata> metadata;

    // Register command handler
    public void register(String name, CommandHandler handler, CommandMetadata meta) {
        handlers.put(name, handler);
        metadata.put(name, meta);
    }

    // Unregister command
    public void unregister(String name) {
        handlers.remove(name);
        metadata.remove(name);
    }

    // Handle command
    public Optional<String> handle(String commandName, List<String> args, Session session) {
        CommandHandler handler = handlers.get(commandName);
        if (handler == null) return Optional.empty();

        CommandContext ctx = new CommandContext(session, args);
        return Optional.of(handler.execute(ctx));
    }

    // Get all registered commands
    public Set<String> getAllCommands() {
        return handlers.keySet();
    }

    // Get command help
    public String getHelp(String commandName) {
        CommandMetadata meta = metadata.get(commandName);
        return meta != null ? meta.helpText() : "No help available";
    }
}
```

### 2. CommandHandler Interface

**Purpose:** Handler for a specific command

```java
public interface CommandHandler {
    String execute(CommandContext context);
}

public record CommandContext(
    Session session,
    List<String> args
) {
    public IOManager io() { return session.io(); }
    public GlobalContext context() { return session.context(); }
    public String arg(int index, String defaultValue) {
        return index < args.size() ? args.get(index) : defaultValue;
    }
}
```

### 3. CommandMetadata

**Purpose:** Metadata about a command

```java
public record CommandMetadata(
    String name,
    String description,
    String usage,
    List<CommandParameter> parameters,
    Set<String> aliases
) {
    public String helpText() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (!aliases.isEmpty()) {
            sb.append(" (aliases: ").append(String.join(", ", aliases)).append(")");
        }
        sb.append("\n  ").append(description);
        sb.append("\n  Usage: ").append(usage);
        if (!parameters.isEmpty()) {
            sb.append("\n  Parameters:");
            parameters.forEach(p ->
                sb.append("\n    ").append(p.name()).append(" - ").append(p.description())
            );
        }
        return sb.toString();
    }
}

public record CommandParameter(
    String name,
    String description,
    boolean required
) {}
```

### 4. CommandProvider Interface

**Purpose:** Components that provide commands

```java
public interface CommandProvider {
    // Register commands with registry
    void registerCommands(CommandRegistry registry);

    // Get command prefix (optional namespace)
    default String getPrefix() { return ""; }
}
```

### 5. Enhanced Command ADT

```java
public sealed interface Command {
    // Built-in commands (always available)
    record Exit() implements Command {}
    record Help(Optional<String> topic) implements Command {}
    record Clear() implements Command {}
    record Unknown(String raw) implements Command {}
    record Message(String text) implements Command {}

    // Pluggable command (dynamically handled)
    record Plugin(String name, List<String> args) implements Command {}

    static Command parse(String input, CommandRegistry registry) {
        if (!input.startsWith("/")) {
            return new Message(input);
        }

        String[] parts = input.substring(1).split("\\s+");
        String commandName = parts[0];
        List<String> args = List.of(Arrays.copyOfRange(parts, 1, parts.length));

        return switch (commandName) {
            case "exit", "quit" -> new Exit();
            case "help" -> new Help(
                args.isEmpty() ? Optional.empty() : Optional.of(args.get(0))
            );
            case "clear" -> new Clear();
            default -> registry.getAllCommands().contains(commandName)
                ? new Plugin(commandName, args)
                : new Unknown(input);
        };
    }
}
```

## Example: Context Management Commands

```java
public class ContextManagerCommandProvider implements CommandProvider {
    private final ContextManager contextManager;

    @Override
    public void registerCommands(CommandRegistry registry) {
        // /context status
        registry.register("context", new CommandHandler() {
            @Override
            public String execute(CommandContext ctx) {
                String subcommand = ctx.arg(0, "status");
                return switch (subcommand) {
                    case "status" -> getStatus(ctx.session());
                    case "summarize" -> summarize(ctx.session());
                    case "checkpoint" -> checkpoint(ctx.session());
                    case "restore" -> restore(ctx.session(), ctx.arg(1, ""));
                    default -> "Unknown subcommand: " + subcommand;
                };
            }
        }, new CommandMetadata(
            "context",
            "Manage conversation context",
            "/context <status|summarize|checkpoint|restore>",
            List.of(
                new CommandParameter("subcommand", "status, summarize, checkpoint, or restore", true)
            ),
            Set.of()
        ));
    }

    private String getStatus(Session session) {
        Agent agent = getAgent(session);  // Get from session
        ContextBudget budget = contextManager.getBudget(agent);
        return String.format(
            "Context: %d/%d tokens (%.1f%% used)\n" +
            "Messages: %d\n" +
            "Last summary: %s",
            budget.usedTokens(),
            budget.maxTokens(),
            budget.percentUsed() * 100,
            agent.getHistory().size(),
            lastSummaryTime()
        );
    }
}
```

## Example: Agent Network Commands

```java
public class AgentNetworkCommandProvider implements CommandProvider {
    private final AgentNetwork network;

    @Override
    public void registerCommands(CommandRegistry registry) {
        // /agents list
        registry.register("agents", (ctx) -> {
            String subcommand = ctx.arg(0, "list");
            return switch (subcommand) {
                case "list" -> listAgents();
                case "status" -> getAgentStatus(ctx.arg(1, ""));
                case "switch" -> switchAgent(ctx.arg(1, ""), ctx.session());
                default -> "Unknown subcommand: " + subcommand;
            };
        }, new CommandMetadata(
            "agents",
            "Manage agent network",
            "/agents <list|status|switch> [agent-id]",
            List.of(
                new CommandParameter("subcommand", "list, status, or switch", true),
                new CommandParameter("agent-id", "Agent identifier", false)
            ),
            Set.of("agent")
        ));

        // /delegate
        registry.register("delegate", (ctx) -> {
            String capability = ctx.arg(0, "");
            String task = String.join(" ", ctx.args().subList(1, ctx.args().size()));
            return delegateTask(capability, task);
        }, new CommandMetadata(
            "delegate",
            "Delegate task to capable agent",
            "/delegate <capability> <task description>",
            List.of(
                new CommandParameter("capability", "Required agent capability", true),
                new CommandParameter("task", "Task to delegate", true)
            ),
            Set.of("assign")
        ));
    }
}
```

## Example: Todo Service Commands

```java
public class TodoServiceCommandProvider implements CommandProvider {
    private final TodoService todoService;

    @Override
    public void registerCommands(CommandRegistry registry) {
        registry.register("task", (ctx) -> {
            String subcommand = ctx.arg(0, "list");
            return switch (subcommand) {
                case "add" -> addTask(ctx.args().subList(1, ctx.args().size()));
                case "list" -> listTasks();
                case "done" -> markDone(Integer.parseInt(ctx.arg(1, "0")));
                case "remove" -> removeTask(Integer.parseInt(ctx.arg(1, "0")));
                default -> "Unknown subcommand: " + subcommand;
            };
        }, new CommandMetadata(
            "task",
            "Manage tasks",
            "/task <add|list|done|remove> [args]",
            List.of(
                new CommandParameter("subcommand", "add, list, done, or remove", true)
            ),
            Set.of("todo")
        ));
    }
}
```

## Integration with InteractionMode

```java
public final class StreamingChat implements InteractionMode {
    private final CommandRegistry commandRegistry;

    public StreamingChat(CommandRegistry registry) {
        this.commandRegistry = registry;
    }

    @Override
    public void processIteration(Session session, Agent agent) {
        Command cmd = session.io().read("magenta> ");

        switch (cmd) {
            case Command.Exit e -> session.setExit(true);

            case Command.Help h -> {
                if (h.topic().isPresent()) {
                    String help = commandRegistry.getHelp(h.topic().get());
                    session.io().println(help);
                } else {
                    printGeneralHelp(session, commandRegistry);
                }
            }

            case Command.Plugin p -> {
                Optional<String> result = commandRegistry.handle(
                    p.name(), p.args(), session
                );
                if (result.isPresent()) {
                    session.io().println(result.get());
                } else {
                    session.io().error("Command handler error");
                }
            }

            case Command.Message m -> processMessage(session, agent, m);

            case Command.Unknown u ->
                session.io().error("Unknown command: " + u.raw());
        }
    }

    private void printGeneralHelp(Session session, CommandRegistry registry) {
        StringBuilder help = new StringBuilder();
        help.append("Available commands:\n");
        help.append("  /exit, /quit - Exit application\n");
        help.append("  /help [command] - Show help\n");
        help.append("  /clear - Clear screen\n");

        for (String cmd : registry.getAllCommands()) {
            help.append("  /").append(cmd).append("\n");
        }

        help.append("\nUse /help <command> for detailed help");
        session.io().println(help.toString());
    }
}
```

## Configuration

```json
{
  "commands": {
    "providers": [
      "com.magenta.context.ContextManagerCommandProvider",
      "com.magenta.agent.AgentNetworkCommandProvider",
      "com.magenta.domain.TodoServiceCommandProvider"
    ],
    "disabled": ["task"]  // Disable specific commands
  }
}
```

## Dynamic Command Discovery

```java
public class CommandLoader {
    public void loadProviders(CommandRegistry registry, GlobalContext context) {
        List<String> providerClasses = context.config()
            .commands()
            .providers();

        for (String className : providerClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                CommandProvider provider = (CommandProvider) clazz
                    .getDeclaredConstructor(GlobalContext.class)
                    .newInstance(context);

                provider.registerCommands(registry);
            } catch (Exception e) {
                System.err.println("Failed to load provider: " + className);
            }
        }
    }
}
```

## Command Namespacing

For avoiding naming conflicts:

```java
// With namespace prefix
public class ContextManagerCommandProvider implements CommandProvider {
    @Override
    public String getPrefix() {
        return "ctx";  // Commands: /ctx:status, /ctx:summarize
    }
}

// Without namespace (global commands)
public class TodoServiceCommandProvider implements CommandProvider {
    @Override
    public String getPrefix() {
        return "";  // Commands: /task, /todo
    }
}
```

## Command Aliases

```java
registry.register("task", taskHandler, new CommandMetadata(
    "task",
    "Manage tasks",
    "/task <subcommand>",
    parameters,
    Set.of("todo", "t")  // Aliases: /todo, /t
));
```

## Implementation Phases

### Phase 1: Basic Registry
- CommandRegistry class
- CommandHandler interface
- Parse Plugin commands
- Handle in InteractionMode

### Phase 2: Command Providers
- CommandProvider interface
- Load providers from config
- Dynamic registration

### Phase 3: Metadata & Help
- CommandMetadata
- Auto-generated help text
- /help <command>

### Phase 4: Namespacing & Aliases
- Command prefixes
- Alias support
- Conflict detection

### Phase 5: Context-Specific Commands
- IOManager-specific commands
- Agent-specific commands
- Dynamic availability

## Open Questions

1. **Permissions:** Should commands have permission requirements?
2. **Completion:** Should we support tab-completion for commands?
3. **History:** Should plugin commands be in history?
4. **Validation:** How to validate command arguments?
5. **Error Handling:** How to report command errors consistently?

## Related Features

- [Context Management](context-management.md) - Provides context commands
- [Agent Networks](agent-networks.md) - Provides network commands

## See Also

- [Command Handling](../components/command-handling.md) - Current command system
- [I/O System](../architecture/io-system.md) - IOManager integration
