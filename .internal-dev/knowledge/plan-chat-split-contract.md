# Topic

Anonymous chat planning and saved plan chat contracts.

# Source References

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/SavedPlanChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/PlanController.java`
- `docs/technical/chat-planning-tasks.md`

# Key Takeaways

Anonymous `/chat` plans are conversation-scoped `SESSION_PLAN` records. They ask three backend-seeded questions for goal, assumptions/details/constraints/approach, and deliverables, then continue with the conversational planner. They do not collect structured inputs or outputs and cannot be saved as task templates.

Saved `/plans` chats are plan-scoped and store messages in `plan_chat_messages`, not `ai_chat_memory`. They seed four questions for goal, runtime inputs, high-level deliverables, and structured outputs, then update a durable `TASK_TEMPLATE` draft.

Deliverables are high-level user-visible or operational outcomes. Outputs are named structured values for workflow chaining, downstream plan inputs, or highly specific directed results.

# Engine Relevance

Future planning work should choose the path first. Use `/chat` only for ad hoc session planning and anonymous execution. Use `/plans` when a plan needs durable inputs, typed outputs, agent submission, workflows, or reusable task definitions.

# Open Questions

When richer saved plan chat editing is needed, the saved chat path should get plan-scoped model tools instead of reusing anonymous session plan tools.
