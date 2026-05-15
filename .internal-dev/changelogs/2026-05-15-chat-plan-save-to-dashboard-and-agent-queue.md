# Date

2026-05-15

# Change Summary

- Fixed chat plan save flow so “Save to plans” creates a real `TASK_TEMPLATE` (dashboard-visible plan), not only a session-plan status flip.
- Added naming on save from chat.
- Added chat-side “Send to agent” flow with agent dropdown and queue submission.
- Kept execute action available after save (`SAVED_TASK` state now renders action panel correctly).

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`

# Behavioral Impact

- From chat planning, save now prompts for a plan/task name and writes a reusable task template that appears under `/plans`.
- Chat now keeps `Execute now` and `Send to agent` actions available after save.
- “Send to agent” now opens a real agent select dropdown and submits a queued task assignment through `/api/plans/{planId}/submit`.

# Risks

- The send flow depends on `/api/agents` availability and active non-disabled agent profiles.
- Existing chat panel state still relies on frontend session-local memory for the last saved task id; after full reload, sending will prompt for save/name again if no saved id is cached client-side.

# Follow-up Items

- Optional UX follow-up: persist the last saved task id/title in chat plan state so reloads can send directly without re-saving.
