---
task: issue-remediation-planning
created: 2026-05-23
status: completed
---

# Issue Remediation Planning Notes

## Global Assumptions

- Dwight requested an inventory of GitHub issues and `.internal-dev` bug trackers, accounting for mirrored issues.
- Active mirrored planning targets found so far:
  - GitHub #6 / `.internal-dev/bugs/public-alpha-remediation/bug-empty-job-runs-remain-running/report.md`
  - GitHub #7 / `.internal-dev/bugs/2026-05-23-chat-surface-enum-case/report.md`
- GitHub issues #3, #4, and #5 are closed; archived local bugs are not implementation targets unless current code review proves a live regression.
- Continue AgentMail intake during work. Acknowledge inbound messages before acting and record actionable mail in `.internal-dev/inbox`.

## Active Agents

- Main Codex: issue inventory, orchestration, implementation integration, validation, email reporting.

## Completed Work

- Received and acknowledged Dwight's "Bug and issue handling" email through the `.internal-dev/inbox` workflow.
- Confirmed `gh` access to `dhickel/Magenta`.
- Listed open GitHub issues and local active bug reports.
- Sent "Bug and issue planning work begun" email.
- Planned and implemented GitHub #7 / `.internal-dev/bugs/2026-05-23-chat-surface-enum-case/report.md`.
- Planned and implemented GitHub #6 / `.internal-dev/bugs/public-alpha-remediation/bug-empty-job-runs-remain-running/report.md`.
- Updated API/runtime docs for case-insensitive chat surfaces and empty job no-op completion.
- Prepared local bug reports for archival after validation.

## Validation Results

- Passed: `mvn -q -Dtest=ChatControllerTest,JobServiceTest,OrchestrationRuntimeTest,PublicRunSubmissionControllerTest,OrchestrationControllerTest,PublicApiRouteBindingTest test`
- Passed: `mvn -q test`
- Passed: `git diff --check`
- Passed startup smoke: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached `Started Magenta2Application`; exit code `124` was the expected timeout shutdown.

## Remediation Notes

- Chat surface deserialization now accepts known `BROWSER`, `AVATAR`, and `INTERNAL` values case-insensitively while rejecting blank and unknown values.
- Empty assignment-owned job runs now transition to `COMPLETED` through an explicit `JobService.completeRun(...)` path when a submitted job has no items.

## Blockers

- None currently.

## Closeout Work

- Update plans/reviews/changelogs/knowledge as needed. Done.
- Update local bug reports and GitHub issues as fixes land. Local archival prepared; GitHub closeout follows commit.
- Commit implementation and `.internal-dev` updates by phase. Pending.
- Email detailed progress and final reports. Pending after commit and GitHub closeout.

## Final Validation Status

- Passed backend/API validation. No Playwright pass was required because this remediation touched JSON binding and job-run backend lifecycle behavior, not a UI surface.

## Handoff Notes

- Planning agent should append researched issue plans below.

## Advanced Issue Plans - 2026-05-23

### Scope Snapshot

Active remediation targets:

- GitHub #6 / `.internal-dev/bugs/public-alpha-remediation/bug-empty-job-runs-remain-running/report.md`: empty job assignment execution can complete while the associated `job_runs` row remains `RUNNING`.
- GitHub #7 / `.internal-dev/bugs/2026-05-23-chat-surface-enum-case/report.md`: lowercase `surface` values for `/api/chat` and `/api/chat/stream` fail Jackson enum deserialization before request handling.

Closed issues accounted for:

- GitHub #3, #4, and #5 are closed. Current narrow code review did not prove a live unresolved defect requiring this remediation plan. The current code has same-conversation plan execution guards in `ActiveTurnRegistry.registerPlanExecution(...)`, `PlanService.mode(...)` treats `PlanStatus.NEEDS_REVIEW` as `PlanMode.NORMAL`, `PlanCompletionService` has explicit criterion-level validator JSON and fail-closed preflight behavior, and `ChatService` now persists controlled needs-review messaging and malformed tool-call transcript entries. Do not add #3-#5 to this execution batch unless a focused failing test or runtime reproduction is produced on the current branch.

