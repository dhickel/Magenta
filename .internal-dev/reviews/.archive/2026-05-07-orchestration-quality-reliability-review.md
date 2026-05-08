# Scope

Code-quality, maintainability, and reliability review for the completed orchestration driver implementation. Reviewed package boundaries, service responsibilities, repository schema ownership, transaction and lease behavior, path confinement, controller/API shape, UI state handling, and test coverage.

# Findings

## Blocking: Context-bearing task/workflow APIs create a misleading contract

The task and workflow controllers expose orchestration context fields, and the frontend sends them, but the execution path discards those values. This splits the apparent public API from actual behavior and will be hard to debug from the UI because a run appears to accept agent/job/model/priority context while executing as a plain legacy run.

References:

- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java:119`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java:90`

## Blocking: Schedule processing lacks exactly-once protection

`ScheduleService.pollDueSchedules` performs multiple durable writes without a transaction or idempotency key for the due instant. A crash after advancing `nextRunAt` but before assignment creation can drop a scheduled run; concurrent pollers can enqueue duplicates. The current tests cover a happy path but not duplicate or crash-window behavior.

Reference: `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java:61`

## Nonblocking: Repository-owned schema is pragmatic but should be documented as the local migration convention

The orchestration repositories create their own tables with `ensureSchema()` while the legacy app also uses `schema.sql`. This is workable for the current small SQLite app, but future migrations need a clear convention so schema ownership does not become split across invisible startup side effects.

# Risk Assessment

Package boundaries are mostly clean: web controllers stay thin, orchestration services own use cases, and persistence assumptions are localized in repositories. Workspace path confinement is covered by service validation and tests. Queue priority, lease recovery, event reactions, and job checkpoint resume have focused tests.

The highest residual risks are lifecycle correctness around schedules and the mismatch between task/workflow API contracts and actual execution behavior. Browser UI coverage is useful, but current automated backend coverage does not assert the discarded context fields or scheduler duplicate behavior.

# Recommendations

Make context propagation explicit and tested end to end. At minimum, assert that task/workflow run requests either create durable assignments or persist/return the supplied orchestration context.

Make schedule firing idempotent and transactional. A durable `schedule_id + due_at` correlation, unique assignment source, or handled event key would give tests something concrete to assert.

Document the schema ownership rule in the orchestration package guide if repository-owned DDL remains the intended approach.

# Follow-ups

Add tests for task/workflow orchestration context propagation and schedule duplicate/crash-window behavior before marking final validation accepted.
