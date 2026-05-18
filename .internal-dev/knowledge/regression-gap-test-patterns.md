## Topic
Regression Gap Test Patterns

## Source References
- `.internal-dev/plans/public-alpha-remediation/07-validation-harness-regression/subplan-04-regression-gap-tests.md`
- `src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepositorySchemaMigrationTest.java`

## Key Takeaways
- For route-contract regressions, prefer a Spring `MockMvc` request through the real web context over direct controller construction. This catches removed or accidentally reintroduced public routes, security filter interaction, DTO binding, and exception mapping.
- For no-op execution blockers, assert both the user-visible failure status/event and the absence of a durable assignment or run row. Status-only tests can miss old behavior that returned an error after already enqueueing work.
- For XSS regressions in server-rendered fragments, parse the HTML and assert DOM structure: no executable elements or event attributes, and expected values remain text or input values. This is less brittle than broad string-only escaping checks.
- For warm schema migration regressions, assert migrated row payloads and related rows together. Lease preservation tests should verify workspace owner/root/display/metadata fields and active/release-requested/released lease states in the same fixture.
- For ownership controls, route-level cross-agent tests should assert both the rejection status and that the owning agent's queue row remains unchanged.

## Engine Relevance
Public-alpha blockers often survived older green suites because tests checked helper behavior or happy-path statuses only. Future remediation domains should add one close-to-boundary regression per blocker class, with a negative assertion that proves the old vulnerable side effect did not happen.

## Open Questions
- Live browser XSS checks still require the repository Playwright validation-subagent workflow when the changed surface depends on actual browser event execution rather than server-rendered DOM shape.