### Plan A - GitHub #6: Empty Job Runs Can Remain RUNNING After Assignment Completion

#### Objective

Ensure every `JOB_RUN` assignment that reaches a terminal state leaves its assignment-owned `job_runs` row in a matching terminal state. The immediate bug is the empty-items path: `OrchestrationRunnerService.runJob(...)` starts or resumes a `JobRun`, skips the item loop because `job.items()` is empty, marks the job definition completed, and completes the assignment without ever transitioning the `JobRun` from `RUNNING` to `COMPLETED`.

#### Current-State Code Analysis

- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md` says public operational job APIs allow empty `DRAFT` jobs and expose item routes separately from run routes.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`
  - `saveDefinition(...)` accepts `List.of()` items and defaults blank status to `DRAFT`.
  - `startRun(...)` requires an assignment context, allocates output/workspace paths, and initializes one `JobWorkItemRun` per definition item. For an empty job it creates a `QUEUED` run with `workItemRuns = []`.
  - `markRunning(...)` transitions only `QUEUED -> RUNNING`.
  - `updateWorkItemRun(...)` is the only current successful run completion path. It computes terminal state after item updates, so it is never called for empty jobs.
  - `failRun(...)` and `cancelRunFromAssignment(...)` exist, but there is no equivalent complete method for assignment-owned job runs.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
  - `runJob(...)` lines around 457-536 starts/resumes the job run, checkpoints `jobRunId`, then loops over `items`.
  - On item failure it updates item state to `FAILED` and fails the assignment, but it does not explicitly call `jobService.failRun(...)`; `updateWorkItemRun(...)` currently marks the run failed because all item states are terminal.
  - On non-empty success, the last `updateWorkItemRun(..., "COMPLETED", ...)` marks the run `COMPLETED` when all items are terminal.
  - On empty success, no item update occurs; `jobService.updateDefinitionStatus(job.id(), "COMPLETED")` and `complete(current, outputs, evidence)` run while the `JobRun` remains `RUNNING`.
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
  - `projectScopedJobAssignmentsUseIsolatedPersistentWorkspacesAndProjectOutputs()` currently creates a project-scoped persistent job with `List.of()` items, runs two assignments, and asserts both assignments complete plus workspace/output path isolation. It does not assert either `JobRun.status()`.
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/JobServiceTest.java`
  - Existing tests cover empty draft definition creation, run allocation, work-item completion, failure, cancellation, and stale summary behavior. There is no empty run terminal transition coverage.

#### Target Behavior

Safest implementation assumption: empty jobs should complete immediately once their assignment runner owns the lease, not be rejected during execution.

Why this is safest:

- The web package guide explicitly allows empty `DRAFT` job definitions so users can create metadata before adding items.
- Existing public run routes submit `JOB_RUN` assignments instead of calling direct job execution, and current runner behavior already completes empty assignments. Rejecting empty jobs now would be a behavior change to assignment and UI flows, not just a stale-run fix.
- The current empty-job integration-style test implicitly treats empty job assignment execution as valid by asserting completed assignments for an empty job.

What to verify before coding:

- Confirm with product/user whether running an empty `DRAFT` job from the public UI should remain a no-op success. If the desired product behavior is "empty draft jobs may exist but cannot be run," implement that as a separate validation change at submission boundaries (`JobController.startRun(...)`, `OrchestrationController.startJobRun(...)`, and schedule/template creation), not as this bug fix.

Target state under the safe assumption:

- Empty job run transitions `RUNNING -> COMPLETED`.
- `job_runs.completed_at` is set.
- `job_runs.final_message` is set to a concise completion message, for example `Job run completed`.
- The assignment checkpoint continues to include `jobId`, `jobRunId`, `jobAssignmentId`, `jobWorkspacePath`, `jobOutputDir`, and `workspaceId`.
- The assignment status remains `COMPLETED`.
- Definition status still becomes `COMPLETED`.
- Existing non-empty success/failure/cancel semantics are preserved.

#### Implementation Steps

1. Add an explicit successful terminal transition API in `JobService`.

   File: `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`

   Add a method near `failRun(...)` and `cancelRunFromAssignment(...)`:

   ```java
   public JobRun completeRun(String runId, String finalMessage) {
       JobRun run = getRun(runId);
       if (run.status().isTerminal()) {
           return run;
       }
       return jobRepository.saveRun(new JobRun(
           run.id(), run.jobId(), run.jobAssignmentId(), run.workspaceId(), JobRunStatus.COMPLETED,
           run.workItemRuns(), run.workspacePath(), run.outputDir(),
           StringUtils.hasText(finalMessage) ? finalMessage : "Job run completed",
           run.errorText(),
           run.createdAt(), Instant.now(),
           run.startedAt(), Instant.now()
       ));
   }
   ```

   Notes:

   - Preserve idempotency for terminal runs so resumed assignments do not throw after a prior completion.
   - Keep `startedAt` unchanged; if an old run somehow has no `startedAt`, do not invent a separate behavior unless tests prove the need.
   - Use `StringUtils.hasText` and `Instant.now()` consistent with nearby methods.

2. Call the explicit completion API at the end of `OrchestrationRunnerService.runJob(...)`.

   File: `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`

   In `runJob(...)`, just before or immediately after `jobService.updateDefinitionStatus(job.id(), "COMPLETED")`, call:

   ```java
   jobRun = jobService.completeRun(jobRun.id(), "Job run completed");
   ```

   Recommended placement:

   - After the item loop and before publishing `JOB_STATUS_CHANGED`.
   - Before `complete(current, outputs, evidence)` so any future output/evidence enrichment can see the terminal run if needed.

   Preserve current checkpoint/output behavior. Do not add synthetic item evidence for empty jobs unless product asks for it; an empty `workItemRuns` list is the truthful representation.

3. Audit failure semantics without broadening the fix.

   The current failure path reaches `JobRunStatus.FAILED` through `updateWorkItemRun(...)` after a failed item. Do not refactor this in the #6 patch unless a test reveals a non-empty failure leaves the run active.

   If implementer wants a defensive hardening after the main fix, add a small assertion test that non-empty failed item still fails the job run; do not change failure code speculatively.

4. Add focused tests.

   Preferred test update:

   - Extend `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java::projectScopedJobAssignmentsUseIsolatedPersistentWorkspacesAndProjectOutputs()`.
   - After fetching `firstRun` and `secondRun`, add:

   ```java
   assertThat(firstRun.status()).isEqualTo(JobRunStatus.COMPLETED);
   assertThat(secondRun.status()).isEqualTo(JobRunStatus.COMPLETED);
   assertThat(firstRun.completedAt()).isNotNull();
   assertThat(secondRun.completedAt()).isNotNull();
   assertThat(firstRun.finalMessage()).isEqualTo("Job run completed");
   assertThat(secondRun.finalMessage()).isEqualTo("Job run completed");
   ```

   Also add a service-level test:

   - File: `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/JobServiceTest.java`
   - New test name: `completeRunTransitionsEmptyRunToCompleted()`
   - Create an empty job definition, call `startRun(...)`, `markRunning(...)`, then `completeRun(...)`, and assert `COMPLETED`, `completedAt != null`, `finalMessage`, empty `workItemRuns`, and idempotent second `completeRun(...)`.

5. Tracker and docs closeout after implementation.

   - Update `.internal-dev/bugs/public-alpha-remediation/bug-empty-job-runs-remain-running/report.md` status from `Open` to fixed/validated only after tests and startup smoke pass.
   - Add a changelog under `.internal-dev/changelogs/2026-05-23-empty-job-run-terminal-status.md`.
   - If the local bug is finalized, move it to `.internal-dev/bugs/.archive/public-alpha-remediation/bug-empty-job-runs-remain-running/report.md` during closeout.
   - Comment on and close GitHub #6 with the commit hash and validation commands after the implementation commit lands.
   - No end-user docs update is required for the immediate no-op completion fix unless the UI text or API contract changes. If the implementer chooses rejection semantics instead, update API/docs for `/api/jobs/{jobId}/runs`.

#### Validation Commands

Run focused tests:

```bash
mvn -Dtest=JobServiceTest,OrchestrationRuntimeTest test
```

Run route/controller coverage that submits job runs:

```bash
mvn -Dtest=PublicRunSubmissionControllerTest,OrchestrationControllerTest,PublicApiRouteBindingTest test
```

Run full tests if focused tests pass:

```bash
mvn test
```

Run application wiring smoke:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Manual database/API acceptance check, if a local app run is available:

- Create or use an empty job.
- Submit `/api/jobs/{jobId}/runs`.
- Let the assignment runner process it.
- Verify `work_assignments.status = COMPLETED` and the linked `job_runs.status = COMPLETED`, with non-null `completed_at`.

#### Stop Conditions

- Stop and ask the user if product direction is to reject empty job runs instead of completing them. That changes public run behavior and should be planned as a validation/API contract change, not silently folded into this fix.
- Stop if `completeRun(...)` exposes an unexpected active-run edit guard issue, because job definition mutation guards depend on `countActiveRunsByJobId(...)`.

### Plan B - GitHub #7: Chat Surface Enum Rejects Lowercase API Values

#### Objective

Accept known `ChatSessionSurface` values case-insensitively at the JSON request boundary for `/api/chat` and `/api/chat/stream`, while continuing to reject unknown values. The fix must not make arbitrary strings silently map to a default surface.

#### Current-State Code Analysis

- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatRequest.java`
  - `MsgRequest.surface` is typed as `ChatSessionSurface`.
  - Jackson tries to deserialize the enum before `ChatController.chat(...)`, `ChatController.stream(...)`, `ChatService.chat(...)`, or `RequestResolver.resolve(...)` run.
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatSessionSurface.java`
  - Current enum values are exact constants: `BROWSER`, `AVATAR`, `INTERNAL`.
  - No `@JsonCreator`, `@JsonValue`, or normalizer exists.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/RequestResolver.java`
  - `resolve(...)` persists a supplied non-null surface through `ChatSessionMetadataRepository.saveSurfaceIfAbsent(...)`.
  - This is the correct layer for session metadata behavior, but it is too late for lowercase JSON because deserialization fails first.
