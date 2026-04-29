# Summary

Tool-heavy chat work is not surfaced clearly enough for users to distinguish real evidence review from generated extraction or truncated context.

# Scope

Chat UI history, context usage reporting, and tool transcript rendering.

# Reproduction

Run a plan that uses tools to query a large database and generate an intermediate file. The browser transcript shows terse tool summaries and the context meter may show a small number relative to the underlying corpus. It does not clearly show actual rows/posts sampled, whether generated files were read back, or whether raw tool output was truncated.

# Expected

Users should be able to inspect a concise work ledger: tool calls, important arguments, output truncation status, generated artifact paths, counts claimed by scripts, and whether source evidence was read into model context.

# Actual

`ToolTranscriptService.renderForHistory()` renders only `Tool <name> completed ... Returned N characters.` Raw tool output and truncation details are hidden from history. `ToolTranscriptService` also caps stored raw output at 40,000 characters and marks large results as truncated, while the UI does not make that limitation obvious.

# Evidence

Conversation `48e9dc4f-5aab-4d8f-bba4-b430bf451362` created and read `summaries.md`; the `file_read` result returned 43,927 characters, but persisted raw output was capped and the history view only summarized the tool call.

# Impact

Users may see a polished answer and a small context number without knowing whether the model read enough data, whether the evidence was truncated, or whether generated artifacts were verified.

# Status

Open.

# Next Action

Add a compact tool-work ledger to history or plan execution responses, including tool names, key arguments, generated files, extracted counts, truncation state, and source artifact paths.
