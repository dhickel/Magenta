# Phase 05 - Jobs And Projects Operational Surfaces

## Context

Jobs and projects currently have enough backend intent to be valuable, but the UI/API contracts are broken and the data model boundaries are unclear. Jobs need a manual interface similar to plans, and projects need a robust overview with agents and workspace context.

## Goal

Make jobs and projects reliable operational entities. Jobs should manage a complex deliverable through multiple task/workflow items. Projects should own a project directory, optional git source, assigned agents, related jobs, outputs, and status overview.

## In Scope

- Job model/API convergence from Phase 01 completed contract.
- Manual job editor.
- Job overview and detail pages.
- Project create/edit/detail repair.
- Project agent assignment UX.
- Project directory/git metadata display.
- Project overview dashboard.

## Out of Scope

- Full agent-created job/workflow chat loop.
- Git clone/pull automation unless already supported by workspace service.
- Multi-agent project collaboration beyond assignment/membership visibility.

## Implementation Steps

1. Job creation flow.
   - Allow draft jobs with no items.
   - Required fields:
     - title;
     - owner agent;
     - optional project;
     - summary/goal;
     - worktype profile;
     - default model;
     - deliverable definition.
   - Do not make users paste JSON for job items.
   - Implement create/edit actions with HTMX form posts/puts by default.

2. Job item editor.
   - Structured list editor for ordered items.
   - Item fields:
     - key;
     - type: plan/task template or workflow;
     - selected plan/workflow;
     - input mappings;
     - model override;
     - priority;
     - retry count;
     - continue on failure.
   - For large deliverables, add a job-level "deliverable breakdown" list that maps high-level outcomes to job items.
   - Validate item references before save.
   - Use HTMX partial swaps for add/edit/remove/reorder item operations where practical.

3. Job dashboard/detail.
   - Top summary:
     - status;
     - owner agent;
     - project;
     - workspace;
     - current/last run;
     - output directory;
     - failed/waiting item counts.
   - Main:
     - item sequence table;
     - run timeline;
     - recent outputs;
     - inbox/waiting approvals related to job.
   - Side:
     - settings/worktype;
     - recurrence if enabled;
     - assignment queue links.
   - Prefer HTMX section refreshes for timeline, recent outputs, and queue-linked fragments.

4. Job submission.
   - Replace direct "Run" with "Submit to agent".
   - A job detail page may show run history, but the primary action should enqueue or assign work to the owner agent.
   - If a lower-level API still creates `JobRun` directly, hide it from normal UI.

5. Project create flow.
   - Use canonical fields:
     - name;
     - description;
     - owner agent;
     - git repo URL;
     - project directory/workspace;
     - worktype profile;
     - model.
   - Owner agent select must load real agents.
   - If no agents exist, project create should explain the missing dependency and link to agents.
   - Use HTMX for project create/edit forms and assignment updates.

6. Project workspace and git.
   - `ProjectService` already creates a project workspace directory.
   - Add API response that exposes safe workspace metadata.
   - If `gitRepoUrl` is set, show it and future intended sync status.
   - Do not implement git clone/pull unless specifically scoped.

7. Project overview.
   - Project detail should show:
     - active jobs;
     - assigned agents and roles;
     - workspace/directory;
     - recent outputs;
     - recent events;
     - project network/messages if supported.
   - "Network" should not render an object as if it were an array. The current `/api/projects/{projectId}/network` returns a `NetworkResponse`, so either change API to return events/messages or update UI to render members correctly.
   - Keep JavaScript optional and minimal; use it only when HTMX would be materially more complex than the behavior being delivered.

8. Tests.
   - Job create draft with no items.
   - Job add item validates reference.
   - Job output returns array shape.
   - Project create requires owner agent.
   - Project detail loads workspace.
   - Project list/detail use `name`/`description`.

## Validation

- `mvn test`
- Startup smoke.
- Browser validation:
  - create project with owner agent;
  - edit project name/description/git URL;
  - assign another agent;
  - create job under project;
  - add plan and workflow job items;
  - verify core job/project edit flows are HTMX-driven;
  - submit job to agent;
  - dashboard shows project and job activity.

## Exit Criteria

- Projects are no longer broken in UI.
- Jobs can be manually created and edited with structured items.
- Jobs and projects have robust overview pages suitable for operational use.
