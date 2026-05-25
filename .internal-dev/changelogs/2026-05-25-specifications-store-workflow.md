# Date

2026-05-25

# Change Summary

Replaced the active internal-dev focus/notes workflow with a flat `.internal-dev/specifications/` store. Added specification routing, schemas, domain contracts, decisions, deferred-feature and horizon-idea registers, and a migration/drop audit for retired focus and notes data.

# Files

- `AGENTS.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `.internal-dev/specifications/index.md`
- `.internal-dev/specifications/workflow.md`
- `.internal-dev/specifications/schema.md`
- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/service-graph.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/decisions.md`
- `.internal-dev/specifications/deferred-features.md`
- `.internal-dev/specifications/horizon-ideas.md`
- Deleted retired `.internal-dev/focus/`
- Deleted retired `.internal-dev/notes/`

# Behavioral Impact

No product behavior changed. The internal workflow now routes intended contracts to specifications, durable decisions to `specifications/decisions.md`, accepted future capability to `specifications/deferred-features.md`, future direction to `specifications/horizon-ideas.md`, and reusable learning to `.internal-dev/knowledge/`.

# Specification Impact

Specification Impact: created the canonical specification store and migrated active workflow intent from retired focus and notes artifacts into flat living specification files.

# Risks

Historical changelogs, reviews, and archived plans may still mention retired focus/notes paths as historical evidence. Active workflow guidance in `AGENTS.md`, `.internal-dev/AGENTS.md`, and the specification store now avoids those stores.

# Follow-up Items

- None.

# Validation

- Static validation commands from the implementation directive were run after implementation.
- Product validation not run: documentation/workflow-only change; no Java, resource, route, UI, schema, or configuration behavior changed.