- Similar local enum patterns exist:
  - `TaskValueType.fromWireName(...)` accepts lowercase/wire names and throws `IllegalArgumentException` on unknown values.
  - Workflow enums such as `WorkflowNodeType.fromWireName(...)` use `@JsonCreator`, lowercase normalization, and unknown-value rejection.

#### Target Behavior

- `{"surface":"browser"}`, `{"surface":"BROWSER"}`, and mixed-case variants such as `{"surface":"Browser"}` deserialize to `ChatSessionSurface.BROWSER`.
- Same for `avatar` and `internal`.
- `null` or missing `surface` remains null and preserves current optional behavior.
- Blank strings should be rejected, not normalized to null, because `surface` is an optional field and callers can omit it. Silent blank-as-null would weaken validation and hide bad clients.
- Unknown values such as `{"surface":"mobile"}` still fail with HTTP 400 on real requests and fail ObjectMapper deserialization in unit tests.

#### Implementation Steps

1. Add a boundary normalizer to `ChatSessionSurface`.

   File: `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatSessionSurface.java`

   Implement the same style as `WorkflowNodeType`:

   ```java
   import java.util.Locale;

   import com.fasterxml.jackson.annotation.JsonCreator;

   public enum ChatSessionSurface {
       BROWSER,
       AVATAR,
       INTERNAL;

       @JsonCreator
       public static ChatSessionSurface fromWireName(String value) {
           if (value == null || value.isBlank()) {
               throw new IllegalArgumentException("Chat session surface must not be blank");
           }
           String normalized = value.trim().toUpperCase(Locale.ROOT);
           for (ChatSessionSurface surface : values()) {
               if (surface.name().equals(normalized)) {
                   return surface;
               }
           }
           throw new IllegalArgumentException("Unknown chat session surface: " + value);
       }
   }
   ```

   Do not add a global Jackson case-insensitive enum setting. That would weaken all enums and create unnecessary blast radius.

   Do not change `ChatRequest.MsgRequest.surface` to `String`; keeping the record typed as `ChatSessionSurface` confines normalization to the enum and preserves downstream type safety.

