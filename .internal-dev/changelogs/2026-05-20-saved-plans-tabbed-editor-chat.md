# Date

2026-05-20

# Change Summary

Converted the saved `/plans` detail area to a tabbed editor/chat surface. New saved plan creation now prompts for a plan name before creating a `TASK_TEMPLATE` draft. New plan chat drafts open directly on the `Planning Chat` tab and use deterministic persisted plan-scoped Q/A messages. Manual editor saves append concise field-level context into saved plan chat history when chat history already exists.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatModuleRenderer.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/SavedPlanChatService.java`
- `src/main/resources/static/js/orchestration/plans.js`
- `src/main/resources/static/css/orchestration.css`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/SavedPlanChatServiceTest.java`
- `docs/end-user/plans-and-tasks.md`

# Behavioral Impact

- `/plans` shows `Editing Details` and `Planning Chat` tabs for persisted plans.
- `New Plan` and `New Plan Chat` open a naming modal instead of immediately creating an unnamed draft.
- `New Plan Chat` seeds the four opening assistant questions and opens the chat tab.
- Existing draft chat starts with “Any details you want to provide before continuing?”
- Approved/finalized plan chat starts with “What do you need to change in this plan?”
- Saved plan chat uses SimplyPages `ChatModule` structure without `/chat` session sidebar coupling.

# Risks

- Saved-plan chat is still deterministic and field-oriented; it does not perform model-backed plan editing.
- The unsaved edit warning is client-side and depends on HTMX event metadata.

# Follow-up Items

- None recorded for this phase.
