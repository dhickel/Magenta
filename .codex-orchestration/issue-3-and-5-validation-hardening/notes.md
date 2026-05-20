# Issue 3 And 5 Validation Hardening

## Global Assumptions
- Issue #3 implementation is the blocking first orchestration.
- Issue #5 validation hardening may be reviewed in parallel, but no issue #5 code edits begin until issue #3 passes focused validation.
- Overlapping plan executions are rejected, not attached.
- Malformed tool-call JSON is reported back to the model with context; existing loop safeguards handle repeated failures.
- Recovered malformed tool-call diagnostics must not pollute plan status or validation feedback.

## Active Agents
- Einstein: completed read-only issue #3 advanced plan.
- Erdos: completed read-only issue #5 validator-path review.

## Completed Work
- Created combined branch `issue-3-execution-and-validation-hardening`.
- Implementation Agent A completed issue #3 only:
  - Added conversation-scoped anonymous plan execution guard in `ActiveTurnRegistry`.
  - Wired `/api/chat/{conversationId}/plan/execute` and `/api/chat/{conversationId}/plan/execute/stream` to reject overlapping executions with HTTP 409 before plan execution side effects.
  - Added tool-call argument JSON preflight in `ChatService` before `ToolCallingManager.executeToolCalls(...)`.
  - Added synthetic malformed-argument tool diagnostics, audit recording, stream/history tool activity, and a system control message for model retry.
  - Updated focused tests and docs for the new behavior.
- Implementation Agent B completed issue #5 validator hardening:
  - Added a local `PlanCompletionValidator` boundary and default `ChatModelPlanCompletionValidator` wrapper for the raw validator model call.
  - Hardened validator prompts and user input sections to treat the approved plan, evidence, artifact contents, prior feedback, and final message as untrusted data.
  - Carried forward `Artifact: ...` paths previously recorded by `plan_report`, deduped them with current `plan_complete.artifactPaths`, and read all selected artifacts for validation.
  - Recorded validation feedback with the validator model used, unavailable validator model state, or a deterministic preflight skip reason.
  - Removed executor-model fallback from completion validation; validation now fails closed when no planning validator model resolves.
  - Added focused tests for exact validator request shape, artifact carry-forward, preflight model skip, and missing planning validator model behavior.
  - Updated end-user and technical docs plus `.internal-dev` changelog and knowledge artifacts.

## Validation Results
- 2026-05-20 Implementation Agent A: `mvn -q -Dtest=ActiveTurnRegistryTest,ChatControllerTest,ChatServiceTest,AuditRepositoryTest,ToolLoopGuardTest test` passed.
- 2026-05-20 Implementation Agent A: `timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0` started successfully on a random port before the timeout terminated it with code 124.
- 2026-05-20 Implementation Agent B: `mvn -q -Dtest=PlanServiceTest test` passed.
- 2026-05-20 Implementation Agent B: `mvn -q -Dtest=PlanServiceTest,PlanSaveToolsTest,ChatServiceTest test` passed.
- 2026-05-20 Implementation Agent B: `timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0` started successfully on port 37717 before the timeout terminated it with code 124.

## Remediation Notes
- Issue #3 recovered malformed tool-call argument errors now stay inside the tool loop and do not call plan execution failure reporting.

## Blockers
- None currently.

## Implementation Agent B Changed Files
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionValidator.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/ChatModelPlanCompletionValidator.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `docs/technical/chat-planning-tasks.md`
- `docs/end-user/plans-and-tasks.md`
- `.internal-dev/changelogs/2026-05-20-plan-completion-validator-hardening.md`
- `.internal-dev/knowledge/plan-completion-validator-boundary.md`

## Closeout Work
- Add `.internal-dev` plan/changelog/knowledge/review artifacts.
- Update relevant `docs/`.
- Commit only task-related files.
- Push branch and close covered GitHub issues after validation and user-directed closeout.

## Final Validation Status
- Passed with recorded residual risk: focused Playwright validation did not run a complete successful live `plan_complete` happy path; automated tests cover validator behavior.

## Final Validation Pass (2026-05-20, independent)
- `mvn -q test` passed.
- `timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0` passed startup smoke (app started, then timeout terminated as expected).
- Focused Playwright MCP validation run against `http://localhost:18080` with isolated DB `jdbc:sqlite:/tmp/magenta2-issue3-5-validation.sqlite`:
  - Chat surface loaded (`Magenta Chat`) and required DOM anchors were present.
  - SSE smoke on `/api/chat/stream` produced `start -> context -> chunk -> done`.
  - Planning endpoints were exercised in focused negative/guard paths:
    - `/api/chat/{conversationId}/plan/answers` returned expected 400 for invalid/incomplete answer payload in draft flow.
    - `/api/chat/{conversationId}/plan/approve` returned expected 400 when required plan fields were not yet satisfied.
    - `/api/chat/{conversationId}/plan/execute/stream` returned 400 when preconditions were not met; overlapping execute attempts included one 409 conflict response, consistent with single-flight guard behavior.
- Screenshots captured:
  - `artifacts/validation/issue3-5-chat-page.png`
  - `artifacts/validation/issue3-5-chat-plan-mode.png`
- Console log artifact:
  - `.playwright-mcp/console-2026-05-20T17-01-34-544Z.log` (contains expected 400/409 request errors from intentional negative-path validation).

## Final Validation Outcome
- No regressions observed against issue #3/#5 acceptance criteria in code inspection + automated tests + focused live endpoint/browser checks.
- Remaining residual risk: this focused browser pass did not fully complete an end-to-end `plan_complete` happy path in MCP because the live plan remained in early draft/approval gating during negative-path validation; validator-path behavior is otherwise strongly covered by passing focused tests (`PlanServiceTest`, `ChatServiceTest`) included in the full test suite.

## Handoff Notes
- Preserve unrelated dirty files already present in the worktree.
