# Alpha Documentation And Entity Selectors Plan Suite

## Context

Magenta recently completed a large refactor and is approaching alpha. Two remaining alpha-readiness tracks need coordinated implementation:

1. Build proper documentation in a documentation folder, including technical architecture/API/service specifications and end-user usage documentation.
2. Replace manual ID entry for selectable Magenta entities with reusable searchable selectors that recommend existing records, still permit manual entry, and validate that manually entered values exist.

This suite is intentionally implementation-ready for a weaker follow-on agent. It should be executed by a coordinating agent that assigns subplans to subagents. Use `gpt-5.5` with reasoning effort `medium` for implementation and documentation subagents unless a blocker justifies temporarily delegating a narrower hard question to a higher-thinking agent. Repo testing remains governed by root `AGENTS.md`: all testing, including Playwright validation, uses `gpt-5.3-codex` with reasoning effort `medium`.

## Source Inputs

- Root `AGENTS.md` and `.internal-dev/AGENTS.md`.
- Package guides under `src/main/java/io/mindspice/magenta2/**/AGENTS.md`.
- Current code in `io.mindspice.magenta2.api.web`, `ai.chat`, `ai.orchestration`, `ai.orchestration.workflow`, `ai.orchestration.workspaces`, `ai.config.user`, and `ai.agent.job`.
- Current UI pages and fragments in `OrchestrationController`, `FrontendController`, static orchestration JS, and CSS.
- Existing SimplyPages docs under `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs`.
- Existing public-alpha route inventory under `.internal-dev/plans/public-alpha-quality-review/`.

## Plan Files

- `00-orchestration-plan.md`: execution order, branch/commit policy, subagent model policy, integration gates.
- `01-documentation-foundation.md`: docs folder structure, docs `AGENTS.md`, root `AGENTS.md` docs policy, README refresh.
- `02-technical-documentation.md`: technical architecture, services, API/SSE, persistence, security, operations, frontend internals.
- `03-end-user-documentation.md`: end-user guides for chat, dashboard, plans, workflows, jobs, agents, projects, outputs, settings.
- `04-selector-backend-contract.md`: reusable entity lookup service, option model, search/validate fragment/API endpoints.
- `05-selector-component-contract.md`: reusable SimplyPages selector component, HTMX-first interaction model, narrow JS allowance.
- `06-selector-low-risk-integrations.md`: replace straightforward manual ID fields first.
- `07-selector-dependent-integrations.md`: replace dependent selectors such as assignment type to target ID and job item type to plan/workflow.
- `08-validation-closeout.md`: test matrix, focused Playwright subagent scope, docs review, `.internal-dev` closeout, archival.

## Execution Summary

Phase 0: Create branch and baseline inventory.

Phase 1: Documentation foundation and governance.

Phase 2: Technical and end-user documentation content, split by domain where possible.

Phase 3: Entity selector backend contract and fragment endpoints.

Phase 4: Reusable selector component and focused component tests.

Phase 5: Low-risk selector integrations.

Phase 6: Dependent selector integrations.

Phase 7: Full validation, `.internal-dev` closeout, archive the completed plan suite, and final commit.

## Subagent Roster

Documentation subagents:

- Docs foundation and governance.
- Technical architecture/API docs.
- End-user docs.
- Documentation verification pass.

Selector subagents:

- Selector backend/read-model implementation.
- Selector component and styles.
- Low-risk page integration.
- Dependent-flow page integration.
- Focused Playwright validation.

Subagents are not alone in the codebase. They must not revert edits made by others, and they must adapt their work to concurrent branch changes. The coordinator owns sequencing, integration review, and final validation.

## Acceptance Criteria

- A real `docs/` documentation system exists, with `docs/AGENTS.md`, index pages, technical docs, end-user docs, and current README pointers.
- Root `AGENTS.md` contains explicit policy requiring documentation updates for end-user behavior changes and technical changes.
- All exposed alpha functionality has a documentation home or an intentionally documented gap.
- A reusable selector backend and SimplyPages component exist for agents, jobs, plans/tasks, workflows, projects, workspaces, models, and relevant run/output IDs where applicable.
- Manual ID entry fields in current user-facing operational flows are replaced or explicitly justified as still manual.
- Manual entries validate existence and show user-visible errors before unsafe actions are submitted.
- Selector interactions are HTMX-first; any JavaScript is narrowly justified for combobox keyboard/focus synchronization only.
- Focused automated tests, startup smoke, and Playwright subagent validation prove changed UI flows against a live app.
- `.internal-dev` changelog and knowledge notes are written, out-of-scope bugs are logged, and this plan directory is moved to `.archive/` only after completion.

