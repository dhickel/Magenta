# Final Validation Criteria

## Context

This document defines the second-pass validation gate for the corrective implementation described in:

- `.internal-dev/plans/readiness-fixes/post-validation-correction-plan.md`

It must be run after agents implement that correction plan. The validation target is narrow: prove the findings in `.internal-dev/reviews/2026-05-08-non-security-alpha-remediation-validation-review.md` are fixed and that the original readiness validation criteria are no longer blocked by those findings.

## Goal

Validate that the corrective pass:

- Fixes the browser-visible assignment request validation regression.
- Makes disabled schedules and event reactions inert at runtime.
- Brings orchestration side-panel chat into the shared SSE lifecycle model.
- Adds meaningful stream lifecycle and `ChatStreamEvent` wire-contract tests.
- Completes required automated, startup, browser, review, changelog, knowledge, and work-log evidence.

## In Scope

- Code inspection for the exact corrective targets in `post-validation-correction-plan.md`.
- Focused unit, controller, repository/service, serialization, and stream lifecycle tests.
- Full `mvn test`.
- Bounded Spring Boot startup smoke with isolated SQLite.
- Browser validation through Playwright MCP or a documented fallback probe.
- `.internal-dev` evidence closeout for the corrective implementation.

## Out of Scope

- Security-class fixes: auth, secrets, SSRF, frontend injection, selected-agent shell policy, or shell command policy redesign.
- Revalidating already-passed readiness remediation groups except where corrective changes touched them.
- Productizing schedules or event reactions. Passing means disabled features are hidden and inert by default.
- Accepting direct controller-unit tests as the only proof for behavior that depends on Spring MVC validation or browser payload shape.

## Implementation Steps

1. Build the correction inventory.
   - List each validation-review finding as `fixed`, `not fixed`, or `not applicable`.
   - For each fixed item, record changed production files and changed tests.
   - The inventory must include:
     - agent assignment request validation
     - schedule runtime feature flag
     - reaction runtime feature flag
     - side-panel SSE lifecycle
     - stream lifecycle coverage
     - `ChatStreamEvent` serialization
     - browser validation evidence
     - work-log/changelog/knowledge evidence

2. Run code checks for assignment validation.
   - Confirm `AgentOrchestrationController.assign` no longer accepts `@Valid @RequestBody AssignmentRequest`.
   - Confirm the route uses a DTO that does not contain required `agentId`.
   - Confirm path `agentId` is the only source used for the created `AssignmentRequest.agentId`.
   - Confirm unknown JSON body `agentId` is ignored or rejected deliberately; preferred behavior is ignored by route-specific DTO.
   - Confirm `AssignmentRequest.agentId` remains validated for API/service paths that still own agent id in the body.

3. Run code checks for schedule and reaction runtime gating.
   - Confirm `ScheduleService.pollDueSchedules` returns without side effects when `magenta.features.schedules-enabled=false`.
   - Confirm disabled schedule polling does not create schedule firings, assignments, due events, or advance `nextRunAt`.
   - Confirm `OrchestrationEventService.handle` does not evaluate reactions or enqueue assignments when `magenta.features.reactions-enabled=false`.
   - Confirm disabled reaction handling still marks the event handled, or documents a different no-repeat strategy in code/tests.
   - Confirm controller-level 404 behavior for disabled schedule/reaction routes remains intact.

4. Run code checks for side-panel SSE lifecycle.
   - Confirm `/api/agents/{agentId}/chat/stream` returns its `SseEmitter` before model/chat work completes.
   - Confirm side-panel chat work is offloaded to `Schedulers.boundedElastic()` or equivalent bounded background execution.
   - Confirm `SseStreamLifecycle.SubscriptionGuard` is used and disposed on completion, timeout, and error.
   - Confirm existing side-panel event names and fields remain compatible with `src/main/resources/static/js/orchestration/agent-chat.js`:
     - `start`: `agentId`, optionally `agentName`
     - `done`: `agentId`, `conversationId`, `model`, `message`
     - `error`: `error`
   - Confirm blank message and unsupported response cases emit an SSE `error` event and complete cleanly.

5. Run code checks for stream lifecycle tests.
   - Confirm `SseStreamLifecycleTest` exercises actual registered lifecycle callbacks or a package-private callback object, not only `guard.dispose()`.
   - Confirm tests cover callback behavior for completion, timeout, and error.
   - Confirm controller tests cover terminal behavior for chat, task, workflow, and side-panel streams where touched.
   - Confirm at least one test covers cancellation/interrupt behavior for chat stream or documents why cancellation is only covered by live/browser validation.

