# Service Architecture Contracts

## Scope

This artifact records the architecture contracts that are currently sound, the contracts that are broken or ambiguous, and the target contracts recommended for a durable Magenta business system.

## Findings

### Contracts To Preserve

- Controllers should remain transport adapters. Business rules belong in services and persistence details belong in repositories.
- Public plan/task/workflow run stream routes should submit durable assignments, not execute model work inline.
- Plan and workflow runs should snapshot definitions at run start so historical runs remain meaningful after definition edits.
- Workflow inbox messages and runtime direct-line agent inbox messages should remain separate tables and lifecycle models.
- Workspace path creation should remain centralized under `WorkspaceDirectoryService` and confined beneath the configured data root.
- Output reads/downloads should continue using real-path confinement checks.
- Tool transcripts should remain Magenta-owned context/audit data, not provider-only hidden state.
- File AI config should remain the model endpoint source; runtime settings should select and override configured model keys.

### Broken Or Ambiguous Contracts

- Execution authority: it is unclear whether `WorkAssignment`, `JobRun`, or `WorkflowRun` owns status, cancellation, retry, and completion.
- `WAITING`: it is unclear whether this status means inbox/user resume, workspace lease backoff, or queue-retry eligibility.
- Recurrence: public `job_recurrences` creates runs but not executable assignments, while agent schedules do create assignments.
- Workflow sync execution: the current sync path shares async start behavior and can execute twice.
- Workspace identity: `jobs/<id>` and `jobs/<id>/workspace` are both used as job workspace roots.
- Workspace attribution: `workspaceId` is accepted as metadata without validating the workspace exists.
- Workflow outputs: artifacts can be written into workflow temp directories and lack full output context.
- System chat settings: persisted settings do not map to a concrete chat execution surface.
- Delete ownership: cross-domain cleanup is split between controllers, services, repositories, and FKs without one clear rule.
- Repository bootstrap: code-level DDL and `schema.sql` are both treated as authoritative but do not match exactly.

## Risk Assessment

Ambiguous contracts create more operational risk than missing features. They cause code to be correct only under the assumptions of one caller. As the system adds business workflows, subagents, and durable jobs, these assumptions will turn into data loss, duplicate execution, and audit gaps unless they are made explicit.

## Recommendations

### Target Execution Contract

Submitted work should enter through `AssignmentService`.

`WorkAssignment` should be the authoritative lifecycle for queued/running/waiting/cancelled/failed/succeeded execution. `JobRun`, `WorkflowRun`, and `PlanRun` should be child records whose lifecycle transitions are driven from assignment execution.

Cancellation should be checked at these boundaries:

- before assignment acquisition
- before each job item
- before workflow ready-node execution
- before long task/chat execution where practical
- after model/tool execution before terminal persistence

### Target Workspace Contract

Every workspace owner type should have one canonical record root and one canonical filesystem root. If job workspaces are `jobs/<id>/workspace`, both `WorkspaceService` and `WorkspaceDirectoryService` must agree.

Assignment/job `workspaceId` should mean a valid existing workspace, not best-effort metadata. If metadata-only attribution is desired, use a different field name.

### Target Output Contract

Every output artifact should carry:

- `run_id`
- `run_type`
- `output_name`
- `artifact_type`
- durable `file_path`
- optional but validated `agent_id`, `job_id`, `project_id`, `workspace_id`

Workflow-native outputs should be first-class artifacts, not temp files.

### Target Chat/Audit Contract

Every chat turn path should persist a consistent minimum event set:

- user message
- assistant message or explicit terminal error
- tool execution events when tools run
- context usage snapshot
- compaction event when compaction occurs
- audit sequence entries for lifecycle reconstruction

Interrupt API responses should distinguish “queued for next checkpoint” from “actively cancelling/steering model execution.”

### Target Schema Contract

`schema.sql` should remain canonical only if repository bootstrap can reproduce its required tables, indexes, and compatibility columns. Otherwise, move to explicit migrations and keep repository DDL minimal.

Delete/purge ownership should be repository-local where possible, with tests under `foreign_keys=true`.

## Follow-ups

- Turn the target contracts into implementation phases.
- Update package `AGENTS.md` files after the team decides the execution, security, workspace, and schema contracts.
- Add contract tests before feature growth in workflows, jobs, and subagent orchestration.
