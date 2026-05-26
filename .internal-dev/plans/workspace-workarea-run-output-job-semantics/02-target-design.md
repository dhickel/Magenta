# Target Design

## Filesystem Layout

```text
root/
  workspace/
  chats/
  agents/
  projects/

workspace/<agentWorkspaceId>/
  home/
  workareas/<workAreaId>/
  outputs/
  runs/<runId>/outputs/
```

- `root/workspace/` contains agent workspace roots. `agentWorkspaceId` may initially equal `agentId` after plain-segment validation, but callers must go through layout helpers rather than concatenate ids.
- `root/agents/` is for agent metadata/internal structures, not the execution workspace root.
- `root/projects/` contains shared project directories that are fully browsable/editable through MVP project browsing.
- There is no `temp/`, no `scratch/` in the target contract, and no singular `run/` physical directory. `run/` may remain a model-facing alias for current run staging.

## Work Areas

- WorkArea is the primary user-facing workspace abstraction.
- New Work Area directories are created at `<agent workspace>/workareas/<workAreaId>/` or the equivalent project-owned Work Area area when project Work Areas are implemented.
- Use `work_areas.id` as the stable disk segment. Display names remain in `work_areas.display_name`.
- Keep `work_areas.area_relative_path` as a backend physical relative-path field for compatibility/backfill. New rows should set it to `workareas/<id>`.
- Home remains system-owned and lives at `home/`. It is not unmarked or renamed through normal user flows.

## Run Staging And Promotion

- Task/workflow/job execution writes files to run-local staging: `runs/<runId>/outputs/` under the relevant agent workspace root.
- Agent-facing `outputs/` resolves to the run-local output staging directory for the active run.
- On successful backend completion/validation, declared outputs are copied/promoted from run-local outputs to final destinations.
- Jobless task/workflow final outputs go to the agent workspace final `outputs/`.
- Job-bound task/workflow/job final outputs go to the bound Work Area or project output destination.
- Run staging directories are retained for at least one day and can be cleaned only by a retention-aware cleanup path.

## Job Semantics

- A job is task-like executable work bound to an agent, project, and Work Area, with possible future job-prompt context.
- Jobs do not own directories, persistent workspaces, or multi-task container filesystem state.
- Job definitions may keep metadata, recurrence, item sequencing, and future prompt profile/context fields, but directory ownership and persistent job workspace options are legacy compatibility only.
- Job-bound output routing is a route decision on the run/assignment, not a job-owned filesystem destination.

## Static Path Source Of Truth

- Introduce one application-owned static layout source under `io.mindspice.magenta2.ai.orchestration.workspaces`, named `WorkspacePathLayout`.
- `WorkspacePathLayout` should expose constants for structural segments and aliases: `workspace`, `agents`, `projects`, `chats`, `home`, `workareas`, `runs`, `outputs`, `root`, `run`, and any retained legacy marker names.
- `WorkspacePathLayout` should expose helper methods for relative path composition, for example:
  - `agentWorkspaceRoot(agentWorkspaceId)`
  - `agentHome(agentWorkspaceId)`
  - `workArea(agentWorkspaceId, workAreaId)`
  - `runRoot(agentWorkspaceId, runId)`
  - `runOutputs(agentWorkspaceId, runId)`
  - `agentFinalOutputs(agentWorkspaceId)`
  - `chatFiles(conversationId)`
  - `projectRoot(projectId)`
- `WorkspaceDirectoryService` remains the service that confines, creates, and resolves paths under data root. Callers should not rebuild structural relative paths manually.
- Prompt text, file/shell tool alias parsing, controllers, tests, docs, and package guides should reference the same vocabulary and avoid ad hoc legacy strings.

## API And UI Contract

- Non-job task/workflow submission payloads require `runDisplayName`.
- Queued assignments persist `run_display_name`; actual plan/workflow run rows copy it when allocated.
- MVP browser UX exposes selected Work Areas and projects for browse/edit. Internal workspace root/runs/outputs can be diagnostic/read-only later.
- Work Area/project browser interactions stay HTMX-first and service-owned; JavaScript remains narrow and justified only where it is already the simpler path.

## Deferred Follow-Ups To Record

- Direct write-blocking to final output directories and important system structures.
- Agent metadata/home semantic expansion, including possible `/agents/<agentId>/home` alias into assigned workspace `home/`.
- Advanced unrestricted filesystem browser.
- Project git behavior.
