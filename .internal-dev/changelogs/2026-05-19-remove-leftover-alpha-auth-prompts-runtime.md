# Remove Leftover Alpha Auth Prompts (Runtime)

## Date

2026-05-19

## Change Summary

Removed the remaining alpha authentication/CSRF runtime implementation so unsafe routes no longer trigger login prompts or CSRF failures. Also removed frontend auth helper injection, deleted the obsolete helper asset, and updated Playwright harness/config to validate open-alpha behavior instead of auth-gate behavior.

## Files

- `pom.xml`
- `src/main/java/io/mindspice/magenta2/api/web/AlphaSecurityConfiguration.java` (deleted)
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/application.yml`
- `src/main/resources/static/js/alpha-security.js` (deleted)
- `src/test/java/io/mindspice/magenta2/api/web/AlphaSecurityConfigurationTest.java` (deleted)
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java`
- `src/test/resources/application.yml`
- `playwright.config.js`
- `tests/playwright/public-alpha-harness.spec.js`
- `tests/playwright/README.md`

## Behavioral Impact

- No application-level Basic auth prompt or CSRF gate remains in the Spring web layer.
- Plan creation and other unsafe mutation flows now pass directly to domain/controller validation paths.
- Chat/session mutations (including delete flows) are no longer blocked by alpha auth prompts.
- Playwright harness no longer sends alpha credentials/CSRF headers and now asserts domain-validation behavior without auth gating.

## Risks

- The app now relies on deployment perimeter controls for access restriction; there is no in-app auth barrier in this layer.
- Any legacy automation expecting auth-gate 401/403 responses will need updating.

## Follow-up Items

- If/when access control returns, implement as a new scoped design instead of reviving removed alpha gate artifacts.
- Keep Playwright focused checks aligned with current access posture to prevent false positives/negatives.
