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

## Work Area File Explorer

Avatar Work Areas expose a confined file explorer for agent or project workspace files. The explorer is a details/list view, not a card grid. It shows table columns for name, file type, size, created timestamp, last modified timestamp, tags, and row actions. Selecting a row updates a separate inspector panel with metadata, full tags, and available operations.

The explorer supports:

- opening a Work Area by clicking its card (no separate Browse button);
- Work Area-confined folder navigation through toolbar and breadcrumb controls;
- creating folders, `.txt` files, and `.md` files;
- Markdown viewing with Rendered and Text tabs;
- plain text raw viewing/editing;
- contained image viewing and safe download links;
- custom tags and note labels for files and directories;
- rename, delete confirmation, copy, and move operations.

Copy and move use a directory-picker popover and remain under the selected Work Area; users do not type internal destination paths. Unsupported or binary files do not show a row View action. The backend rejects traversal, absolute paths, symlink path components, unsafe text-edit extensions, oversized text saves, protected Home/system Work Areas, active Work Area descendants, and Work Areas referenced by queued or running assignment metadata.

Tag editing follows the shared progressive search selector interaction. Existing tags and create-new flows are both supported, tag options are filtered by selected item type (file or directory), and wrong-type assignments are rejected server-side.

## Project Network And Memberships

The project network section shows linked agents. The agents section lists assigned members and roles, and provides HTMX controls to add a member, choose a role, or remove a member.

Membership removal is guarded while that agent has active assignments or holds the active project workspace lease. If removal fails, let the active work complete, cancel it, or requeue workspace-blocked assignments after the lease clears.

## Active Jobs And Outputs

The active jobs section lists jobs associated with the project and links to the real job detail page. The recent outputs section lists output artifacts attributed to the project workspace.

Use `/jobs` for job editing and `/outputs` for full artifact filtering, inline viewing, and downloads.

## Workspace Relationship To Agents And Jobs

Projects provide shared context and a durable workspace. Agents execute assignments. When a task, workflow, or job submission includes a project, the project workspace is the effective durable workspace for files and outputs. Without a project, the executing agent workspace is used.

During execution, `outputs/` means the active run's staging area. After backend completion, validation, or promotion, project-scoped output artifacts appear under project filters and are promoted to the selected project or Work Area output destination. Jobs do not own separate workspace directories.

The normal user workflow is to browse and edit project directories and Work Areas. Internal agent workspace roots, run staging, and system output structures are diagnostic surfaces rather than normal project management controls.

Workspace leases indicate active ownership or use. A lease is an orchestration/runtime coordination record; it is not the same thing as a container, though container-backed execution may use the workspace while the lease is held.

## Common Errors

- **Name is required**: every project needs a name.
- **No agents exist**: create an agent before creating a project.
- **Project not found**: the project was deleted or the page is stale.
- **Workspace: ...**: workspace summary failed; inspect runtime configuration or logs.
- **Release Requested: true**: release has been requested and should complete after the current turn can drain.

## Alpha Limits

Project CRUD and membership editing are available in the Projects UI. Project workspace lease controls remain limited to release requests and inspection there; deeper file operations are handled through the Avatar Work Area file explorer. Some project summary panels display raw member, lease, run, or job IDs for traceability. Searchable selectors are used in related job/project fields where available, but exact workspace and lease diagnostics may still require reading displayed IDs.
