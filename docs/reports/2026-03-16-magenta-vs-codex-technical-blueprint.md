# Magenta2 vs. OpenAI Codex Technical Blueprint

## 1. Executive Summary
This report provides a comprehensive technical comparison between the Magenta2 runtime and the OpenAI Codex CLI. While Magenta2 excels in granular task tracking and state integrity for long-running research tasks, Codex CLI demonstrates superior efficiency through plan-centric batching and a robust "rollout" persistence model. This blueprint outlines a strategic path for Magenta2 to adopt high-performance patterns from Codex while maintaining its unique strengths in reliability and Java-native integration.

## 2. Comparative Analysis

### 2.1 Tooling Layer
- **Magenta2**: Focuses on built-in, statically defined tools (File, SQL, Shell). High reliability but limited extensibility.
- **Codex**: Leverages a dynamic tool ecosystem with a focus on "Plan" management. Tools are often wrappers around SDKs or external services.
- **Gap**: Magenta2 lacks a standardized way to integrate third-party toolsets (e.g., MCP) and rich media return types.

### 2.2 Harnessing & Safety
- **Magenta2**: Uses a `ToolManager` with basic approval gates. Execution is sequential and tightly coupled to the session loop.
- **Codex**: Implements a "Guardian" pattern with rule-based syscall interception and environment isolation. Execution is more resilient to model hallucinations.
- **Gap**: Magenta2 needs a more robust safety layer that can intercept low-level system calls and enforce complex security policies.

### 2.3 Context & Compaction
- **Magenta2**: Relies on natural language summaries for compaction, which can be lossy and lead to "context drift."
- **Codex**: Uses `replacement_history` and `reference_context_item` to maintain a machine-usable state anchor. Compaction is treated as an explicit state transition.
- **Gap**: Magenta2 needs a deterministic way to reconstruct active context from durable history without relying solely on LLM-generated summaries.

## 3. Codex Tool Catalog

| Tool Name | Description | Arguments | Return Type |
|-----------|-------------|-----------|-------------|
| `update_plan` | Batched update of the project roadmap. | `plan_items: List<Task>` | `PlanStatus` |
| `read_file` | Reads file content with optional line ranges. | `path: String, start: Int, end: Int` | `String` |
| `grep_search` | Pattern matching across the codebase. | `pattern: String, include: String` | `List<Match>` |
| `submit_result` | Finalizes a task and returns results to the user. | `result: String, artifacts: List<Path>` | `Void` |
| `agent_delegate` | Spawns a sub-agent for a specific sub-task. | `task: String, context: Map` | `AgentResult` |

## 4. Strategic Recommendations for Magenta2
1. **Adopt Plan-Centric Workflows**: Transition from granular `todo` items to a structured `Plan` object that can be updated in batches. This should eventually replace or wrap the existing `TodoTools` and its database-backed persistence layer.
2. **Implement MCP Bridge**: Allow Magenta2 to consume tools from the Model Context Protocol ecosystem.
3. **Enhance Compaction Logic**: Use "Reference Context" anchors to prevent state loss during history compression.
4. **Introduce Syscall Sandboxing**: Implement a gated shell that utilizes OS-level sandboxing (e.g., `seccomp`, `bwrap`, or restricted containers) to intercept and validate system calls against a security policy.

## 5. Technical Specifications

### 5.1 ToolResultV2
- **Purpose**: Support for rich media (images, tables) and structured metadata in tool outputs.
- **Specification**:
  ```java
  public record ToolResultV2(
      String summary,
      Map<String, Object> metadata,
      List<MediaAttachment> attachments,
      boolean success
  ) {}

  public record MediaAttachment(
      String mimeType,
      byte[] data,
      String url,
      String description
  ) {}
  ```

### 5.2 McpBridgeTool
- **Purpose**: Dynamic discovery and execution of MCP-compliant tools.
- **Pattern**: Discovery -> Schema Mapping -> Dispatch -> Result Normalization.

### 5.3 SyscallGatedShellTool
- **Purpose**: A shell environment that utilizes OS-level sandboxing to intercept `exec`, `open`, and `network` calls.
- **Mechanism**: The Java `Guardian` service configures the sandbox environment (e.g., generating a `seccomp` profile or `bwrap` arguments) before execution, ensuring calls are checked against a whitelist/blacklist.

## 6. Architectural Roadmap

### Phase 1: Guardian & Safety (Short-term)
- Implement the `Guardian` service for rule-based tool validation.
- Introduce `ToolResultV2` and `MediaAttachment` records for improved output structure.
- Integrate the `Guardian` service into the existing `ShellTools` for basic command-string validation while planning for full OS sandboxing.

### Phase 2: Event-Driven Core (Mid-term)
- Refactor the execution loop to be event-driven (`AgentControl`).
- Enable asynchronous tool execution and multi-agent orchestration.
- Begin the migration of `TodoTools` to the new `Plan` object architecture.

### Phase 3: Rollout Persistence (Long-term)
- Implement `RolloutRecorder` for deterministic session reconstruction.
- Move to a "Reference Context" model for compaction to eliminate context drift.
- Finalize the transition to the Plan-centric workflow.
