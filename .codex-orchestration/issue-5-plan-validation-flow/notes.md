# Issue 5 Plan Validation Flow Notes

## Global Assumptions
- Primary target is GitHub issue #5 in `dhickel/Magenta`.
- Related issue #4 is in scope only where needed to stop `NEEDS_REVIEW` from reopening planning UI.
- Related issue #3 is a constraint for durable observability, but broad malformed tool-call repair is out of scope unless the code path requires it.
- Implementation agents use `gpt-5.5` with medium reasoning. Planning and review agents use `gpt-5.5` with high reasoning.

## Active Agents
- Orchestrator: main Codex thread.

## Completed Work
- Created branch `issue-5-plan-validation-flow`.

## Validation Results
- `./mvnw -q -Dtest=PlanServiceTest,ChatServiceTest,PlanSaveToolsTest test` could not run because this checkout has no `mvnw`.
- `mvn -q -Dtest=PlanServiceTest,ChatServiceTest,PlanSaveToolsTest test` passed on 2026-05-20.

## Remediation Notes
- Implemented phase 01/02 code and focused tests: `NEEDS_REVIEW` session plans now resolve to `NORMAL` mode and receive no PLAN/EXECUTE runtime instructions.
- Saved-plan execution now only completes through validator-approved `plan_complete`; still-executing plans after exhausted execution are marked `NEEDS_REVIEW` and persist/return a controlled review message.
- Validator feedback now includes durable pass/fail status and per-criterion remediation while retaining old `findings`/`remediationSteps` parsing.
- Chat UI hides planning controls for `NEEDS_REVIEW` and labels the state as validation/review feedback.

## Blockers
- None yet.

## Closeout Work
- Need `.internal-dev` plan/changelog/knowledge/notes as applicable.
- Need docs updates if behavior/API/UI changes.
- Need final commit including implementation and `.internal-dev` updates, staging only intended files.

## Final Validation Status
- Not started.

## Handoff Notes
- Preserve unrelated pre-existing worktree changes.

## Planning/Review Findings
- `plan_complete` validation remains tool-loop internal only while `PlanStatus` stays `EXECUTING`; once retries exhaust, stream/non-stream finalization can still mark an ordinary assistant response `COMPLETED`, so execution completion needs an explicit validated-vs-review result boundary.
- `NEEDS_REVIEW` currently falls through to `PLAN` mode for session plans, reopening generic planning UI and `Cancel planning`; handle it as an execution-review state or `NORMAL` plus review metadata.
- Persisted tool transcript reconstruction uses `ToolTranscriptService` entries in `ai_chat_memory`; reload should preserve tool cards, but stream reload can replace live cards with shorter persisted summaries/truncated details and does not preserve malformed pre-tool-call attempts.
- Validation feedback/evidence is already exposed through `ChatPlanState`, but UI copy/actions should distinguish validation failure/review from draft planning and avoid generic continuation prompts.

## Completed Work
- Added phase plan artifacts under `.internal-dev/plans/issue-5-plan-validation-flow/`.

## Validation Results
- `mvn -q -Dtest=PlanServiceTest,ChatServiceTest,PlanSaveToolsTest test` passed after orchestrator cleanup.

## Remediation Notes
- Adjusted non-stream saved-plan execution so `final-message.md` is persisted only for `PlanStatus.COMPLETED`, not `NEEDS_REVIEW`.

## Remediation Notes
- Closed review finding: validator completion now fails closed unless final message exists, artifacts are readable, and validator criteria cover/pass every deliverable and validation criterion.
- Closed review finding: streaming validator-passed completion now persists the final-message artifact.

## Validation Results
- `mvn -q -Dtest=PlanServiceTest,ChatServiceTest,PlanSaveToolsTest test` passed after fail-closed remediation.
- Playwright MCP validation passed for mocked `NEEDS_REVIEW` chat UI at `http://localhost:18080/chat`; screenshots in `.playwright-mcp/`.

## Validation Results
- `mvn -q test` passed.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached `Started Magenta2Application` on port 42863 before timeout-triggered graceful shutdown.

## Final Validation Status
- Backend focused tests: passed.
- Full Maven tests: passed.
- Spring Boot startup smoke: passed.
- Focused Playwright UI validation: passed with screenshots in `.playwright-mcp/`.

## Closeout Work
- Added changelog `.internal-dev/changelogs/2026-05-20-plan-validation-review-flow.md`.
- Added knowledge note `.internal-dev/knowledge/plan-validation-review-flow.md`.
- Added review artifact `.internal-dev/reviews/2026-05-20-plan-validation-review-flow-review.md`.
- Archived phase plan artifacts to `.internal-dev/plans/.archive/issue-5-plan-validation-flow/`.
- Updated docs under `docs/end-user`, `docs/technical`, and `docs/api`.

## Remediation Notes
- Closed final review finding: validator JSON schema now fails closed when required top-level keys or criterion object keys are missing; added regression coverage for incomplete `complete=true` validator output.

## Validation Results
- `mvn -q -Dtest=PlanServiceTest,ChatServiceTest,PlanSaveToolsTest test` passed after schema strictness remediation.

## Validation Results
- Final `mvn -q test` passed after validator schema strictness remediation.
- Final `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached `Started Magenta2Application` on port 34365 before timeout-triggered graceful shutdown.

## Final Validation Status
- All required validation gates passed.

## Handoff Notes
- Pre-existing unrelated worktree changes remain unstaged.

## Validation Results
- Final rerun after strict validator schema fix: `mvn -q test` passed.
- Final rerun after strict validator schema fix: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached `Started Magenta2Application` on port 34365 before timeout-triggered graceful shutdown.
