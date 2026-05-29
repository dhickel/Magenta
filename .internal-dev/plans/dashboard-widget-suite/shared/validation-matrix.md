---
schema_version: 1
document_type: validation-matrix
status: planning
owner: advanced-planner
created: 2026-05-29
---

# Validation Matrix

## Model And Tooling Constraints

| role | required model | reasoning | stop rule |
| --- | --- | --- | --- |
| implementation workers | `gpt-5.5` | high | Stop if unavailable. |
| code validators/red-team | `gpt-5.5` | xhigh | Stop if unavailable. |
| planning red-team after this output | `gpt-5.3-codex` | xhigh | Main thread handles after plan suite. |
| Playwright/browser proof | `gpt-5.5` | high, xhigh if selectable | If unavailable, record `TOOLING_CONSTRAINT` and stop before substituting. |

This model matrix intentionally follows the user's explicit dashboard-suite override for implementation, validation, and browser proof. It supersedes the repo's default testing-agent model for this plan only; any fallback still requires `TOOLING_CONSTRAINT` evidence and user approval before dispatch.

## Required Commands By Phase

Minimum focused commands, adjusted only when files touched are narrower:

- Foundation/schema: `mvn -Dtest=AvatarRepositoryTest,AvatarServiceTest,AvatarDashboardControllerTest test`
- Tool changes: `mvn -Dtest=ChatToolRegistryTest,AvatarToolsTest,AgentOperationalToolConfigurationTest,AgentOperationalToolServiceTest,AgentToolAuthorizationServiceTest test`
- Project/Work Area/output changes: `mvn -Dtest=ProjectServiceTest,ProjectRepositoryTest,WorkAreaServiceTest,WorkAreaExplorerServiceTest,OutputArtifactServiceAttributionTest,OutputArtifactPathSemanticsTest test`
- Controller/API changes: `mvn -Dtest=AvatarDashboardControllerTest,WorkAreaControllerTest,OutputControllerTest,AgentOrchestrationControllerTest test` as applicable.
- Final broad check after all phases: `mvn test`
- Startup smoke after wiring/schema/tool phases: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

## Phase Validation

| phase | validator checks | browser required |
| --- | --- | --- |
| 01 Foundation | registry completeness, instance migration, settings validation, multi-instance, compatibility routes, schema tests, docs/spec deltas | Yes: layout/edit/catalog/settings basics |
| 02 Personal planning | planner service model, recurrence/projection, day map, task/routine/calendar routes, non-punitive skip/snooze/restart | Yes: Today, Tasks/Routines, Calendar |
| 03 Notes/projects | personal vs file notes, project artifact schemas, Work Area confinement, project household data, source settings | Yes: Notes, Projects, file-note views |
| 04 Agent ops | selected-agent settings, outputs scoping, Work Area mini-view, service boundaries, normal-agent tool scoping | Yes: Agent Status/Queue, Outputs, Files/Notes |
| 05 Tracking/context | habits, reminders, dashboard context read-only boundary, no external notifications unless approved | Yes: Habits, Reminders, Context |
| 06 Integration/closeout | docs/spec/changelog/archive, stale references, evidence JSON, cross-unit contract drift | Yes: final full target matrix |

## Evidence Index Contract

Canonical path: `artifacts/dashboard-widget-suite/validation-summary.json`.

Required top-level fields:

- `task_slug`
- `status`: one of `planning_only`, `implementation_in_progress`, `implementation_checks_passed`, `validator_failed`, `repair_in_progress`, `code_validation_passed_playwright_pending`, `playwright_failed`, `blocked_tooling_constraint`, `fully_validated`
- `model_constraints`
- `work_units`
- `commands`
- `validators`
- `browser`
- `artifacts`
- `superseded_artifacts`
- `tooling_constraints`
- `residual_risks`
- `stale_reference_sweep`
- `final_reconciler`

Cross-field consistency rule: if any required unit validator is missing/failed, browser proof is pending/failed, startup failed, or unresolved residual risks remain, `status` must not be `fully_validated`.

## Stale-Reference Sweep

Before final validation, search docs and `.internal-dev` for:

- `dashboard-widget-suite` paths that point to old `/tmp` evidence.
- stale agent ids or validator ids.
- `pending`, `planned`, `not implemented`, `TODO` claims in finalized docs.
- old phase names after phase renumbering.
- superseded artifact directories not listed in validation summary.
