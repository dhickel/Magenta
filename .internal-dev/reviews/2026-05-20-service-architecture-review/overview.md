# Service Architecture Review

## Scope

This review covers the current Magenta Spring Boot architecture across chat, tools, context management, audit history, planning/tasks, runtime assignments, jobs, workflows, workspaces, outputs, settings/configuration, API/HTMX surfaces, and SQLite persistence.

Inputs were produced through the orchestrated review suite:

- A0 domain inventory
- A1 chat/context/threading/audit review
- A2 tool execution/history/state review
- A3 plans/tasks lifecycle review
- A4 runtime orchestration/jobs review
- A5 workflow engine review
- A6 workspaces/outputs review
- A7 API/frontend surface review
- A8 configuration integration review
- A9 persistence/schema review
- A10 cross-integration synthesis

The review was read-only. No production code was changed and no automated tests were run.

## Findings

### Critical: workflow synchronous execution can duplicate a run

`WorkflowRunner.runSynchronously` starts a run through the same path that submits async execution, then immediately executes the same persisted run synchronously. The assignment and job workflow paths call this synchronous method, so a workflow run can race itself, duplicate task-node execution, create duplicate approvals or artifacts, and finish nondeterministically.

Evidence:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`

### High: execution state has too many partial owners

Assignments, `job_runs`, and `workflow_runs` each hold lifecycle state, but cancellation, failure, recurrence, checkpoint, and terminal transitions are not consistently bridged. A `JOB_RUN` assignment can fail or complete while its durable `job_runs` row remains `RUNNING`; public job recurrence creates inert `job_runs` without assignment execution; `WAITING` assignments are still picked up by normal polling.

Evidence:

- `OrchestrationRunnerService`
- `JobService`
- `OrchestrationRuntimeRepository`
- `OrchestrationEventService`

### High: chat turn history is not a single durable contract

Plain SSE chat appears to bypass the same audit and assistant-message persistence pipeline used by tool turns. Chat memory writes use whole-conversation delete/reinsert snapshots, which can erase newer messages if a stale snapshot is saved. Interrupts can be accepted during model call phases but are only consumed at tool checkpoints.

Evidence:

- `ChatService`
- `ContextManagementAdvisor`
- `ChatMemoryRepository`
- `ActiveTurnRegistry`
- `ChatController`

### High: tool and runtime settings are partly unenforced

System-chat settings are persisted and visible in the settings UI, but chat execution does not consume the system-chat model, prompt, context limit, or approved tools. Shell tool allowlists are captured at bean construction, so runtime settings/profile edits do not affect `shell_exec` until restart.

Evidence:

- `RuntimeSettings`
- `RuntimeSettingsService`
- `AgentShellToolService`
- `AgentOrchestrationController`
- `ChatService`

### High: workspace identity and output attribution drift across domains

Job workspace records point at `jobs/<id>`, while `WorkspaceDirectoryService.jobWorkspace()` creates `jobs/<id>/workspace`. Workflow output artifacts are materialized under workflow temp paths without full attribution context. Assignment/job `workspaceId` values are accepted without verifying the workspace exists, then copied into output artifact metadata.

Evidence:

- `WorkspaceService`
- `WorkspaceDirectoryService`
- `WorkflowRunner`
- `OutputArtifactService`
- `AssignmentService`
- `JobService`

### High: plan/task lifecycle gates are inconsistent

The REST plan finalize endpoint does not actually finalize drafts, saved plan/task submit routes accept incomplete or draft definitions, and anonymous plan execution can finish without validator-gated `plan_complete`.

Evidence:

- `PlanController`
- `TaskController`
- `PlanService`
- `ChatService`
- `TerminalTurnRepair`

### High: API/HTMX surfaces can silently lose domain fields

The large HTMX `OrchestrationController` reconstructs workflow definitions through paths that can drop advanced fields like `maxConcurrency` and `uiLayout`. This creates divergence between REST and UI behavior and contradicts the thin-controller architecture rule.

Evidence:

- `OrchestrationController`
- `WorkflowController`
- `WorkflowDefinition`

### High: delete and purge paths leave orphaned or blocked rows

Workspace owner deletion does not delete `workspace_leases`, which can block FK-enabled deletion. Agent hard-delete can orphan `assignment_conversation_links`. Plan chat messages can orphan through task deletion paths that bypass `PlanController`.

Evidence:

- `WorkspaceRepository`
- `AgentProfileService`
- `OrchestrationRuntimeRepository`
- `PlanRepository`
- `TaskController`

## Risk Assessment

The dominant system risk is contract drift at integration boundaries. Individual domains are mostly recognizable and coherent, but several cross-domain invariants are implicit:

- what owns execution state
- what makes a workflow/job/task cancellable
- what workspace identity means
- how output artifacts are attributed and retained
- which chat turn events must always appear in memory and audit
- whether runtime settings are live policy or startup defaults
- whether repository bootstrap is equivalent to `schema.sql`

These risks matter because Magenta is intended to become a durable business system. The current shape can lose state, duplicate work, misattribute artifacts, leave stale database rows, and present UI/API controls that do not enforce the behavior they imply.

## Recommendations

1. Define one authoritative execution state model. Prefer `WorkAssignment` as the durable execution owner, with `JobRun` and `WorkflowRun` as child records whose transitions are driven by assignment execution.
2. Split async and sync workflow run creation so `runSynchronously` cannot submit background execution.
3. Make chat turn persistence explicit for every path: user, assistant, tool, context, compaction, error, and audit events.
4. Replace whole-conversation memory rewrite for append/finalize paths or guard it with a per-conversation repository contract.
5. Treat runtime tool settings as live policy or rename them as startup-only. Remove or hide system-chat fields until a concrete consumer exists.
6. Establish a canonical workspace owner/path contract and validate `workspaceId` at assignment/job creation.
7. Require output artifacts to carry a consistent attribution context for run type, run id, workspace, and optional agent/job/project.
8. Move HTMX mutation logic out of `OrchestrationController` into services or focused request mappers that preserve complete domain records.
9. Make delete/purge ownership explicit in repositories and add FK-enabled cleanup tests.
10. Add schema drift tests comparing repository bootstrap metadata to canonical `schema.sql`.

## Follow-ups

- Create a remediation plan under `.internal-dev/plans/` after the review is accepted.
- Prioritize the workflow duplicate-execution bug before relying on workflow assignments in production-like runs.
- Decide whether open-alpha security docs or `api/web/AGENTS.md` is the security source of truth, then update the stale side.
- Decide whether saved plan chat is intended to be deterministic editor assistance or model-backed draft mutation.
