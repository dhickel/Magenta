# Final Validation Criteria

## Context

This document defines the final validation gate for the orchestration driver implementation. It applies after the planned phases are implemented and before the work is considered complete.

The validation must prove that the implementation matches the orchestration specification, preserves existing Magenta behavior, and is maintainable enough for future agent/job/task/workflow work.

## Goal

Validate the completed orchestration driver with automated backend tests, live browser testing, startup smoke testing, and two independent code-review tracks:

- Specification and implementation-detail adherence.
- Code quality, maintainability, and reliability.

## In Scope

- Backend repository, service, controller, and integration-style tests.
- Playwright MCP validation against a live Spring Boot app and isolated SQLite database.
- Existing chat, plan, task, workflow, and tool regression coverage.
- Review of schema, APIs, services, runner behavior, UI behavior, and documentation.
- Review artifacts written to `.internal-dev/reviews/`.
- Bug artifacts for any blocking or out-of-scope issues discovered.

## Out of Scope

- Expanding feature scope during validation.
- Adding external webhook, repo clone, file watcher, branching job graph, or token-level resume behavior.
- Accepting manual-only validation for behavior that can be covered by tests.

## Implementation Steps

1. Run the full automated backend test suite:
   - `mvn test`
   - All existing tests must pass.
   - New tests must cover orchestration repositories, services, controllers, queue runner, scheduler, event reactions, workspace confinement, model override precedence, and seed/default behavior.
2. Run focused backend validation for required behaviors:
   - Fresh DB seeds a usable default DB agent.
   - Runtime settings resolve default model, planning model, summary model, and compaction model correctly.
   - Agent CRUD, clone, disable/delete, tool allowlist, and shell allowlist work.
   - Managed agent and job workspaces are created under `dataRoot` and reject path escapes.
   - User-facing jobs are separate from internal conversation-title jobs.
   - Assignments can be created only through user submission, schedule trigger, or internal event reaction.
   - Queue priority ordering, leases, cancellation, interruption, stale lease recovery, and step-boundary resume work.
   - Job item execution persists checkpoints and resumes from the last incomplete item.
   - Task/workflow execution accepts agent/job/model/priority context.
   - Model override precedence is: explicit request, step override, job default, task/workflow default, agent default, runtime global default.
3. Run startup smoke testing:
   - Start with a fresh isolated SQLite database.
   - Use a bounded command such as:
     ```bash
     timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-orchestration-validation.sqlite'
     ```
   - If startup cannot run because secrets, model endpoints, or local services are unavailable, record the blocker explicitly.
4. Run Playwright MCP validation using the documented live-chat workflow:
   - Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before running browser validation.
   - Start the app against an isolated SQLite database.
   - Validate `/chat` still loads and existing chat workflows remain intact.
   - Validate `/agents` overview: create, clone, update, disable/delete, and open agent details.
   - Validate agent detail dashboard: profile, inbox, queue, schedules, event reactions, workspace, assignments, run history.
   - Validate collapsible agent side-panel chat on agent, task, workflow, and job pages.
   - Validate `/jobs` and `/jobs/{id}`: create job, bind managed workspace, add ordered task/workflow/wait/report items, set model overrides, run job.
   - Validate pause/cancel/resume behavior and reload persisted checkpoint state after browser or MCP timeouts.
   - Validate `/tasks` and `/workflows` editors still work and can submit orchestration-context runs.
   - Capture browser console/network failures; distinguish expected negative-path 400s from frontend defects.
5. Perform specification-adherence review with a dedicated reviewer:
   - Review implementation against all phase plan files and this validation document.
   - Confirm public APIs match intended contracts.
   - Confirm schema supports the required lifecycle and no separate hidden orchestration store was introduced.
   - Confirm agents, jobs, assignments, schedules, event reactions, and workspaces follow the agreed domain boundaries.
   - Confirm resume is implemented at step boundaries, not as an unsupported partial model continuation.
   - Confirm `/chat` UI and `chat-client.js` were not modified except for explicitly approved nonbehavioral fixes.
   - Write findings to `.internal-dev/reviews/<date>-orchestration-spec-adherence-review.md`.
6. Perform code-quality, maintainability, and reliability review with a separate reviewer:
   - Review package boundaries, naming, service size, repository APIs, transaction boundaries, and error handling.
   - Look for duplicated functionality already available elsewhere in the repo.
   - Check concurrency behavior, lease handling, cancellation, idempotency, and restart recovery.
   - Check path confinement and workspace safety.
   - Check UI maintainability, static asset organization, state handling, accessibility basics, and mobile/desktop layout stability.
   - Check tests are meaningful and not overfit to implementation details.
   - Write findings to `.internal-dev/reviews/<date>-orchestration-quality-reliability-review.md`.
7. Triage review findings:
   - Blocking findings must be fixed before completion.
   - Nonblocking defects must be recorded in `.internal-dev/bugs/`.
   - Deferred future ideas require user confirmation before writing `.internal-dev/notes/`.
8. Complete `.internal-dev` workflow after implementation:
   - Write changelog entry under `.internal-dev/changelogs/`.
   - Capture reusable knowledge under `.internal-dev/knowledge/`.
   - Archive finalized plan artifacts only after the implementation and validation are accepted.

## Validation

Final validation passes only when all of these are true:

- `mvn test` passes.
- Spring Boot startup smoke test passes or has a documented external blocker.
- Playwright MCP validation covers chat regression, agent dashboard, agent side-panel chat, task/workflow editors, job run, queue state, pause/cancel/resume, checkpoint reload, and persisted state after timeouts.
- Specification-adherence review has no unresolved blocking findings.
- Code-quality/reliability review has no unresolved blocking findings.
- Any out-of-scope bugs found during validation are logged in `.internal-dev/bugs/`.
- Changelog, knowledge, and any confirmed notes are written.

## Exit Criteria

- The implemented orchestration driver demonstrably matches the agreed plans.
- Existing chat, planning, task, workflow, tool, and internal title-job behavior is preserved.
- Agent/job workspace behavior is durable, path-confined, and observable.
- Queue, scheduler, event reaction, and step-boundary resume behavior is tested and reviewed.
- The UI is validated through Playwright MCP and does not regress the existing `/chat` UI.
- Two independent review artifacts exist: one for specification adherence and one for quality/reliability.
