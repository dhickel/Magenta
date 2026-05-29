---
schema_version: 1
document_type: senior-engineer-guidance
status: planning
owner: advanced-planner
created: 2026-05-29
---

# Senior Engineer Guidance

## Architecture Rules

- Start from the registry/instance/binding model. Do not add feature widgets on top of the static `WIDGETS` list.
- Keep dashboards layout-owned and agent-agnostic. Widget binding is per-instance settings only.
- Keep data in the owning service/database. `avatar.sqlite` owns personal organizer/dashboard state; runtime/project/workspace/output services own their domains.
- Services own validation and use cases. Controllers translate HTTP/HTMX and render fragments.
- Repositories own SQL and JSON persistence details. Do not parse settings ad hoc in controllers.
- Prefer additive migrations and compatibility bridges over destructive rewrites.
- If a service API is missing, add a focused service method or return to planning. Do not bypass services with direct repository/filesystem access.

## UI Rules

- Use SimplyPages `Row`/`Column` for layout and module width.
- Use stable IDs and HTMX target contracts; OOB responses update modal host plus changed summary where needed.
- Keep summary cards bounded. Detail/settings surfaces handle complexity.
- Use existing file explorer/details-list visual language for file-backed note widgets.
- Use existing entity selector patterns for agent/project/Work Area binding.
- JavaScript must be narrow and justified in the worker output.

## Security And Trust

- Do not add arbitrary script execution.
- Do not let normal-agent tools accept arbitrary `agentId`.
- Do not create cross-database foreign keys.
- Keep Work Area/file operations behind confinement services.
- Destructive/high-impact mutations require exact confirmation where current domain patterns require it.

## Migration Discipline

- Write tests that boot old schema/fixture state and prove upgrade to target model.
- Preserve old dashboard rows/widgets and settings JSON.
- Use service-level registry validation for single vs multi-instance policy.
- Backfill `widget_type` and `single_instance_key` deterministically.

## Validation Discipline

- Every phase must run focused tests and bounded startup when wiring/schema changes.
- UI phases must have code validation before Playwright.
- Browser proof returns to the validator for reconciliation.
- Evidence status must be conservative and cross-field consistent.
