# Projects And Workspaces

Use `/projects` to organize work around a project record and inspect its workspace, linked agents, jobs, and outputs.

## Page Layout

The projects page has:

- A sidebar with **New Project** and project rows.
- An editor for project fields.
- Existing-project sections for workspace, network, agents, active jobs, recent outputs, and advanced metadata.

## Create A Project

1. Open `/projects`.
2. Select **New Project**.
3. Fill **Name**.
4. Optionally choose a **Legacy Initial Agent**.
5. Optionally fill description and Git repo URL.
6. Choose manager type and default model if needed.
7. Save.

Projects are shared workspaces, not executable work units. The legacy initial agent field is compatibility metadata and can be blank. Add agents as members when they need to work with the project.

## Edit A Project

Select a project from the sidebar. You can update:

- Name.
- Description.
- Git repo URL.
- Manager type.
- Default model.

Deleting a project is destructive. Magenta blocks deletion while active assignments, leases, jobs, or runs still reference the project. Confirm the project is no longer active before deleting it.

## Workspace Section

For existing projects, the workspace section shows:

- Legacy initial agent, when compatibility metadata exists.
- Root kind.
- Display path.
- Member/link count.
- Lease ID.
- Mounted agent.
- Release requested state.

If a lease is active, the UI may show **Request release after current turn**. This requests release after the current agent turn rather than immediately breaking active work.

## Project Network And Memberships

The project network section shows linked agents. The agents section lists assigned members and roles, and provides HTMX controls to add a member, choose a role, or remove a member.

Membership removal is guarded while that agent has active assignments or holds the active project workspace lease. If removal fails, let the active work complete, cancel it, or requeue workspace-blocked assignments after the lease clears.

## Active Jobs And Outputs

The active jobs section lists jobs associated with the project and links to the real job detail page. The recent outputs section lists output artifacts attributed to the project workspace.

Use `/jobs` for job editing and `/outputs` for full artifact filtering, inline viewing, and downloads.

## Workspace Relationship To Agents And Jobs

Projects provide shared context and a durable workspace. Agents execute assignments. When a task, workflow, or job submission includes a project, the project workspace is the effective durable workspace for files and outputs. Without a project, the executing agent workspace is used.

Project-scoped outputs appear under project filters and are written under the project workspace:

- Tasks: `outputs/tasks/<taskId>/<runId>`
- Workflows: `outputs/workflows/<workflowId>/<runId>`
- Jobs: `outputs/jobs/<assignmentId>/<jobRunId>`

Workspace leases indicate active ownership or use. A lease is an orchestration/runtime coordination record; it is not the same thing as a container, though container-backed execution may use the workspace while the lease is held.

## Common Errors

- **Name is required**: every project needs a name.
- **No agents exist**: create an agent before creating a project.
- **Project not found**: the project was deleted or the page is stale.
- **Workspace: ...**: workspace summary failed; inspect runtime configuration or logs.
- **Release Requested: true**: release has been requested and should complete after the current turn can drain.

## Alpha Limits

Project CRUD and membership editing are available in the UI. Deep workspace operations remain limited to release requests and inspection. Some project summary panels display raw member, lease, run, or job IDs for traceability. Searchable selectors are used in related job/project fields where available, but exact workspace and lease diagnostics may still require reading displayed IDs.
