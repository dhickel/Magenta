# Topic

Saved plan chat model-backed planning workflow

# Source References

- `SavedPlanChatService`
- `SavedPlanPlanningModelClient`
- `SavedPlanTools`
- `PlanService`
- `OrchestrationController`

# Key Takeaways

Saved `/plans` planning chat is plan-scoped and must remain separate from `/api/chat`, `ai_chat_memory`, and chat session metadata. Opening questions are useful as lightweight intake, but their answers should be passed to the model as seed context rather than parsed as final fields.

Saved-plan model turns use a dedicated system prompt and plan-id scoped `saved_plan_*` tools. The terminal state should be either queued pending questions or `READY_FOR_APPROVAL`; otherwise the service should queue a controlled follow-up question.

Manual editor saves append `Saved editor updates` transcript messages when plan chat history exists. Model user messages include recent saved-plan transcript context so those edit notices are visible on the next model turn.

# Engine Relevance

This keeps saved plan authoring conversational without polluting normal chat session memory. It also gives users a reliable workflow where manual form edits and model-led planning can coexist in the tabbed editor.

# Open Questions

None.
