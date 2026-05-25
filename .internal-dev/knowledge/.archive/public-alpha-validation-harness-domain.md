# Public Alpha Validation Harness Domain

The public alpha validation harness should exercise both route binding and browser-origin behavior.

- Use `@SpringBootTest` plus `MockMvc` for route binding coverage when route wiring, filters, serialization, and controller method signatures are the risk. Keep model-backed chat calls out of these tests unless the contract requires them.
- SQLite-backed repository and Spring fixtures should include `foreign_keys=true` unless a test explicitly documents why it is validating legacy non-FK behavior.
- Schedule and reaction parity tests need to override test-disabled feature flags when they assert runtime polling or event publication behavior.
- The Playwright harness should be live-app-only: start Spring separately with an isolated DB and alpha credentials, then run `tests/playwright/public-alpha-harness.spec.js` with `MAGENTA_PLAYWRIGHT_BASE_URL`.
- Domain validation should include a clean startup and a second warm startup against the same isolated SQLite DB to catch migration regressions that one-shot tests miss.
