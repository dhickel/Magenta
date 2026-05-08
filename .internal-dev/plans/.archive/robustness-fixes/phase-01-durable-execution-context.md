# Phase 01: Durable Execution Context

## Context

The reviews identified that `TaskService.executionRunsByConversationId` (a `ConcurrentHashMap<String, String>` mapping `conversationId → runId`) is in-memory and lost on restart. Deeper investigation revealed two distinct problems:

1. **Durability gap**: The map is volatile. If the application restarts during an EXECUTE_TASK session, the AI loses its connection to the active `TaskRun` and tools like `task_report`/`task_complete` fail.
2. **Wiring gap**: `registerExecutionContext()` is never called anywhere. The entire EXECUTE_TASK chat mode is structurally complete (mode detection, runtime instructions, tool guards) but unreachable because no code path populates the map.

The resolution chain is:
- `ChatService.toolChat()` line 883 calls `taskService.runIdForConversation(conversationId)` to set the `PlanToolContext.runId`
- `runIdForConversation()` reads from the in-memory map
- Tools call `PlanToolExecutionContext.current().runId()` to get the active run

## Goal

Persist the `conversation_id → active_task_run_id` mapping so it survives restarts, and wire up the EXECUTE_TASK entry point so the mode is functionally reachable.

## In Scope

- Add `active_task_run_id` column to `ai_chat_session_metadata`
- Add read/write methods to `ChatSessionMetadataRepository`
- Update `TaskService` to use the persisted mapping instead of (or in addition to) the in-memory map
- Wire `registerExecutionContext()` into the task execution flow (likely in `TaskController` or a new `TaskService` method that starts a run for chat execution)
- Ensure `clearExecutionContext()` persists the removal

## Out of Scope

