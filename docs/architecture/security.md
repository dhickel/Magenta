# Security Architecture

## Overview

Magenta's security system operates at two levels:
1. **Tool Approval** - SecurityManager enforces policies before tool execution
2. **I/O Filtering** - SecurityFilter processes input/output at the I/O boundary

Both are instance-based, context-aware, and composable.

## Security Components

```
┌──────────────────────────────────────────────────────────┐
│                    GlobalContext                         │
│  ┌────────────────────┐    ┌─────────────────────────┐  │
│  │ SecurityManager    │    │   ToolProvider          │  │
│  │  - Policy engine   │───▶│  - Injects security     │  │
│  │  - Approval flow   │    │  - Creates tools        │  │
│  └────────┬───────────┘    └─────────────────────────┘  │
└───────────┼──────────────────────────────────────────────┘
            │ creates
            ▼
   ┌────────────────┐
   │SecurityFilter  │
   │  - inputFilter │  Applied at IOManager level
   │  - outputFilter│  Transparent to application
   │  - toolFilter  │  (toolFilter unused - tools call SecurityManager)
   └────────┬───────┘
            │ set on
            ▼
   ┌────────────────┐
   │   IOManager    │
   │  - Applies     │  All I/O automatically filtered
   │    filters     │
   └────────────────┘
```

## SecurityManager (Policy Engine)

### Structure

```java
public class SecurityManager {
    private final SecurityConfig config;

    public SecurityManager(SecurityConfig config) {
        this.config = config;
    }

    public boolean requireToolApproval(String toolType, String command, IOManager io) {
        // Policy enforcement logic
    }

    public SecurityFilter createFilter(IOManager io) {
        // Create context-specific filter
    }
}
```

### Tool Approval Flow

```
Tool called with command
  ↓
SecurityManager.requireToolApproval(toolType, command, io)
  ↓
1. Check Blacklist
   ├─ Match found? → Print blocked message → return false
   └─ No match? → Continue
  ↓
2. Check Whitelist
   ├─ Exact match? → return true
   └─ No match? → Continue
  ↓
3. Check Approval Required
   ├─ Tool type in approval list?
   │  ├─ Yes → Prompt user via io.read() → return user choice
   │  └─ No → Continue
  ↓
4. Default: Allow (return true)
```

### Configuration

**config.json:**
```json
{
  "security": {
    "approval_required_for": ["shell", "web_fetch"],
    "always_allow_commands": [
      "ls", "pwd", "cd", "echo",
      "git status", "git diff"
    ],
    "blocked_commands": [
      "rm -rf /",
      ":(){ :|:& };:",
      "mkfs",
      "dd if=/dev/zero"
    ]
  }
}
```

### Example: Shell Tool Integration

```java
@Tool("Execute a shell command")
public String runShellCommand(@P("The command to run") String command) {
    // Security check BEFORE execution
    if (!securityManager.requireToolApproval("shell", command, io)) {
        return "Error: Command execution denied by user or security policy.";
    }

    // Execute command
    DefaultExecutor executor = DefaultExecutor.builder().get();
    // ... execution logic
}
```

### Approval Interaction

**Terminal Context (Interactive):**
```
User: "Run 'rm important.txt'"
  ↓
Agent: [calls ShellTools.runShellCommand("rm important.txt")]
  ↓
SecurityManager: requireToolApproval("shell", "rm important.txt", io)
  ↓
IOManager.Terminal: [prints security warning in yellow]
  ╔═══════════════════════════════════════════╗
  ║ SECURITY: Shell command requires approval ║
  ║ Command: rm important.txt                 ║
  ║ Allow execution? [y/N]:                   ║
  ╚═══════════════════════════════════════════╝
  ↓
User types: "n"
  ↓
SecurityManager: returns false
  ↓
Tool: returns "Error: Command execution denied"
```

**Internal Context (Programmatic):**
```java
IOManager.Internal io = new IOManager.Internal();

// Option 1: Pre-approve by simulating user input
io.enqueueInput("y");  // Auto-approve
boolean approved = securityManager.requireToolApproval("shell", "ls", io);

// Option 2: Use whitelist in config
// (Commands in always_allow_commands bypass prompts)

// Option 3: Use custom SecurityFilter
io.setSecurityFilter(customFilter);
```

## SecurityFilter (I/O Filtering)

