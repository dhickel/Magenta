## Date

2026-05-01

## Change Summary

Made planning more deterministic by adding current planning task state, replacing broad model-facing draft updates with simple keyed planning tools, and adding backend repair for PLAN turns that try to finish without either a queued clarification question or a ready-for-approval plan.

## Files

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/*`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/plan/PlanSaveTools.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/*`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/schema.sql`
- focused planning/chat tests

## Behavioral Impact

PLAN mode now guides the model through goal, deliverables/outputs, user guidance, clarification, detailed steps, and approval readiness. Planning tools can set the goal/current task and add, replace, or delete one keyed item at a time. Queued questions are answered one by one without waking the model until the final queued answer is submitted. Ready-for-approval UI now includes a continue-planning action that asks what should be clarified before approval.

## Risks

Existing plan data remains stored in the prior list-oriented shape; keyed tool operations use integer positions over those existing lists rather than a new normalized item table. A future migration may still be useful if stable sparse keys or richer item metadata are needed.

## Follow-up Items

- Split prompt/approval UI payloads from `ChatPlanState` if the API contract needs plan elements and control state fully separated.
- Consider a normalized `ai_chat_plan_items` table if keyed item identity must remain stable across deletes without compaction.
