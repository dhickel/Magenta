---
schema_version: 1
document_type: worker-directive
status: planning
phase: 01
role: platform-foundation
worker_model: gpt-5.5
worker_reasoning: high
validator_model: gpt-5.5
validator_reasoning: xhigh
---

# Phase 01 Platform Foundation Directive

## Objective

Replace the static dashboard widget model with a registry-backed widget instance/settings/binding platform and migrate current dashboard rows/widgets without product data loss.

## Editable Targets

- `src/main/java/io/mindspice/magenta2/avatar/dashboard/**` new package.
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/resources/avatar-schema.sql`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- Focused tests under `src/test/java/io/mindspice/magenta2/avatar/` and `src/test/java/io/mindspice/magenta2/api/web/`.
- Docs/specs listed in closeout.

## Forbidden Scope

- Do not implement feature-rich widgets beyond migrated placeholders/shells.
- Do not add script/plugin execution.
- Do not make dashboards agent-owned.
- Do not change runtime/project/workspace ownership.

## Supporting Docs To Read

Read this plan suite, `.internal-dev/specifications/{web,simplypages,architecture,service-graph,services,api,decisions,deferred-features}.md`, targeted dashboard knowledge, package `AGENTS.md`, and the SimplyPages docs/demo named in the handoff.

## Implementation Steps

1. Add registry records/enums/services for widget definitions, instance policy, binding mode, settings schema/defaults, renderer keys, refresh policy, empty state, and tool descriptors.
2. Migrate persisted widgets from `widget_key` semantics to `widget_type`/`widgetInstanceId`; resolve `unique(dashboard_id, widget_key)` with partial/sentinel uniqueness only for single-instance widgets.
3. Add service-level validation for adding widgets: registry exists, supported width, row capacity, instance policy, settings defaults.
4. Add primary summary/detail/settings route family using widget instance ids while preserving compatibility routes where needed.
5. Implement generic settings modal shell with agent/project/Work Area/source placeholders and recoverable invalid-binding state.
6. Refactor static render switch behind registry renderer dispatch without broad unrelated rewrites.
7. Update specs/docs/decisions for registry, multi-instance, route/migration contract.

## Acceptance Criteria

- Multiple instances of a multi-instance widget type can exist on one dashboard.
- Single-instance widgets are rejected by service validation and persisted constraint.
- Legacy Assistant seed renders after migration.
- Settings JSON defaults and invalid binding errors are deterministic.
- Detail/settings routes use stable HTMX targets and OOB refreshes.
- Widget catalog no longer disables all existing types; it follows registry instance policy.

## Negative Checks

- No raw repository/filesystem access from controllers.
- No calendar/planner/project feature expansion beyond platform shells.
- No duplicate shell/nav/root after dashboard fragment swaps.

## Validation Commands

- `mvn -Dtest=AvatarRepositoryTest,AvatarServiceTest,AvatarDashboardControllerTest test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

## Browser Checklist

Delegate Playwright after code validation: `/`, `/dashboards/assistant?edit=true`, dashboard switching, add row/widget, multi-instance add, single-instance rejection, settings modal open/save/cancel, desktop and mobile screenshots with visual critique.

## Stop Conditions

Stop if additive migration cannot preserve rows/widgets/settings. Stop on model/tooling unavailability. Stop if SimplyPages patterns cannot support needed route/update model without a design revision.

## Do Not Close Unless

- Migration tests cover old and new schemas.
- Registry/instance policy tests pass.
- Specs/docs are updated.
- Validator reconciles Playwright evidence.
