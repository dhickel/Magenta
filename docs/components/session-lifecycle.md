# Session Lifecycle

## Overview

Sessions in Magenta manage the lifecycle of agent interactions. This document explains how sessions work, their relationship to agents and I/O, and how to create custom session types.

## Core Components

### Session Interface

```java
public interface Session {
    IOManager io();
    GlobalContext context();
    boolean shouldExit();
    void setExit(boolean exit);
}
```

**Purpose:** Define the contract for any session type

**Responsibilities:**
- Provide access to I/O (input/output)
- Provide access to global context (config, security, tools)
- Manage exit state (when to stop the session)

**Design Philosophy:** Minimal interface focused on essential session capabilities

### AgentSession Implementation

```java
public class AgentSession implements Session {
    private final IOManager io;
    private final GlobalContext context;
    private final Agent agent;
    private final InteractionMode mode;
    private boolean exitFlag;

    public AgentSession(IOManager io, GlobalContext context, String agentName) {
        this.io = io;
        this.context = context;
        this.exitFlag = false;

        // Load agent configuration
        AgentConfig agentConfig = context.config().agents().get(agentName);
        this.agent = new Agent(agentConfig, context);

        // Default interaction mode
        this.mode = new InteractionMode.StreamingChat();
    }

    public void run() {
        while (!shouldExit()) {
            mode.processIteration(this, agent);
        }
    }
}
```

**Responsibilities:**
- Compose IOManager, Agent, and InteractionMode
- Load agent configuration from GlobalContext
- Run the interaction loop
- Delegate iteration processing to InteractionMode

## Lifecycle Phases

### 1. Creation

```java
// In Main.java
IOManager.Terminal io = new IOManager.Terminal();
io.setColorsConfig(config.colors);
io.setSecurityFilter(securityManager.createFilter(io));

AgentSession session = new AgentSession(io, context, "assistant");
```

**What Happens:**
1. IOManager created (Terminal for user interaction)
2. Security filter configured
3. AgentSession receives IOManager and GlobalContext
4. Agent loaded from configuration by name
5. InteractionMode initialized (StreamingChat by default)

### 2. Initialization

```java
// Inside AgentSession constructor
AgentConfig agentConfig = context.config().agents().get(agentName);
// Example: { model: "llama-3.3-70b", tools: ["shell", "web"], ... }

this.agent = new Agent(agentConfig, context);
// Agent loads:
//   - ChatModel (Streaming or Blocking)
//   - System prompt
//   - Tools from ToolProvider
//   - Empty conversation history
```

**Agent Creation Flow:**
```
AgentConfig (from JSON)
  ├─ model: "llama-3.3-70b"
  ├─ tools: ["shell", "web", "filesystem"]
  ├─ system_prompt_key: "assistant_prompt"
  └─ color: "CYAN"
      │
      ▼
ModelConfig (from JSON)
  ├─ model_name: "llama3.3:70b"
  ├─ endpoint: "ollama-local"
  ├─ streaming: true
  └─ max_tokens: 8000
      │
      ▼
Agent Instance
  ├─ ChatModel.Streaming(model)
  ├─ conversationHistory: []
  └─ agentConfig: AgentConfig
```

### 3. Running

```java
session.run();

// Expands to:
while (!shouldExit()) {
    mode.processIteration(this, agent);
}
```

**Iteration Flow:**
```
1. mode.processIteration(session, agent)
   │
   ├─ Read input: Command cmd = session.io().read("magenta> ")
   │
   ├─ Route command:
   │   ├─ Command.Exit → session.setExit(true)
   │   ├─ Command.Help → print help text
   │   ├─ Command.Message → process chat message
   │   └─ Command.Unknown → print error
   │
   ├─ If Message:
   │   ├─ Add to history: agent.addMessage(UserMessage)
   │   ├─ Create handler: session.io().createResponseHandler(color)
   │   ├─ Generate: agent.model().generate(history, handler)
   │   └─ Add response: agent.addMessage(AiMessage)
   │
   └─ Return to step 1 (unless shouldExit)
```

### 4. Termination

```java
// User types "/exit" or Ctrl+D
Command.Exit received
  │
  ▼
session.setExit(true)
  │
  ▼
while (!shouldExit()) breaks
  │
  ▼
session.io().close()  // Clean up terminal/resources
  │
  ▼
Application exits
```

## GlobalContext: Shared State

### Structure

```java
public record GlobalContext(
    Config config,
    Map<String, Arg.Value> args,
    SecurityManager securityManager,
    ToolProvider toolProvider
) {}
```

**Purpose:** Application-wide, immutable state shared across all sessions and agents

