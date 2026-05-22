---
schema_version: 1
document_type: changelog
date: 2026-05-22
owner: codex
status: complete
---

# Chat Completion Compaction Reload Repair

## Change Summary

Fixed the completed anonymous chat-plan reload path so completed conversations remain visible when stored context exceeds the compaction trigger and the local compaction model is unavailable. History reload and completed execution finalization now use a non-mutating context usage snapshot instead of invoking model-backed compaction.

## Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/StoredContextUsage.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisorTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`
- `docs/technical/chat-planning-tasks.md`
- `.internal-dev/plans/chat-completion-compaction-reload-repair/phase-01-orchestrated-diagnosis-and-fix.md`
- `.internal-dev/knowledge/chat-completion-context-maintenance.md`
- `.internal-dev/focus/decisions.md`

## Behavioral Impact

- Completed anonymous plan conversations can reload even when context usage is over the compaction trigger.
- `GET /api/chat/{conversationId}/history` no longer invokes the summary/compaction model.
- Completed execution stream finalization records usage from a snapshot and does not block completion on post-completion compaction.
- Compaction failures during maintenance degrade context-usage metadata instead of throwing through read/finalize paths.
- Prompt-time model sends still own compaction, trimming, or fail-closed behavior when a new model call needs a bounded prompt.

## Validation

- `mvn -Dtest=ContextManagementAdvisorTest,ChatServiceTest test`
- `mvn -Dtest=ContextManagementAdvisorTest,ChatServiceTest,ChatControllerTest test`
- `mvn -Dtest='*Chat*Test,*Plan*Test' test`
- Spring Boot startup on port `18080`
- Live API check for conversation `867101a0-a201-4f3e-b689-b31d820c1971`: history returned `200`, `171` messages, plan status `COMPLETED`, and no compaction retry logs.
- Playwright validation on `/chat`: selected the same conversation, `/history` returned in `126 ms`, `/files` returned `count=6`, and console/network errors were `0`.

## Risks

- The history payload can report usage above the configured trigger after a degraded snapshot. That is intentional for read-only reloads; a future model send must still enforce prompt context limits.
- The affected live conversation has five final deliverables and six visible files because one older user-experience draft remains in the chat file directory beside its final `v2` version.

## Follow-up Items

- None required for this fix.
