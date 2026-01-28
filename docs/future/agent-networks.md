# Agent Networks (Planned Feature)

**Status:** Design Phase
**Target Version:** 2.0

## Overview

Agent networks enable multiple agents to work together, communicate, delegate tasks, and coordinate actions. This builds on Magenta's existing IOManager.Internal for agent-to-agent communication.

## Vision

```
User → Terminal Agent → Delegates to Specialized Agents
                        ├─ Code Agent (writes code)
                        ├─ Researcher Agent (gathers information)
                        ├─ Tester Agent (runs tests)
                        └─ Coordinator Agent (manages subtasks)
```

## Planned Components

### 1. AgentNetwork

**Purpose:** Manage a network of cooperating agents

```java
// Planned API (subject to change)
public class AgentNetwork {
    private final Map<String, AgentNode> agents;
    private final MessageRouter router;
    private final GlobalContext context;

    // Register agent in network
    public void register(String agentId, Agent agent, IOManager io) {
        agents.put(agentId, new AgentNode(agent, io));
    }

    // Send message to specific agent
    public CompletableFuture<String> sendMessage(String fromId, String toId, String message) {
        AgentNode target = agents.get(toId);
        target.io().enqueueInput(message);
        return target.processNextMessage();
    }

    // Broadcast to all agents
    public void broadcast(String fromId, String message) {
        agents.values().stream()
            .filter(node -> !node.id().equals(fromId))
            .forEach(node -> node.io().enqueueInput(message));
    }

    // Query for capable agent
    public Optional<String> findCapableAgent(String capability) {
        return agents.values().stream()
            .filter(node -> node.hasCapability(capability))
            .map(AgentNode::id)
            .findFirst();
    }
}
```

### 2. AgentNode

**Purpose:** Wrapper for agent with network metadata

```java
public record AgentNode(
    String id,
    Agent agent,
    IOManager io,
    Set<String> capabilities,
    AgentStatus status
) {
    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    public CompletableFuture<String> processNextMessage() {
        return CompletableFuture.supplyAsync(() -> {
            Command cmd = io.read("");
            if (cmd instanceof Command.Message msg) {
                agent.addMessage(UserMessage.from(msg.text()));
                ResponseHandler handler = io.createResponseHandler(null);
                agent.model().generate(agent.getHistory(), handler);
                return ((IOManager.Internal) io).readOutput();
            }
            return "";
        });
    }
}

enum AgentStatus {
    IDLE, BUSY, OFFLINE
}
```

### 3. MessageRouter

**Purpose:** Route messages between agents

```java
public interface MessageRouter {
    // Direct routing
    void route(AgentMessage message, String targetId);

    // Capability-based routing
    void routeByCapability(AgentMessage message, String capability);

    // Load-balanced routing
    void routeBalanced(AgentMessage message, List<String> candidateIds);
}
```

### 4. AgentMessage ADT

**Purpose:** Typed inter-agent communication

```java
public sealed interface AgentMessage {
    String from();
    String to();
    Instant timestamp();

    record TaskRequest(
        String from,
        String to,
        String taskDescription,
        Map<String, Object> parameters,
        Instant timestamp
    ) implements AgentMessage {}

    record TaskResult(
        String from,
        String to,
        String result,
        boolean success,
        Instant timestamp
    ) implements AgentMessage {}

    record QueryRequest(
        String from,
        String to,
        String query,
        Instant timestamp
    ) implements AgentMessage {}

    record QueryResponse(
        String from,
        String to,
        String answer,
        double confidence,
        Instant timestamp
    ) implements AgentMessage {}

    record Observation(
        String from,
        String to,
        String observation,
        Instant timestamp
    ) implements AgentMessage {}
}
```

### 5. Coordination Patterns

#### Master-Worker
```java
public class MasterWorkerNetwork {
    private final String masterId;
    private final Set<String> workerIds;

    public List<CompletableFuture<String>> distributeWork(List<String> tasks) {
        return tasks.stream()
            .map(task -> {
                String workerId = selectWorker();
                return network.sendMessage(masterId, workerId, task);
            })
            .toList();
    }
}
```

#### Pipeline
```java
public class PipelineNetwork {
    private final List<String> stages;

    public CompletableFuture<String> process(String input) {
        CompletableFuture<String> result = CompletableFuture.completedFuture(input);
        for (String stageId : stages) {
            result = result.thenCompose(
                output -> network.sendMessage("pipeline", stageId, output)
            );
        }
        return result;
    }
}
```

#### Peer-to-Peer
```java
public class PeerToPeerNetwork {
    public void collaborate(String taskDescription) {
        // Agents discover each other's capabilities
        // Self-organize to solve task
        // No central coordinator
    }
}
```

## Proposed Configuration

```json
{
  "agent_networks": {
    "main_network": {
      "agents": [
        {
          "id": "coordinator",
          "agent_config": "coordinator",
          "capabilities": ["planning", "delegation", "coordination"],
          "autostart": true
        },
        {
          "id": "coder",
          "agent_config": "coder",
          "capabilities": ["coding", "debugging", "refactoring"],
          "autostart": false
        },
        {
          "id": "researcher",
          "agent_config": "researcher",
          "capabilities": ["research", "web_search", "summarization"],
          "autostart": false
        }
      ],
      "routing": {
        "strategy": "capability_based",
        "fallback": "coordinator"
      },
      "coordination": {
        "pattern": "master_worker",
        "master": "coordinator"
      }
    }
  }
}
```

## Use Cases

### 1. Code Generation with Testing

