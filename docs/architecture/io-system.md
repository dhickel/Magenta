# I/O System Architecture

## Overview

Magenta's I/O system is built around polymorphic contexts that enable the same agent code to work in different environments: terminal interaction, agent-to-agent communication, network protocols, and database persistence.

## Core Abstraction: IOManager

```java
public sealed interface IOManager extends InputPipe, OutputPipe, AutoCloseable
        permits IOManager.Terminal, IOManager.Internal {

    // Factory: Create response handlers
    ResponseHandler createResponseHandler(Integer colorCode, int delayMs);
    ResponseHandler createResponseHandler(Integer colorCode);

    // Security: Integrated filtering
    SecurityFilter securityFilter();
    void setSecurityFilter(SecurityFilter filter);
}
```

### Design Rationale

**Why sealed?**
- Exhaustive pattern matching
- Compiler-verified variant coverage
- Clear set of supported contexts

**Why extends InputPipe + OutputPipe?**
- Separation of concerns (input vs output)
- Components can depend on specific capabilities
- Easy to mock for testing

**Why nested classes?**
- Encapsulation: variants live with interface
- Discoverability: `IOManager.Terminal` is self-documenting
- Package organization: related types together

## IOManager Variants

### Terminal Variant

**Purpose:** Human-facing terminal interaction using JLine3

```java
final class Terminal implements IOManager {
    private final org.jline.terminal.Terminal terminal;
    private final LineReader reader;
    private final PrintWriter writer;
    private ColorsConfig colorsConfig;
    private SecurityFilter securityFilter;

    public Terminal() {
        this.terminal = TerminalBuilder.builder()
            .system(true)
            .build();
        this.reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .build();
        this.writer = terminal.writer();
        this.securityFilter = SecurityFilter.identity();
    }
}
```

**Features:**
- Color-coded output via JLine AttributedString
- Input history and editing (arrow keys, Ctrl+R)
- Slash command handling (`/clear` clears screen locally)
- Styled prompts and formatted output
- Character-by-character streaming with delays

**Input Flow:**
```
User types input
  → LineReader.readLine(prompt) [JLine handles editing]
  → Command.parse(input) [Convert to ADT]
  → securityFilter.inputFilter(cmd, this) [Apply security]
  → Return filtered Command
```

**Output Flow:**
```
Code calls print(text, color)
  → securityFilter.outputFilter(text) [Apply security]
  → Convert color to AttributedStyle
  → writer.print(AttributedString) [JLine renders with ANSI codes]
  → writer.flush()
```

**Use Cases:**
- Interactive user sessions
- Development and debugging
- Real-time streaming responses
- Colorful, formatted output

**Example:**
```java
IOManager.Terminal io = new IOManager.Terminal();
io.setColorsConfig(config.colors);
io.setSecurityFilter(securityManager.createFilter(io));

// Read with styled prompt
Command cmd = io.read("magenta> ");

// Write with color
io.println("Agent response", OutputStyle.AI_RESPONSE.code);

// Create streaming handler
ResponseHandler handler = io.createResponseHandler(colorCode, delayMs);
```

### Internal Variant

**Purpose:** Agent-to-agent communication via in-memory queues

```java
final class Internal implements IOManager {
    private final ConcurrentLinkedQueue<String> inputQueue;
    private final ConcurrentLinkedQueue<String> outputQueue;
    private SecurityFilter securityFilter;

    public Internal() {
        this.inputQueue = new ConcurrentLinkedQueue<>();
        this.outputQueue = new ConcurrentLinkedQueue<>();
        this.securityFilter = SecurityFilter.identity();
    }

    // Enqueue input for this manager to read
    public void enqueueInput(String input) {
        inputQueue.offer(input);
    }

    // Read output written by this manager
    public String readOutput() {
        return outputQueue.poll();
    }
}
```

**Features:**
- No terminal dependencies (pure Java)
- Thread-safe concurrent queues
- No color support (colors ignored)
- Instant read/write (no user interaction)
- Programmatic I/O testing

**Input Flow:**
```
Agent A enqueues input
  → inputQueue.offer(text)
  → Agent B calls read()
    → inputQueue.poll() [Get next message]
    → Command.parse(text)
    → securityFilter.inputFilter(cmd, this)
    → Return filtered Command
```

**Output Flow:**
```
Agent calls println(text)
  → securityFilter.outputFilter(text)
  → outputQueue.offer(text)
  → Other agent can readOutput()
```

