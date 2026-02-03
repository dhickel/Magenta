# Context Management Workflow

This guide explains how to manage conversation context, including archiving, retrieval, and automatic compaction.

## Overview

Magenta's context management system:
- Automatically tracks conversation history
- Compacts context when token limits are approached
- Allows archiving important conversations
- Enables context retrieval and restoration
- Supports semantic memory with vector storage

## Context Lifecycle

```
User Input
    ↓
Context Manager (tracks message)
    ↓
Token Estimation (count tokens)
    ↓
Window Manager (check limits)
    ↓
Compaction (if threshold reached)
    ↓
Model (receives managed context)
```

## Key Concepts

### Context Elements

Each message in the context is a `ContextElement`:
- **System:** System prompts and instructions
- **User:** User messages
- **Assistant:** Agent responses
- **Tool:** Tool execution results
- **Summary:** Compacted conversation summaries

### Token Limits

Configuration settings that control context size:

```json
{
  "models": {
    "your-model": {
      "max_context": 16384,      // Maximum tokens in context
      "compact_threshold": 13107  // Trigger compaction at 80%
    }
  }
}
```

### Compaction Strategies

When context exceeds the threshold:
1. **Summarize:** Older messages replaced with AI summary
2. **Truncate:** Oldest messages dropped
3. **Sliding:** Keep recent + system, drop middle

## Using Context Management

### View Context Status

Check current context size and token usage:

```
magenta> /context status
```

Output:
```
Context Status:
────────────────────────────────────────────────────
Elements: 42
Total Tokens: 12,450 / 16,384 (76%)
Threshold: 13,107 tokens (80%)
Compaction Status: Not triggered
Last Compaction: Never
────────────────────────────────────────────────────
```

### Manual Compaction

Force context compaction before the threshold:

```
magenta> /context compact
```

The system will:
1. Identify old messages (beyond recent 10)
2. Summarize them with the AI model
3. Replace with a single Summary element
4. Report new token count

Output:
```
Context compacted successfully.
Before: 12,450 tokens (42 elements)
After: 5,230 tokens (12 elements)
Saved: 7,220 tokens
```

### Archive Context

Save the current context for later retrieval:

```
magenta> /context archive feature-implementation
```

Or use the agent's tools:
```
magenta> Archive the current conversation with key "feature-implementation"
```

This saves all context elements to the database with the specified key.

### Load Archived Context

Retrieve and restore a previously archived context:

```
magenta> /context load feature-implementation
```

Or via tools:
```
magenta> Retrieve the archived context from "feature-implementation"
```

