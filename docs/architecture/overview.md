# Architecture Overview

## System Architecture

Magenta is built on a layered, composable architecture that separates concerns and enables extensibility.

```
┌─────────────────────────────────────────────────────────────┐
│                     Application Layer                       │
│  Main.java, AgentSession, InteractionMode                   │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                      Session Layer                           │
│  Session (interface), GlobalContext, Agent                   │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                        I/O Layer                             │
│  IOManager (sealed), InputPipe, OutputPipe, SecurityFilter   │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                       Domain Layer                           │
│  Tools, Task, TodoService, AgentFactory                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                    Persistence Layer                         │
│  Database, Vector Store, Configuration                       │
└─────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. GlobalContext
**Purpose:** Application-wide, immutable state shared across all sessions and agents.

**Contains:**
- `Config` - Full application configuration (agents, models, security, etc.)
- `Map<String, String> args` - Parsed command-line arguments
- `SecurityManager` - Security policy enforcement
- `ToolProvider` - Tool factory and registry

**Lifecycle:** Created once at startup, shared by all agents.

### 2. Session
**Purpose:** Interface defining a session's contract for I/O and context access.

**Responsibilities:**
- Provide access to IOManager (I/O operations)
- Provide access to GlobalContext (shared state)
- Manage exit control (shouldExit, setExit)

**Implementations:**
- `AgentSession` - AI agent with conversational interaction

### 3. Agent
**Purpose:** Lightweight wrapper around agent configuration and state.

**Contains:**
- `AgentConfig` - Name, model, tools, system prompt
- `ChatModel` - AI model (Streaming or Blocking)
- `List<ChatMessage>` - Conversation history

**Does NOT:**
- Manage I/O (that's IOManager's job)
- Handle session lifecycle (that's AgentSession's job)

### 4. IOManager
**Purpose:** Polymorphic I/O abstraction supporting multiple contexts.

**Variants:**
- `Terminal` - Human-facing terminal I/O (JLine)
- `Internal` - Agent-to-agent queue-based I/O

**Responsibilities:**
- Input reading and command parsing
- Output writing with color support
- Security filtering (transparent)
- ResponseHandler creation

### 5. SecurityManager & SecurityFilter
**Purpose:** Enforce security policies across tool execution and I/O.

**SecurityManager:**
- Instance-based policy enforcement
- Tool approval workflow (blacklist, whitelist, prompt)
- Creates SecurityFilter for IOManager contexts

**SecurityFilter:**
- Functional, composable filters
- Three filter types: input, output, tool
- Applied transparently at I/O boundary

### 6. ToolProvider
**Purpose:** Centralized tool creation with dependency injection.

**Features:**
- Creates tools from configuration names
- Injects SecurityManager and IOManager
- Maintains tool registry
- Supports tool composition (e.g., DelegateTool uses ToolProvider)

## Component Relationships

```
Main
  ├── creates → GlobalContext
  │   ├── contains → Config
  │   ├── contains → SecurityManager
  │   └── contains → ToolProvider
  │
  ├── creates → IOManager.Terminal
  │   └── receives → SecurityFilter (from SecurityManager)
  │
  └── creates → AgentSession
      ├── receives → IOManager
      ├── receives → GlobalContext
      └── creates → Agent
          ├── loads → AgentConfig (from GlobalContext)
          ├── creates → ChatModel (from config)
          └── uses → Tools (from ToolProvider)
```

## Data Flow: User Message to Agent Response

1. **User Input**
   - User types message in terminal
   - `IOManager.Terminal.read()` captures input

2. **Command Parsing**
   - `Command.parse()` converts to ADT
   - `SecurityFilter.inputFilter()` applied

3. **Session Processing**
   - `AgentSession` receives Command
   - Delegates to `InteractionMode.processIteration()`

4. **Agent Processing**
   - Message added to conversation history
   - `Agent.model().generate()` called

5. **Response Streaming**
   - ResponseHandler created by IOManager
   - LangChain4j streams tokens
   - `SecurityFilter.outputFilter()` applied
   - Output written to terminal

6. **History Update**
   - Agent response added to conversation history
   - Ready for next iteration

## Configuration-Driven Behavior

All agent behavior is defined in `config.json`:

```json
{
  "global": {
    "base_agent": "assistant",
    "storage_path": "data/magenta.db"
  },
  "agents": {
    "assistant": {
      "model": "llama-3.3-70b",
      "tools": ["shell", "web", "filesystem", "todo"],
      "system_prompt_key": "assistant_prompt",
      "color": "CYAN"
    }
  },
  "models": {
    "llama-3.3-70b": {
      "model_name": "llama3.3:70b",
      "endpoint": "ollama-local",
      "streaming": true
    }
  },
  "security": {
    "approval_required_for": ["shell", "web_fetch"],
    "always_allow_commands": ["ls", "pwd"],
    "blocked_commands": ["rm -rf"]
  }
}
```

**Benefits:**
- No hardcoded agent roles
- Easy to add new agents
- Security policies centralized
- Model selection flexible

## Design Patterns in Use

| Pattern | Usage | Example |
|---------|-------|---------|
| **Sealed ADT** | Type-safe variants | `IOManager.Terminal`, `Command.Exit` |
| **Composable Interfaces** | Small, focused contracts | `InputPipe`, `OutputPipe` |
| **Composition** | Build from components | `AgentSession` composes Agent, IOManager |
| **Factory** | Configuration-driven creation | `AgentFactory`, `ToolProvider` |
| **Strategy** | Pluggable behavior | `InteractionMode`, `ChatModel` |
| **Observer** | Reactive updates | `TodoService` property change |
| **Functional Composition** | Chainable filters | `SecurityFilter.andThen()` |

## Extension Points

Magenta is designed to be extended at multiple points:

1. **New IOManager Variants** - Add Network, Database contexts
2. **New InteractionModes** - Add BatchProcessing, REPL variants
3. **New Tools** - Implement LangChain4j @Tool methods
4. **New SecurityFilters** - Compose custom filtering logic
5. **New Session Types** - Implement Session interface
6. **New Endpoints** - Add EndpointConfig variants

## Key Architectural Benefits

1. **Polymorphism** - Components work with interfaces, not concrete classes
2. **Type Safety** - Sealed interfaces enable exhaustive matching
3. **Testability** - Use `IOManager.Internal` for tests, no terminal needed
4. **Extensibility** - Add new variants without modifying existing code
5. **Separation of Concerns** - Each layer has clear responsibilities
6. **Configuration-Driven** - Behavior changes without code changes
7. **Security by Design** - Filtering integrated at I/O layer

## Next Steps

- [Composability](composability.md) - Deep dive into ADTs and interfaces
- [I/O System](io-system.md) - Understand IOManager architecture
- [Session Lifecycle](../components/session-lifecycle.md) - Session management
