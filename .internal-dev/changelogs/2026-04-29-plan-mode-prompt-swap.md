Date
2026-04-29

Change Summary
Updated chat plan mode so /plan is a no-argument command that switches to a standalone planning system prompt and immediately starts the planning conversation.

Files
- src/main/java/io/mindspice/magenta2/api/web/ChatController.java
- src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java
- src/main/java/io/mindspice/magenta2/ai/chat/plan/
- src/main/java/io/mindspice/magenta2/ai/chat/tool/plan/PlanSaveTools.java

Behavioral Impact
Plan mode no longer accepts /plan goals. The model asks for the goal in the first planning turn, drives clarification through the dedicated plan prompt, and persists the clarified goal through plan_save before telling the user to approve with /exec-plan or /clr-exec-plan.

Plan drafts also include optional notes for vital planning details that are not execution steps. Notes are persisted in SQLite, exposed in plan state, and injected into execution mode with the saved plan.

Validation
Ran mvn -q test.

Follow-up Fixes
- Added a browser in-flight guard so users cannot submit another chat message or command while a response is pending.
- Failed streamed model turns now remove the just-persisted dangling user message when no assistant response completed.
- File reads and searches now stream large files instead of loading full file content into context; displayed lines are capped while preserving line numbers and hashes.
- Tool transcripts now hard-cap stored raw output to reduce oversized context failures.
