# Services UX Playwright Review

Date: 2026-05-21

## Scope

Reviewed the recent services/project/job/workflow/task UX changes on the current branch using Playwright against `http://localhost:18080`.

The app was started with an isolated SQLite database at `/tmp/magenta2-services-ux-playwright-review-2026-05-21.sqlite?foreign_keys=true`. The pass seeded one project, one plan/task template, one workflow, one persistent-workspace job, one task assignment, and one job-run assignment through public APIs to make project/job/workspace context visible in the UI.

No production code was changed.

## Pages Covered

- `/dashboard`
- `/agents`
- `/agents/{agentId}`
- Agent detail tabs: Dashboard, Queue, Inbox, Jobs, Schedules, Reactions, Workspace, Outputs, History
- Agent chat entry point
- `/plans`, plan editor, plan submit context
- `/workflows`, workflow editor, workflow submit validation path
- `/jobs`, `/jobs/{jobId}`
- `/projects`, project detail/member controls
- `/outputs`
- `/settings`
- `/inbox`
- Mobile/narrow checks at `390x844` for dashboard, agents, agent detail, plans, workflows, jobs, projects, outputs, settings, and inbox

## Functional Findings

### High

- The agent detail checklist expectation says there should be a `Chat` tab, but the page exposes `Chat with Agent` as a separate accordion/header above the tab row, not as a tab button. The chat panel does open and renders an input plus `Send`, but the route does not match the documented tab contract.

### Medium

- Workflow submit context could not be fully validated with the seeded empty workflow because the UI correctly blocked submission with `Workflow must contain at least one executable node before validation, submission, or run`. This validated the error path, not the project/workspace submit form path.
- Starting plan/job assignments for visible context caused background model execution to begin. Shutdown interrupted an Ollama call to `http://192.168.1.112:11434/api/chat`, producing shutdown-time warnings. This did not surface as a browser error, but future UI-only validation should seed non-running fixture state or disable executors if available.

### Low

- Project detail membership controls render and membership add/remove UI is present. The current member row concatenates ID, role, and `Remove` tightly (`...44208a1reviewerRemove` in text extraction), which is functional but visually dense.
- Plan submit context renders Agent, Model Override, Priority, Project, and Compatibility Workspace controls after opening the submit panel.
- Job detail renders owner agent, project, persistent job workspace, run assignment context, effective project workspace, job workspace state, and output count.
- Output filters render direct provenance controls for agent, job, project, workspace ID, plan/workflow ID, job assignment ID, job run ID, run ID, run type, and artifact type.

## Layout Findings

### High

- Mobile sidebar toggle is positioned far below the viewport. At `390x844`, before toggle the sidebar bounds were around `top=2093`; after toggle it moved from offscreen left to `left=12` but remained at `top=2093`. The drawer becomes visible only after scrolling deep down the page, so mobile navigation is effectively broken on long pages.
- Mobile dashboard tables clip important columns. The Active Work and Agents tables show right-side content cut off at the card edge, with agent IDs and queue/status columns partially hidden instead of converting to stacked rows or a horizontally scrollable table.

### Medium

- Job detail run summary table is too dense even at `1440px`. Long assignment IDs, workspace IDs, project names, and output paths wrap into narrow columns, making the table difficult to scan. DOM inspection found offscreen cells in the later table columns and clipped text in workspace/job workspace cells.
- Settings mobile form has minor clipped controls: the default agent ID input and model select text have small horizontal clipping at `390px`.

### Low

- The overall desktop shell is usable and visually consistent across dashboard, agents, plans, workflows, projects, outputs, settings, and inbox.
- The UI still reads as a mostly single-hue blue palette. It is coherent, but the operational surfaces would benefit from more neutral table/card backgrounds and stronger semantic status accents.
- Several icon-only or symbolic controls are very small, notably the plan delete button and job item `x` control.

## Screenshots

