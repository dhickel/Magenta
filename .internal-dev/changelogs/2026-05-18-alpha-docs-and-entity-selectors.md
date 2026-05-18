# Date

2026-05-18

# Change Summary

Implemented the alpha documentation and reusable entity selector orchestration.

- Added detailed docs under `docs/` for end users, technical contributors, and API consumers.
- Added a reusable web-layer entity selector package for searching and validating agents, plans/tasks, workflows, jobs, projects, workspaces, models, runs, and mixed submit targets.
- Replaced many operational UI manual ID fields with HTMX-backed searchable selectors, including submit forms, job editor fields, job item plan/workflow/model fields, schedules, reactions, settings, and output filters.
- Added selector CSS and focused tests for selector rendering, lookup validation, and existing orchestration controller flows.
- Recorded and archived the execution plan suite.

# Files

- `docs/`
- `src/main/java/io/mindspice/magenta2/api/web/selector/`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/css/orchestration.css`
- `src/test/java/io/mindspice/magenta2/api/web/selector/`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/plans/.archive/alpha-docs-and-entity-selectors/`

# Behavioral Impact

Operators can now search for common Magenta entities in the operational UI instead of copying opaque IDs for the updated fields. Manual entries still submit as normal form values but are validated before unsafe operations where the selector is used. Documentation now has a stable docs root, maintenance guide, end-user guides, technical architecture/API/service references, and selector route notes.

# Risks

- Some compact list/filter surfaces still use simple dropdowns or exact IDs where that is currently more appropriate, such as exact run ID filtering and existing summary tables.
- Selector routes are read-only public GET fragments under the current alpha access model, so they expose the same operational list data already visible in the UI.
- Playwright validation is focused on changed selector pages rather than a full end-to-end campaign.

# Follow-up Items

- Revisit remaining plain dropdown filters if the UI needs full combobox behavior on every filter surface.
- Add richer keyboard navigation for selectors only if user testing shows HTMX-only behavior is not enough.
