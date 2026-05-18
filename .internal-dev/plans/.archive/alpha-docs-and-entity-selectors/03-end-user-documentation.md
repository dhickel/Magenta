# End-User Documentation

## Context

Approaching alpha means users need practical docs for operating Magenta, not only technical API notes. End-user docs must describe current UI behavior and avoid exposing implementation-only jargon unless needed for accurate operation.

## Goal

Create task-oriented end-user documentation for Magenta's exposed alpha workflows.

## In Scope

- Quickstart and navigation.
- Chat and planning usage.
- Dashboard overview.
- Plans/tasks authoring and submission.
- Workflow authoring and running.
- Jobs and recurrence.
- Agents, queue, schedules, reactions, workspace/output/history panels.
- Projects/workspaces.
- Inbox, outputs, settings.
- Common errors and recovery.

## Out of Scope

- Marketing copy.
- Admin deployment hardening beyond current alpha operations.
- Screenshots unless the implementing agent can produce and maintain them cheaply.

## Implementation Steps

1. Write `docs/end-user/quickstart.md`.
   - Starting the app.
   - Opening `/chat` and operational pages.
   - Alpha credentials and CSRF behavior at a user level.
   - Basic model selection/config assumptions.

2. Write `docs/end-user/chat.md`.
   - Start/continue chats.
   - Planning mode, answering questions, approving/continuing/canceling plans.
   - Saving/continuing plan chats.
   - Session history, titles, favorites, archives, deletion.

3. Write `docs/end-user/dashboard.md`.
   - What dashboard counts mean.
   - Active work, open projects, agents, inbox/outputs/events side panels.

4. Write `docs/end-user/plans-and-tasks.md`.
   - Create/edit plans.
   - Inputs, outputs, deliverables, steps, validation criteria, assumptions, evidence, feedback, questions.
   - Finalize/submit to agent.
   - Runs/history and common validation failures.

5. Write `docs/end-user/workflows.md`.
   - Create a workflow draft.
   - Add nodes, routes, route conditions, input/output mapping.
   - Validate graph.
   - Submit to agent.
   - Resume approval/waiting runs.

6. Write `docs/end-user/jobs.md`.
   - Create jobs.
   - Owner agent, project, manager type, default model.
   - Ordered plan/workflow items, bindings JSON, recurrence.
   - Submit, runs, cancel, outputs/events.

7. Write `docs/end-user/agents.md`.
   - Agent list/detail pages.
   - Profile/prompt/tools/shell editing.
   - Queue controls: pause, resume, cancel, force interrupt, delete terminal history.
   - Schedules/reactions.
   - Workspace, outputs, exec, retained history, lifecycle actions.

8. Write `docs/end-user/projects-and-workspaces.md`.
   - Project CRUD.
   - Memberships and network.
   - Workspace status, links, release request.
   - Relationship to jobs/agents/outputs.

9. Write `docs/end-user/inbox-outputs-settings.md`.
   - Inbox messages and approvals/responses.
   - Output filters, content, downloads.
   - Runtime/chat/system settings.

10. Add `docs/end-user/00-index.md`.
    - Link all pages.
    - Provide "start here" workflows by user intent.

## Validation

- Each page is checked against the live UI routes or controller fragments.
- Docs use the terms visible in the UI where possible.
- No instructions require manually entering opaque IDs after selector work is complete unless the field intentionally remains manual.
- Known alpha limitations are clearly labeled.

## Exit Criteria

- A user can operate all exposed alpha UI areas using docs alone.
- End-user docs stay aligned with selector UI changes from later phases.

