# Date
2026-05-15

# Change Summary
Remediated the issue-tracker UI cluster across chat, agents, and plans: structured collapsed plan display, forward-only chat-session origin tracking, agent-scoped chat history, compact agent cards, one inline agent chat surface, optional continuation instructions, full-width readable plan rows, and arrow reorder controls.

# Files
Chat/session models and persistence, plan rendering/state, chat and orchestration controllers, chat/agent browser clients, orchestration/chat CSS, schema, focused web tests, and the archived plan suite.

# Behavioral Impact
New ordinary chats and agent chats are classified separately; new agent-chat sessions no longer leak into `/chat`. Saved plans render as a collapsed document with evidence kept separate. Agent pages use compact cards and inline chat with conversation continuity. Plan continuation now accepts an optional instruction while preserving blank-resume behavior.

# Risks
Historical sessions without origin remain visible by design. Browser validation covered structural flows, but model-backed end-to-end chat semantics still depend on the configured live model environment.

# Follow-up Items
No deferred work was introduced in this pass.
