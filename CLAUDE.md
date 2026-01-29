# Magenta - AI Agent Task Management System

## Project Overview

Magenta is a hierarchical, agent-driven task management system built with LangChain4j. It provides a conversational CLI interface for autonomous AI agents that can delegate to specialized sub-agents, manage tasks, interact with files/shell, and persist state to a database.

**Core Goals:**
- Configuration-driven agent behavior (JSON-based, no hard-coded roles)
- Multi-agent hierarchy with delegation capabilities
- Type-safe, functional, data-driven design
- Streaming chat with rich terminal output (JLine)

## Architecture Principles

### 1. Prefer ADTs and Composability Over Inheritance

Use this priority order:
1. **Algebraic Data Types (sealed interfaces + nested implementations)** - First choice for modeling variants
2. **Composition** - Build complex behavior from simple, reusable components
3. **Inheritance** - Only when there's a clear "is-a" relationship and shared behavior

**ADT Structure (Preferred Pattern):**
```java
public sealed interface TypeName permits TypeName.Variant1, TypeName.Variant2 {
    // Common interface methods

    final class Variant1 implements TypeName { /* implementation */ }
    final class Variant2 implements TypeName { /* implementation */ }
}
```

**Key ADT Implementations:**
- `Command` (Exit | Help | Clear | History | Unknown | Message) - Nested records
- `IOManager` (Terminal | Internal) - Nested classes with state
- `ChatModel` (Streaming | Blocking)
- `EndpointConfig` (Ollama | OpenAI | Anthropic | RemoteAgent)
- `Arg.Value` (String | Int | Float | Flag)

**Why Nested Implementations:**
- Encapsulation: Variants live with their interface
- Exhaustive matching: Sealed permits ensures all variants are known
- Discoverability: `IOManager.Terminal` is clearer than separate `TerminalIOManager`
- Package organization: Related types stay together

### 2. Composable Interfaces Pattern

**Design Philosophy:** Build complex systems from small, focused interfaces that can be composed together.

**Core Composable Interfaces:**

| Interface | Responsibility | Usage |
|-----------|---------------|-------|
| `InputPipe` | Reading input, parsing commands | `Command read(String prompt)` |
| `OutputPipe` | Writing output | `print()`, `println()` with optional colors |
| `IOManager` | Coordinates I/O, security filtering, extends InputPipe + OutputPipe | Context-specific "firmware" layer with integrated security |
| `ResponseHandler` | Streaming response handling | `write()`, `complete()`, `error()` |
| `SecurityFilter` | I/O and tool filtering | Composable functional filters for input/output/tools |

**How They Compose:**

```
IOManager (sealed interface)
├── extends InputPipe (interface)
├── extends OutputPipe (interface)
├── extends AutoCloseable (interface)
├── has: SecurityFilter (integrated)
└── factory: createResponseHandler()

IOManager.Terminal implements IOManager
├── manages: JLine Terminal, LineReader, PrintWriter, SecurityFilter
├── handles: Terminal-specific commands (/clear)
├── filters: All input/output through SecurityFilter
└── creates: ResponseHandlers that use filtered OutputPipe

IOManager.Internal implements IOManager
├── manages: ConcurrentLinkedQueues, SecurityFilter
├── handles: Agent-to-agent communication
├── filters: All input/output through SecurityFilter
└── creates: ResponseHandlers that use filtered OutputPipe

Session (interface)
└── defines contract: io(), config(), args(), responseHandler(), shouldExit(), setExit()

ConfigManager (singleton)
├── holds: Config (loaded once at startup)
└── holds: Args (parsed once at startup)

AbstractSession (base)
├── accesses: ConfigManager.config() (shared singleton)
├── accesses: ConfigManager.args() (shared singleton)
├── owns: IOManager (session-level)
├── owns: ResponseHandler (session-level, lazy)
└── manages: exitFlag (session-level)

AgentSession (concrete)
├── extends: AbstractSession
├── owns: SecurityManager (per-agent, from agent's security config)
├── owns: ToolProvider (per-agent, uses agent's SecurityManager)
├── owns: Agent (per-agent instance)
├── owns: InteractionMode (per-agent behavior)
└── composes: ResponseHandler from IOManager + Agent color + Config delay

Agent
├── owns: ChatModel, ConversationHistory
└── provides: color config, system prompt

Writer implements ResponseHandler
├── uses: OutputPipe (filtered automatically by IOManager)
└── resets: Buffer cleared in complete()/error() for reuse

SmoothWriter extends Writer
├── adds: Character-by-character delay with virtual thread
└── resets: Fresh queue/latch/thread on each reset

SecurityFilter (functional composition)
├── inputFilter: (Command, IOManager) → Command
├── outputFilter: String → String
└── toolFilter: (ToolExecutionRequest, IOManager) → ToolExecutionRequest
```

