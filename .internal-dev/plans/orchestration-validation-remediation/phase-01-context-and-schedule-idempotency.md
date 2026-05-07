# Context

Final orchestration validation found two blocking gaps:

- Task and workflow run APIs accept orchestration context but discard it during execution.
- Schedule due processing creates assignments without durable per-due-time idempotency.

The findings are captured in:

- `.internal-dev/reviews/2026-05-07-orchestration-spec-adherence-review.md`
- `.internal-dev/reviews/2026-05-07-orchestration-quality-reliability-review.md`
- `.internal-dev/bugs/orchestration-final-validation-context-and-schedule-gaps/report.md`

This plan remediates those blockers without expanding orchestration scope beyond the final validation criteria.

# Goal

Make final orchestration validation acceptable by ensuring:

- Task and workflow submissions either preserve and apply supplied orchestration context or route through durable assignment execution.
- Scheduled assignment creation is idempotent for each `schedule_id + due_at` firing.
- The behavior is covered by focused backend tests, startup smoke testing, and live browser validation.

# In Scope

- Task run request handling for `agentId`, `jobId`, `workspaceId`, `modelOverride`, and `priority`.
- Workflow run request handling for `agentId`, `jobId`, `workspaceId`, `modelOverride`, and `priority`.
- Model/context precedence validation for task/workflow-triggered orchestration work:
  explicit request, step override, job default, task/workflow default where present, agent default, runtime global default.
- Durable schedule firing idempotency keyed by schedule and due instant.
- Transactional schedule polling behavior around due detection, firing record creation, event publication, assignment creation, and next-run advancement.
- Focused tests for context propagation and duplicate/crash-window schedule behavior.
- Updating the open bug report status and review follow-up references after implementation.
- Re-running the final validation subset relevant to these fixes.

# Out of Scope

- Adding external webhook triggers, repo clone/file watcher triggers, branching job graphs, or token-level model continuation.
- Redesigning task/workflow engines beyond the context needed for orchestration execution.
- Replacing the existing repository-owned schema convention with a migration framework.
- Changing existing `/chat` behavior except for validation-only coverage.
- Treating the Playwright MCP profile-lock problem as an application defect.

# Implementation Steps

1. Establish the intended execution contract.
   - Keep legacy task/workflow runs working when no orchestration context is supplied.
   - For context-bearing requests, create or link durable orchestration work so the supplied agent, job, workspace, model override, and priority are observable and executable.
   - Return enough run metadata for callers/tests to verify the accepted context was applied.

2. Add a small run-context carrier in the service layer.
   - Introduce task and workflow run context records rather than passing raw controller request records into services.
   - Normalize blank strings to absent values at the controller boundary.
   - Validate referenced agent, job, and workspace IDs through existing orchestration services/repositories.

3. Wire task and workflow context through execution.
   - Update `TaskController.streamRun` to pass full run context instead of only `inputValues`.
   - Update `WorkflowController.streamRun` to consume the request body and pass full run context.
   - Prefer routing context-bearing runs through `AssignmentService` when that matches the existing assignment/job execution model.
   - Preserve legacy synchronous task/workflow behavior for requests without orchestration context.

4. Make model/context precedence explicit.
   - Reuse existing model resolution where possible.
   - Add the missing task/workflow default layer only if the current task/workflow domain already has a natural place for it.
   - If task/workflow defaults do not exist yet, document that absence in tests and ensure explicit request, job default, agent default, and runtime default precedence are still covered.

5. Add durable schedule firing idempotency.
   - Add a repository-owned table or unique source key that records `schedule_id`, `due_at`, and the created assignment/event references.
   - Enforce uniqueness on `schedule_id + due_at`.
   - Make repeated polling for the same due instant return or skip the existing firing instead of creating another assignment.

6. Tighten schedule polling transaction boundaries.
   - Treat due time as an immutable value captured before computing the next run.
   - Persist the firing, publish/record the due event, create the assignment, and advance `nextRunAt` in a transaction where the existing repository pattern allows it.
   - If full cross-repository transaction handling is not currently available, add the smallest local transaction boundary needed and test the externally visible behavior.

7. Add focused tests.
   - Controller/service tests proving task run context is not discarded.
   - Controller/service tests proving workflow run context is not discarded.
   - Tests proving one due instant creates at most one scheduled assignment across repeated polls.
   - Tests covering the schedule failure window that final validation called out, or the closest deterministic service-level equivalent.
   - Regression tests confirming legacy task/workflow runs still work without orchestration context.

8. Update documentation artifacts after code passes validation.
   - Update the open bug report with fixed status, changed files, and validation evidence.
   - Add a changelog entry for the remediation.
   - Add reusable knowledge if the implementation establishes a schedule idempotency or orchestration-context convention.
   - Archive the plan only after the user accepts final validation.

# Validation

- Run `mvn test`.
- Run a bounded startup smoke with a fresh isolated SQLite database:

  ```bash
  timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-orchestration-remediation.sqlite'
  ```

- Run focused manual/API validation against a fresh database:
  - Submit a task run with agent, job, workspace, model override, and priority; verify the values are persisted or reflected in the created assignment/run metadata.
  - Submit a workflow run with the same context; verify the values are persisted or reflected in the created assignment/run metadata.
  - Trigger or poll a due schedule twice for the same due instant; verify only one assignment is created.

- Run browser validation:
  - Use Playwright MCP first, following `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`.
  - If MCP remains blocked by the browser profile lock, record the blocker and run `.internal-dev/test-fixtures/orchestration-driver/live-validation.js` as the fallback probe.

- Re-check the two review findings and confirm no blocking findings remain.

# Exit Criteria

- Task and workflow orchestration context is no longer silently discarded.
- Scheduled due processing is idempotent per due instant.
- Focused tests cover the fixed behavior and regressions.
- `mvn test` passes.
- Startup smoke passes or has a documented external blocker.
- Browser validation passes through MCP or has a documented MCP infrastructure blocker plus passing fallback validation.
- The open bug artifact is updated with resolution evidence.
- A changelog entry is written.
