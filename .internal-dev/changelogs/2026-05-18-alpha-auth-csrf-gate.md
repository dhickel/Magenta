# Alpha Auth CSRF Gate

## Date

2026-05-18

## Change Summary

Added the public-alpha access gate for subplan 01 of `01-security-access-control`. Read-only routes remain public, while unsafe web/API mutation and control requests now require the configured alpha basic credential and a CSRF token.

## Files

- `pom.xml`
- `src/main/java/io/mindspice/magenta2/api/web/AlphaSecurityConfiguration.java`
- `src/main/resources/static/js/alpha-security.js`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/application.yml`
- `src/test/resources/application.yml`
- `src/test/java/io/mindspice/magenta2/api/web/AlphaSecurityConfigurationTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-01-critical-security-unauthenticated-control-surface/report.md`

## Behavioral Impact

Unsafe methods (`POST`, `PUT`, `PATCH`, `DELETE`, and other non-read methods) now require alpha authentication and CSRF. Browser shells load a shared helper that adds the CSRF header for HTMX and same-origin `fetch` mutations.

## Risks

Existing API clients and browser sessions must supply the configured alpha credentials and CSRF token for mutation calls. The default alpha password is a development fallback and should be overridden with `MAGENTA_ALPHA_PASSWORD` before remote exposure.

## Follow-up Items

Run the planned validation-agent pass for the full security-access-control domain before marking bug-01 passed.
