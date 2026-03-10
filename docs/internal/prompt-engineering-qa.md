# Prompt and Tool Description QA Report

## System Prompt
- **Target File:** `configs/prompts/base/system.md`
- **Identity:** Managent
- **Token Estimate:** ~1200 tokens
- **Status:** Reviewed and Verified.

## Tool Descriptions Checklist

| Tool Name | Token Estimate (approx) | Complexity Class | Status |
| :--- | :--- | :--- | :--- |
| `read_file` | 130 | Batch A (Simple) | Reviewed |
| `list_directory` | 105 | Batch A (Simple) | Reviewed |
| `file_metadata` | 95 | Batch A (Simple) | Reviewed |
| `list_agents` | 90 | Batch A (Simple) | Reviewed |
| `grep_files` | 115 | Batch B (Moderate) | Reviewed |
| `write_file` | 110 | Batch B (Moderate) | Reviewed |
| `delete_file` | 100 | Batch B (Moderate) | Reviewed |
| `sqlite_query` | 105 | Batch B (Moderate) | Reviewed |
| `todo_create` | 95 | Batch B (Moderate) | Reviewed |
| `todo_list` | 90 | Batch B (Moderate) | Reviewed |
| `todo_update` | 90 | Batch B (Moderate) | Reviewed |
| `todo_delete` | 85 | Batch B (Moderate) | Reviewed |
| `search_replace` | 175 | Batch C (Complex) | Reviewed |
| `shell_command` | 160 | Batch C (Complex) | Reviewed |
| `sqlite_exec` | 145 | Batch C (Complex) | Reviewed |
| `delegate_agent` | 155 | Batch C (Complex) | Reviewed |

## Prompt Design Rationale for Maintainers

### The Split
- **System Prompt (`system.md`):** Contains high-level behavioral constraints, identity, and operational methodologies (Coding, Planning, Conflict Resolution). It is designed to be the "brain" of the agent.
- **Tool Descriptions (`AnnotatedBuiltInToolCatalog.java`):** These are focused, instruction-like descriptions that define the "how" and "when" for each discrete capability. By embedding them in the Java source, we ensure that the LLM's tool-calling schema is always in sync with the actual implementation and security constraints (e.g., shell operator blocking).

### Safe Updates
- **Snapshot Integrity:** Always maintain the emphasis on `snapshotId` in file operations. This is the primary mechanism for state safety in a concurrent or multi-turn environment.
- **Token Budget:** The system prompt is tuned to ~1200 tokens to provide deep context without overwhelming the model's context window or increasing latency excessively. When adding new sections, consider if older sections can be compressed.
- **Deterministic Action:** Descriptions should remain concrete. Avoid marketing language; use imperatives and clearly state requirements, constraints, and failure modes.

### Verification Path
- The `ToolManager` uses LangChain4j's `ToolSpecifications` utility to reflectively extract these descriptions. Any change in the `@Tool` value array will automatically propagate to the agent's available toolset in the next session.
