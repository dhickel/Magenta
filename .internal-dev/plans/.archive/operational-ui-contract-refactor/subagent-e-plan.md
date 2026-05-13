# Subagent E: Workspace Lease And Podman Runtime Hardening — Technical Plan

## Context

Two problems are assigned:
1. Workspace write leases are not concurrency-safe (check-then-insert gap).
2. Docker runtime is fail-fast by default with fragile timeout cleanup.

## Goal

Make workspace write lease acquisition atomic at the database level and make the Podman/Docker runtime fail gracefully with single-budget timeout cleanup.

## In Scope

- Database-enforced active-write exclusivity invariant for workspace leases.
- Atomic writable lease acquisition via unique partial index + insert-and-handle-conflict.
- Podman/Docker runtime configuration explicit in `application.yml` with sensible local defaults.
- Graceful runtime startup (no crash when daemon/image unavailable unless explicitly fail-fast).
- Single-budget command timeout cleanup (stop and remove stuck containers within one timeout window).
- Runtime status endpoint covering enabled-ready, disabled, and unavailable daemon states.

## Out of Scope

- Read lease behavior changes (read leases remain non-exclusive).
- Full production container hardening beyond local alpha Podman compatibility.
- Adding new runtime features or container orchestration machinery.
- Modifying files outside the assigned write scope.

---

## Part 1: Workspace Lease Concurrency Fix

### Problem Analysis

Current code in `WorkspaceLeaseService.acquireWritable()`:

```java
// Step 1: Check
repository.findActiveWritableLease(workspaceId).ifPresent(existing -> {
    if (existing.isActive()) {
        throw new IllegalStateException(...);
    }
});
// Step 2: Insert (unprotected)
WorkspaceLease lease = repository.saveLease(new WorkspaceLease(...));
```

Two concurrent callers can both pass Step 1 before either executes Step 2.

Current schema index:
```sql
create index if not exists idx_workspace_leases_active
    on workspace_leases(workspace_id, mode)
    where released_at is null;
```
This is a non-unique index — it speeds up lookups but does not enforce exclusivity.

### SQLite Invariant Needed

At most one row in `workspace_leases` must exist with:
- `mode = 'WRITE'` AND `released_at IS NULL` AND `workspace_id = <same workspace>`

This is enforced by a **unique partial index**:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_workspace_leases_active_write
    ON workspace_leases(workspace_id)
    WHERE mode = 'WRITE' AND released_at IS NULL;
```

SQLite 3.35+ supports `ON CONFLICT` target columns with partial index WHERE clauses.

### Atomic Insert Strategy

Replace the check-then-insert pattern with **insert-and-handle-conflict**:

1. Attempt an `INSERT INTO workspace_leases (...) VALUES (...) ON CONFLICT(workspace_id) WHERE mode = 'WRITE' AND released_at IS NULL DO NOTHING`.
2. Check `jdbcTemplate.update()` return value:
   - `1` = insert succeeded, lease acquired.
   - `0` = conflict existed, another active write lease is present.
3. On conflict (`0` returned), query for the existing lease and throw `IllegalStateException` with holder details (same error semantics as today).

A new repository method `insertWritableLease(WorkspaceLease)` encapsulates this. The existing `saveLease(WorkspaceLease)` retains `ON CONFLICT(id) DO UPDATE` for upsert semantics (used by extension, release).

### Expected Behavior Under Concurrent Acquisition

- **Two concurrent `acquireWritable` for same workspace, no existing lease**:
  SQLite serializes the inserts. The first succeeds (returns 1). The second hits the unique partial index constraint, `DO NOTHING` fires (returns 0). Service throws `IllegalStateException`. Exactly one active write lease exists.
- **Two concurrent `acquireWritable` for different workspaces**: Both succeed, no conflict.
- **Read leases are unaffected**: The partial index only covers `WHERE mode = 'WRITE' AND released_at IS NULL`. Multiple concurrent read inserts succeed as before.
- **Release then re-acquire**: `releaseLease()` sets `released_at = now`. The released row no longer matches the partial index WHERE clause. A subsequent `acquireWritable` inserts successfully.

### Implementation Steps

1. Add the unique partial index to workspace_leases in `WorkspaceRepository.ensureSchema()` and `schema.sql`.
2. Add `insertWritableLease(WorkspaceLease)` method to `WorkspaceRepository` using `INSERT ... ON CONFLICT ... DO NOTHING`.
3. Refactor `WorkspaceLeaseService.acquireWritable()` to use the new atomic insert and handle the conflict.
4. Retain the existing `findActiveWritableLease` for read-path queries (`hasWritableLease`, `releaseAllFor`).
5. The existing `saveLease` remains for non-writable inserts and upserts.

---

## Part 2: Podman/Docker Runtime Hardening

### Problem Analysis

Current issues:
1. `@ConditionalOnProperty(name = "magenta.docker.enabled", havingValue = "true", matchIfMissing = true)` — enabled by default when no config exists.
2. `verifyDaemon()` at `@PostConstruct` throws `IllegalStateException` if ping or image inspect fails — startup crash.
3. `execCommand()` calls `logCallback.awaitCompletion(timeout)` then `waitContainerCmd.awaitStatusCode(timeout)` — double timeout budget.
4. No `magenta.docker` settings in `application.yml` for discoverability.
5. Timeout cleanup calls `removeContainerCmd` with force but does not stop first, and does not account for the double-wait.

### Podman Default Host/Image/Config

`DockerRuntimeConfig.getDockerHost()` already has sensible Podman defaults:
- Checks `DOCKER_HOST` env var first.
- Falls back to `unix://<XDG_RUNTIME_DIR>/podman/podman.sock`.
- Falls back to `unix:///run/user/<uid>/podman/podman.sock`.
- Last resort: `unix:///var/run/docker.sock`.

