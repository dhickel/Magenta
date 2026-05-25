---
schema_version: 1
document_type: implementation-plan
status: draft
created: 2026-05-22
owner: unassigned
model_plan: gpt-5.5 high
---

# Chat Completion, Compaction, and Reload Repair

## Context

Conversation `867101a0-a201-4f3e-b689-b31d820c1971` currently persists as `SESSION_PLAN` status `COMPLETED` in `~/.magenta/magenta.sqlite`. The stored plan has five deliverables, zero typed `outputs_json`, six validation criteria, passed validator feedback, and a final assistant message. The user-visible session row can show an output count based on chat files, but that count is not the same as typed plan outputs.

The reported log shows a post-completion context maintenance attempt compacting `134326` estimated tokens with trigger `85760`, then failing while calling `http://localhost:11434/api/chat`. The stack path is `ChatService.handleFinalize(...) -> maintainContextUsage(...) -> ContextManagementAdvisor.maintainStoredContext(...) -> compact(...) -> summarize(...)`. The browser history endpoint also calls `maintainContextUsage(...)`, so navigating away and back can fail to reload a completed chat if compaction requires an unavailable model endpoint.

## Goal

Make plan/task completion robust when context compaction fails after useful work has completed. The final completed state and assistant message must remain visible on reload, and context-maintenance failures must be reported as degraded maintenance rather than invalidating completion or causing the chat to appear stuck at "saved planning answer".

## In Scope

- Diagnose the exact relationship between persisted plan completion, chat-file output count, typed plan outputs, and browser reload state.
- Prevent context compaction/model endpoint failures from breaking history reload, completion finalization, and plan execution `done` delivery after completion has already been persisted.
- Preserve fail-closed behavior for actual validator completion; do not mark incomplete work as completed merely because compaction failed.
- Add focused tests around finalization, history reload, context-maintenance degradation, and plan state rendering.
- Validate the current conversation recovery path and a synthetic high-context completion path.

## Out of Scope

- Reworking the plan/task output model.
- Changing the meaning of `outputCount` in the session list unless diagnostics prove the label itself is the confusing user-visible bug.
- Replacing the compaction model provider or rewriting model routing.
- Broad UI redesign of chat or plan execution.

## Implementation Steps

1. Run non-mutating forensics against the live conversation.
   - Query `~/.magenta/magenta.sqlite` for `ai_chat_session_metadata`, `ai_chat_memory`, `audit_event`, `plan_definitions`, and chat output files under `~/.magenta/root/chats/867101a0-a201-4f3e-b689-b31d820c1971/files`.
   - Record whether the final assistant message is present, whether history API fails, and whether `/api/chat/sessions` output count comes from files rather than typed plan outputs.
   - Confirm whether `RuntimeSettingsService` or file config resolved the compaction model to an Ollama-backed local endpoint.

2. Add a degraded context-maintenance result path.
   - Target `ContextManagementAdvisor.maintainStoredContext(...)` and `ChatService.maintainContextUsage(...)`.
   - Catch compaction summary failures caused by transient model transport errors separately from unrecoverable storage errors.
   - Return the best available `ContextUsage` plus a degradation flag/message instead of throwing through history/finalization paths.
   - Do not write partial compaction output unless summary generation succeeds.
   - Log a concise warning with conversation id, model ref, provider endpoint when available, and the maintenance stage.

3. Make completion finalization prefer persisted completion over maintenance success.
   - Target `ChatService.handleFinalize(...)` and `ChatController` stream completion finalizer.
   - Persist final assistant messages and completed plan state before optional context maintenance.
   - If maintenance fails after a passed `plan_complete`, still emit `done` with the persisted final message and plan state, with context usage omitted or marked degraded.
   - Ensure `recordExecutionFailure(...)` is not called for post-completion maintenance failure.

4. Make history reload resilient.
   - Target `ChatController.history(...)`.
   - History must return messages and `planState` even when context maintenance cannot compact.
   - Include a non-fatal context warning only if the API payload already has a compatible place for it; otherwise use `contextUsage = null` and server logs for the first patch.
   - Preserve current behavior for true missing conversation errors.

5. Tighten the UI state after reload.
   - Target `chat-client.js` only if API fixes do not fully solve the visible issue.
   - Verify that `loadHistory(...)` renders the final assistant message and `COMPLETED` plan state.
   - Consider relabeling the session badge from `Outputs` to `Files` only if the user confusion persists and docs agree; otherwise document that typed outputs and chat output files are different contracts.

6. Add tests.
   - `ContextManagementAdvisorTest`: compaction summary transport failure leaves stored messages unchanged and returns degraded maintenance.
   - `ChatServiceTest`: plan completion finalization persists the validated final message even when context maintenance fails afterward.
   - `ChatController` test: `/api/chat/{conversationId}/history` returns persisted history and completed plan state when context maintenance throws a transient compaction exception.
   - Regression fixture: a plan with five deliverables, six validation criteria, and chat files still renders completed, not planning.

## Validation

- Run targeted unit tests:
  - `mvn -Dtest=ContextManagementAdvisorTest test`
  - `mvn -Dtest=ChatServiceTest test`
  - relevant `ChatController` test class once identified or added.
- Run broader chat/plan regression tests:
  - `mvn -Dtest='*Chat*Test,*Plan*Test' test`
- Run bounded app startup:
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Before browser validation, read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`.
- Use a Playwright subagent to validate:
  - reload conversation `867101a0-a201-4f3e-b689-b31d820c1971`;
  - final assistant message is visible;
  - plan state is completed, not planning;
  - session file/output panel still lists created files;
  - temporarily blocking the compaction endpoint does not hide persisted completion.

## Exit Criteria

- Completed plan executions remain completed and reloadable when post-completion context maintenance fails.
- Context compaction failures are observable but non-fatal for history reload and post-completion `done` emission.
- The current conversation is recoverable in the browser without requiring manual database edits.
- Tests prove the failure mode cannot regress silently.
- Required docs, `.internal-dev` changelog, reusable knowledge, and commit workflow are completed during implementation closeout.

## Orchestration

Shared notes path for execution: `.codex-orchestration/chat-completion-compaction-reload-repair/notes.md`.

All subagents should use `gpt-5.5` with high reasoning, per the user request.

Parallel non-mutating group A:
- Forensics agent: inspect the live DB, audit trail, output files, and endpoint behavior. No file edits.
- Code-path review agent: trace completion, context maintenance, history reload, and stream finalization. No file edits.
- Test-design agent: identify existing test seams and propose exact regression tests. No file edits.

Serial edit group:
- Implementation agent 1: add degraded context-maintenance behavior in `ContextManagementAdvisor` and `ChatService`.
- Validation agent 1: run focused tests and review stored-message invariants.
- Implementation agent 2: adjust stream/history finalization paths and API payload behavior.
- Validation agent 2: run controller/service tests and synthetic failure tests.
- Implementation agent 3, only if needed: adjust `chat-client.js` reload/status handling.
- Playwright validation agent: run focused browser validation and capture screenshots after the app starts.

Closeout group:
- Documentation agent: update docs for context maintenance degradation and typed outputs vs chat output files only if the implementation changes user-visible/API behavior.
- Closeout review agent: verify `.internal-dev` changelog, knowledge notes, unfinished-work updates if anything remains, and commit readiness.
