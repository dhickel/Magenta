# Composability in Magenta

## Philosophy

Magenta's architecture is built on two foundational principles:

1. **Algebraic Data Types (ADTs)** - Model variants using sealed interfaces
2. **Composable Interfaces** - Build complex behavior from simple, focused contracts

This document explains how these patterns work and how to use them effectively.

## Algebraic Data Types (ADTs)

### What Are ADTs?

ADTs model data that can take multiple, fixed forms. In Magenta, we use sealed interfaces with nested implementations.

**Pattern Structure:**
```java
public sealed interface TypeName permits TypeName.Variant1, TypeName.Variant2 {
    // Common interface methods

    final class Variant1 implements TypeName { /* implementation */ }
    final class Variant2 implements TypeName { /* implementation */ }
}
```

### Why Nested Implementations?

Nesting variants inside the interface provides:
- **Encapsulation** - Variants live with their interface
- **Discoverability** - `IOManager.Terminal` is clearer than `TerminalIOManager`
- **Exhaustive Matching** - Sealed permits ensures all variants are known
- **Package Organization** - Related types stay together

### ADT Examples in Magenta

#### 1. Command (Data Variants)

```java
public sealed interface Command {
    record Exit() implements Command {}
    record Help() implements Command {}
    record Clear() implements Command {}
    record History() implements Command {}
    record Unknown(String raw) implements Command {}
    record Message(String text) implements Command {}

    static Command parse(String input) {
        return switch (input.trim()) {
            case "/exit", "/quit" -> new Exit();
            case "/help" -> new Help();
            // ... other cases
            default -> new Message(input);
        };
    }
}
```

**Usage:**
```java
Command cmd = Command.parse(userInput);
switch (cmd) {
    case Command.Exit e -> session.setExit(true);
    case Command.Help h -> printHelp();
    case Command.Message m -> processMessage(m.text());
    // Compiler ensures all cases handled
}
```

**Benefits:**
- Type-safe command handling
- Exhaustive pattern matching
- No need for instanceof chains
- Clear, self-documenting code

#### 2. IOManager (Behavioral Variants)

```java
public sealed interface IOManager extends InputPipe, OutputPipe, AutoCloseable
        permits IOManager.Terminal, IOManager.Internal {

    ResponseHandler createResponseHandler(Integer colorCode, int delayMs);
    SecurityFilter securityFilter();
    void setSecurityFilter(SecurityFilter filter);

    final class Terminal implements IOManager {
        private final org.jline.terminal.Terminal terminal;
        private final LineReader reader;
        private SecurityFilter securityFilter;
        // Terminal-specific implementation
    }

    final class Internal implements IOManager {
        private final ConcurrentLinkedQueue<String> inputQueue;
        private final ConcurrentLinkedQueue<String> outputQueue;
        private SecurityFilter securityFilter;
        // Queue-based implementation
    }
}
```

**Usage:**
```java
// Human-facing session
IOManager terminal = new IOManager.Terminal();
AgentSession userSession = new AgentSession(terminal, context, "assistant");

// Agent-to-agent communication
IOManager internal = new IOManager.Internal();
AgentSession backgroundAgent = new AgentSession(internal, context, "analyzer");
```

**Benefits:**
- Polymorphic I/O - same code works with any variant
- Context-specific behavior encapsulated
- Easy to add new contexts (Network, Database)
- Type-safe exhaustive switching

#### 3. ChatModel (Strategy Variants)

```java
public sealed interface ChatModel {
    void generate(List<ChatMessage> messages, ResponseHandler handler);
    String generateBlocking(List<ChatMessage> messages);

    record Streaming(StreamingChatLanguageModel model) implements ChatModel {
        @Override
        public void generate(List<ChatMessage> messages, ResponseHandler handler) {
            model.generate(messages, handler);
        }
    }

    record Blocking(ChatLanguageModel model) implements ChatModel {
        @Override
        public void generate(List<ChatMessage> messages, ResponseHandler handler) {
            String response = model.generate(messages);
            handler.complete(response);
        }
    }
}
```

**Benefits:**
- Unified interface for streaming and blocking models
- Agent code doesn't care which model type is used
- Easy to switch model strategies via configuration

### When to Use Records vs Classes

| Use Records When | Use Classes When |
|------------------|------------------|
| Data is immutable | State needs to be mutable |
| No complex behavior | Complex methods and logic |
| Simple value holders | Managing resources (AutoCloseable) |
| Variants are just data | Variants have different lifecycles |

**Examples:**
- **Records:** `Command.Exit()`, `Command.Message(String text)`
- **Classes:** `IOManager.Terminal`, `IOManager.Internal`

## Composable Interfaces

### What Are Composable Interfaces?

Small, focused interfaces that define a single responsibility and can be combined to create complex behavior.

### Design Principles

1. **Single Responsibility** - Each interface does one thing well
2. **Small Surface Area** - Minimal methods, focused contract
3. **Compose, Don't Inherit** - Combine interfaces via implementation
4. **Implementation Agnostic** - No assumptions about how it's implemented

### Core Composable Interfaces

#### InputPipe
```java
public interface InputPipe {
    Command read(String prompt);
}
```

**Responsibility:** Reading input and parsing commands
**Used by:** IOManager implementations, test harnesses
**Benefits:** Any component can read commands without knowing I/O details

#### OutputPipe
```java
public interface OutputPipe {
    void print(String text);
    void println(String text);

    default void print(String text, int color) { print(text); }
    default void println(String text, int color) { println(text); }
}
```

**Responsibility:** Writing output with optional color
**Used by:** Writer, SmoothWriter, IOManager implementations
**Benefits:** Any component can write output without knowing I/O details