Default image: `python:3.11-slim` (in the config class, but not in application.yml).
Default timeout: 600s.
SELinux relabel: `true` by default (no-op on non-SELinux hosts).

### Changes

1. **Disable by default**: Change `matchIfMissing = true` to `matchIfMissing = false`.
2. **Explicit config block in application.yml**:
   ```yaml
   magenta:
     docker:
       enabled: false
       # host: unix:///run/user/1000/podman/podman.sock
       agent-image: python:3.11-slim
       exec-timeout-seconds: 600
       selinux-relabel: true
   ```
3. **Graceful `verifyDaemon()`**: Catch exceptions, log warnings, set a `daemonAvailable` flag. Do not throw from `@PostConstruct`. The bean is constructed but reports its state.
4. **Add `isAvailable()` method** to `DockerRuntimeClient` that returns the daemon availability flag.
5. **Update `healthCheck()`** to use the flag and report a clear disabled message if unavailable.
6. **Update `RuntimeController.dockerStatus()`** to reflect three distinct states:
   - `enabled=false` → disabled
   - `enabled=true, daemon unavailable` → enabled but unavailable
   - `enabled=true, daemon available` → ready, with health detail
7. **Fix double-timeout in `execCommand()`**:
   - Remove `logCallback.awaitCompletion()` with full timeout.
   - Use `waitContainerCmd().awaitStatusCode()` as the single wait point.
   - After wait completes (or times out), close log streaming with a short 5s grace period.
   - If container is still running after the full timeout, explicitly `stopContainerCmd` then `removeContainerCmd`.
   - The total wall-clock budget is bounded to `execTimeoutSeconds + 15s` (grace for log drain + stop).

### Timeout Cleanup Semantics

```
1. Start container + log streaming
2. waitContainerCmd.awaitStatusCode(execTimeoutSeconds)  ← single budget
   a. Container exits normally → exit code returned, proceed to log drain
   b. Timeout exception → container still running
3. If timeout:
   a. stopContainerCmd(containerId).withTimeout(10).exec()  ← SIGTERM, 10s
   b. removeContainerCmd(containerId).withForce(true).exec() ← force remove
4. Close log callback with 5s grace period
5. Return partial ExecResult with TIMED_OUT state
```

Add `TIMED_OUT` to `InspectContainerState` enum.

### Implementation Steps

1. Add `magenta.docker.*` block to `application.yml` with `enabled: false`.
2. Change `@ConditionalOnProperty` to `matchIfMissing = false`.
3. Refactor `DockerRuntimeClient`:
   - Add `daemonAvailable` boolean flag.
   - `verifyDaemon()` sets flag instead of throwing.
   - Add `isAvailable()` accessor.
   - Refactor `execCommand()` for single-budget timeout with proper cleanup.
   - Add `TIMED_OUT` to `InspectContainerState`.
4. Update `RuntimeController.dockerStatus()` to use `isAvailable()`.
5. Update `DockerStatusResponse` or the controller logic to distinguish enabled/disabled/unavailable.

---

## Validation Plan

### Workspace Lease Tests

Create `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseServiceTest.java`:

1. `acquireWritable_singleSucceeds` — basic acquisition.
2. `acquireWritable_secondConflicts` — sequential conflict throws.
3. `acquireWritable_concurrentSameWorkspace` — two threads, same workspace, exactly one wins.
4. `acquireWritable_differentWorkspaces` — two threads, different workspaces, both win.
5. `releaseThenReacquire` — release enables re-acquisition.
6. `readLeasesCoexist` — multiple read leases on same workspace succeed.
7. `extendByWrongHolderFails` — extension rejected for non-holder.

### Docker/Podman Tests

Create `src/test/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClientTest.java`:

1. `statusEndpoint_Disabled` — when config is disabled, status shows disabled.
2. `statusEndpoint_Unavailable` — when enabled but daemon down, status shows unavailable.
3. `pingReturnsFalseWhenDaemonDown` — unit-level test of ping failure path.

Note: Full integration tests requiring a running Podman daemon are skipped when the daemon is unavailable.

### Timeout Test

In `DockerRuntimeClientTest`:
1. `execCommand_TimeoutStopsContainer` — execute `sleep 9999` with short timeout, verify container is stopped/removed and elapsed time is bounded to timeout + grace, not double.

---

## Risks And Assumptions

- SQLite's partial unique index with `ON CONFLICT ... WHERE` is supported in SQLite 3.35+. The `org.xerial:sqlite-jdbc` driver bundles a recent enough SQLite. If the bundled version is older, fall back to application-level synchronization with a `ReentrantLock` per workspace key.
- The `on conflict(workspace_id) where mode = 'WRITE' and released_at is null do nothing` syntax is valid SQLite. If the JDBC driver or Spring's `JdbcTemplate` has issues with the WHERE clause in the conflict target, fall back to catching `SQLiteConstraintException` from a plain `INSERT`.
- Read leases use the existing path (non-unique index, no conflict handling) — no change needed.
- Podman socket path detection in `DockerRuntimeConfig.getDockerHost()` is already correct for rootless Podman on Linux.
- The `python:3.11-slim` image is a reasonable "batteries included" default for local agent execution. Users can override via config.
- No other subagent (A, C, F) is modifying the files in my write scope. Confirmed disjoint: A owns runtime/AssignmentService+OrchestrationRunnerService+JobService, C owns workflow/WorkflowService+WorkflowValidator+WorkflowController, F is read-only audit.
