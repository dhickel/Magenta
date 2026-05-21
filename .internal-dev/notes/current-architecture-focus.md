# Current Architecture Focus

Date: 2026-05-21

## Purpose

This is a running architecture focus note for Magenta's file, workspace, and orchestration model. It captures the intended direction for projects, agent workspaces, tasks/plans, workflows, jobs, outputs, temp work, and future refactor planning.

Update this file when workspace or orchestration assumptions change. Treat it as intended architecture, not proof of current implementation behavior.

## Current Focus

Magenta is currently tying together several systems that grew in parallel:

- Chat planning and task execution.
- Workflow execution.
- Job orchestration.
- Agent workspaces.
- Project workspaces.
- Output artifact tracking.
- Runtime file tools.

The near-term architectural goal is to make file handling predictable by clearly separating:

- Long-lived workspaces.
- Per-run execution/temp space.
- Explicit tracked outputs.
- Work unit abstractions.
- Orchestrator and visibility abstractions.

## Abstraction Families

Magenta has two primary abstraction families.

### Work Unit Abstractions

Work units are executable. They produce observable runtime state and may produce explicit outputs.

- Task/plan: the smallest executable unit. "Task" is the preferred product/domain term. "Plan" remains an existing implementation term and may appear in code, database tables, and older docs until renamed or wrapped later.
- Workflow: an executable composition of tasks, routing, approvals, validations, and handoff points.
- Job: a higher-level executable orchestration unit. Jobs can contain repeatable instructions, coordinate tasks and workflows, pursue one-off or recurring goals, and optionally preserve their own persistent workspace.

### Orchestrator And Workspace Abstractions

Orchestrator/workspace abstractions provide execution context, persistence, visibility, and coordination. They are not necessarily executable work themselves.

- Agent workspace: a persistent workspace owned by an agent. Agent-scoped work runs here when no project context is attached.
- Project workspace: a persistent shared workspace used for long-running work across agents. A project is not a work unit. It is a visibility and data workspace abstraction that lets multiple agents operate against shared context.
- Job workspace: optional persistent workspace for a specific job assignment/instance. Jobs are unusual because they are both work units and, when configured, workspace-owning orchestrators.

## Projects

A project is best understood as an agent-workspace-like abstraction without a single owning agent.

Projects should not have owner agents. Work is submitted through an agent, optionally with an attached project. When a project is attached, the effective durable workspace becomes the project workspace, regardless of which agent executes the task, workflow, or job.

Project responsibilities:

- Hold persistent shared files for long-running work.
- Provide visibility over agents, jobs, tasks, workflows, outputs, and runtime history related to that project.
- Provide a coordination point for multi-agent work.
- Leave room for workspace leasing and locking so multiple agents do not race on shared files.

Project non-responsibilities:

- A project is not itself an executable work unit.
- A project should not directly execute without an agent runtime path.
- A project should not own an agent as its permanent owner.

## Agents

Agents are executable actors with their own persistent workspace. Agent-scoped work uses the agent workspace as the effective durable workspace.

When work is submitted with a project context, the agent still performs the work, but project files and explicit outputs belong to the project workspace. The agent workspace remains available for agent-specific state, scratch, tools, and non-project work.

## Tasks And Plans

Tasks/plans are executable work units. "Task" and "plan" are currently used interchangeably in parts of the codebase. Future user-facing language should prefer task where it describes executable work, while preserving compatibility with existing plan tables, services, and APIs until a deliberate rename or facade is planned.

Task runs should always have per-run execution/temp space. Task runs should not gain their own stable persistent workspace across runs. Shared iteration should happen through the effective durable workspace, not by reusing task temp directories.

Task outputs:

- Only explicit outputs should be tracked as output artifacts.
- Temp files and incidental working files should not be indexed as outputs unless explicitly declared or published.
- Output artifacts should carry enough metadata to locate the run, task/plan, agent, project, job, effective workspace, and output path.

## Workflows

Workflows are executable work units that coordinate tasks and routing. They may need temp space for validation nodes, task-to-task intermediate state, and local workflow execution artifacts.

Workflow runs should have per-run execution/temp space. Workflow temp space should be retained until the workflow is terminal and marked complete or failed according to runtime state. Workflow final outputs should be explicit tracked outputs under the effective durable workspace.

