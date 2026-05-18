# Shell Tool Confinement Pattern

## Topic

Public-alpha shell tool confinement for filesystem-backed orchestration.

## Source References

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationTaskContext.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `.internal-dev/plans/public-alpha-remediation/02-workspace-tools-outputs/subplan-01-shell-tool-confinement.md`

## Key Takeaways

- Treat wildcard shell command execution as an unsafe operator override, not a default or migration fallback.
- Keep wildcard executable override separate from path and wrapper confinement. Even with wildcard enabled, wrapper executables, shell-control syntax, absolute filesystem paths, and parent traversal should fail before `ProcessBuilder` starts.
- Prefer active run context over broad agent context. When `hostWorkspacePath` is present, shell cwd resolution should start from that assignment workspace and only branch to the run output path or the current linked project scope.
- Legacy config seeding should not silently import `*` into runtime agent profiles. Drop it unless the explicit unsafe shell override is true.

## Engine Relevance

This pattern keeps model-accessible shell behavior bounded in the filesystem runtime without pretending `ProcessBuilder` is an OS sandbox. Future file, output, and project workspace tools should use the same source-of-truth ordering: active run workspace first, explicit linked scopes second, data-root fallback only for non-orchestration legacy tool calls.

## Open Questions

- Domain 02 subplan 04 still needs to decide how project workspaces are materialized under `workspace/projects/{projectId}` for user-visible parity.
- Later validation should decide whether wildcard approved-tools semantics should also require an operator override outside the shell-specific bug-08 scope.
