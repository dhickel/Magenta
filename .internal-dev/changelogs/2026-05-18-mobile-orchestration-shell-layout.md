# Date

2026-05-18

# Change Summary

Fixed bug-16 by overriding the SimplyPages sidebar shell grid for operational pages at phone width. The mobile shell now uses one content column while keeping the existing off-canvas sidebar behavior and server-rendered HTMX pages unchanged.

# Files

- `src/main/resources/static/css/orchestration.css`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/knowledge/mobile-operational-shell-sidebar-overrides.md`

# Behavioral Impact

At phone viewport widths, `.main-container.has-sidebar` no longer reserves a fixed sidebar column that squeezes `#content-area` to roughly 70-100px. Desktop layout remains governed by the existing SimplyPages grid and Magenta operational page styles.

# Validation

- `mvn -Dtest=OrchestrationControllerTest,FrontendControllerTest test` passed with 84 tests.
- Static scan confirmed the mobile `.main-container.has-sidebar` override and `orchestration.css?v=8` cache-bust references.
- `git diff --check` passed.
- Bounded Spring startup reached `Started Magenta2Application` on port `34115` with isolated SQLite DB `/tmp/domain06-subplan01-parent.sqlite`.

# Risks

Browser-origin Playwright proof at `390x780` is still required by the validation agent. The implementer added static CSS coverage and startup proof but did not run inline Playwright.

# Follow-up Items

- Run the focused validation-agent Playwright check for `/agents/{agentId}` at `390x780`.