The archived context is appended to current context (doesn't replace it).

### Clear Context

Start fresh with an empty context:

```
magenta> /context clear
```

**Warning:** This removes all conversation history. Archive important conversations first.

### Remember Facts

Store specific facts or information for long-term recall:

```
magenta> Remember that the database connection pool size is set to 20
```

Uses the `rememberFact` tool to store in context with high importance.

## Automatic Compaction

### How It Works

1. **Trigger:** Compaction runs when tokens exceed `compact_threshold`
2. **Selection:** Oldest 50% of conversation elements selected
3. **Summarization:** AI model creates summary preserving key points
4. **Replacement:** Old elements replaced with Summary element
5. **Verification:** New token count calculated

### What's Preserved

The compaction strategy preserves:
- All System messages (never compacted)
- Recent messages (last 10 elements)
- Summary structure and key information

### What's Summarized

Subject to compaction:
- Old User messages
- Old Assistant responses
- Tool execution results (unless recent)

## Advanced Patterns

### Session Archiving

Archive at key milestones:

```
# After completing a feature
magenta> Archive current context as "user-auth-complete"

# Before starting new task
magenta> Archive as "before-refactor-2025-02-01"

# After research session
magenta> Archive as "react-hooks-research"
```

### Context Restoration Workflow

```
# 1. Start new session
magenta> /agent code-assistant

# 2. Load relevant context
magenta> /context load user-auth-complete

# 3. Agent now has context from previous session
magenta> Continue implementing the password reset feature

# Agent can reference the archived context
```

### Combining Archives

Load multiple archived contexts:

```
magenta> Load context archive "api-design"
magenta> Load context archive "database-schema"
magenta> Now implement the user API endpoints using both designs
```

### Periodic Archiving

For long sessions, archive periodically:

```
# Every hour or after significant work
magenta> Archive context as "session-2025-02-01-10am"
magenta> [work for an hour]
magenta> Archive context as "session-2025-02-01-11am"
```

## Semantic Memory with Vector Store

For longer-term memory beyond immediate context:

```
# Store important information
magenta> Add this to knowledge base: "Project uses Java 21 with preview features enabled"

# Retrieve relevant information
magenta> Search knowledge base for information about Java version
```

The knowledge tools use vector embeddings for semantic search.

## Configuration Best Practices

### Sizing Recommendations

**Small Models (7B-13B params):**
```json
{
  "max_context": 4096,
  "compact_threshold": 3276  // 80% of 4096
}
```

**Medium Models (30B-70B params):**
```json
{
  "max_context": 8192,
  "compact_threshold": 6553  // 80%
}
```

**Large Models (70B+ params):**
```json
{
  "max_context": 16384,
  "compact_threshold": 13107  // 80%
}
```

### Compaction Threshold

- **70-75%:** More aggressive, costs more API calls
- **80%:** Balanced (recommended)
- **85-90%:** Less frequent, risks context overflow

### Compaction Frequency

Monitor in production:
```
magenta> /context status
```

If compacting too often:
- Increase `max_context`
- Increase `compact_threshold` percentage
- Use more concise prompts
- Reduce tool output verbosity

## Context Debugging

### View Context History

```
magenta> /history
```

Shows recent messages with token estimates.

### Search Context

Find specific topics in conversation:

```
magenta> /history search "database"
```

Returns all messages mentioning "database".

### Monitor Token Usage

```
# Check before important operation
magenta> /context status

# Note the token count
Elements: 35
Total Tokens: 9,240 / 16,384 (56%)

# Perform operation
magenta> [long operation with lots of tool calls]

# Check again
magenta> /context status
Elements: 48
Total Tokens: 13,890 / 16,384 (85%)
Compaction Status: Will trigger on next message
```

## Troubleshooting

### Context Growing Too Fast

**Symptoms:**
- Frequent compactions
- "Context full" errors
- Slow responses

**Solutions:**
1. Increase `max_context` if model supports it
2. Archive and clear context periodically
3. Reduce tool output verbosity
4. Use more concise prompts
5. Compact manually before long operations

### Lost Context After Compaction

**Symptoms:**
- Agent doesn't remember details
- Repeated questions
- Lost work

**Solutions:**
1. Archive before compaction
2. Lower `compact_threshold` for earlier compaction
3. Use `rememberFact` tool for key information
4. Store important details in knowledge base
5. Review system summarization quality

### Archive Not Loading

**Symptoms:**
- `/context load` fails
- "Archive not found" error

**Solutions:**
1. Check archive key spelling (case-sensitive)
2. Verify database is initialized
3. List all archives (future feature)
4. Check database file permissions

### Compaction Failed

**Symptoms:**
- Error during compaction
- Token count not reduced

**Solutions:**
1. Check model is accessible
2. Verify sufficient API credits
3. Review model's context window size
4. Try manual compaction with `/context compact`
5. Check logs for detailed error

## Best Practices

### 1. Archive Strategically

Archive at logical checkpoints:
- ✅ After completing a feature
- ✅ Before major refactoring
- ✅ End of research session
- ❌ Every single message
- ❌ Random intervals

### 2. Use Descriptive Keys

- ✅ "user-auth-implementation-2025-02-01"
- ✅ "api-design-discussion"
- ✅ "bug-fix-memory-leak"
- ❌ "archive1"
- ❌ "temp"
- ❌ "stuff"

### 3. Monitor Proactively

Check context status before:
- Long operations
- Complex multi-step tasks
- Importing large files
- Extensive research

### 4. Balance Retention

- Keep context focused on current task
- Archive completed work
- Clear context when switching projects
- Use knowledge base for persistent facts

### 5. Trust Compaction

The summarization strategy:
- Preserves key information
- Maintains conversation flow
- Reduces tokens 60-80%
- Keeps recent messages intact

## Example Workflow: Long Coding Session

```
# 1. Start session
magenta> /agent code-assistant

# 2. Check initial context
magenta> /context status
Elements: 1 (system prompt only)
Tokens: 450 / 16,384 (3%)

# 3. Work on feature (context grows)
magenta> Implement user authentication
[Agent works, uses tools, context grows to 8,000 tokens]

# 4. Archive checkpoint
magenta> /context archive auth-implementation-checkpoint-1

# 5. Continue work (context continues growing)
magenta> Add password reset functionality
[Context grows to 12,500 tokens - approaching threshold]

# 6. Check status
magenta> /context status
Elements: 47
Tokens: 12,500 / 16,384 (76%)
Compaction Status: Approaching threshold

# 7. Manual compaction before threshold
magenta> /context compact
Context compacted: 12,500 → 5,800 tokens

# 8. Continue with clean context
magenta> Implement email verification
[Context has room to grow again]

# 9. Final archive
magenta> /context archive auth-feature-complete
```

## Related Workflows

- **Multi-Agent:** See `multi-agent-delegation.md` for sharing context between agents
- **Task Execution:** See `task-execution.md` for task-specific context isolation

---

**Pro Tip:** Think of context like working memory - archive important milestones, compact regularly, and use knowledge base for long-term facts.
