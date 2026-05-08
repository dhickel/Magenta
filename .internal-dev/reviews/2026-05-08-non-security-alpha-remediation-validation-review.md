# Scope

Validation review of the implemented `readiness-fixes` branch against:

- `.internal-dev/plans/readiness-fixes/non-security-alpha-remediation-plan.md`
- `.internal-dev/plans/readiness-fixes/non-security-alpha-validation-criteria.md`
- `.internal-dev/plans/readiness-fixes/work-log.md`

Reviewed production code, tests, and `.internal-dev` evidence for the non-security remediation groups. Security-class findings from the readiness/security review remain out of scope.

# Findings

## High: Agent assignment submission is broken by request-body validation

`AssignmentRequest.agentId` is annotated `@NotBlank` (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentRequest.java:8-12`), and `AgentOrchestrationController.assign` validates the request body before replacing the body agent id with the path variable (`src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java:96-102`).

The browser assignment form posts to `/api/agents/{agentId}/assignments` without an `agentId` field because the agent id is already in the URL (`src/main/resources/static/js/orchestration/app.js:167-174`). In a real Spring MVC request, Bean Validation runs before the controller method body, so this browser request is rejected with a 400 before the controller can apply the path `agentId`.

This means the public request validation remediation regressed an alpha-facing assignment workflow. The current unit tests do not catch it because they call the controller method directly with a populated `AssignmentRequest` (`src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java:127-141`), bypassing MVC validation and the actual browser payload shape.

## High: Schedule/reaction feature flags hide the API/UI but not runtime execution

The alpha decision says schedules and event reactions are hidden behind feature flags, but the runtime services still execute persisted enabled data regardless of those flags.

`ScheduleService.pollDueSchedules` is always scheduled and processes due schedules into events and assignments (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java:62-89`). `OrchestrationEventService.handle` always loads enabled reactions and creates assignments from matching templates (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationEventService.java:31-38`). The flags are checked only in `AgentOrchestrationController` request handlers (`src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java:123-157`).

This does not fully satisfy the validation gate for hiding alpha surfaces. Existing schedules/reactions in an upgraded database, test fixture, or manually inserted data can still run while the feature is supposedly disabled.

## Medium: Orchestration side-panel SSE still has divergent lifecycle behavior

The remediation plan required consistent streaming/SSE lifecycle handling across chat, task, workflow, and the orchestration side-panel stream. The side-panel endpoint still performs synchronous model work inside the request handler:

- It creates an emitter at `AgentOrchestrationController.java:165`.
- It calls `chatService.chat(...)` synchronously at `AgentOrchestrationController.java:178-180`.
- It sends only `start`, `done`, or `error` events and returns after the chat call finishes (`AgentOrchestrationController.java:172-206`).

It does not use `SubscriptionGuard`, does not offload model work, and cannot react consistently to client disconnect or cancellation. That leaves one of the four stream classes outside the lifecycle standardization claimed in the work log.

## Medium: Stream validation coverage does not meet the criteria

The validation criteria require stream tests for terminal outcomes including completed, client disconnected, timeout, user cancelled, model/tool failure, validation failure, and internal error for each touched stream class.

Current tests cover some happy paths, error paths, and emitter timeout values, but they do not exercise client disconnect or user cancellation. `SseStreamLifecycleTest` does not actually trigger `SseEmitter` completion, timeout, or error callbacks; it calls `guard.dispose()` directly and asserts handlers were not called in timeout/error cases (`src/test/java/io/mindspice/magenta2/api/web/SseStreamLifecycleTest.java:110-156`). The side-panel stream tests only assert non-null emitters and timeout value (`src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java:33-68`, `:143-173`).

The code may pass the existing tests, but the test suite does not prove the lifecycle outcomes required by the validation document.

## Medium: Chat stream wire-shape change lacks serialization or browser validation

`ChatStreamEvent` was changed from a nullable union record to a sealed hierarchy of typed records (`src/main/java/io/mindspice/magenta2/ai/chat/model/ChatStreamEvent.java:7-60`). This changes JSON serialization shape for some events by omitting fields that used to serialize as nullable fields.

The validation criteria require serialization tests and browser/client validation when the stream wire shape changes. The work log states "No browser changes needed" and "All 288 tests pass," but there are no focused `ChatStreamEvent` serialization tests and no recorded Playwright/fallback browser validation for this remediation.

## Medium: Required validation evidence is incomplete

This review run confirmed:

- `mvn test` passes: 292 tests, 0 failures, 0 errors, 0 skipped.
- Bounded startup smoke reached healthy Tomcat startup on port `35339` against `/tmp/magenta2-alpha-non-security-validation-review.sqlite`; timeout exit 124 was expected after startup.

However, the implementation evidence is still missing criteria-required artifacts:

- No implementation changelog for the remediation branch exists under `.internal-dev/changelogs/`; the only non-security changelogs document plan/criteria creation.
- No prior validation review artifact existed under `.internal-dev/reviews/` before this review.
- No Playwright MCP validation or documented fallback browser probe is recorded for the live chat/SSE/browser changes.

## Low: Work log has stale and malformed status entries

The work-log summary table marks issue 2.2 as `pending` (`.internal-dev/plans/readiness-fixes/work-log.md:14`) even though the later detailed report says it was implemented. The table also resumes with rows for 5.3 and 5.4 inside the issue 5.2 narrative (`.internal-dev/plans/readiness-fixes/work-log.md:55-60`). This makes the produced evidence harder to trust during validation.

# Risk Assessment

Many remediation groups appear properly implemented and tested: runtime settings wiring, shell cleanup, duplicate queued assignment submission, schema consolidation, foreign-key/cascade behavior, audit sequence uniqueness, package ownership moves, and most boundary cleanup have concrete code and focused tests.

The remaining risks are concentrated in alpha-facing behavior rather than compilation. The assignment form regression is a direct user-facing break. The schedule/reaction flags do not fully disable runtime behavior for persisted data. Stream lifecycle and wire-contract changes are under-validated relative to the criteria, especially because browser validation is missing.

# Recommendations

1. Fix assignment request validation by using an agent-assignment request DTO that does not require body `agentId`, or update the browser payload and add a Spring MVC/MockMvc test for the real `/api/agents/{agentId}/assignments` request shape.
2. Apply schedule/reaction feature flags at the service/runtime execution layer, not only controller/UI routes. Add tests proving disabled schedules do not fire and disabled reactions do not enqueue assignments even when rows already exist.
3. Bring side-panel chat into the same lifecycle model as the other stream endpoints or explicitly downgrade it from SSE streaming to a normal non-streaming endpoint and update the plan/validation criteria accordingly.
4. Add lifecycle tests that actually exercise completion, timeout, error, disconnect/cancellation cleanup, and side-panel behavior through either MVC async support, emitter handler instrumentation, or a live browser/API probe.
5. Add `ChatStreamEvent` serialization tests and run Playwright MCP or a documented fallback browser probe against `/chat`, task stream, workflow stream, and side-panel stream behavior.
6. Add the missing implementation changelog and clean up the work-log status table.

# Follow-ups

- Re-run `mvn test` after fixes.
- Re-run bounded startup smoke after fixes.
- Run Playwright MCP or an accepted fallback browser probe because this branch changed live chat/SSE/browser-facing behavior.
- Update this review or create a follow-up validation review once the blocking findings are addressed.
