# Future Features

## Purpose

This note tracks features discovered during the operational UI contract review that should not be implemented as part of the immediate refactor plans.

## Dashboard-Aware System Chat

### Summary

The redesigned dashboard should include chat at the top, but the model prompts and tools that let the assistant reason over the whole system should be implemented later.

### Future Target

- Dashboard chat understands dashboard summary state.
- It can answer questions about active projects, jobs, workflows, agents, inbox messages, and outputs.
- It can propose actions but should not mutate state without explicit user confirmation.

### Likely Targets

- `AgentChatPromptService` or a dashboard-specific prompt service.
- `GET /api/dashboard/summary`.
- New read-only tools for dashboard summary, agent status, job status, project status, output search, and inbox search.
- Approval-gated mutating tools later.

## Agent-Assisted Job And Workflow Creation

### Summary

The plan chat flow already exists and should be preserved. Job and workflow creation by agent chat should be added later.

### Future Target

- User can manually create/edit a job or workflow, then drop into chat to continue the creation loop.
- Agent receives the existing job/workflow state and must grok it before asking questions.
- If state is incomplete, agent asks targeted questions.
- If state appears complete but context is missing, agent summarizes what it has and asks for guidance.

### Prompt Requirements

The future prompt should include:

- current entity type and id;
- full current draft state;
- validation warnings;
- known missing fields;
- instruction to ask one focused question or apply one structured edit at a time;
- instruction not to invent job/workflow references;
- instruction to preserve manual edits unless user explicitly changes them.

### Likely Targets

- New job/workflow draft prompt service.
- Tool APIs for keyed job item edits and workflow route edits.
- Chat page context bridge from `/jobs/{id}` and `/workflows`.

## Advanced Visual Workflow Canvas

### Summary

The immediate workflow plan should start with a structured tree/link builder. A full visual graph canvas may be worthwhile later if workflows become large enough.

### Future Target

- Pan/zoom canvas.
- Node palette.
- Properties panel.
- Minimap or outline.
- Auto-layout.
- Edge routing.
- Keyboard shortcuts.
- Read-only preview mode.

### Decision Gate

Evaluate only after the structured tree builder is dogfooded. Do not add a React graph stack solely for aesthetics while the app is SimplyPages/HTMX-first.

## Project Git Workspace Operations

### Summary

Projects can store a git repo URL and own a project directory. Actual git clone/pull/status operations should be scoped separately.

### Future Target

- Clone project repo into project workspace.
- Show branch, remote, dirty status, and latest commit.
- Pull/fetch with explicit confirmation.
- Link output artifacts and jobs to repository paths.

### Risks

- Filesystem confinement.
- Credentials and private remotes.
- Concurrent agent writes.
- Dirty workspace handling.

## Output Artifact Indexing And Preview

### Summary

The refactor should add an output query API, but richer indexing and preview can be deferred.

### Future Target

- Full-text searchable outputs.
- Preview rendered markdown/json/text.
- Artifact lineage from project -> job -> workflow -> task -> output.
- Safe download/open links.
- Retention policy and cleanup UI.

## Agent Runtime Observability

### Summary

Docker status should be visible in the immediate refactor. Deeper runtime observability can be later.

### Future Target

- Container execution history.
- Exit code distribution.
- Last stdout/stderr snippets.
- Runtime latency and failure metrics.
- Per-agent runtime health timeline.

### Likely Targets

- Docker runtime execution event persistence.
- Agent detail history tab.
- Dashboard runtime health summary.

## Scheduling And Event Reaction Management

### Summary

Schedule and event reaction APIs exist behind feature flags. A robust UI for these is not part of the requested refactor.

### Future Target

- Agent schedule editor.
- Event reaction rule editor.
- Test/simulate reaction matching.
- Audit trail for automatic assignment creation.

## Role-Aware Project Collaboration

### Summary

Projects assign agents, but richer collaboration semantics should be separate.

### Future Target

- Agent roles with permissions.
- Project-scoped inbox/network timeline.
- Mentions and handoffs between agents.
- Project activity audit.

## HTMX Webjar Installation

### Summary

The current `static/webjars/htmx.org/dist/htmx.min.js` file is a noop compatibility stub that prevents browser 404 errors but does not provide real HTMX processing. Page shells render correctly and partial endpoints return valid HTML, but HTMX interactions (hx-get, hx-post, hx-put, hx-delete) do not execute in-browser.

### Future Target

- Install the actual htmx.org webjar as a Maven dependency or serve the real `htmx.min.js` from a proper static path.
- Update SimplyPages to emit the correct htmx URL, or ensure the existing `/webjars/htmx.org/dist/htmx.min.js` path serves real htmx.
- After installation, run full browser interaction validation across all HTMX-driven pages (dashboard partial refresh, plan/workflow/job CRUD, agent tabs, editor saves, submit-to-agent).

### Likely Targets

- `pom.xml` -- add htmx.org webjar dependency.
- `src/main/resources/static/webjars/htmx.org/dist/htmx.min.js` -- replace noop stub with real htmx.
- OrchestrationController -- verify htmx version string in JS includes.
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` -- update with working HTMX interaction tests.

## Workflow Node Expand/Collapse

### Summary

The workflow builder shows all nodes in a flat row list. The plan called for expandable node cards with properties panels showing route details, input bindings, and configuration. The flat list is functional but visually dense for workflows with many nodes.

### Future Target

- Click to expand/collapse individual nodes.
- Expanded view shows: node type badge, plan reference, input/output bindings, incoming/outgoing routes, and config.
- Collapsed view shows: node key, label, type badge, and route count.
- Per-node expand/collapse via HTMX endpoints returning node detail fragments.

## Plan Field Expand/Collapse

### Summary

The plan editor shows all input/output field rows in expanded mode (name, type, required, array, description, schema). The plan called for expandable field rows where the default collapsed view shows only name and type, with expand to edit details.

### Future Target

- Default collapsed field row: name + type badge + required/array indicators.
- Expand to show: description textarea, schema JSON textarea, required checkbox, array checkbox.
- HTMX endpoints for per-field expand/collapse (`GET /plans/_editor/{planId}/fields/{fieldKind}/{index}/expand` and `_collapse`).

## Agent History Tab Content

### Summary

The agent detail "History" tab shows a static placeholder: "Assignment and event history will be displayed here. Each assignment creates a persistent run record with timestamps, status, and results." No actual history query endpoint exists.

### Future Target

- Query endpoint returning agent run history (assignments, completions, failures).
- History table with: timestamp, assignment ID, type (TASK_RUN/WORKFLOW_RUN/JOB_RUN), target reference, status, duration, result summary.
- Click-through to detailed run view.
- Pagination and date range filtering.

