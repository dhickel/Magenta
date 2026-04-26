# Date

2026-04-25

# Change Summary

Reviewed and tightened the Spring AI file tool annotations and updated the default Magenta system prompt with file tool usage patterns.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileTools.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistryTest.java`
- `config/prompts/system.md`
- `.internal-dev/reviews/2026-04-25-file-tool-annotation-review.md`

# Behavioral Impact

- Models receive clearer tool descriptions and argument descriptions for file listing, searching, reading, writing, and anchored replacement.
- `file_write.content` and `file_replace.replacement` are required in the generated tool schema, making omitted content less likely to be confused with intentional empty content.
- The default system prompt now directs models to list/search before reading, read in chunks, and prefer anchored replacement for targeted edits.

# Risks

- Requiring replacement/content means callers must send an explicit empty string when intentionally clearing a file or deleting a range.

# Follow-up Items

- Watch smaller local model behavior to see whether `file_list` needs a filter/glob argument.