Workflow handoff behavior can stay metadata-led for now. Future handoff files may be added later, but the current planning assumption is that workflow links can use run metadata, output artifact metadata, output directories, and chat/conversation IDs.

## Jobs

Jobs are both a work unit abstraction and, optionally, an orchestrator/workspace abstraction.

A job can:

- Own repeatable instructions.
- Run toward a one-off goal.
- Run perpetually or on recurrence.
- Dispatch tasks and workflows.
- Self-orchestrate around a goal.
- Be assigned under an agent workspace or a project workspace.
- Optionally keep a persistent job workspace.

A job cannot:

- Execute outside an agent runtime path.
- Receive direct task/workflow assignment as though it were an agent actor unless mediated by job orchestration.
- Share one persistent workspace across all assignments by default.

Persistent job workspace policy:

- Jobs may have persistent workspaces when explicitly flagged/configured.
- Each job assignment or job instance should get its own persistent job workspace when persistence is enabled.
- Multiple assignments of the same job definition should not accidentally share a job workspace.
- If job workspace persistence is disabled, job temp files follow normal run/temp retention behavior.
- Job explicit outputs still publish to the effective durable workspace, with metadata linking back to the job and job assignment/run.

## Effective Workspace Rule

Every work unit run should resolve to exactly one effective durable workspace:

```text
project attached -> project workspace
no project       -> agent workspace
```

Jobs may additionally have a persistent job workspace when configured. That job workspace is subordinate to the effective durable workspace and should be linked in metadata.

## Target Workspace Layout

Agent workspaces and project workspaces should share a similar shape:

```text
<workspace-root>/
  work/
  outputs/
  runs/
  scratch/
  jobs/
```

Suggested meanings:

- `work/`: persistent shared working files. This is where iterative cross-run work lives.
- `outputs/`: explicit tracked outputs only.
- `runs/`: per-run execution/temp directories or retained run evidence when configured.
- `scratch/`: durable scratch that is not output-indexed.
- `jobs/`: persistent job workspaces for configured job assignments.

Output paths should avoid collisions and remain traceable:

```text
outputs/tasks/<taskId>/<runId>/
outputs/workflows/<workflowId>/<runId>/
outputs/jobs/<jobAssignmentId>/<runId>/
```

Persistent job workspace example:

```text
jobs/<jobAssignmentId>/
```

## Runtime Aliases

File and shell tools should expose stable aliases during execution:

```text
workspace/   -> effective durable workspace root
work/        -> effective workspace work directory
outputs/     -> effective workspace output directory
run/         -> current run temp/execution directory
scratch/     -> effective workspace scratch directory
job/         -> current persistent job workspace, only when present
```

Project-scoped execution should make `workspace/` resolve to the project workspace. Agent-scoped execution should make `workspace/` resolve to the agent workspace.

## Temp And Run Retention

Temp and run files must be kept until their parent work is terminal and marked complete or failed.

Minimum retention rule:

- Do not delete task temp files while a task run is active.
- Do not delete workflow temp files while a workflow run is active or waiting.
- Do not delete job temp files while a job run or job assignment is active.
- Do not delete project or agent workspace files through run cleanup.
- Do not delete persistent job workspaces through run cleanup.

Configurable retention:

- Runtime settings should support retaining temp/run directories after terminal completion.
- Retained temp/run directories are for debugging, review, replay, or audit.
- Explicit outputs are durable independently of temp retention.

## Output Tracking Policy

Magenta should track explicit outputs, not incidental loose files.

Target behavior:

- Declared outputs and explicitly published outputs are indexed in `run_output_artifacts`.
- Output metadata records run, work unit, agent, project, job, workspace, output type, file path, and timestamps.
- Output downloads and inline reads remain confined under the configured data root.
- Working files under `work/`, `scratch/`, `run/`, or a job workspace are not output artifacts unless explicitly published.

The current loose artifact discovery behavior needs review before removal or narrowing. It may have been introduced as a hot fix or compatibility bridge. The refactor planning process must include a risk assessment pass that checks current implementation, tests, prior notes/chats where available, and database/runtime evidence if available, then recommends whether to remove, gate, migrate, or preserve it behind explicit configuration.

