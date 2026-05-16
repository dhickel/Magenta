# Topic
Completed plan UI state latch

# Source References
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/resources/static/js/chat-client.js`
- `chat-memory.db` conversation `a45ad561-8978-4368-bc88-ca3014fca15f`

# Key Takeaways
A session plan can have a terminal status such as `COMPLETED` while still retaining a persisted plan definition for evidence and final-message history. If `ChatPlanState.mode` is derived directly from plan kind (`SESSION_PLAN -> PLAN`) instead of lifecycle mode resolution, the browser treats the plan as active and renders the generic "Planning active" panel.

# Engine Relevance
Use `PlanService.mode(conversationId)` when exposing chat-facing plan mode. The mode answers whether the chat should currently behave as planning/executing/task/normal, while the plan kind only identifies what kind of durable definition is stored.

# Open Questions
None.
