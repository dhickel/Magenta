# Date
2026-05-13

# Change Summary
Fixed duplicated outer UI chrome when navigating between full-page routes (`/`, `/chat`, `/dashboard`) by disabling HTMX link wiring in top navigation components that serve full HTML shells.

# Files
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

# Behavioral Impact
Top nav links now perform full-page navigation instead of HTMX fragment swaps for shell-level route changes. This prevents shell-in-shell rendering and keeps a single border/shell instance per page.

# Risks
Navigation between shell routes now does full reloads by design; this is expected and consistent with current server-rendered full-document endpoints.

# Follow-up Items
- Deferred idea logged: add a reusable policy/helper so full-document routes cannot accidentally re-enable HTMX nav attributes in top-level shell navs.
