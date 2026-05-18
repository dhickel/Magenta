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
4. Choose an **Owner Agent**.
5. Optionally fill description and Git repo URL.
6. Choose manager type and default model if needed.
7. Save.

The owner agent field is currently a plain agent dropdown in the project editor. Job and settings pages have selector-backed entity fields in some related areas, but project ownership itself may still be chosen from a dropdown.

## Edit A Project

Select a project from the sidebar. You can update:

- Name.
- Description.
- Git repo URL.
- Manager type.
- Default model.

Deleting a project is destructive. Confirm the project is no longer referenced by active jobs or outputs before deleting it.

## Workspace Section

For existing projects, the workspace section shows:

- Owner.
- Root kind.
- Display path.
- Member/link count.
- Lease ID.
- Mounted agent.
- Release requested state.

If a lease is active, the UI may show **Release workspace after current turn**. This requests release after the current agent turn rather than immediately breaking active work.

## Project Network And Memberships

The project network section shows the owner and linked agents. The agents section lists assigned members and roles.

Membership editing is limited in the current UI. If you need membership changes that are not exposed by the project page, use the relevant API or future UI once implemented.

## Active Jobs And Outputs

The active jobs section lists jobs associated with the project. The recent outputs section lists output artifacts from project jobs.

Use `/jobs` for job editing and `/outputs` for full artifact filtering, inline viewing, and downloads.

## Workspace Relationship To Agents And Jobs

Projects provide a shared context and workspace relationship. Agents execute assignments and may mount or link project workspaces depending on runtime state. Jobs can be associated with projects and produce outputs that appear under project filters.

Workspace leases indicate active ownership or use. A lease is an orchestration/runtime coordination record; it is not the same thing as a container, though container-backed execution may use the workspace while the lease is held.

## Common Errors

- **Name is required**: every project needs a name.
- **Owner agent is required**: create or choose an owner agent.
- **No agents exist**: create an agent before creating a project.
- **Project not found**: the project was deleted or the page is stale.
- **Workspace: ...**: workspace summary failed; inspect runtime configuration or logs.
- **Release Requested: true**: release has been requested and should complete after the current turn can drain.

## Alpha Limits

Project CRUD is available, but membership editing and deep workspace operations are still limited in the UI. Some project summary panels display raw owner, member, lease, run, or job IDs for traceability. Searchable selectors are used in related job/project fields where available, but exact workspace and lease diagnostics may still require reading displayed IDs.
