# Saved Plan Chat Model Workflow

## Global Assumptions
- Saved `/plans` planning chat should collect opening answers in the UI, then send them to a model-backed planning turn instead of directly copying answers into draft fields.
- Anonymous in-chat planning should be reviewed for consistency but not rewritten unless needed.
- Manual editor saves should continue appending saved-plan chat context so the model sees user-side plan edits.
- Existing unrelated worktree changes must be preserved.

## Active Agents
- planner: completed implementation plan and risk analysis.
- implementation-worker: completed saved-plan model-backed planning workflow implementation.

## Completed Work
- Created branch `saved-plan-chat-model-workflow`.
- Confirmed current saved-plan chat opening answers are directly persisted by `SavedPlanChatService.applyOpeningAnswer`.
- Planner confirmed manual editor saves already append saved-plan chat context via `appendEditorSaveContext`.
- Planning pass inspected only relevant saved-plan chat, plan/task service, prompt/tool, controller, docs, and focused tests.
- Confirmed manual editor saves already call `SavedPlanChatService.appendEditorSaveContext(...)` from `OrchestrationController.updatePlanEditor(...)` and append a `system` chat message when plan-scoped chat history exists.
- Confirmed existing `TASK` tool path expects a draft conversation id through `PlanService.requireTaskDraft(...)`; saved `/plans` chat drafts are stored by plan id and `PlanService.saveTask(...)` clears `conversationId`, so direct reuse of current `TaskTools` by setting context to plan id will not mutate the saved draft without additional saved-plan-specific support.
- Integrated implementation worker changes.
- Added prompt transcript context so manual editor save notices are included in subsequent saved-plan model turns.
- Updated saved-plan chat docs in `docs/end-user` and `docs/technical`.
- Extended editor save context to include section/list edits and record changes in saved-plan chat history.
- Added `.internal-dev` archived plan, changelog, and knowledge artifacts.

## Validation Results
- `mvn -Dtest=SavedPlanChatServiceTest test` passed locally after integration.
- `mvn -Dtest=SavedPlanChatServiceTest test` passed: 8 tests, 0 failures.
- `mvn -Dtest='io.mindspice.magenta2.ai.chat.plan.*Test' test` passed: 65 tests, 0 failures.
- `mvn -Dtest=SavedPlanChatServiceTest,OrchestrationControllerTest test` passed: 98 tests, 0 failures.
- `mvn test` passed before readiness fix: 588 tests, 0 failures.
- `mvn -Dtest=SavedPlanChatServiceTest test` passed after readiness fix: 9 tests, 0 failures.
- `mvn test` passed after readiness fix: 589 tests, 0 failures.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` started successfully after readiness fix and exited via timeout after graceful shutdown.
- Playwright validation loaded `/plans`, created a saved plan chat, captured screenshots, and validated HTMX wiring plus first three opening answers. Fourth answer reached the real Ollama model call and remained pending beyond the Playwright timeout.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` started the Spring Boot context successfully on an ephemeral port, then exited via timeout with code 124 after graceful shutdown.
- 2026-05-20 focused Playwright `/plans` validation (`http://localhost:18080`, SQLite `/tmp/magenta2-saved-plan-chat-validation.sqlite`):
  - Opened `/plans`, created saved plan chat `Validation Plan Chat`, confirmed `Planning Chat` tab renders without layout breakage.
  - Submitted first three opening answers via HTMX and received `200` on `POST /plans/_editor/{planId}/planning-chat/answers`.
  - Fourth answer submission remained in-flight (`POST .../planning-chat/answers` pending; no terminal response before Playwright 120s timeout), consistent with handoff to a real model turn.
  - Console was clean (0 errors/warnings). Planning chat form remained HTMX transport (`hx-post` to `/planning-chat/answers`, target `#plan-editor-container`, no form `onsubmit`).
  - Artifacts: `.codex-orchestration/saved-plan-chat-model-workflow/artifacts/`.

## Remediation Notes
- Integration review found model turns were not seeing transcript/system editor notices; patched user-message construction to include recent saved-plan chat transcript.
- Closeout review found deliverable-only saved plans could not be marked ready despite prompt wording; added saved-plan-specific completion validation that accepts named outputs or deliverables.
- Replace direct opening-answer parsing with seed-context transcript capture, then run a model-backed saved-plan planning turn.
- Add a saved-plan/task planning system prompt that is plan-id scoped and separate from `/api/chat` memory/session metadata.
- Provide saved-plan planning tools or saved-plan-aware draft mutation methods so model tool calls update the `TASK_TEMPLATE` by plan id, not by `/api/chat` conversation id.
- Use the opening answers as the first user message to the model, instructing it to synthesize goal, deliverables, typed inputs, typed outputs, assumptions, steps, and validation criteria, and to continue questioning until the saved plan is ready.

## Blockers
- Full browser validation of fourth-answer terminal behavior is blocked by real model-turn completion timing in the local Ollama call path.

## Closeout Work
- Updated docs for saved-plan chat behavior.
- Added `.internal-dev` changelog, knowledge, and archived plan artifacts.
- Commit implementation plus docs and `.internal-dev` updates after validation.

## Final Validation Status
- Backend/unit/full Maven validation passed. Browser validation is a partial pass with the fourth-answer model turn blocked on local model latency.

## Handoff Notes
- Keep code-editing work serial. Non-mutating review and test-design work can run in parallel.
- Implementation now stores the four saved-plan opening answers only in `plan_chat_messages`, sends labeled seed context to a saved-plan model turn after answer four, and enforces terminal state with either queued pending questions or ready-for-approval.
- Added plan-id scoped `saved_plan_*` tools and `PlanService` saved-task mutation methods so saved `/plans` chat does not depend on `/api/chat` conversation ids, `ai_chat_memory`, or chat session metadata.
- No docs or `.internal-dev` files were edited per task scope; no commit was created.
