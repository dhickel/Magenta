# Date

2026-05-18

# Change Summary

Removed stale static JavaScript modules that were not loaded by active public-alpha pages and still carried obsolete direct-run, inbox, workflow, or output behavior.

# Files

- `src/main/resources/static/js/magenta-tools.js`
- `src/main/resources/static/js/orchestration/inbox.js`
- `src/main/resources/static/js/orchestration/outputs.js`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

# Behavioral Impact

Active `/inbox` and `/outputs` pages remain server-rendered HTMX surfaces. No active page loads the deleted modules; stale direct-run JavaScript transport is no longer shipped as a static asset.

# Risks

External bookmarks or manually authored pages that loaded the deleted static files directly will now receive static asset 404s. No current application page references those files.

# Follow-up Items

Browser-origin validation should confirm no public page requests the deleted static assets.