Artifacts were captured under `test-results/services-ux-playwright-review-2026-05-21/`.

Key screenshots:

- `dashboard-1440x950.png`
- `dashboard-mobile-390x844.png`
- `mobile-sidebar-before-toggle-390x844.png`
- `mobile-sidebar-after-toggle-390x844.png`
- `agents-list-1440x950.png`
- `agent-detail-1440x950.png`
- `agent-chat-entry-after-click-1440x950.png`
- `agent-tab-queue-1440x950.png`
- `plan-submit-form-context-1440x950.png`
- `workflow-submit-form-context-1440x950.png`
- `job-detail-1440x950.png`
- `project-detail-membership-1440x950.png`
- `outputs-provenance-filters-1440x950.png`
- `settings-mobile-390x844.png`

## Recommended UI Changes

1. Fix the mobile sidebar to be `position: fixed` or otherwise anchored to the viewport when opened, with a visible overlay/backdrop and body scroll handling.
2. Convert operational tables to responsive card rows below the tablet breakpoint, or wrap each table in an explicit horizontal scroller with visible affordance.
3. Redesign the job run summary as stacked context blocks or expandable rows rather than a wide table. Keep assignment/run IDs, agent/project, effective workspace, persistent job workspace, and outputs discoverable without forcing every field into columns.
4. Align the agent chat surface with the documented tab model, or update the validation contract if `Chat with Agent` is intentionally an accordion above the tabs.
5. Improve project membership row spacing so agent ID, role, and actions are visually separated on desktop and mobile.
6. Add visible labels for all output provenance filters, especially workspace ID, plan/workflow ID, job assignment ID, job run ID, run ID, and run type. Placeholders alone are easy to lose after a value is entered.
7. Use more semantic color accents for statuses and destructive actions, and reduce the amount of blue-on-blue visual weight in dense operational pages.

## Small Fix Candidates

- Mobile sidebar top anchoring and open-state positioning.
- Add `overflow-x:auto` plus minimum-width handling to dashboard/job tables as an immediate containment fix.
- Label output filter inputs instead of relying on placeholders.
- Increase hit area and accessible labels for icon-only delete/remove controls.
- Separate project member row fields into grid columns or badges.
- Rename or duplicate `Chat with Agent` into a proper `Chat` tab if the tab contract is still desired.

## Blockers

- Full model-backed task/job completion was not validated. The UI-only pass created assignments to inspect context, which triggered background model work. The local Ollama endpoint was contacted and then interrupted during shutdown.
- Workflow submit project/workspace controls were not fully validated because the seeded workflow had no executable node and the UI blocked submission correctly.
- The browser pass used the available Playwright MCP tooling. I cannot actually switch this Codex instance to `gpt-5.5`; the requested reasoning/model setting is therefore not independently verifiable from inside the session.

## Validation Evidence

Commands used:

```bash
rm -f /tmp/magenta2-services-ux-playwright-review-2026-05-21.sqlite /tmp/magenta2-services-ux-playwright-review-2026-05-21.sqlite-*
mkdir -p test-results/services-ux-playwright-review-2026-05-21
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-services-ux-playwright-review-2026-05-21.sqlite?foreign_keys=true --magenta.executor.chat-threads=4'
```

Server URL: `http://localhost:18080`

Playwright evidence:

- 43 PNG screenshots plus `console-messages.txt` and `network-requests.txt` were captured under `test-results/services-ux-playwright-review-2026-05-21/`.
- Console capture reported `Total messages: 0 (Errors: 0, Warnings: 0)`.
- Captured non-static network requests for page loads and HTMX fragments returned `200`.
- Seed API statuses were `200` for agents and `200` for project, plan, workflow, job, task assignment, and job assignment creation.
- Spring Boot startup completed successfully on port `18080`.
- Server shutdown completed successfully after `Ctrl-C`; shutdown interrupted one background model call started by the seeded assignment.
