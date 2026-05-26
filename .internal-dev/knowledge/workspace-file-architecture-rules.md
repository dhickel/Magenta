# Topic

Workspace/file architecture rules after the 2026-05-26 workspace, Work Area, run output, and job semantics lock.

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
- Application structural path segments and aliases must come from centralized layout helpers, not caller-side string concatenation or operator config.
- Data root contains application-owned `workspace/`, `chats/`, `agents/`, and `projects/`; agent execution roots live under `workspace/<agentWorkspaceId>/`.
- Work Areas are the user-facing workspace abstraction. New Work Area directories use stable DB ids under `workspace/<agentWorkspaceId>/workareas/<workAreaId>/`; display names stay DB-owned.
- Tasks/plans, workflows, and jobs use run-local staging under `runs/<runId>/outputs/` for model-facing execution output. They do not own stable persistent workspaces.
- During execution, model-facing `outputs/` resolves to the active run-local `runs/<runId>/outputs/`.
- Final output destinations are written only by backend completion, validation, or promotion logic.
- Jobless task/workflow final outputs promote to the agent workspace final `outputs/`; job-bound task/workflow/job final outputs promote to the bound Work Area or project output destination.
- Jobs bind to an agent, project, and Work Area. They do not own directories, persistent workspaces, or multi-task container filesystem state. Job workspace/output paths from older docs are legacy compatibility only.
- Loose artifact discovery is a confined compatibility bridge, not the target output contract.
- New work should explicitly materialize or publish output artifacts.
- Workflow `WAITING` remains resumable and must not be collapsed to assignment failure.
- Async workflow task-node execution must propagate orchestration context.
- Chat files stay conversation-scoped under `chats/<conversationId>/files/` and are not output artifacts.

# Engine Relevance

Future agents should treat `WorkspacePathLayout`, `EffectiveWorkspaceResolver`, and `WorkspaceDirectoryService` as the source of path semantics. Do not reconstruct project/agent/Work Area/run/output paths in controllers or business services. When adding new task, workflow, or job execution paths, pass explicit `projectId` and Work Area routing through assignment/input context and preserve `workspaceId` only for compatibility.

When changing cleanup, output publishing, or download behavior, verify that run-staging cleanup is retention-aware, keeps active/resumable runs safe, and cannot delete project/agent workspaces, Work Areas, final outputs, or waiting workflow state. Use realpath confinement for any source file copied or registered as an output artifact.

# Open Questions

- When should loose artifact discovery default to off?
- What is the migration/deprecation path for legacy `OrchestrationJobService` and `orchestration_jobs`?
- What diagnostic UX, if any, should expose internal workspace roots, run staging, and final outputs after the MVP Work Area/project browser is stable?
