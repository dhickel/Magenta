# Operational UI Contract Refactor Plan Suite

## Objective

This plan suite turns the current orchestration UI from a set of page-level MVP editors into a cohesive operational console for Magenta. The target is a dashboard-first, contract-correct product surface covering system overview, plans, workflows, jobs, projects, agents, inbox, outputs, and Docker/runtime status.

The plan intentionally separates contract repair from visual redesign. The current checkout has frontend/backend mismatches that will make any larger UI work brittle unless fixed first.

## Inputs And Assumptions

Confirmed inputs:

- User issue report in the current request.
- Current code in `src/main/java/io/mindspice/magenta2/api/web`, `src/main/java/io/mindspice/magenta2/ai/chat/plan`, `src/main/java/io/mindspice/magenta2/ai/chat/task`, `src/main/java/io/mindspice/magenta2/ai/orchestration`, and `src/main/resources/static/js/orchestration`.
- SimplyPages docs for layout, reusable templates, component catalog, and edit workflows.
- Prior orchestration plan suite at `.internal-dev/plans/unified-plan-task-orchestration-refactor/`, used as context only.
- Current checkout is dirty and appears to contain active uncommitted orchestration work. Treat the working tree as the review target.

External UX research inputs:

- Dashboard UX sources consistently emphasize information hierarchy, glanceable operational state, progressive disclosure, and drill-down from summary to detail.
- Workflow builder references consistently separate editor concerns from execution/runtime concerns and use canvas/tree views, node palettes, edge routing, property panels, validation, and schema-driven configuration.

Assumptions to lock for implementation:

- `/chat` remains isolated. New operational chat belongs on dashboard/module routes and must not rewrite the existing chat surface.
- UI should be HTMX-first with SimplyPages server-rendered shells and reusable components. Use HTMX for form submission, table/list refresh, filtering, tab/panel swaps, and row-level actions whenever practical.
- JavaScript is allowed only when it is clearly the path of least resistance (for example, state-heavy workflow tree editing, SSE stream wiring, or client-side affordances that HTMX cannot express cleanly without substantial complexity). Any non-trivial JS addition should include a short rationale in phase implementation notes.
- The public API can preserve deprecated compatibility fields temporarily, but the UI should target one canonical contract.
- Direct "Run" affordances are removed from plan and workflow pages. Users submit work to an agent and inspect queue/assignment status, not raw run streams.
- Worktype profile is the user-facing replacement for "prompt profile". The implementation may keep existing `prompt_profile` database columns as storage compatibility during this refactor.

## Artifact Index

- `00-review-findings.md` - current-state code review and risk map.
- `01-contract-repair-and-data-model.md` - fix broken API/UI contracts and normalize plan/task/job/project terminology.
- `02-dashboard-information-architecture.md` - replace card launcher dashboard with structured system overview.
- `03-plan-editor-and-worktype-profiles.md` - redesign plan editor inputs/outputs and submit-to-agent flow.
- `04-workflow-builder-redesign.md` - redesign workflow model, validation, routing, and UI.
- `05-jobs-projects-operational-surfaces.md` - converge job models, repair projects, and add robust job/project overview surfaces.
- `06-agent-dashboard-docker-runtime.md` - redesign agents with detailed dashboards, structured editors, and Docker visibility.
- `07-validation-rollout.md` - test, browser validation, smoke checks, and acceptance criteria.
- `08-alpha-remediation-orchestration-plan.md` - orchestrator handoff plan for blocker remediation, subagent work packages, missing-functionality audit, and alpha validation gates.

## Scope

In scope:

- Deep UI, UX, and API contract review for dashboard, plans, workflows, jobs, projects, agents, inbox, and outputs.
- Refactor plans that are implementable by a coding agent without inventing architecture.
- Deferred feature tracking in `.internal-dev/notes/future_features.md`.

Out of scope for this plan suite:

- Implementing the code changes now.
- Building full autonomous job/workflow creation via agent chat.
- Building final dashboard-level agent tools/prompts.
- Replacing `/chat`.
- Adding broad auth/security design unless a specific API contract requires it.
