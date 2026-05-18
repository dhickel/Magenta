# Operational UI Validation Gate

## Validator Instructions

Read UI/frontend reviews, Playwright evidence, and bug reports 16, 18, and 20 before validating.

## Required Checks

- Mobile `/agents/{agentId}` content is usable at `390x780`.
- Lifecycle HTMX target updates visible content.
- HTMX error paths return non-2xx.
- Stale active Docker/Podman labels are removed.
- Placeholder events and shallow workspace health are addressed.
- Focused Playwright, focused tests, full `mvn test`, and bounded startup pass.
