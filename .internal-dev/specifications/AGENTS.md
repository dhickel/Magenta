# Specifications Store Guide

`.internal-dev/specifications/` is the flat living store for intended project contracts, durable decisions, accepted deferred capability, and horizon ideas.

## Required Discipline

- Update existing living specification files by default.
- Create a new specification file only for a genuinely new specification class and update `index.md` in the same change with the new file's ownership boundary.
- Keep this directory flat. Do not add architecture, API, services, web, SimplyPages, decisions, deferred, or horizon subdirectories.
- Before changing a service, API, web page or fragment, SimplyPages component/module, architecture, persistence contract, workflow behavior, or product contract, read the relevant specification file.
- During closeout, update affected specifications or record `Specification Impact: none` in the changelog with one sentence explaining why.

## File Routing

- `architecture.md`: system architecture direction, boundaries, persistence ownership, runtime constraints, and architecture drift.
- `service-graph.md`: service dependencies and allowed interaction paths.
- `services.md`: service use-case contracts, service-owned behavior, and validation expectations.
- `api.md`: REST/SSE payload, route, status-code, and API compatibility contracts.
- `web.md`: page, fragment, shell, UX, route, and browser-validation contracts.
- `simplypages.md`: SimplyPages component/module/layout/HTMX contracts and reusable UI policy.
- `decisions.md`: durable decisions with justification, alternatives or tradeoffs when known, caveats, affected specs, source, and review timing.
- `deferred-features.md`: accepted future product capability.
- `horizon-ideas.md`: future direction that is not accepted deferred capability.
- `workflow.md`: specification workflow rules and migration/drop audit.
- `schema.md`: compact schemas and examples for entries in this store.

## Drift Handling

- Code remains observed truth; specifications are intended truth.
- If code and specifications disagree, add a drift record in the affected spec and route the follow-up to implementation, bug tracking, deferred capability, horizon ideas, or decisions.
- Do not duplicate intended truth across competing spec files. Link related specs instead.
