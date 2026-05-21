# Services And UX Architecture Refactor Orchestration State

Date: 2026-05-21

## Current Status

- Workspace/file architecture refactor complete:
  - `3f447ae chore: close workspace file architecture refactor`
  - `e6dfe87 chore: record workspace file closeout commit`
- Second orchestration branch created: `services-ux-architecture-refactor`.
- Initial review/planning suite is starting.

## Objective

Review and align Magenta's backend services, frontend surfaces, and integration flows with the documented architecture for agents, projects, jobs, tasks/plans, workflows, workspaces, and outputs.

## Architecture Baseline

- Orchestrator abstractions:
  - Agents own primary workspaces and execute work.
  - Projects provide a shared, durable, project-scoped workspace and visibility surface under agents.
- Work-unit abstractions:
  - Tasks/plans and workflows are bounded work units submitted to an agent or agent-plus-project context.
  - Jobs are work-unit/orchestrator hybrids that can be assigned, repeat, optionally keep a persistent per-assignment workspace, and launch tasks/workflows toward a goal.
- UX expectations:
  - Users can see how projects attach to agents and how submitted work lands in project or agent workspace context.
  - Users can see and configure job assignment/routing behavior, including persistent workspace status where supported.
  - Output views should make project, agent, job, task/workflow, run, and workspace context discoverable without conflating chat files with output artifacts.

## Initial Review Questions

- Which services still encode project ownership, workspace selection, or job routing in ways that diverge from the architecture?
- Which public API routes or request records are missing project/job assignment fields needed by the UI?
- Which UI surfaces are misleading, missing, or inconsistent for project assignment, job assignment, workspace selection, output visibility, and run status?
- Are there race/concurrency risks around multi-agent project use, job assignment workspaces, or UI actions that should be left open for leasing/locking?
- Which tests and Playwright checks are required before implementation can be considered validated?

## Planned Gated Flow

1. Read-only backend services review.
2. Read-only frontend/UX review.
3. Read-only integration/API review.
4. Risk assessment and testing plan review.
5. Advanced implementation plan synthesis.
6. Serial implementation phases with validation after each phase.
7. xhigh final architecture/code/UX review.
8. Remediation loops if the review or validation fails.
9. Documentation, `.internal-dev`, changelog, and final commit closeout.

## Commit Policy

- Commit setup/planning artifacts before implementation.
- Commit after each validated implementation phase.
- Keep unrelated dirty files out of all commits.

## Unrelated Dirty Files To Avoid

- `AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `.internal-dev/notes/idea_drop.md`
- `.internal-dev/notes/scratch.md`
- `.codex-orchestration/*`
- screenshots and `test-results/`

## Agent Roster

- Pending.

## Validation Log

- Pending.
