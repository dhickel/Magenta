# Alpha Operational UI Polish And Contract Completion

## Purpose

This plan suite turns the 2026-05-14 dashboard, plan, workflow, project, agent, Docker, and model-override notes into direct implementation work for alpha. The goal is not another broad audit; it is a heads-down remediation plan that fixes every listed item unless it is already recorded as explicitly deferred in `.internal-dev/notes/alpha-deferred-targets.md`.

## Source Inputs

- User notes from 2026-05-14 covering `/dashboard`, `/plans`, `/workflows`, `/projects`, `/agents`, Docker runtime display, system chat, and general model overrides.
- `.internal-dev/notes/alpha-deferred-targets.md`
- `.internal-dev/plans/alpha-blocking-operational-completion/00-orchestration-plan.md`
- `.internal-dev/plans/docker-backed-alpha-remediation/README.md`
- `.internal-dev/plans/.archive/operational-ui-contract-refactor/08-alpha-remediation-orchestration-plan.md`
- Current implementation evidence from `OrchestrationController`, `RuntimeSettingsService`, `PlanDefinition`, `WorkflowDefinition`, workflow route records, orchestration CSS/JS, and package `AGENTS.md` files.
- SimplyPages docs and demos, especially HTMX endpoint/swap patterns, editing workflow patterns, modal editing, slot/template guidance, layout primitives, and component testing expectations.

## Execution Order

Run these as separate work packages. Subagents are not alone in the codebase, must not revert unrelated dirty work, and must stay inside the write scopes named by the phase unless a failing test proves an adjacent file must change.

1. `01-dashboard-and-system-chat.md`
2. `02-plan-editor-and-new-plan-chat.md`
3. `03-workflow-graph-editor.md`
4. `04-projects-and-agent-surfaces.md`
5. `05-model-overrides-and-config-contract.md`
6. `06-final-validation-gate.md`

Use `00-orchestration-plan.md` as the binding suite entrypoint before launching any phase.

Phases 1, 2, and 4 can run in parallel only if one orchestrator owns shared edits to `OrchestrationController.java`. Phase 3 should run alone for workflow files. Phase 5 must run after the phase owners have exposed all override locations. Phase 6 is validation-only.

## Deferred Items

Only the following user-approved deferrals are allowed for this suite:

- Existing alpha deferrals in `.internal-dev/notes/alpha-deferred-targets.md`, including drag-canvas editing, cyclic workflows/retry loops, condition-language execution, rich validator feedback loops, and parallel ready-node execution.
- New user deferral added by this suite: test and iron out chat loops with mid-chat planning and task planning in a future sprint.

Everything else listed in the user notes is in scope and must be completed or returned as a blocker with evidence.

## Shared Rules

- Preserve `/chat` route behavior. New operational chat affordances must use separate operational endpoints or the existing agent side-panel route.
- Keep SimplyPages and HTMX as the default for CRUD, filtering, tabs, row add/delete, form submissions, and partial refreshes.
- Use JavaScript only when it is the path of least resistance for live streaming or client-side graph state, and document the justification in the phase closeout.
- Do not flatten structured data into CSV, newline blobs, or opaque JSON when the backing model is typed or list-shaped.
- Controllers stay thin; services own validation and persistence behavior.
- Prefer model keys from `AiConfig.models()` in UI and APIs. Do not mix raw remote model names with selectable model aliases.
