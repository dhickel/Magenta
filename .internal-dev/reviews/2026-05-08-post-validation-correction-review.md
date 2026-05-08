# Scope

Second-pass validation review for the correction implementation against:

- `.internal-dev/plans/readiness-fixes/post-validation-correction-validation-criteria.md`
- `.internal-dev/plans/readiness-fixes/post-validation-correction-plan.md`
- `.internal-dev/reviews/2026-05-08-non-security-alpha-remediation-validation-review.md`

This review inspected the changed production code, changed tests, work-log/changelog/knowledge evidence, and ran the required automated and startup validation. Playwright MCP was attempted first; it was blocked for the live test origin, so a Chrome DevTools Protocol browser-origin fallback was run against the same live app.

# Findings

## High: Side-panel chat still fails the browser-visible SSE gate

Status: still open.

`AgentOrchestrationController.chat` now uses `Flux.defer(...)`, `Schedulers.boundedElastic()`, and `SubscriptionGuard`, so the servlet method itself returns an `SseEmitter`. The browser-visible stream still does not satisfy the validation criteria because it does not send `start` until after `chatService.chat(...)` completes. The `start` payload is only built before the blocking call; it is emitted later in the subscriber together with `done`.

Code evidence:

- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java:185` starts the deferred work.
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java:196` builds the `start` payload.
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java:200` calls blocking `chatService.chat(...)`.
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java:218` sends `start`, after the blocking call has returned.

Live fallback evidence:

- Browser-origin fetch to `/api/agents/{agentId}/chat/stream` timed out after 7 seconds, then again after 30 seconds, without receiving an SSE event.
- The criteria require side-panel stream to return promptly and emit valid `start` plus terminal `done` or `error`.

What went wrong in the correction:

The edit fixed the controller-thread blocking shape but not the wire-level lifecycle. The test `agentChatStreamReturnsBeforeChatServiceCompletes` asserts that the Java method returns while `chatService.chat` is blocked, but it does not initialize the emitter or prove that a browser receives headers or `start` before model work finishes.

Required correction:

Send the `start` event before entering `chatService.chat(...)`, or split the stream into an immediate `start` event followed by bounded-elastic model work and terminal `done`/`error`. Add an initialized-emitter test that captures event timing and proves `start` is written while `chatService.chat` is still blocked.

## Medium: Browser command gate remains incomplete for task and workflow streams

Status: still open as validation evidence.

The fallback browser probe validated `/chat`, chat SSE, assignment creation, and disabled route 404s. It could not validate task or workflow terminal stream behavior:

- `/api/tasks/{taskId}/runs/stream` timed out after 10 seconds, then after 30 seconds.
- `/api/workflows/{workflowId}/runs/stream` with a two-step controlled fixture also timed out after 30 seconds.
- The server logged `reactor.core.publisher.Operators : Operator called default onErrorDropped` with `AsyncRequestNotUsableException` / broken pipe after the aborted task stream, originating from `TaskController` SSE send handling at `src/main/java/io/mindspice/magenta2/api/web/TaskController.java:205`.
- Shutdown also surfaced an uncaught bounded-elastic worker error, `IllegalStateException: ResponseBodyEmitter has already completed`, from the workflow SSE error path at `WorkflowController.java:128`.

This may be partly model/runtime latency, but the second-pass criteria require a passing browser or accepted fallback validation for task and workflow streams. That evidence is not present.

Required correction:

Use deterministic live fixtures that do not depend on slow model completion, or increase the browser probe only if the app also emits prompt `started` events. For client aborts, avoid throwing from the `onNext` path into Reactor's dropped-error handler; dispose the guard and complete the emitter cleanly when `SseEmitter.send` fails.

## Medium: Evidence gate is not complete

Status: still open.

The implementation changelog exists, but it still lists browser validation and re-validation review as follow-up items:

- `.internal-dev/changelogs/2026-05-08-non-security-alpha-remediation-correction.md:42`
- `.internal-dev/changelogs/2026-05-08-non-security-alpha-remediation-correction.md:44`
- `.internal-dev/changelogs/2026-05-08-non-security-alpha-remediation-correction.md:45`

