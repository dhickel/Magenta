# Topic

File tool workspace scope resolution

# Source References

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolServiceTest.java`
- `.internal-dev/plans/public-alpha-remediation/02-workspace-tools-outputs/subplan-02-file-tool-workspace-scope.md`

# Key Takeaways

- File tools should resolve their allowed root per call because `OrchestrationTaskContextHolder` is thread-local and changes between normal chat, agent detail actions, and assignment execution.
- When an active context has a selected Work Area, the default file scope is that Work Area; otherwise the default is the effective durable workspace, not `dataRoot`.
- `outputs/...` maps to the active run-local output staging directory only for contexts that provide one. That physical path is `runs/<runId>/outputs/` under the relevant agent workspace root.
- Final output destinations are backend promotion targets. File tools should not treat final output directories as the model-facing `outputs/` alias.
- `projects/<projectId>/...` maps only to the current context project id through `WorkspaceDirectoryService.projectWorkspace(projectId)`.
- If there is an active agent context but no host workspace path, file tools can use that agent's managed workspace.
- The data-root fallback is intentionally limited to no-context chat/tool usage.

# Engine Relevance

This pattern keeps model-visible file tools aligned with the filesystem-backed runtime contract while preserving the existing non-orchestration chat fallback. Path validation still needs both lexical checks before IO and realpath checks after resolving existing files or nearest existing parents so traversal and symlink escapes cannot cross the selected scope root.

# Open Questions

- Project materialization may change whether project paths are actual workspace links or direct project workspace roots. File tools should follow centralized workspace layout helpers and the same current-project alias behavior used by shell confinement.
