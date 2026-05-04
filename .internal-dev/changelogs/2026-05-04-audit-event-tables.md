# Date
2026-05-04

# Change Summary
Added a single immutable append-only `audit_event` table to capture the full sequential history of every conversation for debugging and automated analysis. Events are recorded at explicit, known points in the chat lifecycle — `user_msg`, `assistant_msg`, `tool_exec`, `compaction`, `context` — rather than reverse-engineered from `ai_chat_memory` state. No dedup, no diffing, no dependency on the working-memory table's churn. Compaction is recorded as its own event type with before/after token counts and the generated summary. Context snapshots are recorded at tool-loop checkpoints and end-of-turn maintenance.

# Files
- `src/main/resources/schema.sql` — replaced three separate audit tables with one wide `audit_event` table
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/AuditRepository.java` — explicit `recordUserMessage()`, `recordAssistantMessage()`, `recordToolExec()`, `recordCompaction()`, `recordContext()` methods; each auto-assigns sequence via `MAX(sequence)+1`; no fingerprinting or dedup
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatMemoryRepository.java` — reverted to original; no audit dependency
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java` — records `compaction` events in `compact()` and `trimToBudget()` only; context snapshot recording moved to ChatService
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java` — records `user_msg` at turn start, `tool_exec` for each tool invocation, `context` at tool-loop checkpoints and end-of-turn maintenance, `assistant_msg` for final response; added `ObjectMapper` for metadata serialization
- `src/main/java/io/mindspice/magenta2/ai/chat/config/ChatBeanConfig.java` — wires `AuditRepository` into `ContextManagementAdvisor`

# Event type reference
| event_type | Recorded when | Key columns populated |
|---|---|---|
| `user_msg` | User message received | `message_text`, `model` |
| `assistant_msg` | Model returns final response | `message_text`, `message_metadata_json` (thinking), `model` |
| `tool_exec` | Tool invocation completes | `tool_call_id`, `tool_name`, `arguments_json`, `arguments_summary`, `result_text`, `result_summary`, `tool_status`, `result_truncated`, `result_large`, `model` |
| `compaction` | Context compacted or trimmed | `compaction_method` (summarize/trim), `compaction_summary`, `used_tokens`, `max_tokens`, `trigger_tokens`, `stored_message_count` |
| `context` | Tool-loop checkpoint or end-of-turn maintenance | `used_tokens`, `max_tokens`, `trigger_tokens`, `percent_used`, `stored_message_count` |

# Behavioral Impact
- No changes to message persistence, compaction, tool execution, or context management logic.
- Audit recording is fire-and-forget: each `AuditRepository` method catches exceptions internally and logs at DEBUG. No audit failure can affect chat.
- `AuditRepository` is `@Autowired(required = false)` at all injection points. If the bean is absent, nothing breaks.
- Sequence is strictly monotonic within a conversation — each insert queries `MAX(sequence)+1` atomically.

# Risks
- The `audit_event` table grows unboundedly. No retention policy yet.
- Streaming chat paths (`plainStream`, streaming tool chat) do not record audit events. Only the synchronous `toolChat` and `plainChat` paths are instrumented.

# Follow-up Items
- Add retention/cleanup policy (e.g., auto-delete conversations older than N days).
- Add an API endpoint or UI view to browse audit history per conversation.
- Instrument streaming chat paths.
