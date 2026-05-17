# Public REST/SSE and Spring Web Coverage Gaps

## Summary

The Maven suite passes, but many public REST/SSE controller groups have no dedicated tests and there is no Spring web/application-context test layer.

## Scope

Test harness and public route coverage.

## Reproduction

1. Scan `src/test` for tests matching major controllers and Spring web annotations.
2. Compare to route inventory.

## Expected

Public-alpha routes have endpoint binding/status/DTO/SSE coverage and at least focused Spring web/context smoke coverage.

## Actual

Many controllers lack dedicated tests; existing web tests mostly instantiate controllers directly.

## Evidence

- No matching `JobControllerTest`, `ProjectControllerTest`, `PlanControllerTest`, `TaskControllerTest`, `WorkflowControllerTest`, `DashboardControllerTest`, `RuntimeSettingsControllerTest`, `RuntimeControllerTest`, `OutputControllerTest`, `ModelControllerTest`, or `FrontendFragmentControllerTest` found.
- No `@SpringBootTest`, `@WebMvcTest`, `MockMvc`, or `TestRestTemplate` matches found in `src/test`.
- Full `mvn test` still passed with 444 tests.

## Impact

High: route binding, validation, production config, resource handling, and SSE bugs can survive the green test suite.

## Status

Open.

## Next Action

Add focused Spring web smoke tests for public REST/SSE groups and browser-backed checks for critical HTMX workflows.
