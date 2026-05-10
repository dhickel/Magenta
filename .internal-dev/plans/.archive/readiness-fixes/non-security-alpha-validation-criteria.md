# Final Validation Criteria

## Context

This document defines the validation gate for `.internal-dev/plans/readiness-fixes/non-security-alpha-remediation-plan.md`. It is used after the non-security alpha readiness fixes are implemented and before the work is considered complete.

The validation must prove that the implementation addressed the functionality and maintainability issues from the non-security alpha reviews without pulling in excluded security-class work.

Source plan:

- `.internal-dev/plans/readiness-fixes/non-security-alpha-remediation-plan.md`

Primary review inputs behind the plan:

- `.internal-dev/reviews/2026-05-08-alpha-robustness-review.md`
- `.internal-dev/reviews/2026-05-08-alpha-cohesion-contracts-review.md`
- `.internal-dev/reviews/2026-05-08-alpha-simplification-refactor-targets-review.md`
- `.internal-dev/reviews/2026-05-08-alpha-consolidated-milestone-review.md`

## Goal

Validate that the implemented remediation:

- Fixes the identified non-security functionality defects.
- Adds regression tests for each fixed behavior.
- Preserves existing chat, task, workflow, orchestration, persistence, and runtime settings behavior.
- Keeps code ownership boundaries clearer than before the remediation.
- Leaves durable `.internal-dev` evidence for what was changed, tested, deferred, or discovered out of scope.

## In Scope

- Code inspection against the source plan's code targets.
- Focused unit, controller, repository, service, integration, and stream lifecycle tests.
- Full `mvn test` regression suite.
- Bounded Spring Boot startup smoke with isolated SQLite.
- Clean-database and upgraded-database SQLite validation.
- Playwright MCP validation when live chat, SSE, browser behavior, task/workflow streaming, or interruption behavior changes.
- `.internal-dev` closeout artifacts after implementation.

## Out of Scope

- Validating authentication, authorization, login/session, API key/secret handling, SSRF redirect behavior, frontend injection fixes, selected-agent shell policy, or shell command policy redesign.
- Accepting manual-only validation for behavior that can be covered by tests.
- Expanding feature scope during validation.
- Treating deferred/refactor candidates as required unless the implementation changed that area or the source plan explicitly made the candidate part of the remediation.

## Implementation Steps

1. Establish the remediation inventory.
   - List each source-plan remediation group as `fixed`, `deferred`, or `not touched`.
   - For every `fixed` group, record changed production files, changed test files, and the primary validation command.
   - For every `deferred` group, confirm the user accepted deferral or record it under `.internal-dev/notes/`.
   - For every new out-of-scope defect found during validation, create a bug artifact under `.internal-dev/bugs/`.

2. Run static code checks before tests.
   - Confirm `ChatBeanConfig` injects and passes `RuntimeSettingsService` to `ContextManagementAdvisor`.
   - Confirm shell process execution cleanup has an interruption path that destroys the child process and cancels/bounds reader futures.
   - Confirm public controllers use request DTOs or validation helpers before passing request data into services.
   - Confirm workflow stream execution no longer runs synchronously on the servlet request thread.
   - Confirm stream endpoints use shared lifecycle support or an explicitly consistent lifecycle pattern.
   - Confirm orchestration queued-work polling cannot submit the same queued assignment repeatedly while it waits for executor capacity.
   - Confirm SQLite foreign-key behavior or explicit cascades are documented and tested.
   - Confirm `PlanMode` no longer lives in `ai.chat.plan` if that package move was implemented.
   - Confirm `AgentJobRepository` no longer lives in `ai.chat.repository` if that package move was implemented.
   - Confirm public create/update APIs no longer accept client-controlled lifecycle fields for remediated endpoints.
   - Confirm package `AGENTS.md` files were read and updated where package ownership changed.

