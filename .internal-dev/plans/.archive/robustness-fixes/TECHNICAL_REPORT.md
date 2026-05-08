# Task Execution & Robustness — Technical Report

**Date:** 2026-05-07
**For:** Implementation agent

---

## 1. Task execution is broken (Critical)

### What's wrong

No code path invokes an AI model to execute a task. Every path produces placeholder outputs via `TaskService.defaultOutputs()`:

| Output type | Placeholder value |
|-------------|-------------------|
| NUMBER | 0 |
| BOOLEAN | true |
| JSON | `{result: <name>, inputs: <inputs>}` |
| STRING | example text or `"Generated ..."` |

### Root cause

`TaskService.registerExecutionContext(conversationId, runId)` at `TaskService.java:278` is **never called** in production code. It's only referenced from `TaskServiceTest.java:36`.

This method is the gate to `PlanMode.EXECUTE_TASK` — the chat mode where an AI model receives execution instructions, uses `task_report` to record evidence, and calls `task_complete` with real outputs. Without it, `TaskService.mode()` never returns `EXECUTE_TASK`.

The entire EXECUTE_TASK chat infrastructure exists and is wired in `ChatService.toolChat()` (lines 880, 992, 1279, 1306, 1530, 1617), but the mode is unreachable because the in-memory map `executionRunsByConversationId` is always empty.

Compare with plan execution which works correctly: `PlanService.markExecuting()` persists `EXECUTE_PLAN` mode to the `ai_chat_plans` table. The task equivalent (`registerExecutionContext`) is in-memory only and was never wired.

### Every broken call site

| File | Line | Method | What it does |
|------|------|--------|--------------|
| `TaskService.java` | 345 | `runSynchronously()` | startRun + defaultOutputs + completeRun. No model. |
| `TaskController.java` | 149-157 | SSE stream endpoint | startRun + completeRun with defaults. No model. |
| `OrchestrationRunnerService.java` | 99 | `runTask()` | Calls `runSynchronously()`. |
| `OrchestrationRunnerService.java` | 162 | `runJobItem()` TASK_RUN branch | Calls `runSynchronously()`. |
| `WorkflowService.java` | 117 | `runSynchronously()` step loop | Calls `taskService.runSynchronously()` per step. |
| `OrchestrationRunService.java` | 25 | `runTask()` | Creates assignment, runner calls `runSynchronously()`. |

### What the fix looks like

Two changes, one file (`TaskService.java`) + one repository (`ChatSessionMetadataRepository.java`):

**A) Persist the conversation→run mapping.** Add `active_task_run_id` column to `ai_chat_session_metadata`. Add `saveActiveTaskRun` / `findActiveTaskRun` methods to `ChatSessionMetadataRepository`. Update `registerExecutionContext`, `clearExecutionContext`, and `runIdForConversation` to read/write the DB (keep the in-memory map as a cache, fall back to DB).

**B) Wire the entry point.** Add a `startChatExecution(conversationId, taskId, inputValues)` method to `TaskService`:
```java
public TaskRun startChatExecution(String conversationId, String taskId, Map<String, Object> inputValues) {
    TaskRun run = startRun(taskId, inputValues);
    registerExecutionContext(conversationId, run.id());
    return run;
}
```
Expose it via `TaskController` so the frontend (or an orchestration job) can start a chat-based task run.

After these two changes, the next chat turn in that conversation will enter EXECUTE_TASK mode, the model will receive `executionInstructions()` describing the task steps, and `task_report`/`task_complete` tools will work with real model-generated outputs.

**C) Orchestration path.** `OrchestrationRunnerService.runJobItem()` and `runTask()` currently call `runSynchronously()` (stub). For orchestrated task execution with real model outputs, the runner needs to call `startChatExecution` and then run a headless chat turn. This is a larger feature — see deferred items at the bottom.

---

## 2. Job item failure handling (Major)

### What's wrong

`OrchestrationRunnerService.runJob()` at line 144 calls `runJobItem()` with no retry and no continue-on-failure. Any exception in any item fails the entire job assignment. The `OrchestrationJobItem` record has no `retryCount` or `continueOnFailure` fields.

### Fix

Add two fields to `OrchestrationJobItem`:
- `int retryCount` (default 0, max 10) — additional attempts after first failure
- `boolean continueOnFailure` (default false) — if true, record error in evidence and skip to next item

Add matching columns (`retry_count integer not null default 0`, `continue_on_failure integer not null default 0`) to `orchestration_job_items` table via the `pragma_table_info` migration pattern used in `OrchestrationRuntimeRepository.ensureSchema()`.

Add retry loop in `runJob()` wrapping the `runJobItem()` call, with 500ms fixed delay between retries. On continue-on-failure, record `{status: FAILED, error, retriesExhausted: true}` in the outputs map and proceed to the next item.

Files: `OrchestrationJobItem.java`, `OrchestrationRuntimeRepository.java` (schema, save, load, row mapper), `OrchestrationRunnerService.java` (runJob loop).

---

## 3. Event handling not transactional (Major)

### What's wrong

`OrchestrationEventService.handle()` at line 28 iterates reactions and calls `assignmentService.create()` for each. No `@Transactional`. If processing 3 reactions and the 2nd throws, the 1st already committed and the event's `handledAt` is never set. Orphaned side effects with no recovery.

### Fix

Add `@Transactional` to `OrchestrationEventService.publish()` (line 19). This covers both the initial `saveEvent` and the `handle()` loop + final `saveEvent` with `handledAt`. Single annotation. Spring's `DataSourceTransactionManager` already works with `JdbcTemplate` — `OrchestrationRuntimeRepository.acquireLease()` already uses `@Transactional` proving the infrastructure is in place.

File: `OrchestrationEventService.java`.

---

## 4. Deferred items (not bugs, future work)

| Issue | Reason |
|-------|--------|
| `runSynchronously` returns placeholder data in orchestration | This is a stub for testing orchestration plumbing. Model-backed orchestration execution needs `startChatExecution` + headless chat turn integration — a feature, not a fix. Fix #1 (durable execution context) is the prerequisite. |
| Distributed lease clock drift | Single-instance SQLite. Irrelevant. |
| ThreadLocal context propagation risk | No tool spawns background work. `PlanToolContext` is set and cleared within the same synchronous call stack. Safe currently. |
| Lease heartbeat for long tasks | Current tasks take milliseconds. Only relevant when model-backed orchestration execution exists. |

---

## Implementation order

1. **Fix #3** (transactional events) — one annotation, lowest risk
2. **Fix #1** (durable execution context) — unblocks real task execution
3. **Fix #2** (job item resilience) — schema + retry logic

Fixes #1 and #2 are independent. #3 is trivial and should go first.