2. Add focused serialization/deserialization tests.

   Preferred file: `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java` or a new model-focused test `src/test/java/io/mindspice/magenta2/ai/chat/model/ChatSessionSurfaceTest.java`.

   If using `ChatControllerTest`, add imports for `JsonMappingException` or use AssertJ generic exception assertions.

   Test names and assertions:

   - `msgRequestAcceptsLowercaseSurface()`
     - `new ObjectMapper().readValue("{\"conversationId\":\"...\",\"message\":\"ping\",\"model\":\"test\",\"surface\":\"browser\"}", ChatRequest.MsgRequest.class)`
     - Assert `request.surface() == ChatSessionSurface.BROWSER`.
   - `msgRequestAcceptsMixedCaseSurface()`
     - Use `"Avatar"` and assert `AVATAR`.
   - `msgRequestRejectsUnknownSurface()`
     - Use `"mobile"` and assert deserialization throws with message containing `Unknown chat session surface`.
   - `msgRequestRejectsBlankSurface()`
     - Use `" "` and assert deserialization throws with message containing `must not be blank`.
   - Preserve an existing or new missing-surface case to assert `surface() == null`.

3. Add a route-level regression only if cheap and stable.

   Because `/api/chat` invokes model-backed chat in full integration tests, prefer ObjectMapper-level coverage for the request boundary. If a controller slice/stub path already exists or can be added without booting the full model stack, add a MockMvc-style request-body binding assertion for lowercase `surface`.

   Do not make a real model call just to prove enum binding. The ObjectMapper test directly covers the bug described in #7.

