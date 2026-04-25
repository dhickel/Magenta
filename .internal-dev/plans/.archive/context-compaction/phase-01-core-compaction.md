# Context
Magenta currently relies on message-count chat memory trimming. The next step is bare-bones true context management with token-aware compaction, a required summarization agent, API usage telemetry, and UI compaction notices.

# Goal
Replace sliding-window behavior with token-size-based compaction before model calls.

# In Scope
- Required top-level `summarizationAgent` external config.
- Default 10 percent context buffer.
- Spring AI token estimation.
- Automatic compaction before chat requests.
- Hidden compacted summaries plus visible user notices.
- Token usage reporting in chat API and web UI.

# Out of Scope
- Multi-agent orchestration beyond the summarization call.
- Tool-aware or multimodal compaction.
- Long-term semantic memory.
- User-editable compaction controls in the UI.

# Implementation Steps
- Update AI config records, loader validation, example config, and summarization prompt.
- Replace message-count memory wiring with repository-backed memory plus context compaction advisor.
- Add context usage DTOs and include usage in history, stream, and non-stream responses.
- Render system compaction notices and token usage in the browser chat UI.
- Add focused unit tests and run Maven validation.

# Validation
- Config loader tests cover required summarization agent validation.
- Context compaction tests cover below-threshold and above-threshold behavior.
- Controller/service/UI tests cover response shape and rendered markup.
- `mvn test` passes.

# Exit Criteria
- Long conversations compact before exceeding the configured model context threshold.
- Normal chat history hides generated summaries while displaying a compaction notice.
- The web UI shows current token usage and percentage for the active session.
