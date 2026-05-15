# Unified Plan/Task Orchestration Refactor

## Purpose

This plan suite breaks the requested orchestration rewrite into implementation-ready phases for a less senior agent. The target is a clean-break refactor: old task/workflow/job/project orchestration data may be dropped, and the new system should be built around the robust existing planning/task behavior rather than the MVP workflow/job wrappers.

## Locked Decisions

- Plan and task become one backend concept. A task is a finalized executable plan.
- Planning-mode and reusable-task authoring use the same persistence and tool surface; prompts and UI decide which experience is exposed.
- Existing app data may be destructively reset during implementation.
- Docker is mandatory for agent task/workflow/job execution.
- Workflow gates are first-class workflow steps.
- `/chat` remains isolated from orchestration UI. Orchestration pages may have their own side-panel chat bridges.

## Phase Order

0. `architecture-map.md`
0. `api-schema-examples.md`
0. `implementation-playbook.md`
1. `phase-01-unified-plan-task-core.md`
2. `phase-02-workspace-output-docker-runtime.md`
3. `phase-03-workflows-gates-inbox.md`
4. `phase-04-jobs-projects-agent-networks.md`
5. `phase-05-dashboard-ui.md`
6. `final-validation-criteria.md`

## Architecture References

- `architecture-map.md` defines the target data model, package ownership, API surfaces, execution flow, and implementation gotchas.
- Each phase is intended to be implemented and validated independently before the next phase begins.
- If a phase uncovers a needed scope expansion, record it in `.internal-dev/notes/` instead of silently expanding the implementation.
