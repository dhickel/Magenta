# Phase 05 - Full-Screen Orchestration Dashboard

## Context

The current orchestration UI is useful but crowded and MVP-shaped. The target UI should be a full-screen operational dashboard for editing prompts, plans/tasks, workflows, jobs, projects, inboxes, outputs, and agent state.

## Goal

Replace the thin orchestration UI with a SimplyPages-based dashboard while keeping `/chat` isolated.

## In Scope

- Full-screen dashboard shell for orchestration pages.
- Plan/task editor using unified schema.
- Workflow editor with node types and gates.
- Job and project management views.
- User and agent inbox views with approval response controls.
- Output browser.
- Agent/job/project side-panel chat.

## Out of Scope

- Marketing/landing page redesign.
- Replacing the main `/chat` client.
- Advanced visual workflow graph editor; use structured forms/tables first.

## Implementation Steps

1. Read SimplyPages docs before editing UI:
   - layout/grid docs;
   - component catalog;
   - slot/template docs;
   - editing/modal docs if using modals.
2. Keep `FrontendController` thin.
   - Use SimplyPages components instead of raw HTML strings where practical.
   - For stateful dashboard behavior, keep dedicated static JS modules under `static/js/orchestration/`.
3. Add dashboard routes:
   - `/dashboard`
   - `/plans`
   - `/workflows`
   - `/jobs`
   - `/projects`
   - `/inbox`
   - `/outputs`
   - `/agents`
   - `/settings`
4. Preserve `/chat`.
   - `/chat` continues to load the chat client.
   - Orchestration pages load orchestration modules.
5. Build reusable UI modules:
   - entity list/detail shell;
   - schema field editor;
   - output artifact list;
   - workflow node editor;
   - inbox approval row;
   - run event timeline;
   - side-panel agent chat host.
6. Plan/task editor:
   - Edit title, goal, summary, notes, deliverables, inputs, outputs, assumptions, steps, validation criteria, prompt profile, and model overrides.
   - Field editor must expose type, array checkbox, description, schema/example.
   - Clearly distinguish deliverables from outputs.
7. Workflow editor:
   - Add/edit/reorder nodes.
   - Show gate node settings and binding compatibility warnings.
8. Job/project pages:
   - Show workspace, runs, outputs, schedules, agents, network messages, and status.
9. Inbox page:
   - User inbox plus selected agent inbox.
   - Approval responses should call the resume endpoint and refresh affected run state.
10. Output browser:
   - Browse by agent, job, project, run id, and artifact type.
   - Link copied/written output files where safe.

## Validation

- Controller tests for route rendering and correct script inclusion.
- JS/browser validation:
  - dashboard loads without console errors;
  - plan/task editor saves a field with `array = true`;
  - workflow gate can be created;
  - approval response resumes a waiting workflow;
  - output browser shows materialized files;
  - `/chat` still loads its own client and remains functional.
- Run browser validation on desktop and mobile widths to catch layout overflow.

## Exit Criteria

- Orchestration UI uses full available screen space and supports the new model.
- Users can manually edit prompts, plans/tasks, workflows, jobs, projects, inboxes, and outputs.
- `/chat` remains isolated.
- The UI is usable for dogfooding the new orchestration model.

