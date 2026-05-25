# Topic

Public API route-binding coverage pattern

# Source References

- `src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java`
- `.internal-dev/plans/public-alpha-remediation/07-validation-harness-regression/subplan-01-spring-web-route-coverage.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-17-high-test-coverage-route-context-gaps/report.md`

# Key Takeaways

- Use `@SpringBootTest` with `@AutoConfigureMockMvc` when the goal is to catch route mapping, request binding, security-filter, serialization, and application-context errors that direct controller construction misses.
- Back public route tests with an isolated SQLite database using `jdbc:sqlite:<tmp>.db?foreign_keys=true` so route tests can exercise real services without mutating the developer database.
- For public submit-to-agent SSE routes, assert semantic event names in the serialized stream body, such as `event:submitted` and `event:failed`, not Java class names.
- Avoid invoking model-backed chat in route-binding tests. Public chat coverage can still prove route binding through safe read routes and direct-execution rejection routes.
- On Java 25, Mockito inline mocking may fail on large concrete services. A real Spring context was more reliable than a broad `@WebMvcTest` slice for this route coverage.

# Engine Relevance

This pattern gives Magenta a durable regression layer between direct controller tests and browser campaigns. It is useful for public REST/SSE groups where a green unit test can miss mapping annotations, content negotiation, alpha security behavior, async SSE serialization, or application wiring failures.

# Open Questions

- Later validation-harness subplans should decide whether to split this broad route test into smaller domain-owned classes if startup time becomes a problem.
- Live model-backed chat streaming still belongs in the browser/MCP validation workflow, not in this fast route-binding test.
