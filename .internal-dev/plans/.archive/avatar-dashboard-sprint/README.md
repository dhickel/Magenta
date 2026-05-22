# Avatar Dashboard Sprint Planning Suite

## Context

This suite prepares the Avatar sprint for later implementation. It is planning-only: no Avatar code, schema, routes, runtime behavior, or plugin runtime is implemented by this pass.

Source inputs:

- User-provided Avatar sprint orchestration brief.
- Root `AGENTS.md` and `.internal-dev/AGENTS.md`.
- `.internal-dev/focus/AGENTS.md`, `current-focus.md`, `unfinished-work.md`, `architecture-focus.md`, and `decisions.md`.
- `.internal-dev/notes/current-architecture-focus.md`.
- Read-only domain planning outputs for Avatar persistence, dashboard UI, agent workspace tools, output/temp publishing, assistant behaviors, and plugin research.

## Goal

Create a handoff-grade plan suite for the Avatar sprint. Later agents should be able to implement the sprint without inventing data ownership, UI routing, tool scope, output publication semantics, plugin boundaries, validation gates, or orchestration mechanics.

## In Scope

- Domain plans for:
  - Avatar core and persistence.
  - Workspace outputs and temp publishing.
  - Agent workspace tooling.
  - Avatar assistant behaviors.
  - Avatar dashboard UI.
- Final orchestration plan with ownership lanes, parallelization rules, serialization points, validation gates, branch/commit flow, and stop rules.
- Source-backed plugin-system research review in `.internal-dev/reviews/`.
- `.internal-dev` closeout records for the planning work.

## Out of Scope

- Implementing Avatar code, `avatar.sqlite`, routes, tools, widgets, or tests.
- Implementing plugin runtime, Kawa integration, Java SPI loading, or SimplyPages plugin DSL.
- External mailbox polling, OAuth, calendar provider sync, or third-party plugin marketplace behavior.
- Worktrees for later implementation unless the user explicitly changes that constraint.

## Plan Files

- `phase-01-avatar-core-persistence.md`
- `phase-02-workspace-outputs-temp-publishing.md`
- `phase-03-agent-workspace-tools.md`
- `phase-04-avatar-assistant-behaviors.md`
- `phase-05-avatar-dashboard-ui.md`
- `final-orchestration-plan.md`

Supporting review:

- `.internal-dev/reviews/2026-05-22-avatar-plugin-system-research.md`

## Locked Decisions

- Avatar user-centric data uses a separate `avatar.sqlite` under the Magenta root.
- Avatar is the selected first durable focus for implementation; this planning pass records it as the next focus and implementation should promote it to active when phase work starts.
- Dashboard editing is HTMX-first using SimplyPages docs and demos.
- Plugin runtime is deferred research-only for this sprint.
- Kawa/DSL/plugin research informs later design but does not create runtime dependencies in this sprint.
- Later implementation may parallelize only non-overlapping ownership lanes and must not use worktrees unless the user changes that constraint.

## Validation

Planning validation for this suite is document consistency:

- Every phase file uses the `.internal-dev/AGENTS.md` phase headings.
- Final orchestration plan names ownership boundaries and serialization points.
- Plugin research is source-backed and explicitly keeps runtime implementation out of scope.
- Focus and decision records reflect durable Avatar sprint decisions without claiming feature implementation has started.

## Exit Criteria

- All plan files and the plugin research review exist.
- `.internal-dev/focus/` reflects the Avatar focus decision and remaining implementation handoff state.
- A changelog entry records the planning suite.
- A git commit contains only the planning and closeout artifacts.
