---
schema_version: 1
document_type: work-unit-index
status: planning
owner: advanced-planner
created: 2026-05-29
---

# Dashboard Widget Suite Work Units

## Sequencing

1. `phase-01-platform-foundation`: registry, widget instance/settings/binding model, migration, route contracts, basic settings/detail shell, docs/spec decisions.
2. `phase-02-personal-planning-core`: Today Planner, Tasks/Routines, Calendar/Schedule, recurrence/reminder/day-map core.
3. `phase-03-notes-project-context`: Notes, Projects, Contacts/Materials, file-backed project schemas and note source modes.
4. `phase-04-agent-operational-widgets`: Agent Status/Queue, Agent Outputs, Agent Files/Notes, selected binding UX and service boundaries.
5. `phase-05-tracking-alerts-context`: Habits/Trackers, Reminders/Alerts, read-only Dashboard Context Panel.
6. `phase-06-integration-docs-validation`: docs/spec/changelog/archive, evidence index, stale-reference sweep, integration validation, final browser proof reconciliation.

## Dependency Rules

- Phase 02-05 depend on Phase 01 passing validation.
- Phase 03 and Phase 04 can run after Phase 01 and Phase 02 service contracts are stable, but they must not mutate planner core tables without coordinating through Phase 02 outcomes.
- Phase 05 depends on Phase 02 reminder/day-map decisions.
- Phase 06 starts only after all mutating phases pass unit validation.

## Commit Policy For Future Execution

The repo workflow requires a dedicated branch before multi-phase implementation starts and commits at the end of each phase. The main thread must create the branch before dispatching Phase 01. This planning task itself must not commit.