**Benefits:**
- **Polymorphism:** Pass `OutputPipe` to `Writer`, works with any `IOManager`
- **Separation of Concerns:** Each interface handles one responsibility
- **Testability:** Use `IOManager.Internal` for tests, no terminal needed
- **Extensibility:** Add new `IOManager` variants without changing `Writer`
- **Transparent Security:** All I/O automatically filtered, no manual security checks needed
- **Efficient Reuse:** ResponseHandlers created once per session, reset after each message
- **Encapsulation:** Session manages complete lifecycle, no object passing between layers

**Example - Context-Dependent I/O with Security:**
```java
// Terminal context - user interaction with security
IOManager.Terminal terminal = new IOManager.Terminal();
terminal.setSecurityFilter(securityManager.createFilter(terminal));

// Create ResponseHandler for streaming (security applied automatically)
ResponseHandler handler = terminal.createResponseHandler(colorCode, delayMs);

// All I/O is transparently filtered
Command cmd = terminal.read("magenta> "); // Input filtered
terminal.println("response"); // Output filtered

// Internal context - agent communication
IOManager.Internal internal = new IOManager.Internal();
internal.setSecurityFilter(SecurityFilter.identity()); // Or custom filter
internal.enqueueInput("message from agent A");
Command cmd = internal.read(""); // No prompt needed, still filtered
internal.println("response from agent B"); // Filtered

// Session owns and manages ResponseHandler lifecycle
ResponseHandler handler = session.responseHandler(); // Lazily composed from config
agent.model().generate(agent.conversationHistory(), handler)
    .thenAccept(response -> agent.addMessage(AiMessage.from(response)))
    .join();
// Handler automatically resets in complete()/error(), ready for next iteration
```

### 3. Functional and Data-Driven

- Configuration is the single source of truth (`Config.java` + JSON)
- `ConfigManager` manages Config and Args as application-wide singletons
- Call `ConfigManager.initialize(args)` once at startup
- Access via `ConfigManager.config()` and `ConfigManager.args()` anywhere
- Use function references and streams where natural
- Prefer immutable records for data classes
- Use `CompletableFuture` for async operations
- Let data shape behavior, not deep class hierarchies

### 4. Minimal, Focused Changes

**Do NOT:**
- Over-abstract or create unnecessary layers
- Use complex multi-class, high inheritance layouts unless explicitly asked
- Refactor or move existing code outside the tight domain of an edit
- Add speculative "improvements" beyond what's requested

**Do:**
- Add methods when needed
- Make minimal, targeted changes
- Keep solutions simple and focused
- If refactoring seems necessary, **prompt for confirmation first**

## Project Direction

**Current State:** Terminal-based CLI with multi-agent session switching, task management, and tool execution.

**Architecture Evolution:** Moving towards a fully composable, multi-context system:

1. **Polymorphic I/O Contexts**
   - **Terminal:** Human-agent interaction (JLine, colors, commands)
   - **Internal:** Agent-to-agent communication (queues, no terminal deps)
   - **Database:** Agent state persistence and retrieval
   - **Network:** Future remote agent communication