3. Add or update focused tests for every fixed behavior.
   - Runtime settings:
     - Extend `src/test/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisorTest.java`.
     - Prove changed runtime compaction model and context buffer settings affect advisor decisions.
     - Prove default behavior still applies when runtime settings are absent.
   - Shell cancellation cleanup:
     - Extend `src/test/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolServiceTest.java`.
     - Add an interrupted long-running process test that proves the child process is destroyed.
     - Add or preserve timeout cleanup coverage.
     - Add a cancellation/retry classification test if `ChatService` retry logic changes.
   - Request validation and stable error mapping:
     - Extend `ChatControllerTest`, `TaskControllerTest`, `AgentOrchestrationControllerTest`, and add `WorkflowControllerTest` if workflow controller coverage is missing.
     - Cover null body, blank required fields, invalid ids, invalid enum/status values, missing referenced records, and conflict cases.
     - Assert stable 400, 404, and 409 responses where applicable.
   - Stream lifecycle:
     - Add tests for completed, client disconnected, timeout, user cancelled, model/tool failure, validation failure, and internal error outcomes for each stream class touched.
     - Extend `ChatControllerTest`, `TaskControllerTest`, `AgentOrchestrationControllerTest`, and workflow stream coverage.
     - Add unit tests for any new SSE lifecycle helper.
   - Workflow stream offloading:
     - Add or extend workflow controller/service tests proving `streamRun` returns promptly and execution continues asynchronously.
     - Prove cancellation or disconnect follows the same terminal-state policy as task streaming.
   - Duplicate queued assignment submission:
     - Extend `OrchestrationDurableRuntimeTest` or `OrchestrationRuntimeTest`.
     - Simulate executor saturation or a delayed worker start.
     - Prove one queued assignment is submitted once across repeated polls.
     - Prove executor submission rejection does not kill polling and leaves work eligible.
   - SQLite schema and foreign keys:
     - Add repository/integration tests for a clean database.
     - Add an upgraded-database fixture or setup path that starts from representative old tables and verifies required columns/tables are added.
     - Prove task and workflow deletes do not leave orphaned runs.
   - Audit sequence robustness:
     - Add or extend audit repository tests.
     - Insert audit events concurrently for one conversation.
     - Assert sequence values are unique and ordered or that conflicts retry deterministically.
   - Controller and DTO boundary cleanup:
     - Add controller contract tests proving service-owned lifecycle fields are rejected, ignored, or not present in request DTOs.
     - Verify existing UI/API tests use the new wire shape.
   - Package moves:
     - Run compile/tests after moving `PlanMode` or `AgentJobRepository`.
     - Add a lightweight package/import check if the project already uses such checks; otherwise rely on compile plus `rg` checks listed below.
   - `ChatService` seam extraction:
     - Add focused tests for extracted collaborators only when behavior moved out of `ChatService`.
     - Preserve existing `ChatServiceTest`, plan, task, tool-loop, and audit behavior.
   - Dead code/stale utility cleanup:
     - Use `rg` to prove removed symbols have no source or test references.
     - Let full compile and tests prove no runtime reference remains.
   - Typed stream event DTOs:
     - If wire shape changed, add serialization tests and update browser/client tests.
   - Workflow/schedule/reaction alpha decision:
     - If productized, add API/UI tests for accepted behavior.
     - If hidden, add route or feature-flag tests proving alpha users cannot reach it accidentally.
     - If documented experimental, verify docs and UI/API wording match the decision.

4. Run targeted validation commands.
   - Use focused Maven test selectors for changed areas before running the full suite, for example:

     ```bash
     mvn test -Dtest=ContextManagementAdvisorTest,AgentShellToolServiceTest
     mvn test -Dtest=ChatControllerTest,TaskControllerTest,AgentOrchestrationControllerTest
     mvn test -Dtest=OrchestrationDurableRuntimeTest,OrchestrationRuntimeTest
     mvn test -Dtest=AgentJobServiceTest,AgentJobRepositoryTest
     mvn test -Dtest=PlanServiceTest,TaskServiceTest,WorkflowServiceTest
     ```

   - Adjust selectors to actual new test class names.
   - If a test class is renamed or added, record the actual command in the validation evidence.

5. Run full automated validation.
   - Run:

     ```bash
     mvn test
     ```

   - The suite must pass without skipped or quarantined remediation tests.
   - Any unrelated failure must be either fixed or recorded with evidence proving it predates this remediation.

6. Run startup smoke validation.
   - Start with a fresh isolated SQLite database:

     ```bash
     rm -f /tmp/magenta2-alpha-non-security-validation.sqlite
     timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-alpha-non-security-validation.sqlite'
     ```

   - Treat exit code 124 as acceptable only if logs show healthy Spring Boot/Tomcat startup before the timeout.
   - If startup cannot run because a local model endpoint, secret, or service is unavailable, record the exact blocker.

