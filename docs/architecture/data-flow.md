# Data Flow Through Magenta

## Overview

This document traces how data flows through Magenta's layered architecture, from user input to agent response and back.

## Complete Flow: User Message to Agent Response

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. USER INPUT                                                        │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
            User types: "Explain functional programming"
                               │
┌──────────────────────────────┴──────────────────────────────────────┐
│ 2. I/O LAYER: Input Reading                                          │
│                                                                       │
│   IOManager.Terminal.read("magenta> ")                               │
│   ├─ JLine LineReader.readLine() → "Explain functional programming" │
│   ├─ Command.parse() → Command.Message("Explain functional...")     │
│   ├─ SecurityFilter.inputFilter() → Command (unchanged)             │
│   └─ Return: Command.Message                                         │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────┐
│ 3. SESSION LAYER: Command Routing                                    │
│                                                                       │
│   AgentSession.run() loop iteration                                  │
│   ├─ Receives Command.Message                                        │
│   ├─ Pattern match: case Command.Message                             │
│   └─ Delegates to InteractionMode.processIteration(session, agent)   │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────┐
│ 4. INTERACTION LAYER: Message Processing                             │
│                                                                       │
│   InteractionMode.StreamingChat.processIteration()                   │
│   ├─ Add to history: agent.addMessage(UserMessage.from(text))       │
│   ├─ Get conversation: agent.getHistory() → List<ChatMessage>       │
│   ├─ Create handler: session.io().createResponseHandler(color)      │
│   └─ Generate response: agent.model().generate(history, handler)     │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────┐
│ 5. MODEL LAYER: AI Generation                                        │
│                                                                       │
│   ChatModel.Streaming.generate(messages, handler)                    │
│   ├─ LangChain4j StreamingChatLanguageModel                          │
│   ├─ Calls Ollama/OpenAI/Anthropic API                              │
│   ├─ Streams tokens asynchronously:                                  │
│   │   ├─ handler.write("Functional ")                               │
│   │   ├─ handler.write("programming ")                              │
│   │   ├─ handler.write("is...")                                     │
│   │   └─ handler.complete(fullResponse)                             │
│   └─ Returns CompletableFuture<Response<AiMessage>>                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ (each token)
┌──────────────────────────────┴──────────────────────────────────────┐
│ 6. RESPONSE HANDLING: Token Streaming                                │
│                                                                       │
│   ResponseHandler (Writer or SmoothWriter)                           │
│   ├─ write(token) called for each token                              │
│   │   ├─ Append to buffer: buffer.append(token)                     │
│   │   ├─ Output to pipe: pipe.print(token, colorCode)               │
│   │   └─ (SmoothWriter adds character delay)                        │
│   └─ complete(fullResponse) called at end                            │
│       └─ pipe.println("") → newline                                  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ (each print call)
┌──────────────────────────────┴──────────────────────────────────────┐
│ 7. I/O LAYER: Output Writing                                         │
│                                                                       │
│   IOManager.Terminal.print(token, colorCode)                         │
│   ├─ SecurityFilter.outputFilter(token) → token (unchanged)         │
│   ├─ Create AttributedString with color style                        │
│   ├─ PrintWriter.print(styledString) → terminal                      │
│   └─ Flush immediately                                               │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────┐
│ 8. TERMINAL: Display                                                 │
│                                                                       │
│   JLine Terminal renders with ANSI escape codes                      │
│   User sees: "Functional programming is..." (in cyan)                │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────┐
│ 9. HISTORY UPDATE: Context Preservation                              │
│                                                                       │
│   After streaming completes:                                         │
│   ├─ agent.addMessage(AiMessage.from(fullResponse))                 │
│   └─ Conversation history now contains both messages                 │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────┐
│ 10. LOOP: Ready for Next Input                                       │
│                                                                       │
│   AgentSession.run() continues loop                                  │
│   └─ Back to step 2: io.read("magenta> ")                           │
└───────────────────────────────────────────────────────────────────────┘
```

## Command Routing Flow

```
Command (sealed ADT)
  │
  ├─ Command.Exit
  │  └─ InteractionMode: session.setExit(true)
  │
  ├─ Command.Help
  │  └─ InteractionMode: io.println(helpText)
  │
  ├─ Command.Clear
  │  └─ IOManager.Terminal: terminal.puts(CLEAR) [handled locally]
  │
  ├─ Command.History
  │  └─ InteractionMode: print agent.getHistory() (TODO)
  │
  ├─ Command.Unknown
  │  └─ InteractionMode: io.error("Unknown command")
  │
  └─ Command.Message
     └─ InteractionMode: Process as AI chat message