2. **Multi-Agent Workflows**
   - **Session Switching:** `SessionManager` enables switching between agent sessions in terminal
   - Agent sessions cached with independent conversation histories
   - Commands: `/agent <name>`, `/sessions`, `/agents`
   - Agents communicate through `IOManager.Internal` instances
   - Each agent workflow can have different I/O contexts
   - Terminal agents for user interaction, internal agents for background tasks
   - Composable interfaces enable seamless context switching

3. **Separation of Concerns by Layer**
   - **I/O Layer:** `InputPipe`, `OutputPipe`, `IOManager` variants with integrated `SecurityFilter`
   - **Session Layer:** `SessionManager` (singleton, owns IO), `Session`, `AbstractSession`, `AgentSession`
   - **Agent Layer:** `Agent`, `AgentFactory`, `AgentNetwork`, delegation logic
   - **Domain Layer:** `Task`, `TodoService`, business logic
   - **Security Layer:** `SecurityFilter`, `SecurityManager` (per-agent, managed by session)
   - **Persistence Layer:** Database, vector store, memory

**Design Goal:** Each component handles its own logic without tight coupling:
- `Writer` only knows about `OutputPipe`, not `IOManager` specifics
- `Session` works with any `IOManager` implementation (IO is injected, not owned)
- `SessionManager` owns `IOManager` lifecycle, sessions own behavior
- Commands are parsed context-independently
- Tools execute regardless of I/O context
- Security filtering is transparent at the I/O layer (no manual checks needed)
- `IOManager` creates context-appropriate ResponseHandlers (factory pattern)
- `Session` owns and manages ResponseHandler lifecycle (no object passing)
- ResponseHandlers reset after complete/error for efficient reuse

## Code Organization

```
src/main/java/com/magenta/
├── config/       # Configuration, argument parsing
├── session/      # Session lifecycle, state management
├── agent/        # Agent creation, delegation, networking
├── io/           # I/O abstractions (IOManager, pipes, writers, commands)
├── model/        # Chat model abstraction (Streaming, Blocking)
├── tools/        # Tool implementations (@Tool annotated)
├── domain/       # Business logic (Task, TodoService)
├── memory/       # Vector store for semantic memory
├── data/         # Database persistence
└── security/     # Security/approval management
```

## Key Patterns in Use

| Pattern | Example | Notes |
|---------|---------|-------|
| Sealed ADT + Nested | `IOManager`, `Command`, `EndpointConfig` | Exhaustive matching, encapsulated variants |
| Composable Interfaces | `InputPipe`, `OutputPipe`, `ResponseHandler` | Small, focused contracts |
| Inheritance + Composition | `AbstractSession` → `AgentSession` | Clear shared vs per-agent state separation |
| Singleton | `ConfigManager`, `SessionManager` | Global state management |
| Builder | `AgentSession.Builder` | Encapsulates complex object construction |
| Factory | `AgentFactory`, `IOManager.createResponseHandler()`, `SessionManager.getOrCreateAgentSession()` | Create from config or context |
| Functional Interface | `InteractionMode` | Can use lambda for simple cases, or implement for complex behavior |
| Functional Composition | `SecurityFilter.andThen()` | Composable filters with function references |
| Observer | `TodoService` + PropertyChangeSupport | Reactive updates |
| Strategy | `ChatModel` | Pluggable behaviors |

## Build & Run

```bash
# Build
mvn clean compile

# Run
mvn exec:java -Dexec.mainClass="com.magenta.Main"

# Package
mvn clean package
```

## Session Management

### SessionManager (Production)

The `SessionManager` singleton manages terminal lifecycle and agent session switching:

```java
// Startup (Main.java)
TerminalIOManager terminalIO = new TerminalIOManager();
AgentSession initialSession = AgentSession.builder()
    .agent(ConfigManager.config().baseAgent())
    .messageHandler(new StreamingChat())
    .commandHandler(new DefaultCommandHandler())
    .inputParser(Input::defaultParser)
    .ioManager(terminalIO)
    .build();

SessionManager.initialize(terminalIO, initialSession);
SessionManager.getInstance().run();
SessionManager.getInstance().close();
```

