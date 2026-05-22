# SimplyPages Upstream Module Candidates

Created: 2026-05-18

## Context

The reusable HTMX autocomplete/search component has been generalized in the SimplyPages temp checkout at `/tmp/simplypages-upstream-lMVtYR/SimplyPages` on branch `feature/reusable-autocomplete-module`.

This note records the follow-up Magenta frontend surfaces that look general enough to upstream into SimplyPages as reusable components or modules.

## Recommended Follow-Up PRs

### 1. Entity selector endpoint support

- Magenta references:
  - `src/main/java/io/mindspice/magenta2/api/web/selector/EntitySelectorComponents.java`
  - `src/main/java/io/mindspice/magenta2/api/web/selector/EntitySelectorController.java`
  - `src/main/resources/static/css/orchestration.css`
- Generalized shape: `EntitySelectorModule` or `EntitySelectorEndpointSupport` layered on top of the new autocomplete component.
- Suggested PR boundary: endpoint/query/status helpers only; keep Magenta entity types out of SimplyPages.
- Risk: Medium. The new autocomplete component may already cover most UI rendering, so this should be scoped to reusable server endpoint conventions and validation fragments.

### 2. Master/detail browser shell

- Magenta references:
  - operational pages for plans, workflows, jobs, projects, and agents in `OrchestrationController`
  - sidebar/detail layout styles in `orchestration.css`
- Generalized shape: `MasterDetailBrowserModule` with slots for sidebar actions, filter input, list fragment target, and detail container.
- Suggested PR boundary: layout and HTMX target contract only; no domain row rendering.
- Risk: Medium. The API must stay generic and not encode orchestration-specific assumptions.

### 3. Inline editable list helpers

- Magenta references:
  - plan/workflow list-row editors in `OrchestrationController`
  - row action styles in `orchestration.css`
- Generalized shape: `InlineEditableListModule` plus `InlineFieldRow` primitives with declarative HTMX endpoints for update, delete, move up, and move down.
- Suggested PR boundary: start with a string-list editor; defer schema-heavy structured editors.
- Risk: Medium-high. This can sprawl if it tries to model every editor shape.

### 4. Status badge primitive

- Magenta references:
  - status rendering helpers in `OrchestrationController`
  - status badge helpers in `src/main/resources/static/js/orchestration/dom.js`
  - status chip styles in `orchestration.css`
- Generalized shape: `StatusBadge` component with semantic variants such as `positive`, `negative`, `neutral`, and `warning`.
- Suggested PR boundary: component, base CSS, and short migration docs.
- Risk: Low.

### 5. HTMX tab navigation helper

- Magenta references:
  - detail tabs in `OrchestrationController`
  - tab helper code in `src/main/resources/static/js/orchestration/dom.js`
  - tab styles in `orchestration.css`
- Generalized shape: `HtmxTabNav` that emits buttons or links with `hx-get`, `hx-target`, and `hx-swap`.
- Suggested PR boundary: server-rendered HTMX-only tab primitive first; optional active-state JavaScript can be a later enhancement.
- Risk: Low-medium.

### 6. Polling fragment panel

- Magenta references:
  - dashboard and operational panels in `OrchestrationController`
- Generalized shape: `PollingPanel` or `PollingSection` wrapper with title, target id, endpoint, trigger cadence, and placeholder content.
- Suggested PR boundary: component only; application controllers own data loading and rendering.
- Risk: Low.

### 7. Transcript/event feed panel

- Magenta references:
  - chat transcript fragments in `FrontendFragmentController`
  - orchestration audit/transcript rendering in `OrchestrationController`
- Generalized shape: `TimelineTranscriptModule` with message/event item render hooks and optional polling wrapper.
- Suggested PR boundary: generic timeline container and safe default item renderer; no orchestration event schema in SimplyPages core.
- Risk: Medium. Sanitization and content-rendering rules must stay strict.

## Exclusions

Do not upstream these as framework primitives without a separate design pass:

- Magenta plan, workflow, job, project, or agent domain editors.
- Agent lifecycle controls, runtime lease controls, diagnostics, and workspace panels.
- Assignment submit-to-agent semantics.
- Magenta security wiring and app-specific endpoint names.
