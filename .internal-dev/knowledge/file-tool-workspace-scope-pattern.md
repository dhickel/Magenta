# Topic

File tool workspace scope resolution

# Source References

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolServiceTest.java`
- `.internal-dev/plans/public-alpha-remediation/02-workspace-tools-outputs/subplan-02-file-tool-workspace-scope.md`

# Key Takeaways

- File tools should resolve their allowed root per call because `OrchestrationTaskContextHolder` is thread-local and changes between normal chat, agent detail actions, and assignment execution.
- When an active context has `hostWorkspacePath`, the default file scope is the active assignment workspace, not `dataRoot` and not the broader agent workspace.
- `outputs/...` maps to the active `hostOutputPath` only for contexts that provide one.
- `projects/<projectId>/...` maps only to the current context project id through `WorkspaceDirectoryService.projectWorkspace(projectId)`.
- If there is an active agent context but no host workspace path, file tools can use that agent's managed workspace.
- The data-root fallback is intentionally limited to no-context chat/tool usage.

# Engine Relevance

This pattern keeps model-visible file tools aligned with the filesystem-backed runtime contract while preserving the existing non-orchestration chat fallback. Path validation still needs both lexical checks before IO and realpath checks after resolving existing files or nearest existing parents so traversal and symlink escapes cannot cross the selected scope root.

# Open Questions

- Later domain 02 project materialization work may change whether project paths are actual workspace links or direct project workspace roots. File tools currently follow the same current-project alias behavior already used by shell confinement.