```

**Benefits of ADT:**
- Exhaustive pattern matching (compiler checks)
- Type-safe command handling
- Clear command semantics
- Easy to add new command types

## Tool Execution Flow

### Example: Shell Command Execution

```
User: "Run 'ls -la'"
  │
  ▼
Agent decides to use ShellTools.runShellCommand("ls -la")
  │
  ├─ LangChain4j detects @Tool annotation
  ├─ Constructs ToolExecutionRequest
  └─ Invokes: runShellCommand("ls -la")
      │
      ├─ 1. Security Check
      │   │
      │   └─ securityManager.requireToolApproval("shell", "ls -la", io)
      │       ├─ Check blacklist → no match
      │       ├─ Check whitelist → no match
      │       ├─ Check approval required → "shell" is in list
      │       ├─ Prompt user: io.read("Allow? [y/N]")
      │       │   └─ User types "y"
      │       └─ Return true (approved)
      │
      ├─ 2. Execute Command
      │   │
      │   ├─ DefaultExecutor executor = new DefaultExecutor()
      │   ├─ ByteArrayOutputStream stdout = new ByteArrayOutputStream()
      │   ├─ CommandLine cmdLine = CommandLine.parse("ls -la")
      │   ├─ executor.execute(cmdLine) → exit code 0
      │   └─ Capture output
      │
      └─ 3. Return Result
          │
          └─ Return "Exit Code: 0\nOutput: [file listing]"
              │
              ▼
          LangChain4j adds ToolExecutionResultMessage to conversation
              │
              ▼
          Agent incorporates result into next response
              │
              ▼
          Agent: "I ran 'ls -la' and found these files: ..."
```

## Multi-Agent Communication Flow (Future)

```
User Agent (Terminal)                Background Agent (Internal)
     │                                        │
     │  "Analyze these logs"                  │
     ├──────────────────────────────────────▶ │
     │                                        │
     │                         AgentA creates IOManager.Internal
     │                         AgentA.io.enqueueInput("analyze: logs.txt")
     │                                        │
     │                                   AgentB reads input
     │                                   AgentB.io.read("")
     │                                        │
     │                                   AgentB processes
     │                                   Runs analysis tools
     │                                        │
     │                                   AgentB outputs result
     │                                   AgentB.io.println("Analysis complete")
     │                                        │
     │◀─────────────────────────────────────  │
     │  AgentA reads: agentB.io.readOutput()  │
     │                                        │
     │  "Analysis found 42 errors"            │
     └──────────────────────────────────────▶ User
```

**Key Points:**
- Each agent has its own IOManager instance
- Internal IOManagers use queues for communication
- Same agent code works with Terminal or Internal
- Security filters apply to both contexts

## Configuration Flow

```
Application Startup
  │
  ├─ 1. Parse CLI Arguments
  │   └─ Arg.parseAll(args) → Map<String, Arg.Value>
  │
  ├─ 2. Load Configuration
  │   ├─ ConfigManager.loadOrFail(configPath)
  │   ├─ Parse JSON → Config record
  │   └─ Validate structure
  │
  ├─ 3. Create Global Instances
  │   ├─ SecurityManager(config.security)
  │   ├─ ToolProvider(null, null, securityManager)
  │   └─ GlobalContext(config, args, securityManager, toolProvider)
  │
  ├─ 4. Create I/O Manager
  │   ├─ new IOManager.Terminal()
  │   ├─ io.setColorsConfig(config.colors)
  │   └─ io.setSecurityFilter(securityManager.createFilter(io))
  │
  ├─ 5. Create Agent Session
  │   ├─ new AgentSession(io, context, config.global.baseAgent)
  │   ├─ Load AgentConfig from context.config().agents
  │   ├─ Load ModelConfig from context.config().models
  │   ├─ Create ChatModel (Streaming or Blocking)
  │   └─ Initialize conversation history
  │
  └─ 6. Run Session
      └─ session.run() → starts interaction loop
```

## Data Dependencies Graph

```
Main.java
  │
  ├─ creates → Config (from JSON)
  │             │
  │             ├─ used by → SecurityManager
  │             ├─ used by → AgentFactory
  │             └─ used by → Agent (loads model/prompt)
  │
  ├─ creates → GlobalContext
  │             │
  │             ├─ contains → Config
  │             ├─ contains → SecurityManager
  │             ├─ contains → ToolProvider
  │             └─ contains → CLI args
  │
  ├─ creates → IOManager.Terminal
  │             │
  │             ├─ receives → ColorsConfig
  │             ├─ receives → SecurityFilter
  │             └─ used by → AgentSession
  │
  └─ creates → AgentSession
                │
                ├─ receives → IOManager
                ├─ receives → GlobalContext
                ├─ creates → Agent
                │             │
                │             ├─ loads → AgentConfig
                │             ├─ creates → ChatModel
                │             └─ uses → ToolProvider.createTools()
                │
                └─ creates → InteractionMode.StreamingChat
