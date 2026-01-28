# Context Management (Planned Feature)

**Status:** Design Phase
**Target Version:** 2.0

## Overview

Context management in Magenta will provide intelligent handling of conversation history, semantic memory, and context compression to work within model token limits while preserving important information.

## Planned Components

### 1. ContextManager

**Purpose:** Coordinate context-related operations

```java
// Planned API (subject to change)
public interface ContextManager {
    // Summarize old messages to free context
    void summarize(Agent agent, int keepRecentCount);

    // Compress context using importance scoring
    void compress(Agent agent, double importanceThreshold);

    // Query semantic memory
    List<MemoryEntry> search(String query, int limit);

    // Save checkpoint of current context
    String checkpoint(Agent agent);

    // Restore from checkpoint
    void restore(Agent agent, String checkpointId);
}
```

### 2. Context Strategies

Different strategies for managing context:

#### Sliding Window
```java
final class SlidingWindow implements ContextStrategy {
    // Keep last N messages, discard older ones
    // Simple, predictable, but loses history
}
```

#### Summarization
```java
final class Summarization implements ContextStrategy {
    // Periodically summarize old messages
    // Preserves gist, loses details
}
```

#### Importance-Based
```java
final class ImportanceBased implements ContextStrategy {
    // Score messages by importance
    // Keep important, summarize/discard unimportant
}
```

#### Hierarchical
```java
final class Hierarchical implements ContextStrategy {
    // Multiple levels of detail
    // 1-line summary → paragraph → full message
    // Agent can "drill down" for more detail
}
```

### 3. Semantic Memory Integration

```java
public interface SemanticMemory {
    // Store information in vector database
    void store(String content, Map<String, Object> metadata);

    // Retrieve relevant information
    List<MemoryEntry> retrieve(String query, int limit);

    // Update existing memory
    void update(String entryId, String content);

    // Forget specific memories
    void forget(String entryId);
}
```

### 4. Context Budget Tracking

```java
public class ContextBudget {
    private final int maxTokens;
    private int usedTokens;

    public boolean hasSpace(ChatMessage message) {
        int messageTokens = estimateTokens(message);
        return (usedTokens + messageTokens) <= maxTokens;
    }

    public void makeSpace(int tokensNeeded) {
        // Trigger compression/summarization
    }

    public double percentUsed() {
        return (double) usedTokens / maxTokens;
    }
}
```

## Proposed Configuration

```json
{
  "context_management": {
    "strategy": "importance_based",
    "max_messages": 50,
    "summarize_threshold": 40,
    "importance_threshold": 0.6,
    "checkpoint_interval_messages": 10,
    "semantic_memory": {
      "enabled": true,
      "embedding_model": "text-embedding-ada-002",
      "vector_store": "chroma",
      "similarity_threshold": 0.7
    }
  }
}
```

## Use Cases

### 1. Long Conversations
- User has extended conversation over hours
- Context manager summarizes old messages
- Recent context preserved, history available on demand

### 2. Project Memory
- Agent remembers project-specific information
- Stored in vector database with project tags
- Retrieved when relevant to current task

### 3. Multi-Session Continuity
- User returns days later
- Agent loads checkpoint + relevant memories
- Conversation continues naturally

### 4. Context Pressure
- Approaching token limit
- Context manager triggers compression
- Less important messages summarized
- Critical information preserved

## Integration Points

### With Session
```java
public class AgentSession implements Session {
    private final ContextManager contextManager;

    public void run() {
        while (!shouldExit()) {
            // Check context budget
            if (contextManager.shouldCompress(agent)) {
                contextManager.compress(agent, 0.6);
            }

            mode.processIteration(this, agent);

            // Periodic checkpoint
            if (shouldCheckpoint()) {
                contextManager.checkpoint(agent);
            }
        }
    }
}
```

### With Agent
```java
public class Agent {
    private ContextBudget budget;

    public void addMessage(ChatMessage message) {
        if (!budget.hasSpace(message)) {
            contextManager.makeSpace(estimateTokens(message));
        }
        conversationHistory.add(message);
    }
}
```

### Command Integration
```java
// Possible future commands
/context status          // Show context usage
/context summarize       // Manual summarization
/context checkpoint      // Save current state
/context restore <id>    // Load checkpoint
/context search <query>  // Search semantic memory
```

## Implementation Phases

### Phase 1: Basic Context Window
- Simple sliding window (keep last N messages)
- Token counting
- Manual summarization command

### Phase 2: Automatic Compression
- Importance scoring
- Automatic summarization when threshold reached
- Configurable strategies

### Phase 3: Semantic Memory
- Vector store integration
- Automatic memory extraction
- Relevant memory retrieval

### Phase 4: Checkpoints
- Session persistence
- Checkpoint save/restore
- Multi-session continuity

## Open Questions

1. **Token Estimation:** How to accurately estimate tokens before sending to model?
2. **Importance Scoring:** What factors determine message importance?
3. **Summarization Quality:** How to ensure summaries preserve critical information?
4. **Memory Relevance:** How to determine which memories are relevant to current context?
5. **Performance:** What's the overhead of context management operations?

## Related Features

- [Agent Networks](agent-networks.md) - Agents may need separate context management
- [Pluggable Commands](pluggable-commands.md) - Context commands may be context-dependent

## See Also

- [Session Lifecycle](../components/session-lifecycle.md) - How sessions manage state
- [Agent System](../components/agent-system.md) - Agent structure and behavior