#### IOManager (Composed Interface)
```java
public sealed interface IOManager extends InputPipe, OutputPipe, AutoCloseable {
    // Combines input, output, and resource management
}
```

**Responsibility:** Coordinates I/O, security filtering, handler creation
**Benefits:** Components using IOManager get full I/O capabilities

#### ResponseHandler
```java
public interface ResponseHandler {
    void write(String token);
    void complete(String fullResponse);
    void error(String error);
}
```

**Responsibility:** Handle streaming response tokens
**Used by:** LangChain4j streaming models
**Benefits:** Decouples response handling from I/O implementation

### Composition Example: Writer

```java
public class Writer implements ResponseHandler {
    private final OutputPipe pipe;  // Interface, not concrete class
    private final Integer colorCode;

    public Writer(OutputPipe pipe, Integer colorCode) {
        this.pipe = pipe;
        this.colorCode = colorCode;
    }

    @Override
    public void write(String token) {
        if (colorCode != null) {
            pipe.print(token, colorCode);
        } else {
            pipe.print(token);
        }
    }
}
```

**Benefits:**
- `Writer` works with ANY OutputPipe implementation
- No dependency on IOManager specifics
- Easy to test with mock OutputPipe
- Can use with Terminal, Internal, or future variants

### Functional Composition: SecurityFilter

```java
public record SecurityFilter(
    BiFunction<Command, IOManager, Command> inputFilter,
    Function<String, String> outputFilter,
    BiFunction<ToolExecutionRequest, IOManager, ToolExecutionRequest> toolFilter
) {
    public SecurityFilter andThen(SecurityFilter next) {
        return new SecurityFilter(
            (cmd, io) -> next.inputFilter().apply(this.inputFilter().apply(cmd, io), io),
            text -> next.outputFilter().apply(this.outputFilter().apply(text)),
            (req, io) -> next.toolFilter().apply(this.toolFilter().apply(req, io), io)
        );
    }
}
```

**Usage:**
```java
SecurityFilter redactSensitive = new SecurityFilter(
    (cmd, io) -> cmd,  // pass-through
    text -> text.replaceAll("password", "***"),
    (req, io) -> req
);

SecurityFilter logCommands = new SecurityFilter(
    (cmd, io) -> { log(cmd); return cmd; },
    text -> text,
    (req, io) -> req
);

SecurityFilter combined = redactSensitive.andThen(logCommands);
io.setSecurityFilter(combined);
```

**Benefits:**
- Pure functional composition
- Filters are data, not behavior
- Easy to test and reason about
- Composable with `andThen()`

## Pattern Decision Matrix

| Scenario | Pattern | Example |
|----------|---------|---------|
| Fixed set of variants | Sealed ADT + nested | `IOManager.Terminal` |
| Multiple implementations | Interface + classes | `InputStream` |
| Data with variants | Sealed interface + records | `Command`, `Arg.Value` |
| Behavior with variants | Sealed interface + classes | `IOManager`, `ChatModel` |
| Mixin capabilities | Composable interfaces | `InputPipe`, `OutputPipe` |
| Pluggable behavior | Strategy interface | `InteractionMode` |

## Benefits of This Approach

### 1. Type Safety
```java
// Compiler ensures all cases handled
IOManager io = getIOManager();
switch (io) {
    case IOManager.Terminal t -> handleTerminal(t);
    case IOManager.Internal i -> handleInternal(i);
    // Compiler error if new variant added without handling
}
```

### 2. Polymorphism
```java
// Same code works with any IOManager variant
public void streamResponse(IOManager io, String text) {
    ResponseHandler handler = io.createResponseHandler(null);
    handler.write(text);
    handler.complete(text);
}
```

### 3. Extensibility
```java
// Add new variant without changing existing code
sealed interface IOManager permits Terminal, Internal, Network {
    final class Network implements IOManager {
        // New context for remote agents
    }
}
```

### 4. Testability
```java
// Use Internal variant for tests, no terminal needed
@Test
public void testAgentResponse() {
    IOManager.Internal io = new IOManager.Internal();
    io.enqueueInput("test message");
    AgentSession session = new AgentSession(io, context, "test-agent");
    // Assert on io.readOutput()
}
```

## Anti-Patterns to Avoid

### ❌ Don't: Deep Inheritance Hierarchies
```java
// BAD: Hard to extend, tight coupling
class IOManager { }
class TerminalIOManager extends IOManager { }
class ColoredTerminalIOManager extends TerminalIOManager { }
```

### ✅ Do: Composition and Sealed ADTs
```java
// GOOD: Flat hierarchy, clear variants
sealed interface IOManager permits Terminal, Internal { }
```

### ❌ Don't: Large, Multi-Purpose Interfaces
```java
// BAD: Too many responsibilities
interface SessionManager {
    Command read();
    void write(String text);
    Agent getAgent();
    Config getConfig();
    void saveToDatabase();
}
```

### ✅ Do: Small, Focused Interfaces
```java
// GOOD: Single responsibility each
interface InputPipe { Command read(String prompt); }
interface OutputPipe { void print(String text); }
interface Session { IOManager io(); GlobalContext context(); }
```

## Summary

Magenta's composable architecture provides:
- **Type-safe variants** via sealed ADTs
- **Small, focused contracts** via composable interfaces
- **Flexible composition** via interface implementation
- **Functional composition** via data-driven filters
- **Easy extensibility** via sealed permits

This approach scales from simple use cases to complex multi-agent systems while maintaining clarity and type safety.

## Next Steps

- [I/O System](io-system.md) - Deep dive into IOManager
- [Data Flow](data-flow.md) - How composition enables data flow
- [Extending I/O Guide](../guides/extending-io.md) - Add your own IOManager variant
