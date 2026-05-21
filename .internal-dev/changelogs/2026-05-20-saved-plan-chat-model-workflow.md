# Date

2026-05-20

# Change Summary

Saved `/plans` planning chat now treats the four opening answers as seed context for a plan-id scoped model turn instead of parsing them directly into saved plan fields. The model receives saved-plan planning instructions, labeled opening answers, and recent transcript context, then updates the draft through saved-plan tools or queues follow-up questions.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/SavedPlanChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/SavedPlanModelClient.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/SavedPlanPlanningModelClient.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/savedplan/SavedPlanTools.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/savedplan/SavedPlanToolConfiguration.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/SavedPlanChatServiceTest.java`
- `docs/end-user/chat.md`
- `docs/end-user/plans-and-tasks.md`
- `docs/technical/api-reference.md`
- `docs/technical/chat-planning-tasks.md`
- `docs/technical/services.md`

# Behavioral Impact

Natural-language opening answers no longer become malformed input names or copied deliverables. Saved plan chat continues through model-led questioning and plan-scoped tools. Manual editor changes are appended to the saved-plan transcript so subsequent model turns can account for user-side edits.

# Risks

Saved-plan model turns require configured model routing and tool calling support. If no model client is available, the service queues a controlled follow-up question rather than silently completing the turn.

# Follow-up Items

None.