- Model-backed chat execution of tasks (that's Issue B, deferred)
- ThreadLocal propagation to background threads (that's Issue F, deferred)
- Any changes to how tools resolve context — `PlanToolExecutionContext` stays as-is

## Design Decisions

### Where to store the mapping

**Chosen: `ai_chat_session_metadata` table** with a new `active_task_run_id` column.

Rationale:
- This table already stores per-conversation state (`model`, `planning_model`, `title`)
- It's the natural place for scoped conversation metadata
- No new table needed — keeps schema surface small
- Follows the existing pattern: `ai_chat_session_metadata` already tracks which planning model is active per conversation

Rejected alternative: A dedicated `conversation_task_runs` table. This would be more normalized but is overkill for a 1:1 mapping (one conversation has at most one active run). The session metadata table already serves as a per-conversation key-value store.

### How to wire the entry point

Currently `runSynchronously()` is the only way to start a run. For chat-based execution we need a method that:
1. Creates the run via `startRun()`
2. Persists the `conversationId → runId` mapping
3. Leaves the run in RUNNING state for the AI to execute via tools

**New method: `TaskService.startChatExecution(conversationId, taskId, inputValues)`**
- Calls `startRun(taskId, inputValues)` — creates RUNNING TaskRun in DB
- Calls `saveActiveTaskRun(conversationId, runId)` — persists the mapping
- Returns the run (the AI will receive execution instructions on the next turn)

**Call site:** `TaskController` — new endpoint or modify an existing stream endpoint, OR called from `ChatService` when the user sends a message in a conversation that has an approved draft ready to execute. The exact UX is a product decision; the plan just ensures the backend method exists and is wired.

### In-memory map: keep or remove?

**Keep as cache, persist as source of truth.** On restart, `runIdForConversation()` falls back to the DB if the map is empty. Writes go to both. This avoids needing to change the hot path while adding durability.

## Implementation Steps

### Step 1: Add `active_task_run_id` to `ai_chat_session_metadata`

**File:** `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatSessionMetadataRepository.java`

In `ensureSchema()`, add a migration block:
```java
if (!columns.contains("active_task_run_id")) {
    jdbcTemplate.execute("alter table ai_chat_session_metadata add column active_task_run_id text");
}
```

Also update the `create table if not exists` statement to include the column for fresh installs:
```sql
active_task_run_id text
```

### Step 2: Add repository methods

**File:** `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatSessionMetadataRepository.java`

```java
public void saveActiveTaskRun(String conversationId, String runId) {
    // upsert: sets active_task_run_id, clears it if runId is null
    jdbcTemplate.update(
        "insert into ai_chat_session_metadata (conversation_id, active_task_run_id) values (?, ?) "
            + "on conflict(conversation_id) do update set active_task_run_id = excluded.active_task_run_id",
        conversationId, runId
    );
}

public String findActiveTaskRun(String conversationId) {
    // returns null if no row or null column
    return jdbcTemplate.query(
        "select active_task_run_id from ai_chat_session_metadata where conversation_id = ?",
        rs -> rs.next() ? rs.getString("active_task_run_id") : null,
        conversationId
    );
}
```

### Step 3: Update TaskService to use persisted mapping

**File:** `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskService.java`

Inject `ChatSessionMetadataRepository` (or use the existing one if already available — check constructor).

Modify `registerExecutionContext()` (lines 278-287):
- After putting into the in-memory map, also call `chatSessionMetadataRepository.saveActiveTaskRun(conversationId, runId)`

Modify `clearExecutionContext()` (lines 289-293):
- After removing from the in-memory map, also call `chatSessionMetadataRepository.saveActiveTaskRun(conversationId, null)`

Modify `runIdForConversation()` (lines 295-297):
- Check in-memory map first (fast path)
- If absent, fall back to `chatSessionMetadataRepository.findActiveTaskRun(conversationId)`
- If found in DB, populate the in-memory map for subsequent fast access

### Step 4: Add `startChatExecution()` entry point

**File:** `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskService.java`

New method:
```java
public TaskRun startChatExecution(String conversationId, String taskId, Map<String, Object> inputValues) {
    TaskRun run = startRun(taskId, inputValues);
    registerExecutionContext(conversationId, run.id());
    return run;
}
```

### Step 5: Wire the entry point

**File:** `src/main/java/io/mindspice/magenta2/api/web/TaskController.java` (or `ChatService`)

Expose `startChatExecution` through the controller. The existing `POST /api/tasks/{taskId}/runs/stream` endpoint at `TaskController` lines dealing with SSE streaming is a candidate — currently it either delegates to orchestration or does synchronous completion. Add a path for chat-based execution that calls `startChatExecution` instead.

Alternatively, add a dedicated endpoint: `POST /api/tasks/{taskId}/runs/chat?conversationId=...`

Exact endpoint design deferred to the implementer based on how the frontend triggers task execution.

### Step 6: Update `schema.sql`

**File:** `src/main/resources/schema.sql`

Add `active_task_run_id text` to the `ai_chat_session_metadata` table definition for clean installs.

## Validation

1. **Unit test**: `TaskServiceTest` — verify `registerExecutionContext` persists to DB, `runIdForConversation` returns from DB after simulated restart (clear in-memory map, re-query)
2. **Integration test**: Start a chat execution, restart the application (or clear the in-memory map), verify the next `mode()` call returns `EXECUTE_TASK` and tools can resolve the runId
3. **Schema migration**: Verify existing databases get the new column without error (test against a DB created before the change)
4. **Smoke test**: Spring context starts successfully, schema initialization completes

## Exit Criteria

- [ ] `active_task_run_id` column exists in `ai_chat_session_metadata`
- [ ] `registerExecutionContext` persists the mapping to DB
- [ ] `runIdForConversation` falls back to DB when in-memory map is empty
- [ ] `clearExecutionContext` clears the persisted mapping
- [ ] A chat-based task execution entry point exists (`startChatExecution`)
- [ ] Existing tests pass
- [ ] Spring context starts cleanly