## Chat Files

Ordinary chat files are a separate system and can stay separate.

Current direction:

- Chat file storage remains conversation-scoped.
- Chat files do not need to become project/agent output artifacts by default.
- Chat execution can continue to use its own file context unless work is submitted into task/workflow/job orchestration.

## Submission And Assignment Rules

Work submission should remain explicit:

- Agents can run tasks, workflows, and jobs assigned or sent to them.
- Agents can run with an attached project context.
- Projects do not execute by themselves.
- Jobs can dispatch or self-run tasks/workflows assigned within the job's orchestration path.
- Jobs can be updated with instructions and configured persistence.
- Users can always enter chat directly when steering is needed.

## Leasing And Race Avoidance

Project workspaces must remain open to leasing and locking semantics.

Near-term lease expectation:

- Project-attached work should acquire a writable project workspace lease when mutating project files.
- Output paths should include work unit/run identifiers to avoid accidental collisions.
- The design should not assume only one agent will ever touch a project.

Future lease possibilities:

- Subtree-level leases for `work/`, `outputs/`, or job workspace subfolders.
- Read leases for inspection.
- Graceful drain for active agent turns.
- Conflict reporting in assignment state.

## Clean Data Root Strategy

For the upcoming refactor, prefer a clean test data root over a broad legacy migration.

Development/testing approach:

- Archive the old local data root before validating the new workspace layout.
- Start with a new clean root for implementation validation.
- Do not spend refactor scope on full historical output migration unless a review finds it is required for compatibility.

## Orchestrated Refactor Plan Requirements

Before implementation, run an orchestrated review and planning process.

Required passes:

1. Current-state divergence review:
   - Identify where current behavior diverges from this note.
   - Inspect services, repositories, schema, file tools, shell tools, task/plan execution, workflow execution, job execution, workspace leasing, and output APIs.
   - Produce concrete findings with file references.

2. Loose artifact discovery risk assessment:
   - Determine why loose artifact discovery exists.
   - Inspect tests, code paths, prior internal notes, and available local data/log evidence.
   - Assess compatibility risk if it is removed or made explicit.
   - Recommend removal, gating, migration, or preservation strategy.

3. Workspace/output risk and mitigation review:
   - Identify race conditions, path migration risks, data loss risks, API compatibility risks, and user-facing behavior changes.
   - Propose mitigation strategies before the implementation plan is written.

4. Testing strategy review:
   - Recommend unit, repository, service, integration, startup, and focused UI/browser checks.
   - Include path confinement, lease conflict, output materialization, temp retention, job persistence, project-scoped execution, and chat separation tests.

5. Implementation plan:
   - Convert the findings and risk assessments into a phase plan.
   - Keep code-modifying phases serial.
   - Include validation gates and rollback/compatibility notes.

6. Architecture review after implementation:
   - Verify adherence to this note.
   - Review approach quality, robustness, race behavior, path confinement, and documentation consistency.
   - Propose fixes before final validation.

## Recommended Testing Procedures

The refactor should include tests for:

- Effective workspace resolution with and without project context.
- Output placement under project workspace regardless of executing agent.
- Output placement under agent workspace when no project is attached.
- Explicit output artifact tracking only.
- Loose file non-discovery unless explicitly preserved by configuration.
- Temp retention while runs are active.
- Temp cleanup after terminal completion when retention is disabled.
- Temp retention after terminal completion when retention is enabled.
- Workflow waiting/resume behavior preserving temp state.
- Job assignment with persistent workspace enabled.
- Multiple job assignments receiving separate persistent workspaces.
- Job assignment with persistent workspace disabled.
- Workspace leases preventing conflicting project writes.
- Data-root path confinement for file tools and output downloads.
- Chat files remaining conversation-scoped and separate from output artifacts.

## Open Questions

- Whether output publication should copy files into `outputs/` or allow metadata to point at durable `work/` paths for some output types.
- Whether retained `runs/` directories should be considered temp, evidence, or both.
- Whether future handoffs should be file-backed under `handoffs/`, database-backed, or both.
- Whether a future UI should expose `work/`, `outputs/`, `runs/`, and job workspace files as separate panels.
- Whether subtree leases are needed in the first refactor or can wait until multi-agent project concurrency becomes active.
