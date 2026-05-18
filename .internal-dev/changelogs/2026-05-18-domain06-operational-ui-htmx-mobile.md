# Domain 06 Operational UI HTMX Mobile

## Summary

Completed the `06-operational-ui-htmx-mobile` public-alpha remediation domain.

## Changes

- Validated mobile operational shell layout, agent lifecycle HTMX swaps, non-2xx HTMX error fragments, stale runtime label cleanup, and agent detail workspace health.
- Fixed the domain-gate stale wording miss after the first validation pass by removing remaining active `container` runtime terms from comments, parameters, and test fixtures.
- Preserved generic DOM/layout `container` terminology where it describes frontend structure rather than runtime provenance.

## Validation

- `mvn -Dtest=OrchestrationControllerTest,AgentShellToolServiceTest,OutputArtifactServiceAttributionTest test` passed with 127 tests.
- Delegated domain gate passed at `990fc12` with full `mvn test` passing 539 tests.
- `node --check src/main/resources/static/js/alpha-security.js`, `git diff --check`, `git show --check HEAD`, and bounded Spring startup passed.
- Live browser validation on port `18080` used isolated SQLite at `/tmp/domain06-reval-live/chat-memory.db` and covered mobile `390x780`, lifecycle HTMX swaps, HTMX error fragments, CSRF handling, placeholder event removal, and service-backed workspace health.

## Out Of Scope

No new out-of-scope bugs or user-approved deferrals were discovered during the domain closeout.
