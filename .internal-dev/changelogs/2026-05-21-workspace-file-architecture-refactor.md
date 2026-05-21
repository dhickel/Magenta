# Date

2026-05-21

# Change Summary

Closed out the workspace/file architecture refactor documentation after phases 01 through 06 landed. The implemented behavior now documents projects as shared durable workspace/visibility records, effective workspace selection by `projectId`, separate per-run temp space, durable task/workflow/job output paths, runtime aliases, compatibility-gated loose artifact discovery, resumable waiting workflows, opt-in per-assignment job workspaces, explicit project submission context, and chat-file separation.

# Files

- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/orchestration-runtime.md`
- `docs/technical/data-model.md`
- `docs/technical/api-reference.md`
- `docs/technical/chat-planning-tasks.md`
- `docs/technical/workflow-engine.md`
- `docs/technical/services.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/end-user/jobs.md`
- `docs/end-user/plans-and-tasks.md`
- `docs/end-user/workflows.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/AGENTS.md`
- `.internal-dev/changelogs/2026-05-21-workspace-file-architecture-technical.md`
- `.internal-dev/knowledge/workspace-file-architecture-rules.md`
- `.internal-dev/plans/workspace-file-architecture-refactor/agent-notes.md`
- `.internal-dev/plans/workspace-file-architecture-refactor/orchestration-state.md`

# Behavioral Impact

Documentation now matches the implemented workspace/file behavior:

- Project-scoped task, workflow, and job runs use the project workspace as the effective durable workspace.
- Agent-scoped runs use the executing agent workspace.
- Task outputs live under `outputs/tasks/<taskId>/<runId>`.
- Workflow outputs live under `outputs/workflows/<workflowId>/<runId>`.
- Job outputs live under `outputs/jobs/<assignmentId>/<jobRunId>`.
- Jobs only keep persistent per-assignment workspace state when `persistentWorkspaceEnabled` is enabled.
- Workflow `WAITING` assignments remain resumable.
- Direct task, plan, workflow, and job submissions can carry `projectId` while retaining `workspaceId` compatibility.
- Ordinary chat files remain separate from output artifacts.

# Risks

- This closeout is documentation-only; the final validation agent still needs to run code validation and xhigh review.
- Legacy job runtime tables and `OrchestrationJobService` remain intentionally preserved for compatibility.
- Loose artifact discovery remains compatibility-on at service policy level and should not be treated as the target output contract for new work.

# Follow-up Items

- Run the final validation and xhigh architecture/code review for the whole refactor.
- Do not archive the plan directory until the user agrees the full refactor is complete.
- Consider a later migration/deprecation plan for legacy orchestration job tables after caller usage is proven.