```

## Security Filter Application Points

```
┌────────────────────────────────────────────────────────┐
│              Application Code Layer                     │
│  (Agent, Session, InteractionMode)                      │
│  ↕ Uses interfaces: InputPipe, OutputPipe             │
└────────────────┬───────────────────────────────────────┘
                 │ All I/O goes through IOManager
                 ▼
┌────────────────────────────────────────────────────────┐
│                 IOManager Layer                         │
│  ┌──────────────────────────────────────────────────┐ │
│  │  SecurityFilter.inputFilter()  ← Applied here    │ │
│  │     ↓                                             │ │
│  │  Command cmd = Command.parse(input)             │ │
│  │  cmd = filter.inputFilter(cmd, this)            │ │
│  │  return cmd                                       │ │
│  └──────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────┐ │
│  │  SecurityFilter.outputFilter() ← Applied here    │ │
│  │     ↓                                             │ │
│  │  String text = ...                               │ │
│  │  text = filter.outputFilter(text)               │ │
│  │  writer.print(text)                              │ │
│  └──────────────────────────────────────────────────┘ │
└────────────────┬───────────────────────────────────────┘
                 │ Filtered I/O
                 ▼
┌────────────────────────────────────────────────────────┐
│            Terminal / Queue / Network                   │
│  (JLine Terminal, ConcurrentLinkedQueue, Socket)        │
└────────────────────────────────────────────────────────┘
```

**Key Insight:** Application code never calls filters directly. Filtering is transparent and automatic at the I/O boundary.

## Tool Security Flow

```
Tool Method Call
  │
  ▼
@Tool
public String runShellCommand(String cmd) {
  │
  ├─ SecurityManager.requireToolApproval("shell", cmd, io)
  │   │
  │   ├─ Check blacklist → blocked? Return false
  │   ├─ Check whitelist → allowed? Return true
  │   ├─ Check approval required
  │   │   ├─ "shell" in list? → prompt user via io.read()
  │   │   └─ User response → return boolean
  │   └─ Default → return true
  │
  ├─ if (!approved) → return error message
  │
  └─ else → execute command → return result
}
```

**Two-Level Security:**
1. **I/O Level:** SecurityFilter processes input/output automatically
2. **Tool Level:** SecurityManager enforces approval workflow explicitly

## Streaming Response Flow

```
Model generates tokens asynchronously
  │
  ├─ Token 1: "Functional"
  │   ├─ handler.write("Functional")
  │   │   ├─ buffer.append("Functional")
  │   │   └─ pipe.print("Functional", colorCode)
  │   │       └─ io.print("Functional", colorCode)
  │   │           └─ filter + render → terminal
  │   └─ User sees: "Functional"
  │
  ├─ Token 2: " programming"
  │   ├─ handler.write(" programming")
  │   │   └─ ... (same flow)
  │   └─ User sees: "Functional programming"
  │
  ├─ Token N: "..."
  │   └─ ... (same flow)
  │
  └─ Complete
      ├─ handler.complete(fullResponse)
      │   └─ pipe.println("") → newline
      └─ agent.addMessage(AiMessage.from(fullResponse))
```

**Benefits:**
- Real-time feedback (user sees response as it's generated)
- Interruptible (can be cancelled mid-stream)
- Responsive UX (no waiting for full response)
- Memory efficient (tokens processed incrementally)

## Summary

Magenta's data flow demonstrates:

1. **Clear Layer Separation** - Data passes through well-defined boundaries
2. **Transparent Security** - Filtering at I/O boundary, invisible to app code
3. **Type-Safe Routing** - Sealed ADTs ensure exhaustive command handling
4. **Polymorphic I/O** - Same flow works with Terminal, Internal, Network
5. **Streaming-First** - Tokens processed incrementally for responsiveness
6. **Configuration-Driven** - Agent behavior controlled by config, not code

Each component handles its responsibility without tight coupling to others, enabling easy extension and testing.

## Next Steps

- [Session Lifecycle](../components/session-lifecycle.md) - Session management
- [Agent System](../components/agent-system.md) - Agent creation and delegation
- [Streaming Responses](../components/streaming-responses.md) - ResponseHandler details