4. Verify downstream behavior.

   `RequestResolver.resolve(...)` already handles non-null `surface` and persists it if absent. No production changes are needed there unless tests reveal a regression.

   Browser JavaScript currently uppercases the payload. Do not change JS for this issue unless a test shows it now sends an invalid value; the target is request-boundary tolerance for direct clients and future callers.

5. Tracker and docs closeout after implementation.

   - Update `.internal-dev/bugs/2026-05-23-chat-surface-enum-case/report.md` status only after tests and startup smoke pass.
   - Add a changelog under `.internal-dev/changelogs/2026-05-23-chat-surface-case-insensitive-api.md`.
   - Move the local bug report to `.internal-dev/bugs/.archive/2026-05-23-chat-surface-enum-case/report.md` after final validation.
   - Comment on and close GitHub #7 with the commit hash and validation commands.
   - Update API docs only if `docs/api` currently documents `MsgRequest.surface` as uppercase-only. If it documents the field generically or not at all, the bug fix can be represented in the changelog.

#### Validation Commands

Run focused tests:

```bash
mvn -Dtest=ChatControllerTest test
```

If a separate model test is added:

```bash
mvn -Dtest=ChatSessionSurfaceTest,ChatControllerTest test
```

Run route binding coverage:

```bash
mvn -Dtest=PublicApiRouteBindingTest test
```

Run full tests:

```bash
mvn test
```

Run application wiring smoke:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Optional manual API acceptance check on a running app:

