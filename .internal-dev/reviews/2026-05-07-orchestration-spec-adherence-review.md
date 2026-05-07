# Scope

Final validation review for the orchestration driver against `.internal-dev/plans/orchestration-driver/final-validation-criteria.md` and archived phase plan `.internal-dev/plans/.archive/orchestration-driver/phase-02-durable-queue-scheduler-events-resume.md`.

Reviewed orchestration repositories, services, controllers, runtime settings integration, task/workflow run API changes, frontend routes, and validation results from `mvn test`, startup smoke, and live browser fallback validation.

# Findings

## Blocking: Task and workflow orchestration context is accepted by API records but ignored

`TaskController.TaskRunRequest` includes `agentId`, `jobId`, `workspaceId`, `modelOverride`, and `priority`, but `streamRun` only reads `inputValues` and calls `taskService.startRun(taskId, inputs)` without creating or linking a work assignment, resolving model precedence, binding workspace context, or preserving the orchestration metadata. See `src/main/java/io/mindspice/magenta2/api/web/TaskController.java:119`.

`WorkflowController.WorkflowRunRequest` has the same context fields, but `streamRun` ignores the body and calls `workflowService.runSynchronously(workflowId)` directly. See `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java:90`.

This misses the final criteria requiring task/workflow execution to accept agent/job/model/priority context and the phase plan requirement to extend task/workflow services with run request context.

## Blocking: Scheduled assignment creation is not idempotent per due time

`ScheduleService.pollDueSchedules` advances `nextRunAt`, publishes `SCHEDULE_DUE`, and creates an assignment with no durable due-time key or uniqueness guard. See `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java:61`.

This does not satisfy the phase plan requirement that scheduler enqueue assignment templates idempotently per due time. It is also fragile on restart or multi-instance execution because nothing prevents duplicate due processing or records that a specific due time already produced exactly one assignment.

## Nonblocking: Playwright MCP path was blocked by an existing browser profile lock

The required MCP-first browser validation was attempted, but `browser_tabs` failed with `Browser is already in use for /home/hickelpickle/.cache/ms-playwright/mcp-chrome-4e05678`. Equivalent live Playwright validation was run with explicit Chromium and passed, but this is not a strict MCP pass.

# Risk Assessment

The implementation is broadly aligned with the durable orchestration runtime shape: durable jobs, job items, work assignments, inbox messages, schedules, reactions, events, runtime settings, agent profiles, and workspaces all persist in SQLite and are exposed through thin web controllers.

The ignored task/workflow context is a functional contract gap. Users can enter orchestration context in the UI, and request records accept it, but the backend does not apply it outside durable assignment/job execution. This can lead to misleading runs and makes model/workspace/job attribution incomplete.

The scheduler idempotency gap is a reliability risk for recurring work. It may not appear in single-process happy-path testing, but it matters for restart recovery and future remote-host operation.

# Recommendations

Add explicit task/workflow orchestration run context in the service layer or route context-bearing task/workflow submissions through `AssignmentService` when agent/job/workspace/model/priority fields are present.

Add a durable schedule firing key, event correlation key, or assignment source key so each schedule due time can be processed once. Cover duplicate poll/restart behavior with a focused repository/service test.

Resolve the MCP browser profile lock before final acceptance, or update the MCP configuration to use an isolated profile so the required validation can run exactly as specified.

# Follow-ups

Validation artifacts created or used during this review:

- `mvn test`: passed, 164 tests, 0 failures.
- Startup smoke with isolated SQLite: passed; app started on random port and shut down by timeout.
- Live browser fallback validation: passed with `.internal-dev/test-fixtures/orchestration-driver/live-validation.js`.