**SessionManager Features:**
- Owns the `TerminalIOManager` lifecycle
- Creates and caches agent sessions on-demand
- Switches between agents without recreating terminal
- Each agent maintains independent conversation history

**Available Commands:**
- `/agent <name>` - Switch to a different agent (creates if doesn't exist)
- `/sessions` - List active cached sessions (shows current with `*`)
- `/agents` - List all available agent configurations

**Example Session:**
```
magenta> /agents
Available agents:
  default (current)
  helpful

magenta> /agent helpful
Switched to agent: helpful

helpful> Hello!
[Agent responds with different personality/model]

helpful> /sessions
Active sessions:
  default
  helpful *

helpful> /agent default
Switched to agent: default

magenta> /exit
```

### Creating Agent Sessions (Testing/Custom)

For testing or custom workflows, create sessions directly:

```java
// Testing with custom IOManager
try (AgentSession session = AgentSession.builder()
        .agent(testConfig)
        .messageHandler(new StreamingChat())
        .commandHandler(new DefaultCommandHandler())
        .inputParser(Input::defaultParser)
        .ioManager(new InternalIOManager())
        .build()) {
    // Test logic
}

// Custom interaction mode
try (AgentSession session = AgentSession.builder()
        .agent(testConfig)
        .messageHandler((session, msg) -> {
            // Custom message handling
            session.io().println("Custom: " + msg);
        })
        .commandHandler(new DefaultCommandHandler())
        .inputParser(Input::defaultParser)
        .ioManager(new InternalIOManager())
        .build()) {
    session.run();
}
```

**Key Points:**
- Sessions implement AutoCloseable - use try-with-resources
- Sessions DON'T own their IOManager (SessionManager owns it in production)
- Calling session.close() is safe but doesn't close IO (SessionManager closes it)
- All components (messageHandler, commandHandler, inputParser, ioManager) must be explicitly set
- Builder validates all required components and fails fast with clear error messages
- IOManager creation must happen before try-with-resources since it throws IOException
- No hidden defaults - every dependency is visible at construction site

**Session Lifecycle:**
- `attachIO(IOManager)` - Inject or swap IOManager
- `runOnce()` - Execute one iteration (read, parse, dispatch)
- `run()` - Loop calling runOnce() until shouldExit()
- `close()` - Close session resources (NOT IOManager)

**Security Configuration:**
- Each agent references a security config by key (like models reference endpoints)
- Multiple security profiles can be defined in `securities` map
- Example: `"securities": { "default": {...}, "strict": {...} }`
- Agent config: `"security": "default"`

**Colors Configuration:**
- Each agent references a colors config by key (same pattern as security and models)
- Multiple color themes can be defined in `colors` map
- Example: `"colors": { "default": {...}, "vibrant": {...} }`
- Agent config: `"colors": "default"`
- Colors are automatically set on IOManager by AgentSession.Builder
- Agent's response color comes from the colors config's "agent" field

**Cursor Configuration:**
- Each agent can define a custom cursor prompt and color
- Agent config fields: `"cursor": "magenta> "` and `"cursor_color": 5`
- Both fields are optional (defaults to "magenta> " with prompt color)
- Cursor is automatically configured on IOManager by AgentSession.Builder
- TerminalIOManager uses the configured cursor for all input prompts
- InternalIOManager ignores cursor settings (no visible prompts)

## .internal-dev Directory

**Purpose:** Store notes, summaries, and living documents that are relevant across sessions between agents.

**Structure:**
```
.internal-dev/
├── notes/       # Session notes, architecture decisions, refactoring summaries
├── outlines/    # High-level system outlines and designs
├── plans/       # Implementation plans and roadmaps
└── reviews/     # Code review notes and architectural reviews
```

**Usage Guidelines:**
- Store significant architectural changes and their rationale in `notes/`
- Document cross-session context that future Claude agents should know
- Keep refactoring summaries and design decisions
- Use markdown format for all documents
- Name files descriptively (e.g., `2026-01-28-iomanager-refactor.md`)

**When to Add Documentation:**
- After major architectural changes
- When establishing new patterns or conventions
- For complex refactorings that span multiple files
- To preserve design decisions and tradeoffs
- When introducing new ADTs or composable interfaces

## Development Guidelines for Claude

1. **Read before editing** - Always read relevant code before making changes
2. **Stay in scope** - Only modify what's directly needed for the task
3. **Preserve existing patterns** - Match the style already in use
4. **Ask before refactoring** - If a change requires moving/restructuring existing code, confirm first
5. **No speculative features** - Only implement what's explicitly requested
6. **Simple over clever** - Three similar lines beats a premature abstraction
7. **Document significant changes** - Store architectural decisions and refactoring summaries in `.internal-dev/notes/`
8. **Keep documentation up to date** - When editing, refactoring, or adding features, update the corresponding documentation in `docs/`. This includes:
   - Architecture docs if design patterns or component relationships change
   - Component docs if interfaces or behavior changes
   - Guide docs if usage changes
   - Add new sections to future docs for planned features being implemented

### Guidelines for New ADTs

When creating new sealed interfaces with variants:

1. **Use nested implementations** (not separate files):
   ```java
   public sealed interface MyType permits MyType.Variant1, MyType.Variant2 {
       // Common interface

       final class Variant1 implements MyType { /* impl */ }
       final class Variant2 implements MyType { /* impl */ }
   }
   ```

2. **Choose the right structure:**
   - **Records:** For simple data variants with no behavior (like `Command`)
   - **Classes:** For variants with state and complex behavior (like `IOManager`)
   - **Mix:** Can use both in same sealed interface if needed

3. **Keep variants final** - Prevents further inheritance, maintains exhaustiveness

4. **Reference as `Interface.Variant`** - e.g., `IOManager.Terminal`, not `TerminalIOManager`

### Guidelines for Composable Interfaces

When designing new composable interfaces:

1. **Single Responsibility** - Each interface should do one thing well
   - `InputPipe` only reads, `OutputPipe` only writes
   - Don't combine unrelated concerns

2. **Small Surface Area** - Minimal methods, focused contract
   ```java
   public interface OutputPipe {
       void print(String text);
       void println(String text);
       // Optional variants with defaults
       default void print(String text, int color) { print(text); }
   }
   ```

3. **Compose, Don't Inherit** - Interfaces can extend multiple interfaces
   ```java
   interface IOManager extends InputPipe, OutputPipe, AutoCloseable {
       // Combines responsibilities without deep hierarchy
   }
   ```

4. **Implementation Agnostic** - Don't assume implementation details
   - `Writer` uses `OutputPipe`, doesn't care if it's Terminal or Internal
   - Enable polymorphism through abstraction

5. **Favor Default Methods** - Provide sensible defaults, implementations override as needed
   ```java
   default void println(String text, int color) {
       println(text); // Ignore color by default
   }
   ```

### Deciding Between Patterns

| Use Case | Pattern | Example |
|----------|---------|---------|
| Fixed set of variants | Sealed ADT + nested | `IOManager.Terminal`, `Command.Exit` |
| Multiple implementations | Interface + classes | `InputStream`, `OutputStream` |
| Data with variants | Sealed interface + records | `Command`, `Arg.Value` |
| Behavior with variants | Sealed interface + classes | `IOManager`, `ChatModel` |
| Mixin capabilities | Composable interfaces | `InputPipe`, `OutputPipe` |
| Pluggable behavior (simple) | Functional interface | `InteractionMode` (use lambda) |
| Pluggable behavior (complex) | Interface + classes | `InteractionMode` (use `StreamingChat`) |
