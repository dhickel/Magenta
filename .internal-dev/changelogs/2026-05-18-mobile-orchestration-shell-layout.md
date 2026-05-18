# Date

2026-05-18

# Change Summary

Fixed bug-16 by overriding the SimplyPages sidebar shell grid for operational pages at phone width. The first v8 cache-busted fix still failed live mobile validation because the fixed sidebar kept `grid-area: sidebar` while the mobile grid defined only `content`, creating implicit grid columns that squeezed the page. The v9 fix keeps one content column and resets the mobile sidebar grid placement so the off-canvas sidebar does not reserve layout width.

# Files

- `src/main/resources/static/css/orchestration.css`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

# Behavioral Impact

At phone viewport widths, `.main-container.has-sidebar` no longer lets the off-canvas `.main-sidebar` create implicit sidebar tracks that squeeze `.content-wrapper` and `#content-area` to roughly 70-100px. Desktop layout remains governed by the existing SimplyPages grid and Magenta operational page styles.

# Validation

- Prior validation failed on commit `342e051`: `/tmp/domain06-subplan01-actual-mobile-measurements-342e051.json` measured `.content-wrapper` and `#content-area` at `84px`, with grid columns `84px 0px 250px`.
- `mvn -Dtest=OrchestrationControllerTest,FrontendControllerTest test` passed with 84 tests.
- Static coverage confirms the mobile `.main-container.has-sidebar > .main-sidebar { grid-area: auto; }` override and `orchestration.css?v=9` cache-bust references.
- `git diff --check` passed.
- Bounded Spring startup reached `Started Magenta2Application` on port `43965` with isolated SQLite DB `/tmp/domain06-subplan01-v9-startup.sqlite`; the command exited `124` only after the timeout wrapper stopped the live app.

# Risks

Browser-origin Playwright proof at `390x780` is still required by the validation agent. The implementer added static CSS coverage and startup proof but did not run inline Playwright.

# Follow-up Items

- Run the focused validation-agent Playwright check for `/agents/{agentId}` at `390x780`.
