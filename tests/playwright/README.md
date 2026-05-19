# Public Alpha Playwright Harness

This harness is a focused browser regression suite for public page reachability and critical HTMX workflows. It assumes Magenta is already running; it does not start Spring Boot.

## Live App Requirement

Start the app separately on the base URL used by the suite. The default is `http://localhost:18080`:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta-playwright-harness.sqlite?foreign_keys=true'
```

Use a fresh SQLite database for validation runs when persistence assertions should be isolated.

## Invocation

```bash
npx playwright test --list
npx playwright test tests/playwright/public-alpha-harness.spec.js
```

Configuration environment variables:

- `MAGENTA_PLAYWRIGHT_BASE_URL`: live app URL. Defaults to `http://localhost:18080`.

## Scope

The suite intentionally stays focused. It covers:

- Public page reachability for the alpha route matrix.
- Agent detail shell at a mobile viewport.
- HTMX plan draft creation, save, reload visibility, and API persistence.
- Chat delete endpoint probe that verifies no auth/CSRF gate (`204`/domain path, not `401`/`403`).
- HTMX workflow draft creation, save, node add, validation, submit-panel validation separation, and API persistence.
- An explicit expected non-2xx unsafe anonymous mutation probe so domain-validation failures are separated from unexpected failures.

Each test attaches browser diagnostics with console warnings/errors, page errors, failed requests, unexpected `500` responses, and static asset failures. Unexpected server errors or failed static assets fail the test.
