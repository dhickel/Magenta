# Deferred and Non-Issue Findings

This archived document records issues from the robustness and threading reviews that were intentionally deferred or classified as non-issues. The original "mock orchestration execution" item was resolved by the model-backed task execution pass and is no longer deferred.

---

## Issue C: Distributed Lease Race Conditions

**Source:** `robustness_review.md` Finding 3

**Summary:** The `acquireLease` mechanism uses timestamp comparison (`lease_expires_at <= now`) which is vulnerable to clock drift in multi-node deployments.

**Disposition: Deferred — irrelevant for current deployment model**

**Justification:**

1. The system uses SQLite as its database, which is inherently single-writer. Multi-node deployment with SQLite is not supported and not planned.

2. The lease mechanism is correctly designed for single-instance concurrency: the `UPDATE ... WHERE status IN ('QUEUED','INTERRUPTED') AND (lease_expires_at IS NULL OR lease_expires_at <= ?)` pattern is atomic within SQLite's serialized write model.

3. The review itself says "Minor (low risk for single-instance)."

**When to revisit:** If/when the system moves to a multi-node deployment with a shared database (PostgreSQL, MySQL). At that point, a fencing token approach (monotonically increasing version number) should replace timestamp comparison.

---

## Issue F: ThreadLocal Context Propagation Risks

**Source:** `threading_state_review.md` Finding 2

**Summary:** `PlanToolExecutionContext` uses a bare `ThreadLocal` to propagate conversation metadata to tools. If tool execution moves to a background thread via `MagentaWorkExecutor`, the context is lost.

**Disposition: Deferred — currently safe, no cross-thread tool execution exists**

**Justification:**

1. All tool execution is synchronous within `ChatService.toolChat()`. The call chain is:
   - `toolChat()` sets `PlanToolExecutionContext` (line 877)
   - Spring AI's `ToolCallingManager` executes tools synchronously on the same thread
   - Tools read `PlanToolExecutionContext.current()` on the same thread
   - `toolChat()` clears the context in `finally` (line 1121)

2. No tool spawns background work. No tool submits to `MagentaWorkExecutor`. The `PlanToolContext` is scoped to a single synchronous turn.

3. The review's concern is valid as a future risk, not a current bug. If model-backed orchestration task execution is added (Issue B), context propagation will need to be addressed as part of that design.

4. Adding a `ContextPropagatingExecutorService` now would be speculative infrastructure with no current consumer.

**When to revisit:** When any tool starts using `MagentaWorkExecutor` for background work. The fix would be:
- A wrapper around `MagentaWorkExecutor.submit*()` that captures the current `PlanToolContext` and sets it on the worker thread
- Or explicit `runId`/`conversationId` passing to background methods instead of relying on ThreadLocal

---

## Issue G: Orchestration Lease Heartbeat

**Source:** `threading_state_review.md` Finding 3

**Summary:** Long-running AI tasks might exceed the 5-minute `LEASE_DURATION`, causing stale lease recovery and potential duplicate execution.

**Disposition: Deferred — future hardening**

**Justification:**

1. Model-backed task execution can now run inside orchestration assignments, but lease heartbeat behavior was left out of this fix because the task addressed placeholder completion and retry semantics.

2. For job runs, the lease is refreshed on each checkpoint after each job item completes, via `checkpointed()`. A multi-item job that takes more than 5 minutes total is safe when each single item completes inside the lease window.

3. The concern materializes when one model-backed task or workflow item takes longer than the lease duration.

4. Making `LEASE_DURATION` configurable via `application.properties` is a trivial change that could be done now, but without a use case it's speculative configuration.

**When to revisit:** When long-running model-backed orchestration items become common. The fix options are:
- Heartbeat: periodically update `lease_expires_at` during long-running item execution
- Configurable lease: make `LEASE_DURATION` a property (`${magenta.orchestration.lease-duration:5m}`)
- Both: heartbeat for active extension, configurable for the base duration

---

## Summary

| Issue | Review Source | Disposition | Reason |
|-------|-------------|-------------|--------|
| C: Distributed lease clock drift | robustness #3 | Deferred | Single-instance SQLite |
| F: ThreadLocal context propagation | threading #2 | Deferred | No cross-thread tool execution exists |
| G: Lease heartbeat for long tasks | threading #3 | Deferred | Future long-running item hardening |

The remaining deferred issues should be addressed only when the deployment or workload profile requires them.
