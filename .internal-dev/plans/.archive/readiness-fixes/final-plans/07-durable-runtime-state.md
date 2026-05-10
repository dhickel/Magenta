# 07 -- Durable Runtime State for Orchestration

## Context

The Magenta2 orchestration runtime manages `WorkAssignment` lifecycle through a lease-based polling runner (`OrchestrationRunnerService`). Each assignment carries a status, position checkpoint, accumulated output/evidence maps, and lease metadata -- all persisted to the `work_assignments` SQLite table.

The operational design is strong: lease acquisition uses atomic UPDATE-with-conditions, checkpoint state is embedded in the same row as runtime status, and stale leases are detected by timestamp comparison. However, the **recovery path** after a runner crash or process restart has three material gaps:

### What Is Broken

**Gap 1: INTERRUPTED assignments are invisible to the polling loop.**
`pollQueuedWork` calls `recoverStaleLeases()` which marks RUNNING+expired → INTERRUPTED (correct). But then `findQueuedAssignments` only queries `where status = 'QUEUED'`. INTERRUPTED assignments are NEVER returned. Meanwhile, `acquireLease` DOES accept INTERRUPTED -- but the finder never presents INTERRUPTED work. An 80%-complete job crashes, gets marked INTERRUPTED with a valid checkpoint at item N, and sits forever unless manually resumed.

**Gap 2: WAITING assignments never auto-resume.**
When a JOB_RUN hits a `WAIT_FOR_MESSAGE` item, the assignment is saved as WAITING. When a message later arrives in the agent's inbox, `InboxService.send` publishes an event, but nothing links the inbox message to the WAITING assignment's `waitingItemId`. The original job stays WAITING indefinitely.

**Gap 3: Task/workflow runs can be silently duplicated on retry.**
`runTask` and `runWorkflow` always create new runs. On recovery (INTERRUPTED → re-acquired → re-executed), a SECOND run is created even if the first had already completed. The checkpoint's `taskRunId` is written but never checked as a deduplication guard.

## Goal

Make orchestration recovery restart-safe and idempotent so interrupted or waiting assignments progress without manual operator intervention.

## In Scope

- Recoverable assignment selection for `QUEUED` + `INTERRUPTED` states.
- WAITING resume trigger from inbox events.
- Task/workflow run deduplication and completion-event idempotency.
- Startup recovery behavior and restart simulation tests.

## Out of Scope

- Replacing the lease model with a new scheduler architecture.
- Introducing a separate recovery service or event store.
- Adding new orchestration features unrelated to recovery correctness.

## Current Architecture

### Core Entity: WorkAssignment

```java
public record WorkAssignment(
    String id, String agentId, String jobId, String jobItemId,
    AssignmentType assignmentType, int priority, OrchestrationStatus status,
    String modelOverride, String workspaceId, int currentItemIndex,
    Map<String, Object> checkpoint, Map<String, Object> input,
    Map<String, Object> output, Map<String, Object> evidence,
    String errorText, String leaseOwner, Instant leaseExpiresAt,
    Instant createdAt, updatedAt, startedAt, completedAt
) {}
```

### Status State Machine

```
QUEUED → RUNNING → COMPLETED/FAILED/WAITING
RUNNING+expired → INTERRUPTED (via markStaleRunningLeases)
INTERRUPTED/WAITING/PAUSED → QUEUED (via resume())
```

### In-Memory vs Persisted

| Component | Nature | Restart Impact |
|---|---|---|
| `leaseOwner` UUID | In-memory | New runner gets new UUID; stale leases detected. Safe. |
| `heartbeatExecutor` | In-memory | All heartbeats stop; leases expire. Safe. |
| `MagentaWorkExecutor.lanes` | In-memory | In-flight work lost; assignments marked INTERRUPTED. Gap 1. |
| WorkAssignment.row | Persisted | Survives restart. Checkpoints preserved. |

### Restart Gap Analysis

**Gap 1 flow:**
```
pollQueuedWork()
  → recoverStaleLeases() → markStaleRunningLeases(now) // RUNNING+expired → INTERRUPTED
  → findQueuedAssignments(4) // WHERE status='QUEUED' only! INTERRUPTED not found
  → acquireLease(QUEUED) // INTERRUPTED never re-acquired
```

**Gap 2 flow:**
```
1. Job running, hits WAIT_FOR_MESSAGE
2. Assignment saved: status=WAITING, checkpoint={waitingItemId: "item-3"}
3. Human sends inbox message → INBOX_MESSAGE_RECEIVED event
4. EventService.handle → creates NEW assignment from template
5. Original WAITING assignment: NEVER RESUMED
```

