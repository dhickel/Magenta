# In-Chat Planning Validation Notes

## Global Assumptions

- Validate the real `/chat` browser planning flow with Playwright against a live Spring Boot app and isolated SQLite database.
- Use the user-provided scenario as a research/report planning prompt, but validation should focus on Magenta planning behavior, console errors, backend errors, database state, file visibility, and validator behavior.
- Keep code-editing work serial. Browser/database validation agents are non-mutating unless explicitly handed a remediation subplan.
- Use model `gpt-5.3-codex` with medium reasoning for testing/validation agents per repo policy.

## Active Agents

- Gibbs (`019e4ebd-b624-7972-8287-7dafc145ba1f`): completed first browser/database reproduction pass.

## Completed Work

- Created dedicated branch `in-chat-planning-validation-remediation`.

## Validation Results

- First Playwright pass used isolated DB `/tmp/magenta2-in-chat-planning-validation.sqlite` and conversation `3da8c656-1d2b-44e9-9de5-1b3e5bb2ad2a`.
- `/plan` entered planning and accepted several answers, then `POST /api/chat/{conversationId}/plan/answers` returned 500 when a planning continuation invoked `web_search` and hit `java.net.ConnectException`.
- Repeated answer retries then returned `400` with `No active planning question exists for this conversation` because the answer had already been consumed.
- Focused regression test added for saved-answer continuation failures caused by model auth errors and tool failures.

## Remediation Notes

- Patched `ChatService.submitPlanAnswer(...)` so failures after a saved answer return a controlled assistant notice and current plan state instead of propagating a servlet error.
- Patched the saved-answer failure response to queue a recovery clarification when a continuation failure or thread interruption leaves a `DRAFT` plan with no pending question.
- Patched stale/no-active planning answer submissions in `PLAN` mode to return the current recoverable plan state instead of surfacing `400 No active planning question exists for this conversation`.
- Extended stale-answer recovery to stale question-index mismatches so the browser can resync to the current prompt instead of treating `400 Stale planning answer` as terminal.
- Patched anonymous completion validation so relative artifact paths are resolved against the chat file directory before falling back to `dataRoot`.
- Patched Spring AI tool argument conversion failures so they become tool-loop diagnostics and retry prompts rather than aborting planning continuation.
- Patched anonymous execution SSE disconnect handling so client transport breaks record `plan_stream_disconnect` instead of marking execution failed.
- Patched `ask_user_questions` normalization so object-shaped question entries from the model are accepted and converted to plain prompt text.

## Blockers

- Need another browser/DB validation pass to confirm execution can complete after transport disconnects are decoupled from domain execution failure.

## Closeout Work

- Required: changelog, docs if behavior changes, bug/knowledge/notes if discovered or deferred, focus update if unfinished work remains, per-phase commits, final email.

## Final Validation Status

- Pending.

## Handoff Notes

- Treat `/chat` anonymous planning as session-local and separate from saved `/plans`.
- Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before browser validation.

## Second Browser/DB Validation Pass (Non-Mutating)

- Date: 2026-05-22
- Branch: `in-chat-planning-validation-remediation`
- Runtime:
  - `mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --magenta.root.path=/tmp/magenta2-in-chat-planning-validation-root-2 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-in-chat-planning-validation-2.sqlite?foreign_keys=true --magenta.executor.chat-threads=4'`
- Conversation: `a2ecde7f-c6ec-49d8-8c66-3b433c490a4c`

Findings:
- First attempt used `/plan <arguments>` and correctly returned `400 BAD_REQUEST "plan does not accept arguments"` (expected with current command contract).
- Full browser flow rerun with `/plan` then answers posted through main composer.
- No 500 and no `No active planning question exists for this conversation` response occurred during answer submission.
- Planning accepted three answers (`goal`, `assumptions`, `deliverables`) but did not surface a new prompt or `READY_FOR_APPROVAL` actions afterward.
- DB state remained `plan_definitions.status=DRAFT`, `planning_task=define_deliverables`, `pending_question_index=0`, `pending_questions_json` empty; no `plan_chat_messages` were persisted.
- `audit_event` recorded multiple `web_search` tool calls with empty query text and `completed` status (`Searched web for `` and returned 0 results.`), then no further planning-state advancement.

## Third Browser/DB Validation Pass (Non-Mutating)

- Date: 2026-05-22
- Runtime used isolated DB `/tmp/magenta2-in-chat-planning-validation-3.sqlite` and root `/tmp/magenta2-in-chat-planning-validation-root-3`.
- Conversation: `36034618-10d8-4eaf-ab25-e433d2aad6d1`.

Findings:
- No browser console errors and no 500s were observed.
- A stale `POST /api/chat/{conversationId}/plan/answers` returned `400 No active planning question exists for this conversation`.
- DB inspection after the run showed the recovery clarification was present in `plan_definitions.pending_questions_json`, but the stale 400 kept the browser on the old error path.
- Added a server-side stale-answer recovery path so the next validation pass should receive a normal response with the refreshed plan state.

## Later Browser/DB Validation Passes (Non-Mutating)

- Subsequent validation showed the planning flow could recover from stale answer submissions and Spring AI tool argument conversion failures.
- A longer no-stale browser run reached `READY_FOR_APPROVAL`, approved the anonymous plan, and started streamed execution.
- Execution later moved to `NEEDS_REVIEW` because the SSE response broke with a `Broken pipe`; audit events showed `plan_execution` and `plan_stream_error` diagnostics tied to the stream failure rather than validator feedback.
- Remediation: treat browser stream disconnects as transport diagnostics (`plan_stream_disconnect`) and allow the underlying execution path to continue toward normal completion or validator-gated failure.

## Eighth Browser/DB Validation Pass (Non-Mutating)

- Date: 2026-05-22
- Branch head: `763789f`
- Runtime used isolated DB `/tmp/magenta2-in-chat-planning-validation-8.sqlite` and root `/tmp/magenta2-in-chat-planning-validation-root-8`.
- Conversation: `da6c802d-c245-42e1-a3c7-c8b3e0fbc83f`.

Findings:
- `/chat` and `/plan` loaded normally, and the validator answered the three live planning prompts without stale or malformed probes.
- No browser console, page, or network 4xx/5xx errors were captured.
- `READY_FOR_APPROVAL` was not reached. The DB stayed at `PLAN`/`DRAFT` with `planningTask=clarify_and_elaborate`.
- Audit events showed repeated `ask_user_questions` failures because the model sent `questions` as objects containing `header`, `question`, and `type`; Spring could not convert those objects into `List<String>`.
- Remediation: changed `ask_user_questions` to accept `List<Object>` and normalize plain strings or object-shaped question entries to user-visible question text.