```
User → Terminal Agent: "Create a REST API for user management"
  │
  ├─ Terminal Agent → Coordinator: "Create REST API"
  │   │
  │   ├─ Coordinator → Coder: "Write API code"
  │   │   └─ Coder: [writes code, saves to files]
  │   │
  │   ├─ Coordinator → Tester: "Run tests on API"
  │   │   └─ Tester: [runs tests, reports results]
  │   │
  │   └─ Coordinator → Terminal Agent: "API created and tested"
  │
  └─ Terminal Agent → User: "Created API with tests passing"
```

### 2. Research and Summarization

```
User → Researcher: "What are the latest trends in AI?"
  │
  ├─ Researcher → Web Agent: "Fetch AI news from sources"
  │   └─ Web Agent: [fetches multiple pages]
  │
  ├─ Researcher → Analyzer: "Extract key trends from these articles"
  │   └─ Analyzer: [analyzes content]
  │
  ├─ Researcher → Summarizer: "Create executive summary"
  │   └─ Summarizer: [generates summary]
  │
  └─ Researcher → User: [presents trends and summary]
```

### 3. Long-Running Background Tasks

```
User → Terminal Agent: "Monitor system logs and alert on errors"
  │
  ├─ Terminal Agent → Monitor Agent: "Start log monitoring"
  │   │
  │   └─ Monitor Agent: [runs in background]
  │       ├─ Periodically checks logs
  │       ├─ If error found → alerts Terminal Agent
  │       └─ Continues monitoring
  │
  └─ User continues other tasks
```

## Agent Discovery

```java
public interface AgentDiscovery {
    // Register agent capabilities
    void advertise(String agentId, Set<String> capabilities);

    // Find agents with capability
    List<String> findAgents(String capability);

    // Query agent metadata
    AgentMetadata getMetadata(String agentId);
}

public record AgentMetadata(
    String id,
    String role,
    Set<String> capabilities,
    AgentStatus status,
    int currentLoad,  // Number of active tasks
    Instant lastSeen
) {}
```

## Communication Protocols

### Synchronous (Request-Response)
```java
// Agent A requests, waits for response
String response = network.sendMessage("agentA", "agentB", "query").get();
```

### Asynchronous (Fire-and-Forget)
```java
// Agent A sends, doesn't wait
network.sendMessage("agentA", "agentB", "notification");
// Continue with other work
```

### Pub-Sub (Broadcast)
```java
// Agent publishes event to topic
network.publish("log_entries", logEntry);

// Agents subscribe to topics
network.subscribe("log_entries", "monitor-agent", this::handleLogEntry);
```

## Failure Handling

### Agent Failure
```java
public class FailureDetector {
    // Detect unresponsive agents
    public boolean isResponsive(String agentId) {
        return network.ping(agentId, Duration.ofSeconds(5));
    }

    // Restart failed agent
    public void restart(String agentId) {
        AgentNode node = network.get(agentId);
        node.status(AgentStatus.OFFLINE);
        // Create new agent instance
        // Restore from checkpoint
        node.status(AgentStatus.IDLE);
    }
}
```

### Message Failure
```java
public class MessageReliability {
    // Retry with exponential backoff
    public CompletableFuture<String> sendWithRetry(
        String from, String to, String message, int maxRetries
    ) {
        return sendAttempt(from, to, message, 0, maxRetries);
    }

    // Dead letter queue for undeliverable messages
    public void handleUndeliverable(AgentMessage message) {
        deadLetterQueue.add(message);
        notifyCoordinator("Message undeliverable: " + message);
    }
}
```

## Integration with Existing System

### IOManager.Internal Usage
```java
// Each agent in network has Internal IOManager
IOManager.Internal agentIO = new IOManager.Internal();
agentIO.setSecurityFilter(SecurityFilter.identity());

// Agents communicate via queues
agentA.io().println("message for agentB");
String output = agentA.io().readOutput();
agentB.io().enqueueInput(output);
```

### DelegateTool Enhancement
```java
@Tool("Delegate task to specialized agent")
public String delegateTask(
    @P("Agent capability required") String capability,
    @P("Task description") String task
) {
    Optional<String> agentId = network.findCapableAgent(capability);
    if (agentId.isEmpty()) {
        return "Error: No agent with capability: " + capability;
    }

    CompletableFuture<String> result = network.sendMessage(
        currentAgentId, agentId.get(), task
    );

    return result.get();  // Wait for result
}
```

## Implementation Phases

### Phase 1: Basic Network
- AgentNetwork class
- Agent registration
- Direct message routing
- IOManager.Internal integration

### Phase 2: Typed Messages
- AgentMessage ADT
- Structured communication
- Message serialization

### Phase 3: Discovery & Routing
- Capability-based discovery
- Smart routing strategies
- Load balancing

### Phase 4: Coordination Patterns
- Master-worker
- Pipeline
- Pub-sub

### Phase 5: Reliability
- Failure detection
- Message retries
- Dead letter queue
- Agent restart

## Open Questions

1. **Message Format:** JSON, Protocol Buffers, or custom format?
2. **Persistence:** Should messages be persisted to database?
3. **Security:** How to authenticate agents and authorize actions?
4. **Observability:** How to monitor network health and message flow?
5. **Scaling:** How many agents can a network support?

## Related Features

- [Context Management](context-management.md) - Agents need context management
- [Pluggable Commands](pluggable-commands.md) - Network commands (/agents, /delegate)

## See Also

- [I/O System](../architecture/io-system.md) - IOManager.Internal for communication
- [Agent System](../components/agent-system.md) - Agent structure
- [Data Flow](../architecture/data-flow.md) - Multi-agent communication flow
