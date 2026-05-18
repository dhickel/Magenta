# Documentation Foundation

## Topic

Alpha documentation folder structure and update governance.

## Source References

- `.internal-dev/plans/alpha-docs-and-entity-selectors/01-documentation-foundation.md`
- `docs/AGENTS.md`
- `docs/README.md`

## Key Takeaways

- `docs/README.md` is the alpha documentation entry point.
- `docs/end-user/00-index.md` owns operator-facing usage docs.
- `docs/technical/00-index.md` owns contributor-facing architecture, service, persistence, frontend, security, and operations docs.
- `docs/api/00-index.md` owns route, payload, streaming, and integration contracts.
- `docs/maestro/` remains older Maestro design/planning material and is not the alpha docs entry point.

## Engine Relevance

Agents implementing future Magenta features should update relevant docs as part of the same change: end-user docs for behavior changes, technical docs for internals/config/schema/API/service changes, and API docs for route or payload changes.

## Open Questions

- Later documentation phases still need code-verified details for each placeholder page.
