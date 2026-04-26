# Date

2026-04-25

# Change Summary

Added graceful fallback when Ollama rejects tool-enabled requests because the selected model does not support tools.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `config/ai-config.example.json`

# Behavioral Impact

- If a tool-enabled request receives Ollama's `does not support tools` error, Magenta retries the request as a normal non-tool chat.
- The selected model is remembered as tool-unsupported for the running process to avoid repeated failing tool calls.
- Other AI errors still propagate normally.
- The example config now points the default tool-enabled agent at `local-gemma-26b`, which the configured Ollama server accepted native tool calls for.

# Risks

- Requests that genuinely need file tools will receive a normal model response rather than tool execution when using a non-tool-capable model.

# Follow-up Items

- Consider adding explicit model capability configuration if users need predictable tool routing across multiple local models.
- Resolve the logged model endpoint routing bug before relying on mixed model endpoints.
