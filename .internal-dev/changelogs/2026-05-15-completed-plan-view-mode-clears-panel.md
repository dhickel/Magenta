# Date
2026-05-15

# Change Summary
Fixed completed in-chat execution plans reporting `mode=PLAN` through `ChatPlanState`, which left the browser planning panel latched on the generic "Planning active" / "Cancel planning" state after `plan_complete` succeeded.

# Files
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`

# Behavioral Impact
Completed, cancelled, approved, saved, and other terminal session-plan states now use the same mode resolution as `PlanService.mode(...)`. A completed plan can still expose its `COMPLETED` status and evidence in the plan state, but the chat UI can clear the active planning controls instead of offering cancellation after completion.

# Risks
Low. The change centralizes the `ChatPlanState.mode` value on the existing lifecycle mode resolver instead of deriving it only from plan kind.

# Follow-up Items
None.