**Lifecycle:** Created once at application startup, never modified

**Usage:**
```java
// In AgentSession
AgentConfig agentConfig = context.config().agents().get(agentName);
String prompt = context.config().prompts().get(agentConfig.systemPromptKey());
Object[] tools = context.toolProvider().createTools(agentConfig.tools(), io);

// In tools
if (!context.securityManager().requireToolApproval("shell", cmd, io)) {
    return "Error: Denied";
}

// Anywhere
String storagePath = context.config().global().storagePath();
```

**Benefits:**
- Single source of truth for configuration
- No global static state
- Easy to test (pass different GlobalContext)
- Explicit dependencies

## Agent: AI Behavior Container

### Structure

```java
public class Agent {
    private final AgentConfig agentConfig;
    private final ChatModel model;
    private final List<ChatMessage> conversationHistory;

    public Agent(AgentConfig config, GlobalContext context) {
        this.agentConfig = config;
        this.model = createModel(config, context);  // Streaming or Blocking
        this.conversationHistory = new ArrayList<>();
    }

    public void addMessage(ChatMessage message) {
        conversationHistory.add(message);
    }

    public List<ChatMessage> getHistory() {
        return Collections.unmodifiableList(conversationHistory);
    }

    public ChatModel model() { return model; }
    public AgentConfig getConfig() { return agentConfig; }
    public Integer getColor() { return agentConfig.color(); }
}
```

**Purpose:** Lightweight wrapper for agent state

**Responsibilities:**
- Hold configuration (model, tools, prompt)
- Manage conversation history
- Provide access to ChatModel

**Does NOT:**
- Manage I/O (that's IOManager's job)
- Handle session lifecycle (that's AgentSession's job)
- Parse commands (that's Command's job)

**Benefits:**
- Agent is independent of Session
- Can create multiple agents in one session
- Easy to serialize/deserialize agent state
- Clear separation of concerns

## InteractionMode: Processing Strategy

### Interface

```java
public sealed interface InteractionMode {
    void processIteration(Session session, Agent agent);

    final class StreamingChat implements InteractionMode {
        @Override
        public void processIteration(Session session, Agent agent) {
            // Read input, process command, generate response
        }
    }

    // Future: BatchProcessing, REPL, etc.
}
```

**Purpose:** Strategy pattern for different interaction styles

**Current Implementation:** StreamingChat (conversational, streaming responses)

**Future Implementations:**
- `BatchProcessing` - Process multiple messages at once
- `REPL` - Read-eval-print loop with command history
- `ToolOnly` - No chat, only tool execution
- `Supervised` - Require approval for each response

**Benefits:**
- Pluggable interaction behavior
- Session logic independent of mode
- Easy to add new modes

## Relationship Diagram

```
┌──────────────────────────────────────────────────────────┐
│                      Main.java                           │
│  ┌────────────────┐    ┌────────────────────────────┐   │
│  │ Configuration  │───▶│    GlobalContext           │   │
│  └────────────────┘    │  - Config                  │   │
│                        │  - SecurityManager         │   │
│  ┌────────────────┐    │  - ToolProvider            │   │
│  │  IOManager     │    │  - CLI args                │   │
│  │  .Terminal     │    └────────────┬───────────────┘   │
│  └───────┬────────┘                 │                   │
│          │                          │                   │
│          └──────────┬───────────────┘                   │
│                     │                                   │
│              ┌──────▼──────────────────────┐            │
│              │    AgentSession             │            │
│              │  implements Session         │            │
│              │                             │            │
│              │  ┌──────────┐  ┌─────────┐ │            │
│              │  │ IOManager│  │ Agent   │ │            │
│              │  └──────────┘  └────┬────┘ │            │
│              │                     │      │            │
│              │  ┌──────────────────▼────┐ │            │
│              │  │ InteractionMode       │ │            │
│              │  │  .StreamingChat       │ │            │
│              │  └───────────────────────┘ │            │
│              └─────────────────────────────┘            │
└──────────────────────────────────────────────────────────┘
```

## Creating Custom Session Types

### Example: HeadlessSession

