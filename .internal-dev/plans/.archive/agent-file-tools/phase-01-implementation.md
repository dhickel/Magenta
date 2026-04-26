# Context

Magenta agents need basic file operation tools that are easy for smaller models to call correctly. The tools should follow the hashline editing approach from the harness article: reads and searches expose compact line anchors, and edits validate those anchors before changing files.

# Goal

Add confined read, search, write, and anchored replace tools for chat agents, operating inside `AiConfig.dataRoot` by default.

# In Scope

- File listing, chunked reading, searching, full-file writing, and anchored replacement.
- Path confinement under `dataRoot`, including traversal and symlink escape rejection.
- Spring AI tool registration through the existing chat tool approval flow.
- Focused unit tests with generated fixture content.

# Out of Scope

- Delete, move, copy, chmod, binary editing, patch application, or agent privilege escalation outside `dataRoot`.
- Durable audit storage beyond existing tool transcripts.

# Implementation Steps

- Add a small file-tool service under `io.mindspice.magenta2.ai.chat.tool.file`.
- Add Spring AI `@Tool` methods for `file_list`, `file_read`, `file_search`, `file_write`, and `file_replace`.
- Register method-based tools with the existing `ChatToolRegistry`.
- Keep example agent config explicit with split read/write tool names.
- Update package guidance for the expanded tool responsibility.

# Validation

- Unit-test path confinement, chunked reading, search behavior, and anchored replacement.
- Run `mvn test`.

# Exit Criteria

- Approved file tool names resolve at startup.
- Reads/searches return deterministic hashline anchors.
- Replacements fail when anchors are stale or ambiguous by line/hash mismatch.
- All tests pass.
