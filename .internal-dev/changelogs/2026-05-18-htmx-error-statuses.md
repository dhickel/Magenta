# Date

2026-05-18

# Change Summary

Implemented public alpha remediation Domain 06 subplan 03 for operational HTMX mutation error statuses. Failed shell exec, queue delete, hard delete, and settings save fragments now keep their operator-visible error bodies while setting meaningful non-2xx HTTP status codes.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/knowledge/htmx-fragment-error-statuses.md`

# Behavioral Impact

HTMX clients and browser automation can now distinguish failed operational mutations from successful swaps by status code without losing the helpful error fragment body. Standard CRUD and mutation flows remain HTMX-first; no JavaScript transport was added.

# Validation

- `mvn -Dtest=OrchestrationControllerTest test` passed with 85 tests.
- `git diff --check` passed.
- Bounded Spring startup reached `Started Magenta2Application` with isolated SQLite DB `/tmp/domain06-subplan03-parent.sqlite`; log: `/tmp/domain06-subplan03-parent-startup.log`.

# Risks

Existing HTMX behavior generally swaps non-2xx responses into configured targets only when error handling allows it, so the delegated browser validation should confirm the visible fragments still render in the live UI for the changed targets.

# Follow-up Items

Run the Domain 06 validation-agent browser-origin HTMX proof for bug-20 before marking the subplan passed.