6. Run code checks for `ChatStreamEvent` serialization.
   - Confirm a focused serialization test exists for every typed event:
     - `Start`
     - `Chunk`
     - `Tool`
     - `SystemNotice`
     - `Interrupt`
     - `Context`
     - `Done`
     - `Error`
   - Confirm serialized JSON includes every field consumed by `chat-client.js`.
   - Confirm the test asserts absence/presence deliberately where the typed hierarchy intentionally removed nullable fields.

7. Run targeted automated tests.
   - Required command group:

     ```bash
     mvn test -Dtest=AgentOrchestrationControllerTest,OrchestrationDurableRuntimeTest
     mvn test -Dtest=SseStreamLifecycleTest,ChatControllerTest,TaskControllerTest,WorkflowControllerTest
     mvn test -Dtest=ChatStreamEventSerializationTest
     ```

   - Adjust class names only if the implementation uses equivalent names. Record the actual command names in the validation review.

8. Run full automated tests.
   - Run:

     ```bash
     mvn test
     ```

   - The full suite must pass with no skipped or quarantined corrective tests.

9. Run startup smoke.
   - Use an isolated SQLite database:

     ```bash
     rm -f /tmp/magenta2-post-validation-correction-smoke.sqlite
     timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-post-validation-correction-smoke.sqlite'
     ```

   - Exit code `124` is acceptable only if logs show healthy Tomcat startup before timeout.

10. Run browser validation.
    - Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` first.
    - Use Playwright MCP first.
    - If Playwright MCP is blocked by browser profile infrastructure, record the exact blocker and run a fallback browser-origin probe against the same live app.
    - Browser validation must cover:
      - `/chat` loads.
      - Basic chat SSE emits expected parseable events.
      - Agent assignment UI/API payload succeeds without body `agentId`.
      - Task run stream emits `started` and terminal event for a controlled fixture.
      - Workflow run stream emits `started` and terminal event for a controlled fixture.
      - Agent side-panel stream returns promptly and emits valid `start` and terminal `done` or `error`.
      - Disabled schedules and reactions routes return 404.
      - No unexpected console errors or failed network requests.

11. Review `.internal-dev` evidence.
    - Confirm readiness work log has a correction status section and no contradictory top-level status for issue 2.2.
    - Confirm implementation changelog exists for the corrective implementation, not only for plan creation.
    - Confirm knowledge docs are updated if the implementation establishes reusable stream lifecycle, alpha-surface, or browser validation conventions.
    - Confirm any newly discovered out-of-scope bugs are logged under `.internal-dev/bugs/`.

12. Write second-pass validation review.
    - Create:

      `.internal-dev/reviews/<date>-post-validation-correction-review.md`

    - The review must explicitly list each prior finding and state `closed` or `still open`.
    - Any still-open high or medium finding blocks completion.

## Validation

Second-pass validation passes only when all gates below pass.

### Assignment Gate

- Browser-shaped assignment payload without body `agentId` succeeds.
- Created assignment uses path `agentId`.
- Tests prove Spring MVC or DTO binding does not reject missing body `agentId`.

### Feature Flag Gate

- Disabled schedules are inert at runtime.
- Disabled reactions are inert at runtime.
- Enabled schedule/reaction behavior still works in tests when flags are explicitly enabled.
- Public disabled routes still return 404.

### SSE Gate

- Side-panel chat is asynchronous relative to the request thread.
- Side-panel chat uses shared lifecycle cleanup.
- Stream lifecycle tests exercise real completion, timeout, and error callbacks.
- Chat/task/workflow/side-panel terminal behaviors are covered by tests or browser validation.

### Wire Contract Gate

- `ChatStreamEvent` typed events have serialization tests.
- Browser-consumed fields remain present and correctly named.
- Browser validation parses actual SSE payloads successfully.

### Command Gate

- Targeted Maven tests pass.
- `mvn test` passes.
- Startup smoke reaches healthy Tomcat startup with isolated SQLite.
- Playwright MCP or accepted fallback browser validation passes.

### Evidence Gate

- Work log corrected.
- Implementation changelog written.
- Second-pass validation review written.
- Knowledge docs updated when relevant.
- Bugs/notes created for any out-of-scope discoveries or deferred ideas.

## Exit Criteria

- Every high and medium finding from `2026-05-08-non-security-alpha-remediation-validation-review.md` is closed.
- No corrective fix relies only on direct controller unit tests where MVC validation or browser behavior is the risk.
- The original non-security alpha validation criteria can be re-run without the previously identified blockers.
- Automated tests, startup smoke, and browser validation all have recorded evidence.
- `.internal-dev` artifacts are complete enough for a later reviewer to understand what failed, what changed, and how it was proven fixed.
