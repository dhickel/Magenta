# Date

2026-04-25

# Change Summary

Added confined agent file tools for listing, chunked reading, searching, writing, and hashline-anchored replacement. File reads and search results now expose deterministic `lineNumber:hash|content` anchors that edits validate before changing files.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistry.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistryTest.java`
- `config/ai-config.example.json`

# Behavioral Impact

Agents can enable `file_list`, `file_read`, `file_search`, `file_write`, and `file_replace` through `approvedTools`. File access is confined to `AiConfig.dataRoot`, and anchored replacement rejects stale or mismatched anchors.

# Risks

The example config now grants the default agent file read and write tools inside `dataRoot`. Deployment configs should keep least-privilege tool approval per agent.

# Follow-up Items

Future privilege expansion outside `dataRoot` should be explicit config, not an implicit broadening of these tools.
