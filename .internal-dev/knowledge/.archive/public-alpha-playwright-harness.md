# Topic

Public alpha focused Playwright harness.

# Source References

- `playwright.config.js`
- `tests/playwright/public-alpha-harness.spec.js`
- `tests/playwright/README.md`
- `.internal-dev/plans/public-alpha-remediation/07-validation-harness-regression/subplan-02-playwright-harness.md`
- `.internal-dev/reviews/public-alpha-quality-review/playwright-public-pages-evidence.md`

# Key Takeaways

The checked-in harness is live-app-only. It assumes Spring Boot is already running and does not start or seed the app itself.

Default invocation:

```bash
npx playwright test --list
npx playwright test tests/playwright/public-alpha-harness.spec.js
```

Default environment:

- `MAGENTA_PLAYWRIGHT_BASE_URL=http://localhost:18080`
- `MAGENTA_ALPHA_USERNAME=alpha`
- `MAGENTA_ALPHA_PASSWORD=test-alpha-password`

When starting the live app for this harness, use an isolated SQLite database and alpha credentials matching the harness defaults:

```bash
MAGENTA_ALPHA_USERNAME=alpha MAGENTA_ALPHA_PASSWORD=test-alpha-password \
mvn spring-boot:run \
  -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta-playwright-harness.sqlite?foreign_keys=true'
```

# Engine Relevance

The suite is meant to catch the blocker classes from the public-alpha review without becoming a broad mandatory browser campaign. It checks public page reachability, mobile agent detail layout, plan HTMX persistence, workflow HTMX draft/node/validation behavior, and the alpha unsafe-mutation auth gate.

Each test attaches a `browser-diagnostics.json` artifact with console warnings/errors, page errors, failed requests, unexpected `500` responses, static asset failures, and explicitly expected non-2xx validation paths.

# Open Questions

None.
