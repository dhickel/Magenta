# Topic
Issue-tracker UI remediation patterns for chat, agents, and plans.

# Source References
- `ChatSessionMetadataRepository`
- `ChatService`
- `PlanService`
- `OrchestrationController`
- `chat-client.js`
- `agent-chat.js`

# Key Takeaways
- Session origin belongs in metadata, not inferred from prompts or history text.
- Keep the plan document and execution evidence separate; the document is stable user-facing structure, evidence is runtime output.
- Agent chat can remain JS-backed for SSE while the surrounding operational UI stays HTMX-first.
- For narrow operational sidebars, cards carry more useful information than compressed multi-column tables.

# Engine Relevance
These choices keep the operational surfaces aligned with the persisted model and prevent live-chat concerns from leaking into broader CRUD UI design.

# Open Questions
None from this pass.
