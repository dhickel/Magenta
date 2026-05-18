# Date

2026-05-18

# Change Summary

Added a focused, reusable Playwright harness for public-alpha browser validation. The harness assumes a live Spring app, supports configurable base URL and alpha Basic auth credentials, captures browser diagnostics, fails on unexpected `500` responses or static asset failures, and separates an expected unsafe anonymous mutation auth failure from unexpected network noise.

# Files

- `playwright.config.js`
- `tests/playwright/public-alpha-harness.spec.js`
- `tests/playwright/README.md`
- `.internal-dev/knowledge/public-alpha-playwright-harness.md`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

# Behavioral Impact

Validation agents can now run a checked-in focused browser suite against an already-running Magenta app on `http://localhost:18080` by default. The suite covers public page reachability, mobile agent shell readability, plan HTMX persistence, and workflow HTMX critical flow.

# Risks

The full suite requires a live app with at least one seeded agent and alpha credentials matching the configured environment.

# Follow-up Items

None.
