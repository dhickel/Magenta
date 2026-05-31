# Phase 08 Validation Report: Run Display Name Boundary

Date: 2026-05-31
Validator: Codex phase validation agent
Directive: `.internal-dev/plans/github-issue-backlog-remediation-20260531/worker-directives/phase-08-run-display-name-boundary.md`
Result: PASS

## Findings

- Residual test-coverage note: `AssignmentTemplateParser`/`AssignmentService` shared validation was source-verified for both unnamed non-job `TASK_RUN` and `WORKFLOW_RUN`, but the focused service test added in `AssignmentContextServiceTest` covers only the `TASK_RUN` rejection. Workflow rejection is covered at `WorkflowController.startRun`, and the shared workflow branch is simple and directly adjacent to the tested task branch, so this is not blocking for Phase 08. Add a direct shared-boundary `WORKFLOW_RUN` negative test in a future hardening pass if this area changes again.

## Criteria Results

| Criterion | Result | Evidence |
| --- | --- | --- |
| Inspect scoped diff for controllers, assignment request/parser, tests, specs/docs/changelog | PASS | Reviewed diffs for `PlanController`, `AgentOrchestrationController`, `AssignmentRequest`, `AssignmentTemplateParser`, focused tests, `.internal-dev/specifications/api.md`, `.internal-dev/specifications/services.md`, `docs/api/00-index.md`, `docs/technical/api-reference.md`, `docs/technical/workspaces-tools-outputs.md`, and `.internal-dev/changelogs/2026-05-31-run-display-name-boundary.md`. `TaskController`, `WorkflowController`, and `AssignmentService` had relevant existing behavior inspected even where no Phase 08 diff was present. |
| Named bypasses covered: `PlanController.submitToAgent`, `PlanController.streamRun`, `AgentOrchestrationController.assign` | PASS | `PlanController.submitToAgent` requires `runDisplayName` before creating `TASK_RUN`; `PublicRunSubmissionControllerTest.planSubmitRejectsMissingRunNameForNonJobSubmission` covers rejection. `PlanController.streamRun` requires `runDisplayName` and emits failed SSE; `PublicRunSubmissionControllerTest.planRunStreamRejectsMissingRunNameForNonJobSubmission` and `PublicApiRouteBindingTest.planStreamRouteRejectsMissingRunDisplayName` cover it. `AgentOrchestrationController.assign` requires names for non-job task/workflow requests; `AgentOrchestrationControllerTest.assignRejectsMissingRunNameForNonJobTaskAssignment` and `PublicApiRouteBindingTest.agentAssignmentRouteRejectsMissingTaskRunDisplayName` cover the named task bypass. |
| `TaskController`/`WorkflowController` behavior remains consistent | PASS | Existing controller logic still normalizes `jobId`, requires `runDisplayName` only when no job context is present, includes display name in submitted SSE events, and preserves job-context compatibility. Focused tests cover task missing-name rejection, task job-context submission without route name, workflow valid-name submission, and workflow missing-name rejection. |
| Shared assignment boundary rejects unnamed non-job `TASK_RUN`/`WORKFLOW_RUN` and preserves `JOB_RUN` | PASS | `AssignmentService.create` calls `AssignmentTemplateParser.validate` immediately before persistence with normalized context. Parser rejects non-job `TASK_RUN` without `runDisplayName`, rejects non-job `WORKFLOW_RUN` without `runDisplayName`, and leaves `JOB_RUN` validation limited to job id presence. `AssignmentContextServiceTest.assignmentCreationRejectsMissingRunDisplayNameForNonJobTaskRun` covers the service path; `PublicRunSubmissionControllerTest.jobRunSubmitsHighPriorityJobAssignment` and existing schedule tests preserve job behavior. |
| API compatibility red-team: constructor ordering, JSON fields, route 400, legacy null reads, docs | PASS | `AssignmentRequest` adds a run-name constructor while preserving older constructors with null run names for compatibility. Request record field names are additive `runDisplayName` and keep existing fields in place. Direct REST routes return controlled 400 JSON errors; stream routes return controlled `failed` SSE events. `AssignmentContextServiceTest.readingLegacyAssignmentWithoutNewColumnsFallsBackToInputProjectContext` keeps old nullable rows readable. Specs and API/technical docs consistently describe required non-job names and `JOB_RUN` exemption. |
| Formatting check | PASS | `git diff --check -- <scoped files>` passed in a clean temp worktree with only Phase 08 patch applied. |
| Focused tests | PASS | `mvn -q -Dtest=PublicRunSubmissionControllerTest,AgentOrchestrationControllerTest,PublicApiRouteBindingTest,AssignmentContextServiceTest test` passed in `/tmp/magenta2-phase08-validate`: 61 tests, 0 failures, 0 errors, 0 skipped across the four named suites. |
| Bounded startup | PASS | Initial startup without copied local config failed because the detached temp worktree lacked ignored `config/ai-config.example.json`; reran with an isolated temp AI config and `--magenta.root.path=/tmp/magenta2-phase08-startup-root`. App logged `Started Magenta2Application in 5.233 seconds` on random port `34599`; `timeout 30s` then shut it down gracefully with exit 124 after successful startup. |
| Browser validation | NOT REQUIRED | Phase 08 changed API/service validation and docs only; validation matrix says no browser proof unless UI forms change. No UI form changes were in scope. |

