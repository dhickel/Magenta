# Date

2026-04-25

# Change Summary

Changed chat streaming so approved tools no longer force the `/api/chat/stream` path into a single blocking response. Tool-capable streams now emit normal text chunks as they arrive, aggregate the completed model turn to detect tool calls, execute any tool calls synchronously, and then continue with the next streamed model turn.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`

# Behavioral Impact

- Normal responses stream even when the default agent has approved tools.
- Tool calls still block while local tool execution runs, then streaming resumes for the model response after tool results are available.
- Models that reject tool calling are remembered and fall back to plain streaming for future requests.

# Risks

- Streaming text emitted before a tool call is not retracted; model behavior should avoid user-visible preambles when it intends to call a tool.
- Tool execution remains synchronous inside the stream flow, so long-running tools pause new response chunks until completion.

# Follow-up Items

- Add integration coverage with a real Ollama tool-capable model if streamed tool-call chunk behavior varies by model.
- Consider stream events for tool-call start/finish if the UI needs to show why output paused.
