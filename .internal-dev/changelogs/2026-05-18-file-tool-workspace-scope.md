# Date

2026-05-18

# Change Summary

Implemented public alpha remediation bug-09 / domain 02 subplan 02. File tools now resolve allowed roots from active `OrchestrationTaskContext` instead of exposing the whole configured data root during orchestration-backed task execution.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileTools.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolServiceTest.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/AGENTS.md`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-09-high-tools-file-scope-data-root/report.md`
- `.internal-dev/knowledge/file-tool-workspace-scope-pattern.md`

# Behavioral Impact

Active task contexts with host workspace paths can read/write the current run workspace, the active output path through `outputs/...`, and the current project workspace through `projects/<projectId>/...`. Active agent-only contexts use that agent workspace. The legacy data-root fallback remains only when no orchestration context is active.

# Risks

Existing orchestration prompts or tests that relied on data-root-relative file paths during active task execution must switch to workspace-relative paths or the supported aliases.

# Validation

- Focused validation passed with `mvn -Dtest=AgentFileToolServiceTest,ChatToolRegistryTest,AgentShellToolServiceTest,OrchestrationRuntimeTest test`.
- `git diff --check` passed.
- Bounded Spring Boot startup reached a healthy app on ephemeral port `37939`.

# Follow-up Items

- Domain 02 subplans 03-07 remain out of scope for this change.
