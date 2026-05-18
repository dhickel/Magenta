# Date

2026-05-18

# Change Summary

Implemented public alpha remediation Domain 06 subplan 03 for operational HTMX mutation error statuses. Failed shell exec, queue delete, hard delete, and settings save fragments now keep their operator-visible error bodies while setting meaningful non-2xx HTTP status codes.

Follow-up fix after validation of commit `df7f99d`: HTMX 1.9 does not visibly swap `>=400` responses by default. The shared alpha security helper now opts into swapping same-origin operational error fragments with live targets and known server-rendered error markers, while leaving 401/403 auth and CSRF banner behavior intact.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/js/alpha-security.js`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/knowledge/htmx-fragment-error-statuses.md`

# Behavioral Impact

HTMX clients and browser automation can now distinguish failed operational mutations from successful swaps by status code without losing the helpful error fragment body. Standard CRUD and mutation flows remain HTMX-first; no JavaScript transport was added. The JavaScript change only adjusts HTMX swap policy for server-rendered same-origin error fragments.

# Validation

- `mvn -Dtest=OrchestrationControllerTest test` passed with 86 tests.
- `node --check src/main/resources/static/js/alpha-security.js` passed.
- `git diff --check` passed.
- Bounded Spring startup reached `Started Magenta2Application` with isolated SQLite DB `/tmp/domain06-subplan03-swap-parent.sqlite`; log: `/tmp/domain06-subplan03-swap-parent-startup.log`.
- Delegated browser-origin validation remains pending.

# Risks

Delegated browser validation should confirm the visible fragments now render in the live UI for the changed targets, especially the settings form failure that failed with `visibleError=false` on `df7f99d`.

# Follow-up Items

Run the Domain 06 validation-agent browser-origin HTMX proof for bug-20 before marking the subplan passed.
