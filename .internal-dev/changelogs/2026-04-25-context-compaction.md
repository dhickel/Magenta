# Date
2026-04-25

# Change Summary
Implemented bare-bones token-aware context compaction for chat sessions. Added a required summarization agent config field, a summarization prompt, context usage telemetry, visible compaction notices, and a web chat token meter.

Follow-up correction: moved streamed assistant persistence out of the low-level advisor response stream and into `ChatService.stream()` using the final accumulated `content()` chunks, preventing malformed or repeated markdown from being saved. Added a second pre-send budget check that trims older retained messages after compaction and aborts before model dispatch if the prompt is still above the trigger budget.

# Files
- `config/ai-config.example.json`
- `config/prompts/summarization.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/**`
- `src/main/java/io/mindspice/magenta2/ai/config/user/**`
- `src/main/java/io/mindspice/magenta2/api/web/**`
- `src/main/resources/static/js/chat-client.js`

# Behavioral Impact
Chat memory is no longer trimmed by message count. Before model calls, Magenta estimates context usage against the selected model context length and compacts older history when usage crosses the configured buffer threshold. The UI hides generated summaries, shows a system notice when compaction occurs, and displays active context usage.

# Risks
Token counts are estimates and may not exactly match every remote model tokenizer.

# Follow-up Items
Add integration coverage with a real Ollama streaming response and consider persisting context usage snapshots if usage needs to survive application restarts exactly.
