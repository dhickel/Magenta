# Security Access Control Domain Validation

## Topic

Public-alpha security access control validation for auth/CSRF, path segment ids, workflow XSS, and assignment lifecycle ownership.

## Source References

- `.internal-dev/plans/public-alpha-remediation/01-security-access-control/validation-gate.md`
- `src/main/java/io/mindspice/magenta2/api/web/AlphaSecurityConfiguration.java`
- `src/main/java/io/mindspice/magenta2/core/util/PlainPathSegmentValidator.java`
- `src/main/resources/static/js/orchestration/workflows.js`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`

## Key Takeaways

- Method-based alpha security keeps public reads available while protecting unsafe mutations with HTTP basic auth and CSRF.
- Data-root confinement must be paired with plain-segment validation before composing ids into fixed filesystem subtrees.
- Workflow graph code must render persisted node fields through DOM text/value APIs, not raw `innerHTML` interpolation.
- Agent-scoped lifecycle routes should call service overloads that accept both `agentId` and `assignmentId`.
- Domain validation evidence should include focused tests, full `mvn test`, bounded startup, and browser-origin proof for UI security bugs.

## Engine Relevance

Future domains should reuse these boundaries rather than weakening them: unsafe public routes stay behind alpha auth/CSRF, path ids use `PlainPathSegmentValidator`, workflow UI preserves inert rendering, and assignment mutation remains route-agent scoped.

## Open Questions

- Whether a later schema/data domain should add a migration or diagnostic for pre-existing invalid persisted ids.
