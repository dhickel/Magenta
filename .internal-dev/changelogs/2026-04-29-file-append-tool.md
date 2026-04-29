## Date
2026-04-29

## Change Summary
Added a first-class `file_append` chat tool so agents can accumulate report, outline, note, and log content without rewriting prior file contents.

## Files
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileTools.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/AGENTS.md`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistryTest.java`

## Behavioral Impact
Agents can now append text directly to existing files, or create-and-append when explicitly requested, instead of using complete-file overwrite flows for append-style work.

## Risks
Append operations are intentionally literal; callers must include any desired leading or trailing newline in the appended content.

## Follow-up Items
- Consider whether prompts for long-running research workflows should explicitly prefer `file_append` for outline accumulation.