**Gap 3 scenarios:**
- Scenario A: Checkpoint saved, crash at next item → resumes correctly ✓
- Scenario B: TaskRun completes but checkpoint save fails, crash → re-executes item, DUPLICATE RUN
- Scenario C: TaskRun still RUNNING when crash occurs → re-executes, DUPLICATE + ORPHAN

### Add Startup Recovery Hook

```java
@EventListener(ApplicationReadyEvent.class)
public void onApplicationReady() {
    int interrupted = repository.markStaleRunningLeases(Instant.now());
    // Auto-resume INTERRUPTED assignments
    // Log: "Startup recovery: interrupted={}, auto-resumed={}"
}
```

---

## Target Architecture

### Design Principles

1. **Favor small, explicit persistence transitions** over complex recovery orchestrators.
2. **Lease/checkpoint transitions must be idempotent.**
3. **The assignment row IS the recovery record** -- no separate recovery log table.
4. **Recovery is a natural extension of the polling cycle**, not a parallel subsystem.

### Changes

**Change A: Expand findQueuedAssignments to include INTERRUPTED**
New method `findRecoverableAssignments(int limit)` queries `where status in ('QUEUED', 'INTERRUPTED')`.

**Change B: Auto-resume WAITING assignments on matching inbox message**
When `InboxService.send` publishes `INBOX_MESSAGE_RECEIVED`, event handler scans for WAITING assignments for the recipient agent and auto-resumes them.

**Change C: Add task/workflow run deduplication via checkpoint**
Before creating a new task/workflow run, check if checkpoint contains a `runId` for a completed run. If so, reuse.

**Change D: Startup recovery hook**
`@PostConstruct` / `@EventListener(ApplicationReadyEvent.class)` method that marks stale leases, leaves recoverable work eligible for the polling loop, and logs recovery counts. It should not synchronously execute recovered assignments during startup.

### Idempotent Transition Design

All existing transitions already idempotent:
- `acquireLease`: atomic UPDATE with WHERE conditions including status filter
- `markStaleRunningLeases`: UPDATE with `where status = 'RUNNING' and lease_expires_at <= ?`
- `saveAssignment`: UPSERT (INSERT ... ON CONFLICT(id) DO UPDATE)
- `revertToQueued`: UPDATE with `where status = 'RUNNING' and lease_owner = ?`

Job completion event currently NOT idempotent. Fix: add `completionEventPublished` flag in checkpoint.

---

## Implementation Steps

### Step 1: Add `findRecoverableAssignments` query
**File:** `OrchestrationRuntimeRepository.java`
```java
public List<WorkAssignment> findRecoverableAssignments(int limit) {
    // where status in ('QUEUED', 'INTERRUPTED')
    // order by priority desc, created_at asc
}
```

### Step 2: Update polling loop
**File:** `OrchestrationRunnerService.java`
Replace `findQueuedAssignments(4)` with `findRecoverableAssignments(4)` in `pollQueuedWork` and `runNextSynchronously`.

### Step 3: Add `findWaitingAssignmentsForAgent` query
**File:** `OrchestrationRuntimeRepository.java`
```java
public List<WorkAssignment> findWaitingAssignmentsForAgent(String agentId) {
    // where agent_id = ? and status = 'WAITING'
}
```

### Step 4: Auto-resume WAITING on inbox message
**File:** `OrchestrationEventService.java`
In `handle()`: after processing reactions, if event type is `INBOX_MESSAGE_RECEIVED`, find all WAITING assignments for the recipient agent and call `assignmentService.resume()` on each.

### Step 5: Add run deduplication
**File:** `OrchestrationRunnerService.java`
In `runTask()` and `runWorkflow()`: before creating new run, check checkpoint for `taskRunId`/`workflowRunId`. If present and run is COMPLETED, reuse. Otherwise re-execute.

### Step 6: Add startup recovery hook
**File:** `OrchestrationRunnerService.java`
`@EventListener(ApplicationReadyEvent.class)` → markStaleRunningLeases → findRecoverableAssignments → auto-resume INTERRUPTED → log counts.

### Step 7: Make job completion event idempotent
**File:** `OrchestrationRunnerService.java`
Before publishing `JOB_STATUS_CHANGED`, check checkpoint's `completionEventPublished` flag.

### Step 8: Add batch `markInterruptedAsQueued` query
**File:** `OrchestrationRuntimeRepository.java`
```java
public int markInterruptedAsQueued(String agentId) {
    // update work_assignments set status = 'QUEUED' where status = 'INTERRUPTED' and agent_id = ?
}
```

---

## Validation

### Restart Simulation Test Design

### Test file: `OrchestrationRestartResilienceTest.java`