```java
public class HeadlessSession implements Session {
    private final IOManager io;
    private final GlobalContext context;
    private final Agent agent;
    private boolean exitFlag;

    public HeadlessSession(GlobalContext context, String agentName) {
        this.io = new IOManager.Internal();  // No terminal
        this.context = context;
        this.exitFlag = false;

        AgentConfig agentConfig = context.config().agents().get(agentName);
        this.agent = new Agent(agentConfig, context);
    }

    public void processMessage(String message) {
        io.enqueueInput(message);
        agent.addMessage(UserMessage.from(message));

        ResponseHandler handler = io.createResponseHandler(null);
        agent.model().generate(agent.getHistory(), handler);

        // Response available via io.readOutput()
    }

    public String getLastResponse() {
        return ((IOManager.Internal) io).readOutput();
    }

    @Override
    public IOManager io() { return io; }

    @Override
    public GlobalContext context() { return context; }

    @Override
    public boolean shouldExit() { return exitFlag; }

    @Override
    public void setExit(boolean exit) { this.exitFlag = exit; }
}
```

**Use Cases:**
- Background tasks
- API servers
- Batch processing
- Testing

**Example Usage:**
```java
HeadlessSession session = new HeadlessSession(context, "analyzer");
session.processMessage("Analyze these logs: ...");
String result = session.getLastResponse();
System.out.println(result);
```

### Example: MultiAgentSession

```java
public class MultiAgentSession implements Session {
    private final IOManager.Terminal terminalIO;
    private final Map<String, Agent> agents;
    private final GlobalContext context;
    private boolean exitFlag;

    public MultiAgentSession(GlobalContext context, List<String> agentNames) {
        this.terminalIO = new IOManager.Terminal();
        this.context = context;
        this.exitFlag = false;
        this.agents = new HashMap<>();

        for (String name : agentNames) {
            AgentConfig config = context.config().agents().get(name);
            agents.put(name, new Agent(config, context));
        }
    }

    public void run() {
        while (!shouldExit()) {
            String agentName = selectAgent();  // User chooses agent
            Agent agent = agents.get(agentName);

            // Process with selected agent
            InteractionMode mode = new InteractionMode.StreamingChat();
            mode.processIteration(this, agent);
        }
    }

    @Override
    public IOManager io() { return terminalIO; }

    @Override
    public GlobalContext context() { return context; }

    @Override
    public boolean shouldExit() { return exitFlag; }

    @Override
    public void setExit(boolean exit) { this.exitFlag = exit; }
}
```

**Use Cases:**
- Switch between specialized agents
- Multi-persona conversations
- Role-based assistance

## Session State Management (Future)

### Persistence

```java
public interface SessionStore {
    void save(Session session, String sessionId);
    Session load(String sessionId, GlobalContext context);
    List<String> listSessions();
}

// Usage
SessionStore store = new DatabaseSessionStore(dataSource);
store.save(session, "user-123-2026-01-28");

// Later
Session restored = store.load("user-123-2026-01-28", context);
```

### Context Summarization

```java
public class ContextManager {
    public void summarizeHistory(Agent agent, int maxMessages) {
        List<ChatMessage> history = agent.getHistory();
        if (history.size() > maxMessages) {
            // Summarize old messages
            String summary = summarize(history.subList(0, history.size() - maxMessages));
            agent.setHistory(List.of(
                SystemMessage.from("Previous conversation: " + summary),
                ...history.subList(history.size() - maxMessages, history.size())
            ));
        }
    }
}
```

## Best Practices

### 1. Use Session Interface in APIs

❌ **Don't:**
```java
public void processRequest(AgentSession session) {
    // Tightly coupled to AgentSession
}
```

✅ **Do:**
```java
public void processRequest(Session session) {
    // Works with any Session implementation
}
```

### 2. Keep Session Lightweight

❌ **Don't:**
```java
public class AgentSession implements Session {
    private Database db;
    private HttpClient client;
    private FileSystem fs;
    // Too many responsibilities
}
```

✅ **Do:**
```java
public class AgentSession implements Session {
    private final IOManager io;
    private final GlobalContext context;  // Context provides shared services
    private final Agent agent;
    // Focused responsibilities
}
```

### 3. Use GlobalContext for Shared State

❌ **Don't:**
```java
public static SecurityManager SECURITY = ...;  // Global static
```

✅ **Do:**
```java
SecurityManager sm = session.context().securityManager();  // From context
```

## Summary

Magenta's session architecture provides:
- **Clear Contracts** - Session interface defines essential capabilities
- **Composition** - AgentSession composes IOManager, Agent, InteractionMode
- **Flexibility** - Easy to create custom session types
- **Shared State** - GlobalContext provides immutable shared configuration
- **Separation** - Agent, I/O, and session lifecycle are distinct concerns

This design enables testing, multi-agent systems, and diverse interaction patterns while keeping code simple and focused.

## Next Steps

- [Agent System](agent-system.md) - Agent creation and delegation
- [Getting Started Guide](../guides/getting-started.md) - Build your first session
- [Creating Agents Guide](../guides/creating-agents.md) - Define custom agents
