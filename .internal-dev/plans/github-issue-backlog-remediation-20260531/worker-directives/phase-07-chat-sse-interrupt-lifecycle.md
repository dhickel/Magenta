# Phase 07 Worker Directive: Chat SSE Cleanup And Interrupt Contract (#14, #15)

## Objective

Remediate GitHub issues #14 and #15 as one coherent chat/SSE active-turn lifecycle fix.

## User-Visible Outcome

Streaming chat/plan execution terminal paths clear active execution state reliably, and advertised interrupt metadata is truthful for plain, tool, and tool-fallback streaming turns.

## Issues

- #14 `SSE: Error callbacks can leave active chat/plan execution state registered`
- #15 `Chat: Plain streaming advertises interrupt token but cannot accept interrupts`

## Direct Targets

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/SseStreamLifecycle.java` only if lifecycle helper needs adjustment
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/execution/ActiveTurnRegistry.java`
- `src/main/java/io/mindspice/magenta2/ai/execution/ActiveTurnPhase.java`
- Tests:
  - `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
  - `src/test/java/io/mindspice/magenta2/api/web/SseStreamLifecycleTest.java`
  - `src/test/java/io/mindspice/magenta2/ai/execution/ActiveTurnRegistryTest.java`
  - `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- Docs/specs:
  - `.internal-dev/specifications/api.md`
  - `.internal-dev/specifications/web.md`
  - `.internal-dev/specifications/architecture.md` if active-turn contract changes
  - `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` if validation learns a new gotcha
  - `.internal-dev/changelogs/2026-05-31-chat-sse-interrupt-lifecycle.md`

## Forbidden Scope

- Do not rewrite the chat client unless browser validation proves a client bug.
- Do not remove interrupt endpoints.
- Do not convert chat streaming architecture to a new reactive model.

## Supporting Docs To Read

- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/web.md`

## Reproduction Probes Required Before Fix

Add focused tests or harnesses for:

- `ChatController.streamResolved` `onError` callback invokes domain cleanup and removes active turn/plan execution registration.
- Plain stream start payload and interrupt endpoint behavior align: either interrupt is accepted during the model call, or the start payload does not advertise an actionable interrupt token/capability.
- Tool path still accepts interrupts at model/tool phases.
- Tool-unsupported fallback to plain streaming follows the same contract as normal plain streaming.

## Implementation Steps

1. Add lifecycle/interrupt tests first.
2. Make `emitter.onError` run cleanup consistently without double-finalizing plan execution.
3. Align plain-stream phase behavior with advertised metadata. Recommended path: set active turn phase to `MODEL_CALL` for plain streaming before blocking model call and ensure interrupt endpoint returns the intended result.
4. If token remains advertised but interrupt cannot affect blocking plain calls, update API payload semantics and browser/client display to avoid false affordance; prefer this only if actionable interrupt is not feasible.
5. Keep active-turn cleanup idempotent.
6. Update specs/knowledge/changelog.

## Senior-Engineer Guidance

- Cleanup must be safe to call from completion, timeout, error, send failure, and subscription terminal paths.
- Avoid double-recording plan failure on client disconnect unless the stream truly failed domain execution.
- Interrupt acceptance is not enough if it is never observed; tests should verify documented outcome.

## Acceptance Criteria

- Active turns and active plan execution registrations are removed on error/disconnect terminal paths.
- Plain, tool, and tool-fallback streaming interrupt behavior matches the API payload.
- Existing plan execution failure handling remains intact.
- Browser validation covers at least one plain chat stream and interrupt/cleanup observable behavior if route behavior changed.

## Negative Checks

- No stuck active stream locks after disconnect/error.
- No duplicate final plan execution records.
- No misleading interrupt affordance in browser/API.

## Validation Commands

- `mvn -q -Dtest=ChatControllerTest,SseStreamLifecycleTest,ActiveTurnRegistryTest,ChatServiceTest test`
- Bounded startup.
- Separate Playwright/browser validation agent if stream/browser behavior changes; use `live-chat-mcp-workflow-testing.md`.

## Browser Checklist If Applicable

- Load `/chat` on an allowed port with isolated SQLite.
- Start a plain stream, inspect `start` payload, call interrupt endpoint with token, and record response.
- Force/abort a stream to verify no stale active-turn conflict on the next turn.
- Capture console/network output.

## Evidence Expectations

- Validator report: `.internal-dev/plans/github-issue-backlog-remediation-20260531/validation/phase-07-validation-report.md`
- Browser artifacts under `artifacts/github-issue-backlog-remediation-20260531/phase-07-browser/` if run.

## Closeout Expectations

Main thread closes #14 and #15 together after validation, commit, push, and email.

## Stop Conditions

- Stop if making plain interrupt truly actionable requires cancelling model-provider calls beyond current architecture.

## Do Not Close Unless

- Both cleanup and interrupt-contract tests pass.
- Browser proof is reconciled when browser/API streaming semantics changed.