**Use Cases:**
- Agent-to-agent messaging
- Background tasks without terminal
- Unit testing agent logic
- Multi-agent coordination
- Headless operation

**Example:**
```java
// Agent A output
IOManager.Internal agentA = new IOManager.Internal();
agentA.println("Task complete: analyzed 100 files");

// Agent B input
IOManager.Internal agentB = new IOManager.Internal();
String message = agentA.readOutput();
agentB.enqueueInput(message);
Command cmd = agentB.read("");  // No prompt needed

// Process command
if (cmd instanceof Command.Message msg) {
    agentB.println("Acknowledged: " + msg.text());
}
```

## InputPipe Interface

```java
public interface InputPipe {
    Command read(String prompt);
}
```

**Responsibilities:**
- Read user/agent input
- Parse into Command ADT
- Apply security filtering
- Return type-safe Command

**Implementations:**
- `IOManager.Terminal` - reads from LineReader
- `IOManager.Internal` - polls from inputQueue

**Benefits:**
- Components can depend on just InputPipe
- Easy to mock for testing
- Context-independent code

## OutputPipe Interface

```java
public interface OutputPipe {
    void print(String text);
    void println(String text);

    default void print(String text, int color) {
        print(text);  // Ignore color by default
    }

    default void println(String text, int color) {
        println(text);  // Ignore color by default
    }
}
```

**Responsibilities:**
- Write text output
- Optionally apply color formatting
- Apply security filtering
- Flush output immediately

**Implementations:**
- `IOManager.Terminal` - writes to JLine PrintWriter with AttributedString
- `IOManager.Internal` - enqueues to outputQueue (ignores color)

**Benefits:**
- Components can write output without knowing I/O details
- `Writer` and `SmoothWriter` work with any OutputPipe
- Default methods allow color-ignorant implementations

## ResponseHandler Interface

```java
public interface ResponseHandler {
    void write(String token);
    void complete(String fullResponse);
    void error(String error);
}
```

**Purpose:** Handle streaming AI model responses

**Implementations:**
- `Writer` - immediate output to OutputPipe
- `SmoothWriter` - queued output with character-by-character delay

**Flow:**
```
LangChain4j StreamingChatLanguageModel
  → handler.write(token1)
  → handler.write(token2)
  → handler.write(tokenN)
  → handler.complete(fullResponse)
```

**Benefits:**
- Decouples model streaming from I/O
- Supports different streaming strategies
- Easy to add new handlers (e.g., BufferedWriter)

## Writer Classes

### Writer (Base Implementation)

```java
public class Writer implements ResponseHandler {
    protected final OutputPipe pipe;
    protected final Integer colorCode;
    protected final StringBuilder buffer;

    @Override
    public void write(String token) {
        buffer.append(token);
        if (colorCode != null) {
            pipe.print(token, colorCode);
        } else {
            pipe.print(token);
        }
    }

    @Override
    public void complete(String fullResponse) {
        pipe.println("");  // Newline at end
    }
}
```

**Behavior:** Immediate output of each token

**Use Cases:**
- Fast, real-time streaming
- No artificial delays
- Testing and debugging

### SmoothWriter (Delayed Output)

```java
public class SmoothWriter extends Writer {
    private final int delayMs;
    private final BlockingQueue<String> queue;
    private final Thread writerThread;

    @Override
    public void write(String token) {
        buffer.append(token);
        queue.offer(token);  // Queue for delayed output
    }

    private void processQueue() {
        while (!stopped) {
            String token = queue.poll();
            for (char c : token.toCharArray()) {
                pipe.print(String.valueOf(c), colorCode);
                Thread.sleep(delayMs);  // Delay per character
            }
        }
    }
}
```

**Behavior:** Character-by-character output with configurable delay

**Use Cases:**
- Simulating typing effect
- Slowing down fast model responses
- Better readability for long responses

## Security Integration

### SecurityFilter Structure

```java
public record SecurityFilter(
    BiFunction<Command, IOManager, Command> inputFilter,
    Function<String, String> outputFilter,
    BiFunction<ToolExecutionRequest, IOManager, ToolExecutionRequest> toolFilter
) {
    public static SecurityFilter identity() {
        return new SecurityFilter(
            (cmd, io) -> cmd,      // Pass-through
            text -> text,          // Pass-through
            (req, io) -> req       // Pass-through
        );
    }
}
```

### Filter Application Points

**Input Filtering:**
```java
// In IOManager.Terminal.read()
Command cmd = Command.parse(input);
cmd = securityFilter.inputFilter().apply(cmd, this);
return cmd;
```

