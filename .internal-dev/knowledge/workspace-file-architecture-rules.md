# Topic

Workspace/file architecture rules after the 2026-05-21 refactor.

# Source References

- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/service-graph.md`
- `.internal-dev/specifications/decisions.md`
- `.internal-dev/plans/workspace-file-architecture-refactor/implementation-plan.md`
- `.internal-dev/plans/workspace-file-architecture-refactor/agent-notes.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/orchestration-runtime.md`
- `docs/technical/data-model.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`

# Key Takeaways

- Projects are shared durable workspace and visibility records. They are not executable work units.
- `ownerAgentId` on projects is nullable legacy compatibility metadata.
- Effective durable workspace selection is centralized: `projectId` means project workspace; otherwise use the executing agent workspace.
- `workspaceId` remains compatibility metadata and must not be interpreted as project context.
- Tasks/plans and workflows use per-run temp space. They do not own stable persistent workspaces.
- Durable task outputs belong under `outputs/tasks/<taskId>/<runId>`.
- Durable workflow outputs belong under `outputs/workflows/<workflowId>/<runId>`.
- Jobs can opt into persistent per-assignment workspace at `jobs/<assignmentId>`.
- Durable job outputs belong under `outputs/jobs/<assignmentId>/<jobRunId>`.
- Runtime aliases are `workspace/`, `work/`, `outputs/`, `run/`, `scratch/`, and `job/` when an active job assignment/run has an opt-in persistent job workspace.
- `outputs/` points at the current run output directory during execution.
- Loose artifact discovery is a confined compatibility bridge, not the target output contract.
- New work should explicitly materialize or publish output artifacts.
- Workflow `WAITING` remains resumable and must not be collapsed to assignment failure.
- Async workflow task-node execution must propagate orchestration context.
- Chat files stay conversation-scoped under `chats/<conversationId>/files/` and are not output artifacts.

# Engine Relevance

Future agents should treat `EffectiveWorkspaceResolver` and `WorkspaceDirectoryService` as the source of path semantics. Do not reconstruct project/agent output paths in controllers or business services. When adding new task, workflow, or job execution paths, pass explicit `projectId` through assignment/input context and preserve `workspaceId` only for compatibility.

When changing cleanup, output publishing, or download behavior, verify that temp cleanup cannot delete project/agent workspaces, durable output directories, waiting workflow temp state, or persistent job workspaces. Use realpath confinement for any source file copied or registered as an output artifact.

# Open Questions

- When should loose artifact discovery default to off?
- What is the migration/deprecation path for legacy `OrchestrationJobService` and `orchestration_jobs`?
- What UX should expose persistent job workspace state, workspace browsing, and project membership management in the later services/UX alignment suite?
