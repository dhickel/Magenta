Context
Magenta needs Spring AI tool execution where tool output remains useful to the model briefly, then becomes concise context without adding a separate result store.

Goal
Implement medium tool architecture with user-controlled Spring AI tool execution, context-only tool transcripts, and turn-based truncation for large tool outputs.

In Scope
- Resolve tools from existing agent approvedTools.
- Run tool-enabled chat turns through a blocking user-controlled tool loop.
- Persist tool activity as marker-prefixed chat memory messages.
- Replace large raw tool outputs with summaries after four subsequent user turns.
- Render tool metadata summaries in browser history.

Out of Scope
- Separate tool-result persistence.
- Tool output lookup tools.
- Replay metadata or automatic replay.
- Approval semantics for state-changing tools.

Implementation Steps
- Add tool registry and transcript services under ai.chat.tool.
- Wire ChatModel and ToolCallingManager into ChatService.
- Add context preparation hooks so tool turns can use existing context compaction behavior.
- Update history conversion for tool transcript messages.
- Add tests for truncation and rendering behavior.

Validation
- Run affected unit tests and full Maven test suite if feasible.
- Verify no schema migration is required.

Exit Criteria
- Tool-enabled turns can execute via user-controlled loop.
- Large tool outputs are visible briefly, then replaced by summaries.
- UI history exposes only tool metadata and summaries.
