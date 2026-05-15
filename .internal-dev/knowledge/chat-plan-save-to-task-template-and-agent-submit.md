# Topic

Chat planning save flow to reusable plans and agent queue handoff.

# Source References

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/java/io/mindspice/magenta2/api/web/PlanController.java`

# Key Takeaways

- Chat “save plan” must create a `TASK_TEMPLATE` row to appear in `/plans`; setting `SESSION_PLAN` status to `SAVED_TASK` alone is insufficient.
- Chat panel logic must treat `SAVED_TASK` as an actionable state, not a generic planning fallback.
- A clean flow is:
  1. Prompt for save name.
  2. Save via chat API returning saved task id/title.
  3. Use saved task id for agent submit to `/api/plans/{planId}/submit`.
- Agent routing UX should provide a selectable agent list from `/api/agents` and filter out disabled profiles.

# Engine Relevance

- Prevents user-visible mismatch where chat reports “saved” but the dashboard plan list remains unchanged.
- Aligns chat flow with orchestration assignment APIs and queue semantics.
- Keeps plan execution and handoff options visible from the same chat context.

# Open Questions

- Should chat plan state include persisted `savedTaskId` so agent handoff remains one-click after full page reload?
- Should submit defaults include optional priority/model override in the chat handoff panel?
