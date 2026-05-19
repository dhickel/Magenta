# Date

2026-05-19

# Change Summary

Separated anonymous `/chat` planning from saved `/plans` planning chat. Anonymous chat plans now start from backend-seeded opening questions, do not expose structured outputs, cannot be saved as task templates, and can execute directly only as approved session plans. Saved plan chat now creates or updates durable `/plans` drafts and collects explicit runtime inputs, deliverables, and typed structured outputs.

Added persistent chat file directories for anonymous execution final messages and file-tool context, plus runtime temp retention behavior through `retainTempWork`.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/*`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/PlanController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/*`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `src/main/resources/schema.sql`
- `src/main/resources/static/chat-client.js`
- `docs/end-user/*`
- `docs/technical/*`
- `docs/api/00-index.md`
- `src/test/java/io/mindspice/magenta2/**/*`

# Behavioral Impact

`/chat` planning is now anonymous and ad hoc. It can execute approved plans with normal or clean context, but it no longer creates saved plan/task definitions.

`/plans` is now the saved planning-chat entry point. Saved plan chat stores plan-scoped messages separately from chat session memory and updates a durable draft with explicit inputs and outputs.

Temp run directories are retained when configured or when completion needs review because required output/final-message/file deliverables were not satisfied.

# Risks

Saved plan chat currently uses deterministic parsing and draft updates rather than a fully model-backed tool loop, so richer conversational editing may need follow-up work.

The `/chat` stream execution path exists for compatibility, but the primary UI uses the non-stream anonymous execution endpoint.

# Follow-up Items

Add richer saved-plan-specific model tools when the plan editor/chat UX needs model-driven structured edits beyond the current seeded-answer path.
