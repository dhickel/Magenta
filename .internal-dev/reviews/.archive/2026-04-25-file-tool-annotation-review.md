# Scope

Review Spring AI annotation quality for Magenta file tools and update the configured system prompt with model-facing file tool usage patterns.

# Findings

- File tools are registered through `MethodToolCallbackProvider` and expose Spring AI `@Tool` names and descriptions.
- Every file tool argument has a `@ToolParam` description.
- `file_write.content` and `file_replace.replacement` should be required in the generated schema so models intentionally supply full-file content or replacement text, including intentional empty strings.
- Existing tool descriptions were technically present but too terse to guide smaller models through listing, searching, chunked reading, and anchored edits.

# Risk Assessment

Weak model-facing descriptions can cause smaller models to over-read files, use whole-file writes for small edits, or omit replacement content accidentally. Optional edit text is especially risky because omission and intentional deletion both collapse to an empty replacement at the service layer.

# Recommendations

- Keep the five-tool surface: `file_list`, `file_search`, `file_read`, `file_write`, and `file_replace`.
- Keep `file_write.content` and `file_replace.replacement` required in Spring AI schema while allowing empty strings for intentional clearing/deletion.
- Keep usage ordering in the system prompt: list or search first, read targeted chunks, edit with anchors, use write only for whole-file creation/replacement.
- Test generated tool schemas directly so annotation drift is caught before model behavior regresses.

# Follow-ups

- Consider a lightweight `file_list` filter or glob argument if real usage shows directory listings are too broad.
- Consider adding prompt examples later only if smaller models still choose inefficient file flows.