The work log also marks evidence closeout as fixed while the browser gate was not complete before this review. This review now supplies the re-validation artifact, but the work log/changelog evidence still needs to be reconciled after the side-panel and browser-stream blockers are fixed.

Required correction:

After the remaining code fixes, update the work log with actual browser evidence, update the correction changelog to remove stale follow-up language, and add a closeout row that references the final second-pass review.

## Low: Runtime flag tests do not assert every required persistence side effect

Status: mostly closed, test coverage gap remains.

Code inspection confirms the runtime gates are implemented:

- `ScheduleService` injects `magenta.features.schedules-enabled:false` and returns early from `pollDueSchedules`.
- `OrchestrationEventService` injects `magenta.features.reactions-enabled:false` and marks disabled events handled without reading reactions or enqueuing assignments.

The tests cover no assignment creation and no schedule `nextRunAt` advance, but they do not directly assert no `schedule_firings` row was created or that disabled reaction events have non-null `handledAt`. The code satisfies the behavior, but the second-pass criteria asked for those exact validation points.

Required correction:

Extend `OrchestrationDurableRuntimeTest` with direct assertions for the persisted schedule firing count and disabled-event `handledAt`.

# Risk Assessment

The assignment validation regression is fixed in code and confirmed by a browser-origin payload without body `agentId`.

The feature flag runtime behavior is fixed in code and largely covered by tests, with minor missing persistence assertions.

The remaining risk is concentrated in live SSE behavior. The side-panel endpoint has the right high-level asynchronous wrapper but still delays the first SSE event until model work completes, so users can see a hanging browser request and cancellation is not proven effective for the blocking model call. Task/workflow browser evidence is also incomplete because the live fallback streams timed out and produced a server-side broken-pipe error after client abort.

# Recommendations

- Fix side-panel SSE to flush `start` before model execution and add an initialized-emitter timing test.
- Add a browser/fallback fixture that proves side-panel, task, and workflow streams emit prompt `started`/`start` and terminal events.
- Harden SSE send failure handling so client disconnects do not become `onErrorDropped` server errors.
- Add exact persistence assertions for disabled schedule firings and disabled reaction `handledAt`.
- Reconcile work-log/changelog evidence after the final browser pass.

# Follow-ups

Validation commands run:

```bash
mvn test -Dtest=AgentOrchestrationControllerTest,OrchestrationDurableRuntimeTest
mvn test -Dtest=SseStreamLifecycleTest,ChatControllerTest,TaskControllerTest,WorkflowControllerTest
mvn test -Dtest=ChatStreamEventSerializationTest
mvn test
rm -f /tmp/magenta2-post-validation-correction-smoke.sqlite
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-post-validation-correction-smoke.sqlite'
```

Results:

- Targeted assignment/feature-flag tests: 23 passed, 0 failed.
- Targeted SSE/controller tests: 63 passed, 0 failed.
- Targeted `ChatStreamEventSerializationTest`: 8 passed, 0 failed.
- Full suite: 306 passed, 0 failed, 0 skipped.
- Startup smoke: Tomcat started on port 43565 with isolated SQLite; timeout exit 124 occurred after healthy startup and graceful shutdown.

Browser validation:

- Playwright MCP attempt: blocked with `net::ERR_BLOCKED_BY_CLIENT` for `http://localhost:18081/chat`.
- Fallback: Chrome DevTools Protocol against live app on `http://localhost:18081`.
- Passed fallback checks: `/chat` DOM loaded, chat SSE emitted `start -> context -> chunk -> done`, assignment UI/API-shaped payload without body `agentId` returned 200 and used the path agent id, disabled schedules and reactions routes returned 404.
- Failed/incomplete fallback checks: side-panel chat timed out after 30 seconds with no received event, task stream timed out after 30 seconds, workflow stream timed out after 30 seconds.

Prior finding inventory:

| Finding | Result |
|---|---|
| Agent assignment request validation | closed |
| Schedule runtime feature flag | closed with minor test gap |
| Reaction runtime feature flag | closed with minor test gap |
| Side-panel SSE lifecycle | still open |
| Stream lifecycle coverage | partially closed; live abort handling remains open |
| `ChatStreamEvent` serialization | closed |
| Browser validation evidence | still open |
| Work-log/changelog/knowledge evidence | still open |