### Structure

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

    public static SecurityFilter identity() {
        return new SecurityFilter(
            (cmd, io) -> cmd,      // Pass-through
            text -> text,          // Pass-through
            (req, io) -> req       // Pass-through
        );
    }
}
```

### Filter Types

#### Input Filter
**Purpose:** Process commands before they reach the agent

**Signature:** `BiFunction<Command, IOManager, Command>`

**Use Cases:**
- Block sensitive command patterns
- Redact personal information from input
- Log all user commands
- Rate limit command frequency
- Transform or normalize commands

**Example:**
```java
BiFunction<Command, IOManager, Command> logInputFilter = (cmd, io) -> {
    if (cmd instanceof Command.Message msg) {
        logger.info("User command: " + msg.text());
    }
    return cmd;
};
```

#### Output Filter
**Purpose:** Process output before it's displayed

**Signature:** `Function<String, String>`

**Use Cases:**
- Redact sensitive information (passwords, API keys)
- Remove ANSI codes for logging
- Translate output
- Add prefixes/suffixes
- Sanitize HTML/markdown

**Example:**
```java
Function<String, String> redactSecretsFilter = text -> {
    return text
        .replaceAll("password=\\S+", "password=***")
        .replaceAll("api_key=\\S+", "api_key=***")
        .replaceAll("token=\\S+", "token=***");
};
```

#### Tool Filter
**Purpose:** Process tool execution requests (currently unused)

**Signature:** `BiFunction<ToolExecutionRequest, IOManager, ToolExecutionRequest>`

**Use Cases (Future):**
- Intercept tool calls before execution
- Modify tool parameters
- Block certain tool combinations
- Log all tool executions

**Note:** Currently, tools call `SecurityManager.requireToolApproval()` directly. Tool filter is reserved for future LangChain4j integration if they expose tool execution hooks.

### Integration with IOManager

**Setup:**
```java
IOManager.Terminal io = new IOManager.Terminal();
SecurityFilter filter = securityManager.createFilter(io);
io.setSecurityFilter(filter);
```

**Input Filtering (Transparent):**
```java
// In IOManager.Terminal.read()
String input = reader.readLine(prompt);
Command cmd = Command.parse(input);
cmd = securityFilter.inputFilter().apply(cmd, this);  // Automatic
return cmd;
```

**Output Filtering (Transparent):**
```java
// In IOManager.Terminal.print()
String filtered = securityFilter.outputFilter().apply(text);  // Automatic
writer.print(createStyledString(filtered, color));
```

**Application code never needs to call filters manually - they're applied automatically at the I/O boundary.**

## Composable Filters

### Chaining Filters

```java
SecurityFilter logInput = new SecurityFilter(
    (cmd, io) -> { log(cmd); return cmd; },
    text -> text,
    (req, io) -> req
);

SecurityFilter redactSecrets = new SecurityFilter(
    (cmd, io) -> cmd,
    text -> text.replaceAll("password", "***"),
    (req, io) -> req
);

SecurityFilter rateLimit = new SecurityFilter(
    (cmd, io) -> {
        if (isRateLimited()) {
            io.error("Rate limit exceeded");
            return new Command.Unknown("rate limited");
        }
        return cmd;
    },
    text -> text,
    (req, io) -> req
);

// Compose: logInput → redactSecrets → rateLimit
SecurityFilter combined = logInput
    .andThen(redactSecrets)
    .andThen(rateLimit);

io.setSecurityFilter(combined);
```

**Execution Order:**
```
Input: User types "password=secret123"
  ↓
logInput: Logs command → pass through
  ↓
redactSecrets: Changes to "password=***"
  ↓
rateLimit: Checks rate → pass through or block
  ↓
