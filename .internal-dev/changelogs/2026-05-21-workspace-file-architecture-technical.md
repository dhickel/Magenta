# Date

2026-05-21

# Change Summary

Technical closeout for the workspace/file architecture refactor. This record captures the implemented architecture contract and the fragile areas future agents should preserve during final validation, review, and follow-on work.

# Files

Primary implementation areas from phases 02 through 06:

- `ai/orchestration/workspaces`: effective workspace resolution, directory layout helpers, output artifact materialization, loose-discovery confinement, output attribution.
- `ai/chat/plan` and `ai/chat/task`: task/plan run metadata, effective output paths, explicit publish support.
- `ai/chat/tool`: runtime alias resolution for file and shell tools.
- `ai/orchestration/workflow`: workflow temp/output separation, waiting/resume behavior, async task-node context propagation.
- `ai/orchestration/runtime`: assignment project context, job run/workspace policy, job output attribution.
- `api/web`: additive `projectId` and `persistentWorkspaceEnabled` payload support plus project UI copy/remediation.

Closeout documentation files:

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
- Relevant package `AGENTS.md` guides under `ai/orchestration`, `ai/orchestration/workspaces`, `ai/chat/plan`, and `ai/chat/tool`.

# Behavioral Impact

The refactor establishes one effective durable workspace per work-unit run:

- `projectId` present: use the project workspace.
- `projectId` absent: use the executing agent workspace.
- `workspaceId`: compatibility metadata, not a substitute for `projectId`.

Runtime temp and durable output paths are separated:

- Task temp stays in run temp space; durable outputs use `outputs/tasks/<taskId>/<runId>`.
- Workflow temp stays in `runtime/workflow-runs/<runId>`; durable outputs use `outputs/workflows/<workflowId>/<runId>`.
- Job persistent workspace is opt-in and assignment-scoped at `jobs/<assignmentId>`; durable outputs use `outputs/jobs/<assignmentId>/<jobRunId>`.

Tool alias contract:

- `workspace/`: effective durable workspace root.
- `work/`: effective workspace `work/`.
- `outputs/`: current run output directory.
- `run/`: current run temp/execution directory.
- `scratch/`: effective workspace `scratch/`.
- `job/`: current persistent job workspace when one exists for the active job assignment/run.

Output artifact policy:

- Explicit materialization/publishing is the target output contract.
- Loose discovery is compatibility-gated and realpath-confined under the data root and expected output directory.
- Chat files under `chats/<conversationId>/files/` remain separate from `run_output_artifacts`.

Runtime correctness changes:

- Workflow `WAITING` maps to assignment `WAITING` and resumes the original run.
- Workflow task-node async execution propagates the active orchestration context.
- Job artifacts carry additive job assignment/run attribution.

# Risks

- The final review should inspect path confinement, symlink handling, cleanup boundaries, and output attribution carefully.
- Loose discovery remains present as compatibility behavior; future work should avoid depending on it for new workflows.
- Legacy `OrchestrationJobService` and `orchestration_jobs` remain intentionally untouched.
- UI support for persistent job workspace exists through the form field, but a fuller UX review is queued for the later services/UX alignment suite.

# Follow-up Items

- Run final validation, Spring context smoke, and xhigh architecture/code review.
- Keep the plan artifacts unarchived until user approval.
- Queue any legacy job migration or loose-discovery default-off decision as a separate planned effort.
