# Open Alpha Auth Residue Removal Checklist

## Topic

A practical checklist to fully remove an app-layer auth/CSRF system without leaving prompt-causing residue.

## Source References

- `src/main/java/io/mindspice/magenta2/api/web/AlphaSecurityConfiguration.java`
- `src/main/resources/static/js/alpha-security.js`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `playwright.config.js`
- `tests/playwright/public-alpha-harness.spec.js`

## Key Takeaways

- Removing only one layer is insufficient; auth prompts can persist from backend filter chains, frontend injected helpers, and test harness defaults.
- Residue scan terms that catch most misses: `alpha-access`, `AlphaSecurity`, `alpha-security.js`, `MAGENTA_ALPHA_`, `XSRF`, `WWW-Authenticate`, `magenta:security-error`.
- If `spring-boot-starter-security` remains after deleting custom security config, validate behavior explicitly to avoid accidental default security surprises.
- Playwright `httpCredentials` can hide auth-removal regressions by continuing to authenticate every request.

## Engine Relevance

Use this checklist when reversing temporary security gates in alpha/beta environments to avoid partial removals that leave user-facing login prompts on mutation routes.

## Open Questions

- Should this checklist be promoted into `docs/technical/security.md` as a rollback/remediation appendix for temporary access-control experiments?