| Test | What it validates |
|------|-------------------|
| Clean startup, no in-progress work | Zero recovery noise |
| Mid-run kill + restart, checkpoint at item N | Resumes from checkpoint correctly |
| Double-restart (crash during recovery) | Idempotent recovery, no ghost leases |
| WAITING auto-resume on inbox message | Message unblocks waiting assignment |
| Task run deduplication (completed run) | No re-execution, output reused |
| Task run re-execution (failed run) | New run created for non-terminal state |
| Polling loop picks up INTERRUPTED | `findRecoverableAssignments` works without explicit resume |
| Job completion event idempotency | Event fires at most once per completion |

### Milestone Gate Validation Contract

Relevant alpha-gate snippets to carry into validation:
- `alpha-milestone-gate-summary.md`: "Durable State: Orchestration state is primarily in-memory; needs a persistent store for resilience across restarts."
- `architectural-alignment-report.md`: "`ActiveTurnRegistry` and `SseStreamLifecycle` manage transient state well. However ... durable state management solution for `WorkAssignment` status ... will be required to handle service restarts."
- `architectural-alignment-report.md`: "Durable Orchestration State: Implement a persistent store for `WorkAssignment` and `InboxMessage` to ensure resilience across restarts."
- `security-and-performance-report.md`: "`OrchestrationRunnerService` uses a lease-based polling system with heartbeats, ensuring reliable execution and recovery of long-running tasks."

The implementing agent must launch a validation sub-agent after completing this plan. The sub-agent must receive this plan file, the alpha-gate snippets above, the final `git diff`, restart simulation test output, and any manual restart proof.

Validation sub-agent prompt:
```text
You are validating the Durable Runtime State remediation in Magenta2. Read `.internal-dev/plans/readiness-fixes/final-plans/07-durable-runtime-state.md`, then manually inspect `OrchestrationRunnerService`, `OrchestrationRuntimeRepository`, `OrchestrationEventService`, `AssignmentService`, schema impacts, and tests. Do not trust the implementer's recovery summary without checking state transitions and idempotency.

Validation contract:
- Confirm stale RUNNING assignments become recoverable and the polling loop actually selects INTERRUPTED assignments.
- Confirm startup recovery does not synchronously run assignments before normal polling/lease ownership rules apply.
- Confirm WAITING assignments resume on relevant inbox messages without resuming unrelated agents' work.
- Confirm task/workflow run deduplication prevents duplicate completed runs while still retrying failed/non-terminal runs.
- Confirm job completion events are idempotent and published at most once.
- Confirm restart simulations prove checkpoint resume after process restart.

Return findings first, ordered by severity, with file/line references and any recovery race or duplicate-execution risk.
```

Manual work proof to verify:
- Inspect SQL predicates for status filters, lease owner checks, and idempotent update conditions.
- Verify restart tests simulate stale leases, WAITING resume, duplicate-run prevention, and double-restart recovery.
- Verify focused orchestration tests, `mvn test`, startup smoke, and manual restart proof where feasible.

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Too many assignments returned on startup | Low | Medium | Respect existing LIMIT; startup hook only marks stale work recoverable and lets polling execute under lease rules |
| WAITING auto-resume triggers for wrong message | Low | High | Intentional: any message is a signal to check waiting work; add filtering later if needed |
| Run dedup lookup overhead | Low | Low | Only fires for INTERRUPTED assignments with checkpoint runId |
| Race between multiple runners | Low | Medium | Existing `acquireLease` is atomic; serializes correctly |

Rollback: Each change is independent. Revert `findRecoverableAssignments` → back to `findQueuedAssignments`. Revert WAITING auto-resume → remove call. Revert dedup → remove checkpoint check. Revert startup hook → remove `@EventListener`.

---

## Exit Criteria

1. `findRecoverableAssignments` returns both QUEUED and INTERRUPTED, consumed by polling loop
2. INTERRUPTED assignments auto-resume and continue from checkpoint without manual intervention
3. WAITING assignments auto-resume when inbox message arrives for the owning agent
4. Task/workflow run deduplication: COMPLETED runs reused, non-terminal runs trigger re-execution
5. `JOB_STATUS_CHANGED` events published at most once per completion
6. Startup recovery enumerates and logs in-progress work
7. All 8 restart simulation tests pass
8. Existing test suite passes: `mvn test`
9. Manual validation: create running job, SIGKILL, restart, verify job completes without API intervention

## Critical Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java` — Core runner: polling fix, startup hook, run dedup, event idempotency
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java` — New queries
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationEventService.java` — WAITING auto-resume
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java` — Existing resume() used by auto-recovery