Result: Command with "password=***"
```

### Context-Aware Filtering

Filters receive `IOManager` parameter, enabling:

**Check IOManager Type:**
```java
BiFunction<Command, IOManager, Command> contextAwareFilter = (cmd, io) -> {
    if (io instanceof IOManager.Terminal) {
        // Interactive session - allow all
        return cmd;
    } else if (io instanceof IOManager.Internal) {
        // Agent-to-agent - restrict commands
        if (cmd instanceof Command.Message msg && msg.text().contains("danger")) {
            return new Command.Unknown("blocked");
        }
    }
    return cmd;
};
```

**Interactive Approval:**
```java
BiFunction<Command, IOManager, Command> confirmationFilter = (cmd, io) -> {
    if (cmd instanceof Command.Message msg && msg.text().contains("delete")) {
        io.println("This command will delete data. Confirm? [y/N]");
        Command response = io.read("");
        if (!(response instanceof Command.Message m && m.text().equalsIgnoreCase("y"))) {
            return new Command.Unknown("cancelled");
        }
    }
    return cmd;
};
```

## Security Best Practices

### 1. Always Use SecurityManager for Tools

❌ **Don't:**
```java
@Tool
public String runCommand(String cmd) {
    // DANGEROUS: No security check
    return executeShellCommand(cmd);
}
```

✅ **Do:**
```java
@Tool
public String runCommand(String cmd) {
    if (!securityManager.requireToolApproval("shell", cmd, io)) {
        return "Error: Command denied";
    }
    return executeShellCommand(cmd);
}
```

### 2. Set SecurityFilter on Every IOManager

❌ **Don't:**
```java
IOManager.Terminal io = new IOManager.Terminal();
// Forgot to set filter - no I/O filtering!
```

✅ **Do:**
```java
IOManager.Terminal io = new IOManager.Terminal();
io.setSecurityFilter(securityManager.createFilter(io));
```

### 3. Use Whitelists Over Blacklists

❌ **Fragile:**
```json
{
  "blocked_commands": ["rm", "del", "erase", "delete", ...]
}
```

✅ **Robust:**
```json
{
  "always_allow_commands": ["ls", "pwd", "git status"],
  "approval_required_for": ["shell"]  // Everything else needs approval
}
```

### 4. Compose Filters Functionally

❌ **Stateful:**
```java
public class StatefulFilter {
    private int count = 0;
    public Command filter(Command cmd) {
        count++;  // Mutable state
        return cmd;
    }
}
```

✅ **Functional:**
```java
BiFunction<Command, IOManager, Command> filter = (cmd, io) -> {
    // Pure function, no mutable state
    return cmd;
};
```

## Security Configuration Examples

### Development (Permissive)
```json
{
  "security": {
    "approval_required_for": [],
    "always_allow_commands": ["*"],
    "blocked_commands": []
  }
}
```

### Production (Strict)
```json
{
  "security": {
    "approval_required_for": ["shell", "web_fetch", "filesystem"],
    "always_allow_commands": [
      "ls", "pwd", "echo", "cat",
      "git status", "git log", "git diff"
    ],
    "blocked_commands": [
      "rm -rf", "mkfs", "dd", ":(){ :|:& };:",
      "curl | sh", "wget | sh"
    ]
  }
}
```

### Agent-to-Agent (Programmatic)
```json
{
  "security": {
    "approval_required_for": [],
    "always_allow_commands": ["*"],
    "blocked_commands": []
  }
}
```

With custom filter:
```java
IOManager.Internal io = new IOManager.Internal();
SecurityFilter agentFilter = new SecurityFilter(
    (cmd, mgr) -> cmd,  // Allow all input
    text -> text,       // No output filtering
    (req, mgr) -> req   // Allow all tools
);
io.setSecurityFilter(agentFilter);
```

## Testing Security

### Unit Tests

```java
@Test
public void testBlockedCommand() {
    SecurityConfig config = new SecurityConfig(
        List.of(),
        List.of(),
        List.of("rm -rf")
    );
    SecurityManager sm = new SecurityManager(config);
    IOManager.Internal io = new IOManager.Internal();

    boolean approved = sm.requireToolApproval("shell", "rm -rf /", io);

    assertThat(approved).isFalse();
}

@Test
public void testWhitelistedCommand() {
    SecurityConfig config = new SecurityConfig(
        List.of("shell"),
        List.of("ls"),
        List.of()
    );
    SecurityManager sm = new SecurityManager(config);
    IOManager.Internal io = new IOManager.Internal();

    boolean approved = sm.requireToolApproval("shell", "ls", io);

    assertThat(approved).isTrue();  // No prompt needed
}

@Test
public void testOutputFiltering() {
    Function<String, String> redact = text ->
        text.replaceAll("password=\\S+", "password=***");

    SecurityFilter filter = new SecurityFilter(
        (cmd, io) -> cmd,
        redact,
        (req, io) -> req
    );

    IOManager.Internal io = new IOManager.Internal();
    io.setSecurityFilter(filter);

    io.println("User: admin, password=secret123");
    String output = io.readOutput();

    assertThat(output).contains("password=***");
    assertThat(output).doesNotContain("secret123");
}
```

## Future Enhancements

### 1. Audit Logging
```java
BiFunction<Command, IOManager, Command> auditFilter = (cmd, io) -> {
    auditLog.record(new AuditEntry(
        timestamp: Instant.now(),
        user: getCurrentUser(),
        command: cmd,
        context: io.getClass().getSimpleName()
    ));
    return cmd;
};
```

### 2. Role-Based Access Control
```java
public boolean requireToolApproval(String toolType, String command, IOManager io, User user) {
    if (user.hasRole("admin")) {
        return true;  // Admins bypass checks
    }
    // ... normal approval flow
}
```

### 3. Policy Engine
```java
public class PolicyEngine {
    public boolean evaluate(Command cmd, Context context) {
        return rules.stream()
            .allMatch(rule -> rule.evaluate(cmd, context));
    }
}
```

### 4. Remote Authorization
```java
IOManager.Network io = new IOManager.Network(socket);
boolean approved = remoteAuthService.requestApproval(toolType, command, io);
```

## Summary

Magenta's security architecture provides:
- **Two-level security** - Tool approval + I/O filtering
- **Context-aware** - Different policies for different IOManager types
- **Composable** - Chain filters functionally
- **Transparent** - Filtering at I/O boundary, invisible to app code
- **Configuration-driven** - Security policies in config.json
- **Testable** - Easy to test with IOManager.Internal

Security is integrated at the architectural level, not bolted on as an afterthought.

## Next Steps

- [Data Flow](data-flow.md) - How security fits into data flow
- [Security Configuration Guide](../guides/security-config.md) - Configure policies
- [Custom Tools Guide](../guides/custom-tools.md) - Integrate security in tools
