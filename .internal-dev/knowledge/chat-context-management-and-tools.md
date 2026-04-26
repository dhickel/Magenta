# Topic

Magenta chat context management and tool-use context model

## Source References

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ToolTranscriptService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistry.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/SQLiteChatMemoryRepository.java`

## Key Takeaways

Magenta stores conversation memory in SQLite through Spring AI `ChatMemoryRepository`. The persisted chat memory is still the single durable conversation source for model-visible context. There is no separate tool-result table or artifact lookup store.

`ContextManagementAdvisor` owns prompt preparation for normal chat turns. It loads stored messages, rewrites expired large tool outputs into summaries, estimates token use, compacts or trims if needed, saves the current user message, and builds the prompt messages sent to the model.

Context compaction uses a summarization agent when estimated usage exceeds the configured trigger budget. It replaces older compactable messages with a hidden system summary marker plus a visible compaction notice, then keeps a recent tail of raw messages.

Tool results are represented as Magenta-owned system marker messages:

- `[[MAGENTA_TOOL_RESULT_FULL]]` stores tool metadata, argument summary, result summary, and raw output.
- `[[MAGENTA_TOOL_RESULT_SUMMARY]]` stores the same metadata but removes raw output and tells the model the raw output was truncated.

Large tool outputs are currently defined as over 4,000 characters. Full raw output is retained until four later user turns have occurred, then `ToolTranscriptService` replaces the full marker with a summary marker during prompt preparation. Small tool outputs are not turn-truncated by this policy, though normal context compaction can still summarize or trim them.

Browser history renders tool activity as `tool` messages with metadata and a result summary only. Raw tool output is not shown in the UI transcript.

## Engine Relevance

### Normal Non-Tool Chat Flow

1. `ChatService.chat()` resolves conversation id and model.
2. If the active agent has no approved tools, `ChatClient` runs with `ContextManagementAdvisor`.
3. `ContextManagementAdvisor.preparePrompt()` loads stored memory, truncates expired large tool results, estimates token usage, compacts/trims if needed, and saves the current user message.
4. The model is called with system prompt, model-rendered memory, and current user message.
5. Assistant output is saved to memory and context usage is recorded.

### Streaming Flow

1. `ChatService.stream()` uses the same normal prompt path when no tools are enabled.
2. Streaming chunks are returned to the caller.
3. On completion, the accumulated assistant text is saved manually to chat memory.
4. If tools are enabled, streaming falls back to a blocking tool-enabled turn and emits the final response as one chunk.

### Tool-Enabled Flow

1. `ChatService.chat()` resolves approved tool names from the active default agent through `ChatToolRegistry`.
2. Tool turns use `ChatModel` and `ToolCallingManager` directly, with `OllamaChatOptions.internalToolExecutionEnabled(false)`.
3. `ContextManagementAdvisor.preparePrompt()` prepares the same managed context used by normal turns and saves the user message.
4. The model is called. If it returns tool calls, Magenta executes them with Spring AI `ToolCallingManager`.
5. Spring AI tool-response messages are used only inside the active loop so the model can continue from the tool result.
6. Magenta converts each tool response into its own persisted tool transcript marker via `ToolTranscriptService`.
7. The loop repeats until the model returns a final assistant message or exceeds the max tool-call iteration limit.
8. Tool transcript markers plus the final assistant message are saved to chat memory.

### Model-Visible Tool Context

- Full markers render as readable system context with tool name, status, timestamp, call id, argument summary, result summary, and raw output.
- Summary markers render the same metadata but explicitly state that raw output is truncated and exact prior output is no longer in context.
- There is intentionally no replay instruction, no lookup id, and no durable result store beyond chat memory.

## Open Questions

- Whether future action tools need approval-specific transcript fields.
- Whether result summaries should become semantic summaries instead of structural character-count summaries.
- Whether each concrete tool should declare retention preferences once large read tools exist.
- Whether streaming should eventually support multi-step tool execution instead of current blocking fallback.
