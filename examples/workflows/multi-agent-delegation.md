# Multi-Agent Delegation Workflow

This guide demonstrates how to use multiple agents with task delegation and message passing in Magenta.

## Overview

Magenta supports multi-agent workflows where agents can:
- Communicate via message queue
- Delegate tasks to specialized agents
- Work on tasks concurrently
- Coordinate complex workflows

## Prerequisites

- Magenta configured with multiple agents
- Agents have `agents` or `agent_comm` tool enabled
- Message queue initialized (automatic)

## Workflow Steps

### 1. Start with Base Agent

Launch Magenta with a configuration that has multiple agents:

```bash
mvn exec:java -Dexec.mainClass="com.magenta.Main" -Dexec.args="--config examples/config/code-assistant.json"
```

This configuration has two agents:
- `code-assistant` - General development assistant
- `reviewer` - Code review specialist

### 2. List Available Agents

Check which agents are available:

```
magenta> /agents
```

Output:
```
Available agents:
────────────────────────────────────────────────────────────
Name            Model         Tools    Security
code-assistant  devstral      5        default
reviewer        codellama     4        default
────────────────────────────────────────────────────────────
```

### 3. Send a Message to Another Agent

From the base agent, send a message:

```
magenta> Send a message to the reviewer agent asking them to review the FileSystemTools.java file
```

The agent will use the `sendMessage` tool:
```
Sending message to reviewer: "Please review src/main/java/com/magenta/tools/FileSystemTools.java for code quality, potential bugs, and security issues."
```

### 4. Switch to Receiving Agent

Switch to the reviewer agent:

```
magenta> /agent reviewer
```

Output:
```
Switching to agent: reviewer
Model: codellama
Tools: filesystem, git, search, context
Security: default
────────────────────────────────────────────────────────────
```

### 5. Check for Messages

Check if there are pending messages:

```
reviewer> /messages
```

Or ask the agent to check:

```
reviewer> Check if I have any messages
```

The agent will use the `checkMessages` tool and see the review request.

### 6. Agent Processes the Request

The reviewer agent reads the file and performs the review:

```
reviewer> Review the FileSystemTools.java file as requested
```

The agent will:
1. Read the file using `readFile` tool
2. Analyze the code
3. Provide detailed feedback

### 7. Send Response Back

The reviewer can send a response:

```
reviewer> Send a message to code-assistant with the review findings
```

### 8. Switch Back and Read Response

```
reviewer> /agent code-assistant

code-assistant> Check my messages
```

The code-assistant receives the review and can act on it.

## Advanced Patterns

### Task Delegation

Instead of just messaging, agents can delegate entire tasks:

```
code-assistant> Delegate the code review task to the reviewer agent
```

This uses the `delegateTask` tool to send a structured task request.

### Broadcast Messages

Send a message to all agents:

```
code-assistant> Broadcast a message: "Project build completed successfully"
```

### Checking Agent Network Status

View the agent network topology:

```
code-assistant> /network
```

Shows:
- Active agents
- Message queue status
- Pending messages per agent

## Example: Complete Review Workflow

Here's a complete workflow demonstrating multi-agent collaboration:

```
# 1. Start with code-assistant
code-assistant> I've just finished implementing a new feature in UserService.java.
                Can you send it to the reviewer for a code review?

[Agent sends message to reviewer]

# 2. Switch to reviewer
code-assistant> /agent reviewer

# 3. Reviewer checks messages and processes
reviewer> Check my messages

[Reviewer sees the review request]

reviewer> Read and review src/main/java/com/magenta/service/UserService.java

[Reviewer performs detailed analysis]

reviewer> Send the review results back to code-assistant with my findings:
          1. Good use of dependency injection
          2. Missing null check on line 45
          3. Consider extracting validation logic to separate method
          4. Add unit tests for edge cases

# 4. Switch back to code-assistant
reviewer> /agent code-assistant

# 5. Code-assistant receives and acts on feedback
code-assistant> Check my messages

[Sees reviewer feedback]

code-assistant> Fix the issues mentioned in the review

[Code-assistant makes the improvements]

code-assistant> Send confirmation to reviewer that issues are fixed
```

## Configuration for Multi-Agent Workflows

### Enable Agent Communication Tools

In your `config.json`, ensure agents have the communication tools:

```json
{
  "agents": {
    "agent-1": {
      "tools": ["filesystem", "git", "agents", "context"]
    },
    "agent-2": {
      "tools": ["filesystem", "search", "agents", "context"]
    }
  }
}
```

### Define Specialized Roles

Create agents with complementary capabilities:

```json
{
  "agents": {
    "developer": {
      "tools": ["filesystem", "git", "shell", "agents"],
      "system_prompt": "developer_prompt"
    },
    "tester": {
      "tools": ["filesystem", "shell", "agents"],
      "system_prompt": "tester_prompt"
    },
    "reviewer": {
      "tools": ["filesystem", "search", "agents"],
      "system_prompt": "reviewer_prompt"
    }
  }
}
```

## Message Queue Behavior

- **Thread-safe:** Multiple agents can send/receive concurrently
- **In-memory:** Messages persist during session only
- **FIFO:** Messages delivered in order sent
- **Targeted:** Messages can be sent to specific agents or broadcast

## Best Practices

### 1. Clear Communication

Be explicit about what you're asking agents to do:
- **Good:** "Review UserService.java for security vulnerabilities"
- **Bad:** "Check this"

### 2. Use Appropriate Tools

- Simple notifications → `sendMessage`
- Structured work → `delegateTask`
- Everyone needs to know → broadcast

### 3. Context Sharing

Agents have independent context. When delegating:
- Include file paths explicitly
- Provide necessary background
- Reference specific line numbers if relevant

### 4. Check Messages Regularly

When working with multiple agents:
```
/agent agent-1
Check messages
Do work
Send results
/agent agent-2
```

### 5. Use Descriptive Agent Names

Name agents by their role:
- `code-reviewer` not `agent-2`
- `tester` not `agent-b`
- `researcher` not `agent-research`

## Troubleshooting

### Messages Not Received

**Problem:** Agent doesn't see messages

**Solutions:**
- Verify both agents have `agents` tool enabled
- Check spelling of agent names (case-sensitive)
- Use `/agents` to see valid agent names
- Try `/messages` command directly

### Agent Can't Communicate

**Problem:** "Tool 'sendMessage' not found"

**Solutions:**
- Add `"agents"` to agent's tools list
- Restart Magenta after config changes
- Verify AgentNetwork is initialized

### Lost Context

**Problem:** Agent doesn't remember earlier conversation

**Solutions:**
- Each agent has independent context
- Include necessary context in messages
- Use `context` tool to archive/retrieve shared context
- Consider having agents archive findings

## Related Workflows

- **Context Management:** See `context-management.md` for sharing context between agents
- **Task Execution:** See `task-execution.md` for template-based task workflows

## Next Steps

1. Try the example workflow above
2. Create agents with complementary tools
3. Design prompts that encourage collaboration
4. Experiment with delegation patterns
5. Build multi-step workflows

---

**Pro Tip:** Design agents like a team - each with specific expertise and tools. Let them collaborate naturally by sending clear messages with context.
