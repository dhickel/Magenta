---
schema_version: 1
document_type: changelog
status: complete
date: 2026-05-29
---

# Date

2026-05-29

# Change Summary

Implemented the Phase 01 dashboard widget platform foundation: registry-backed widget definitions, widget instance ids, additive widget table migration, service-level add/settings validation, instance-id summary/detail/settings routes, settings modal shell, registry renderer dispatch, and catalog instance-policy behavior.

# Files

- `src/main/java/io/mindspice/magenta2/avatar/dashboard/**`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/resources/avatar-schema.sql`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarRepositoryTest.java`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarServiceTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `.internal-dev/specifications/{architecture,service-graph,services,api,web,simplypages,decisions}.md`
- `docs/api/00-index.md`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `docs/technical/avatar-dashboard-layout-persistence.md`

# Behavioral Impact

Dashboard widget placements are now widget instances. Multi-instance registry types such as Notes can be added more than once on a dashboard, while single-instance types are blocked by service validation and SQLite `single_instance_key` uniqueness. Widget cards render with instance-root ids, and settings saves return OOB modal close plus refreshed summary.

# Specification Impact

Updated architecture, service graph, services, API, web, SimplyPages, and decisions specs for the registry/instance/settings route contract.

# Risks

Feature-rich widget detail/settings behavior remains intentionally shallow until later dashboard-widget-suite phases. Playwright visual proof is still delegated and not completed by this worker.

# Follow-up Items

- Delegate Phase 01 browser proof for normal/edit dashboard, catalog policy, multi-instance add, settings modal save/cancel, and desktop/mobile visual critique.
- Continue Phase 02+ widget implementation only after validator reconciliation.
