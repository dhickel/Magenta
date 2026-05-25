---
schema_version: 1
document_type: changelog
status: active
created: 2026-05-25
owner: codex
---

# Primary Top Navigation Normalization

## Date

2026-05-25

## Change Summary

- Normalized the primary top navigation across the home, chat, dashboard, and Avatar shells.
- Moved Avatar out of the primary top nav; Avatar remains reachable from the home page.
- Set the shared top nav order to `Home`, `Dashboard`, `Chat`.
- Added a small shared navigation helper so the three shell builders do not drift independently.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/AppNavigation.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`

## Behavioral Impact

- Users see the same primary navigation links on home, chat, dashboard, and Avatar pages.
- Dashboard pages no longer lose the Home and Dashboard top links.
- Avatar is treated as a home-page entry point rather than a top-level primary nav link.

## Validation

- `mvn -q -Dtest=FrontendControllerTest,AvatarDashboardControllerTest,OrchestrationControllerTest test` passed.

## Follow-up Items

- None.
