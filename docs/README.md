# Magenta Documentation

**Version:** 1.0.0-alpha
**Last Updated:** 2026-01-28

Welcome to the Magenta documentation. Magenta is a hierarchical, agent-driven task management system built on composable interfaces and functional design principles.

## Documentation Structure

### Architecture
Core architectural concepts and design patterns:
- [Overview](architecture/overview.md) - System architecture and components
- [Composability](architecture/composability.md) - ADTs and interface composition
- [I/O System](architecture/io-system.md) - Polymorphic I/O architecture
- [Security](architecture/security.md) - Security filtering and approval
- [Data Flow](architecture/data-flow.md) - How data moves through the system

### Components
Deep dives into specific components:
- [Session Lifecycle](components/session-lifecycle.md) - Session, AgentSession, GlobalContext
- [Agent System](components/agent-system.md) - Agent creation and delegation
- [Command Handling](components/command-handling.md) - Command parsing and routing
- [Streaming Responses](components/streaming-responses.md) - Response handlers and writers

### Guides
Practical guides for extending Magenta:
- [Getting Started](guides/getting-started.md) - Quick start guide
- [Creating Agents](guides/creating-agents.md) - Define custom agents
- [Extending I/O](guides/extending-io.md) - Add new IOManager contexts
- [Custom Tools](guides/custom-tools.md) - Build your own tools
- [Security Configuration](guides/security-config.md) - Configure security policies

### Future Features
Planned features and extension points:
- [Context Management](future/context-management.md) - Smart context handling
- [Agent Networks](future/agent-networks.md) - Multi-agent coordination
- [Pluggable Commands](future/pluggable-commands.md) - Dynamic command systems

## Quick Start

```java
// 1. Load configuration
Config config = ConfigManager.loadOrFail(configPath);

// 2. Create global context
SecurityManager securityManager = new SecurityManager(config.security);
ToolProvider toolProvider = new ToolProvider(null, null, securityManager);
GlobalContext context = new GlobalContext(config, args, securityManager, toolProvider);

// 3. Create I/O manager
IOManager.Terminal io = new IOManager.Terminal();
io.setSecurityFilter(securityManager.createFilter(io));

// 4. Create and run agent session
AgentSession session = new AgentSession(io, context, "assistant");
session.run();
```

## Key Design Principles

1. **Prefer ADTs and Composability** - Use sealed interfaces with nested implementations
2. **Composable Interfaces** - Small, focused interfaces that combine naturally
3. **Functional & Data-Driven** - Configuration is the source of truth
4. **Minimal Changes** - Make targeted, focused modifications
5. **Separation of Concerns** - Each component has one clear responsibility

## Contributing

When extending Magenta:
- Read relevant architecture and component documentation first
- Follow existing patterns (ADTs, composable interfaces)
- Keep documentation up to date with your changes
- See CLAUDE.md for detailed development guidelines