7. Run SQLite upgraded-database validation.
   - Create or reuse an upgraded-database fixture representing the pre-remediation schema.
   - Start the app or relevant repository tests against that database.
   - Verify required schema changes are applied or rejected with a clear documented error.
   - Verify foreign-key/cascade behavior using actual SQLite connections, not only mocked repositories.

8. Run Playwright MCP validation when stream or browser behavior changed.
   - Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before starting.
   - Validate `/chat` loads and a basic chat stream still completes.
   - Validate interruption/cancel behavior for any changed chat stream path.
   - Validate task stream start/progress/completion/failure behavior.
   - Validate workflow stream start/progress/completion/failure behavior if workflows remain alpha-facing.
   - Validate orchestration side-panel stream behavior if side-panel SSE changed.
   - Capture console and network errors.
   - If Playwright MCP is blocked by browser profile infrastructure, record the blocker and run an isolated fallback browser probe against the same live app.

9. Perform a code-review pass against the remediation plan.
   - Review that each fixed group changed only its intended code targets or has a documented reason for touching additional files.
   - Review that security-class issues were not silently mixed into this non-security plan.
   - Review that controller changes reduced controller-owned behavior rather than moving complexity into larger private controller methods.
   - Review that new abstractions are small, named for current behavior, and covered by tests.
   - Write findings to `.internal-dev/reviews/<date>-non-security-alpha-remediation-validation-review.md`.

10. Complete `.internal-dev` closeout.
    - Write a changelog entry summarizing implementation and validation.
    - Update or create knowledge docs for reusable decisions, especially stream lifecycle semantics, schema ownership, or validation workflow.
    - Record out-of-scope bugs immediately under `.internal-dev/bugs/`.
    - Record deferred ideas in `.internal-dev/notes/` only after confirming they are out of scope.
    - Archive the plan artifacts only after implementation and validation are accepted.

## Validation

Final validation passes only when all applicable gates below pass.

### Code Gates

- Runtime settings are wired into context compaction and covered by tests.
- Shell cancellation destroys active child processes and cleans reader futures on interruption, cancellation, and timeout.
- Public request validation returns stable 400/404/409 responses for known invalid inputs and conflicts.
- Stream lifecycle outcomes are explicit and consistent across remediated endpoints.
- Workflow stream execution is asynchronous relative to the servlet request thread.
- Queued orchestration work cannot be submitted repeatedly before executor pickup.
- SQLite schema ownership is documented in code or knowledge docs and enforced by tests.
- Foreign-key or explicit cascade behavior prevents orphaned task/workflow run rows.
- Audit sequence writes are unique under concurrent inserts or have tested retry behavior.
- Controllers remain thin after extraction.
- Public DTOs protect service-owned lifecycle fields on remediated endpoints.
- Package moves leave no stale imports or mismatched package-guide text.

### Test Gates

- Every fixed remediation group has at least one focused regression test.
- New tests assert behavior, not only implementation details.
- Negative-path tests verify response statuses and useful error bodies.
- Stream tests cover terminal outcomes, not only happy path events.
- Persistence tests use real SQLite behavior where SQLite behavior is the risk.
- Existing chat, task, workflow, orchestration, plan, tool, and repository tests still pass.

### Command Gates

- Targeted Maven test commands for changed areas pass.
- `mvn test` passes.
- Bounded startup smoke passes against an isolated SQLite database or has a documented external blocker.
- Playwright MCP validation passes for changed live chat/SSE/browser surfaces, or a documented MCP infrastructure blocker plus passing fallback browser probe is recorded.

### Evidence Gates

- The validation review artifact exists under `.internal-dev/reviews/`.
- The implementation changelog exists under `.internal-dev/changelogs/`.
- Reusable decisions are captured under `.internal-dev/knowledge/` when new conventions are established.
- Out-of-scope defects found during validation are logged under `.internal-dev/bugs/`.
- Deferred items are captured under `.internal-dev/notes/` only after confirmation.

## Exit Criteria

- The implemented changes demonstrably address the non-security functionality issues from the remediation plan.
- No fixed group relies on manual inspection alone when automated validation is feasible.
- No security-class work is required for this validation gate to pass.
- Existing behavior is preserved unless a change is explicitly documented as part of the remediation.
- The full automated suite and startup smoke provide confidence that the app remains runnable.
- Browser validation covers any user-visible stream or interruption changes.
- `.internal-dev` artifacts provide enough evidence for a later reviewer to understand what changed, how it was validated, and what remains deferred.
