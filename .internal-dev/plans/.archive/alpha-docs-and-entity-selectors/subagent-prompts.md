# Subagent Prompt Pack

## General Coordinator Instructions

Dispatch these prompts one phase at a time. Documentation content subagents can work in parallel after `01-documentation-foundation.md` is complete. Selector implementation subagents must run in order: backend, component, low-risk integrations, dependent integrations.

All implementation/documentation subagents should use `gpt-5.5` with reasoning effort `medium` unless the coordinator explicitly delegates a narrow blocker to a higher-thinking agent. Testing and Playwright validation subagents should use `gpt-5.3-codex` with reasoning effort `medium`.

Every subagent must:

- Read the relevant plan file first.
- Read the closest package `AGENTS.md` before editing code in that package.
- Keep changes scoped to assigned files and responsibilities.
- Avoid reverting unrelated user or agent changes.
- Report changed files, tests run, unresolved risks, and any out-of-scope bugs that need `.internal-dev/bugs/` entries.

## Docs Foundation Subagent

Prompt:

```text
Implement `.internal-dev/plans/alpha-docs-and-entity-selectors/01-documentation-foundation.md`.

You own the docs folder structure, docs `AGENTS.md`, docs indexes, README refresh, and the root `AGENTS.md` documentation-maintenance rule. Read root `AGENTS.md`, `.internal-dev/AGENTS.md`, and the live docs tree before editing. Preserve unrelated dirty edits. Do not complete detailed content pages beyond foundation placeholders. Use SimplyPages or application docs only as references, not implementation targets. Report changed files and validation.
```

## Technical Docs Subagent

Prompt:

```text
Implement `.internal-dev/plans/alpha-docs-and-entity-selectors/02-technical-documentation.md`.

You own technical docs under `docs/technical/` and `docs/api/`. Inspect current controllers, services, package guides, `schema.sql`, and config before writing claims. Document all exposed API/SSE families, architecture boundaries, persistence, security, configuration, operations, frontend HTMX/JS islands, chat/plans/tasks, orchestration runtime, workflows, workspaces/tools/outputs. Do not change production code unless a docs generation helper is explicitly approved by the coordinator. Report source files inspected and docs changed.
```

## End-User Docs Subagent

Prompt:

```text
Implement `.internal-dev/plans/alpha-docs-and-entity-selectors/03-end-user-documentation.md`.

You own end-user docs under `docs/end-user/`. Inspect current UI routes/fragments before writing usage instructions. Use terms visible in the UI. Cover chat, dashboard, plans/tasks, workflows, jobs, agents, projects/workspaces, inbox, outputs, and settings. Coordinate with selector changes before final wording about choosing IDs. Report changed files and any current UI gaps discovered.
```

## Selector Backend Subagent

Prompt:

```text
Implement `.internal-dev/plans/alpha-docs-and-entity-selectors/04-selector-backend-contract.md`.

You own the reusable entity lookup/read-model backend and selector fragment/API endpoints. Work in `src/main/java/io/mindspice/magenta2/api/web/selector/` or the closest existing web package if a better local pattern exists. Use existing services for agents, plans/tasks, workflows, jobs, projects, workspaces, and models. Add focused tests. Keep endpoints read-only and controllers thin. Do not integrate page forms yet except as needed for tests.
```

## Selector Component Subagent

Prompt:

```text
Implement `.internal-dev/plans/alpha-docs-and-entity-selectors/05-selector-component-contract.md`.

You own the reusable SimplyPages selector component/helper, CSS, and optional narrow JS. Read the SimplyPages docs named in the plan before editing. Keep the component HTMX-first and form-compatible. Only add JS for keyboard/focus behavior if needed, and document that justification. Add component/render tests. Do not replace page forms yet except in a tiny fixture if needed.
```

## Low-Risk Integration Subagent

Prompt:

```text
Implement `.internal-dev/plans/alpha-docs-and-entity-selectors/06-selector-low-risk-integrations.md`.

You own replacing isolated manual ID fields after selector backend/component work has landed. Target plan/workflow submit workspace selectors, job project selector, settings default agent selector, and schedule/reaction model/workspace/job selectors. Preserve parameter names and existing submit/update behavior. Add focused controller/render tests. Update relevant docs wording for these fields.
```

## Dependent Integration Subagent

Prompt:

```text
Implement `.internal-dev/plans/alpha-docs-and-entity-selectors/07-selector-dependent-integrations.md`.

You own dependent selectors: agent submit assignment type to target ID, and job item type to plan/workflow selector. Preserve existing request parameter names and server-side validation. Preserve `/jobs/_editor/_plan-inputs` guidance after plan selection. Add tests for assignment type switching, unknown target rejection, and job item validation. Update relevant docs.
```

## Playwright Validation Subagent

Prompt:

```text
Validate the completed selector UI changes from `.internal-dev/plans/alpha-docs-and-entity-selectors/08-validation-closeout.md` using Playwright against the running Magenta app. Use model `gpt-5.3-codex` with reasoning effort `medium`. Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` first if any validation touches live chat, SSE, agent/model routing, planning, interruption, chat switching, or concurrent interactions. Keep scope focused to `/plans`, `/workflows`, `/jobs`, `/agents`, and `/settings` selector flows. Report exact pages, actions, screenshots or observations, failures, and whether HTMX remains the main transport.
```

