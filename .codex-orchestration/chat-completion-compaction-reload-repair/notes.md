# Chat Completion Compaction Reload Repair Notes

## Global Assumptions

- User requested execution of `.internal-dev/plans/chat-completion-compaction-reload-repair/phase-01-orchestrated-diagnosis-and-fix.md`.
- Delegated agents should use `gpt-5.5` with high reasoning.
- Code-modifying work is serialized through the main thread unless explicitly reassigned.
- Live conversation under investigation: `867101a0-a201-4f3e-b689-b31d820c1971`.

## Active Agents

- Main orchestrator: setup, serial implementation, integration, final validation, closeout.

## Completed Work

- Created shared notes file.
- Implemented degraded stored-context maintenance for compaction summary failures.
- Added non-mutating `ChatService.snapshotContextUsage(...)` for read/finalize paths.
- Updated completed anonymous plan finalization, plan execution stream completion callbacks, and chat history reload to use context snapshots instead of model-backed maintenance.
- Added focused unit/controller coverage for degraded maintenance, snapshot history payloads, and compaction model failure handling.
- Updated technical docs, internal-dev changelog, reusable knowledge, and durable decision records.

## Validation Results

- `mvn -Dtest=ContextManagementAdvisorTest,ChatServiceTest test` passed.
- `mvn -Dtest=ContextManagementAdvisorTest,ChatServiceTest,ChatControllerTest test` passed.
- `mvn -Dtest='*Chat*Test,*Plan*Test' test` passed.
- Spring Boot startup on port `18080` passed.
- Live API check for conversation `867101a0-a201-4f3e-b689-b31d820c1971` returned history in under 5 seconds with 171 messages, `planState.mode=NORMAL`, `planState.status=COMPLETED`, and no new compaction retry logs.
- 2026-05-22T18:27:32Z browser reload regression validation passed for conversation `867101a0-a201-4f3e-b689-b31d820c1971`: `/chat` loaded, same-origin `GET /api/chat/{id}/history` returned 200 in 126 ms with 171 messages, last assistant content starting `# ViparSpectra XS3000 Pro`, `planState.mode=NORMAL`, `planState.status=COMPLETED`; `GET /api/chat/{id}/files` returned 200 in 17 ms with 6 files. UI session selection set active conversation id and rendered the conversation/output list. Console: 0 messages. Network: all observed requests 200.

## Remediation Notes

- Current conversation state is coherent: the session plan is `COMPLETED`, the transcript contains the trusted final message, and six chat files are visible because the file directory includes one older draft plus five final deliverables.
- The original reload failure mode was caused by reload/finalize paths invoking context maintenance, which could trigger local Ollama compaction retries after a completed plan was already persisted.

## Blockers

- None currently.

## Closeout Work

- Required after implementation: docs if behavior/API changes, `.internal-dev` changelog, reusable knowledge if applicable, unfinished-work update if anything remains, and git commit.

## Final Validation Status

- Passed.

## Handoff Notes

- Keep final state resilient when context maintenance/compaction cannot reach its model endpoint after plan completion has already passed validation.

## Forensics: Conversation 867101a0-a201-4f3e-b689-b31d820c1971 (2026-05-22T18:17:27Z)

- Persisted plan row exists with `id=867101a0-a201-4f3e-b689-b31d820c1971`, `kind=SESSION_PLAN`, `status=COMPLETED`, `updated_at=2026-05-22T18:09:12.176308878Z`, `final_message` length 1874.
- `plan_definitions.conversation_id` is `NULL` for this row, but the plan id equals the conversation id; current `PlanService.mode()` resolves by `findDefinition(conversationId)` before trying `findDefinitionByConversationId(conversationId)`.
- The plan row has 5 `deliverables_json` entries and 0 `outputs_json` entries. This looks like session-plan deliverables, not task outputs.
- No `plan_runs` or `run_output_artifacts` rows were found for this plan id. Evidence is in `ai_chat_memory`, `audit_event`, and chat-owned files.
- `ai_chat_memory` has 171 rows for the conversation, orders 0-170. Final row 170 is assistant text titled `# ViparSpectra XS3000 Pro -- Research Report Complete`, length 1874, matching `plan_definitions.final_message`.
- Chat-owned output files under `~/.magenta/root/chats/867101a0-a201-4f3e-b689-b31d820c1971/files` count 6: the five final deliverables plus an older `User_Experience_and_Sentiment.md` alongside final `User_Experience_and_Sentiment_v2.md`.
- `audit_event` counts: 159 `tool_exec`, 84 `context`, 3 `user_msg`, 3 `assistant_msg`, 2 `error`. Tool counts include 3 completed `plan_complete` calls; final successful validator row is sequence 248, followed by context sequence 249 and assistant message sequence 250.
- Audit errors are both `plan_stream_disconnect` at 2026-05-22T17:54:11Z: client disconnected before receiving an event / before completion. Execution continued afterward, wrote files, and completed.
- Context audit rows near completion report only about 5.2k / 128k tokens (~4.1%), below trigger 85,760; no compaction method or context error was recorded for the final state.
- Current `ChatController.history()` calls `chatService.maintainContextUsage()` before returning history, so context maintenance is on the reload path. Current `ChatService.maintainContextUsage()` catches `RuntimeException`, estimates fallback usage, and returns degraded usage instead of throwing. In the current checkout, a history failure due solely to context maintenance is therefore not very plausible unless a non-runtime error escapes or the deployed code differs from this checkout.