```bash
curl -s -X POST http://localhost:18080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"00000000-0000-0000-0000-000000000099","message":"ping","model":"test","surface":"browser"}'
```

Expected boundary result:

- It must not fail with `Cannot deserialize value of type ChatSessionSurface from String "browser"`.
- It may still fail later for normal model/config/runtime reasons if the local app is not configured for `model:"test"`; that later failure is outside #7.

Negative manual check:

```bash
curl -s -X POST http://localhost:18080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"00000000-0000-0000-0000-000000000099","message":"ping","model":"test","surface":"mobile"}'
```

Expected result:

- HTTP 400 with an error rooted in `Unknown chat session surface: mobile`.

#### Stop Conditions

- Stop if a global Jackson configuration already accepts lowercase enums after the test is written. In that case, #7 may be stale and should be closed with evidence instead of adding enum-specific code.
- Stop if direct request deserialization cannot surface `IllegalArgumentException` cleanly through `GlobalExceptionHandler`; do not weaken unknown-value rejection to get a prettier error.

### Serial Orchestration Order

1. Run a non-mutating preflight on the current branch.
   - `git status --short --branch`
   - Confirm active issues #6 and #7 remain open.
   - Confirm #3-#5 remain closed.
   - Confirm the local bug reports still match the issue URLs.

2. Implement #7 first.
   - It is isolated to the chat model boundary and focused tests.
   - It has low interaction with runtime state.
   - Commit after focused tests pass:
     - Suggested branch if not already on a dedicated issue branch: `issue-remediation/2026-05-23-active-github-issues`.
     - Suggested commit message: `Fix chat surface request deserialization`.

3. Implement #6 second.
   - It touches orchestration runtime state and should follow after the smaller API-boundary patch.
   - Add the service method and runner call, then update focused runtime tests.
   - Commit after focused tests pass:
     - Suggested commit message: `Complete empty assignment-owned job runs`.

4. Run combined validation.
   - Focused test groups for both issues.
   - `mvn test`.
   - Bounded startup smoke.

5. Complete tracker closeout serially.
   - Update both local bug reports with fixed status and validation evidence.
   - Add changelogs.
   - Move finalized local bug reports into `.internal-dev/bugs/.archive/...`.
   - Close GitHub #6 and #7 with commit hash, exact tests, and startup result.
   - Do not reopen or include #3-#5 unless a current failing test or runtime proof is discovered.

6. Final commit and handoff.
   - If the implementation agent uses per-issue commits, make a final closeout commit only if `.internal-dev` tracker/changelog/archive updates were not included in the issue commits.
   - Verify `git diff --check`.
   - Verify no unrelated files are staged, especially existing `.internal-dev/inbox` changes from prior AgentMail work unless the current closeout intentionally updates them.

### Combined Validation Gate

Minimum gate before closing GitHub #6/#7:

```bash
mvn -Dtest=ChatControllerTest,JobServiceTest,OrchestrationRuntimeTest,PublicRunSubmissionControllerTest,OrchestrationControllerTest,PublicApiRouteBindingTest test
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
git diff --check
```

Acceptance criteria:

- Lowercase and mixed-case known chat surfaces deserialize successfully.
- Unknown and blank chat surfaces still fail.
- Empty `JOB_RUN` assignments complete with linked `job_runs.status = COMPLETED`.
- Non-empty job item completion/failure behavior is unchanged.
- Active run guards no longer count empty completed job runs as active.
- The app context starts successfully.

### Blockers And Risks

- Product ambiguity for #6: current code and tests favor immediate no-op completion for empty jobs, but product may prefer rejecting empty job runs at submission. Verify before implementation if this is not already settled.
- Runtime validation dependency: #6 is a state-machine/persistence bug. Do not close it on unit-only coverage if the relevant runtime tests cannot run.
- Error-message exactness for #7 may vary because Jackson wraps `IllegalArgumentException` in `HttpMessageNotReadableException`; tests should assert the domain text appears in the cause/message, not a brittle full wrapper string.
