# Date

2026-05-20

# Change Summary

Corrected the saved `/plans` tabbed editor/chat behavior so the top tab control renders a single active tab window instead of stacking chat below the editor. New saved-plan chat now asks its four deterministic opening questions in the intended order, starting with runtime inputs, and editor-save context messages are stored as context rather than user answers.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/SavedPlanChatService.java`
- `src/main/resources/static/js/orchestration/plans.js`
- `src/main/resources/static/css/orchestration.css`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/SavedPlanChatServiceTest.java`
- `docs/end-user/plans-and-tasks.md`

# Behavioral Impact

- `Editing Details` renders only the editor form and editor-adjacent controls.
- `Planning Chat` renders only the chat module window under the top tabs.
- New plan chat asks runtime inputs, goal, deliverables, and outputs in order.
- Existing draft plan chat still resumes with “Any details you want to provide before continuing?”
- Approved plan chat still resumes with “What do you need to change in this plan?”
- Saved editor changes append a visible `Saved editor updates...` context message without consuming the current planning prompt.

# Risks

- List/field row edits still do not produce context diffs; this correction preserves scalar save context behavior only.

# Follow-up Items

- None.