## Commands Run

```bash
git worktree add --detach /tmp/magenta2-phase08-validate HEAD
git diff -- <scoped Phase 08 tracked files> > /tmp/magenta2-phase08.patch
git -C /tmp/magenta2-phase08-validate apply /tmp/magenta2-phase08.patch
cp .internal-dev/changelogs/2026-05-31-run-display-name-boundary.md /tmp/magenta2-phase08-validate/.internal-dev/changelogs/
```

```bash
git diff --check -- \
  src/main/java/io/mindspice/magenta2/api/web/PlanController.java \
  src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java \
  src/main/java/io/mindspice/magenta2/api/web/TaskController.java \
  src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java \
  src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentRequest.java \
  src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentTemplateParser.java \
  src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java \
  src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java \
  src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java \
  src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java \
  src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentContextServiceTest.java \
  .internal-dev/specifications/api.md \
  .internal-dev/specifications/services.md \
  docs/api/00-index.md \
  docs/technical/api-reference.md \
  docs/technical/workspaces-tools-outputs.md \
  .internal-dev/changelogs/2026-05-31-run-display-name-boundary.md
```

```bash
mvn -q -Dtest=PublicRunSubmissionControllerTest,AgentOrchestrationControllerTest,PublicApiRouteBindingTest,AssignmentContextServiceTest test
```

```bash
timeout 30s mvn -q spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=0 --magenta.root.path=/tmp/magenta2-phase08-startup-root --app.ai.config-path=/tmp/magenta2-phase08-startup-config/ai-config.json"
```

## Evidence Notes

- Focused surefire summaries:
  - `PublicRunSubmissionControllerTest`: 14 tests, 0 failures, 0 errors, 0 skipped.
  - `PublicApiRouteBindingTest`: 10 tests, 0 failures, 0 errors, 0 skipped.
  - `AgentOrchestrationControllerTest`: 27 tests, 0 failures, 0 errors, 0 skipped.
  - `AssignmentContextServiceTest`: 10 tests, 0 failures, 0 errors, 0 skipped.
- Expected negative-route log noise appeared for controlled 400 responses during tests; surefire results reconcile as passing.
- The clean temp worktree avoided unrelated dirty files: `.gitignore`, root `AGENTS.md`, `.internal-dev/reviews/2026-05-28-model-alias-internal-review.md`, and `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/v2/`.

## Proceed Gate

Phase 08 may proceed to commit/push/closeout for GitHub issue #16. Do not include unrelated dirty files in the commit. No browser validation is required for this phase.
