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
- Pending.

## Handoff Notes
- Preserve unrelated dirty files already present in the worktree.
