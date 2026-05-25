# Security Access Control Domain Validation

## Topic

Public-alpha security access control validation for open-alpha route safeguards, path segment ids, workflow XSS, and assignment lifecycle ownership.

## Source References

- `docs/technical/security.md`
- `.internal-dev/knowledge/open-alpha-doc-posture-sync.md`
- `src/main/java/io/mindspice/magenta2/core/util/PlainPathSegmentValidator.java`
- `src/main/resources/static/js/orchestration/workflows.js`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`

## Key Takeaways

- Current alpha access is intentionally open at the application layer; Magenta does not currently enforce built-in HTTP auth, authorization, or CSRF checks on web/API routes.
- Unsafe mutations need explicit controller/service validation, domain ownership guards, path validation, and route-agent scoping rather than relying on a global auth/CSRF filter.
- Data-root confinement must be paired with plain-segment validation before composing ids into fixed filesystem subtrees.
- Workflow graph code must render persisted node fields through DOM text/value APIs, not raw `innerHTML` interpolation.
- Agent-scoped lifecycle routes should call service overloads that accept both `agentId` and `assignmentId`.
- Domain validation evidence should include focused tests, full `mvn test`, bounded startup, and browser-origin proof for UI route-safety bugs.

## Engine Relevance

Future domains should reuse these boundaries rather than weakening them: unsafe public routes use explicit validation and ownership checks, path ids use `PlainPathSegmentValidator`, workflow UI preserves inert rendering, and assignment mutation remains route-agent scoped. If auth/CSRF returns, update `docs/technical/security.md`, web package guidance, and related knowledge in the same pass.

## Open Questions

- Whether a later schema/data domain should add a migration or diagnostic for pre-existing invalid persisted ids.