**Output Filtering:**
```java
// In IOManager.Terminal.print()
String filtered = securityFilter.outputFilter().apply(text);
writer.print(createStyledString(filtered, color));
```

**Benefits:**
- Transparent filtering at I/O boundary
- Context-aware (IOManager passed to filters)
- Composable (andThen() chains filters)
- No manual security checks in application code

## Context-Specific Behavior

### Terminal Context
```java
IOManager.Terminal terminal = new IOManager.Terminal();
terminal.setColorsConfig(config.colors);
terminal.setSecurityFilter(securityManager.createFilter(terminal));

// Rich interaction
Command cmd = terminal.read("styled-prompt> ");
terminal.println("Colored output", OutputStyle.SUCCESS.code);
terminal.error("Error message");  // Red with [ERROR] prefix
```

### Internal Context
```java
IOManager.Internal internal = new IOManager.Internal();
internal.setSecurityFilter(SecurityFilter.identity());

// Programmatic I/O
internal.enqueueInput("message from agent A");
Command cmd = internal.read("");  // Polls queue immediately
internal.println("response from agent B");
String output = internal.readOutput();  // Gets queued output
```

### Future: Network Context
```java
IOManager.Network network = new IOManager.Network(socketChannel);
network.setSecurityFilter(securityManager.createFilter(network));

// Remote communication
Command cmd = network.read("");  // Reads from socket
network.println("response");     // Writes to socket
```

## Polymorphic Usage

### Same Code, Different Contexts

```java
public class AgentSession implements Session {
    private final IOManager io;  // Works with ANY variant

    public void run() {
        while (!shouldExit()) {
            Command cmd = io.read(prompt);  // Polymorphic call
            processCommand(cmd);
        }
    }
}

// Terminal session
AgentSession userSession = new AgentSession(
    new IOManager.Terminal(), context, "assistant"
);

// Background agent session
AgentSession bgAgent = new AgentSession(
    new IOManager.Internal(), context, "analyzer"
);
```

**Benefits:**
- Same session code works in all contexts
- Easy to switch contexts (just change IOManager)
- Testing uses Internal, production uses Terminal

## Testing Strategy

### Unit Tests with Internal IOManager

```java
@Test
public void testAgentResponse() {
    // Setup
    IOManager.Internal io = new IOManager.Internal();
    GlobalContext context = createTestContext();
    AgentSession session = new AgentSession(io, context, "test-agent");

    // Input
    io.enqueueInput("test message");

    // Execute
    session.processIteration();

    // Assert
    String output = io.readOutput();
    assertThat(output).contains("expected response");
}
```

**Benefits:**
- No terminal initialization needed
- Fast, deterministic tests
- Easy to verify input/output
- Can test multi-agent communication

## Extension: Adding New Contexts

### Example: Network IOManager

```java
sealed interface IOManager permits Terminal, Internal, Network {

    final class Network implements IOManager {
        private final SocketChannel channel;
        private SecurityFilter securityFilter;

        @Override
        public Command read(String prompt) {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            channel.read(buffer);
            String input = new String(buffer.array()).trim();
            Command cmd = Command.parse(input);
            return securityFilter.inputFilter().apply(cmd, this);
        }

        @Override
        public void print(String text) {
            String filtered = securityFilter.outputFilter().apply(text);
            channel.write(ByteBuffer.wrap(filtered.getBytes()));
        }

        @Override
        public ResponseHandler createResponseHandler(Integer colorCode) {
            return new Writer(this, null);  // No color over network
        }
    }
}
```

**No changes needed to:**
- `AgentSession` - already uses IOManager interface
- `Writer` - already uses OutputPipe interface
- `InteractionMode` - already uses Session.io()

**This is the power of polymorphism and sealed ADTs.**

## Summary

Magenta's I/O system provides:
- **Polymorphic contexts** via sealed IOManager ADT
- **Composable interfaces** via InputPipe and OutputPipe
- **Transparent security** via integrated SecurityFilter
- **Flexible streaming** via ResponseHandler interface
- **Easy testing** via Internal variant
- **Simple extension** via sealed permits

This architecture enables agent code to work seamlessly across terminal interaction, agent-to-agent communication, network protocols, and future contexts without modification.

## Next Steps

- [Security Architecture](security.md) - Deep dive into filtering
- [Data Flow](data-flow.md) - How data moves through I/O system
- [Extending I/O Guide](../guides/extending-io.md) - Add your own IOManager
