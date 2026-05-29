---
schema_version: 1
document_type: research-handoff
status: ready_for_manual_dispatch
created: 2026-05-29
recommended_model: gpt-5.5
recommended_reasoning: xhigh
---

# Widget Plugin And Live Scripting Research Handoff

## Objective

Research and produce a concrete architecture report for user-defined dashboard widgets and lightweight live scripting in Magenta. The report should explain how Magenta can let agents and users define safe, useful widgets that interact with text files, structured file schemas, external APIs, and approved Java service facades without turning the dashboard into an unsafe arbitrary-code host.

This is a research and planning task only. Do not implement product code.

## Required Context

Start in `/home/hickelpickle/Code/Java/magenta2`.

Read first:

- `AGENTS.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `.internal-dev/plans/dashboard-widget-suite/`
- `.internal-dev/knowledge/dashboard-api-contract.md`
- `.internal-dev/knowledge/dashboard-fragment-navigation.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/file-tool-workspace-scope-pattern.md`
- `.internal-dev/knowledge/shell-tool-confinement-pattern.md`
- Relevant current dashboard/avatar/widget Java classes and docs after the dashboard widget suite branch state.

## Research Questions

1. What is the smallest robust plugin model for Magenta dashboard widgets?
2. How should plugin manifests declare widget identity, settings schema, tool contracts, permissions, file bindings, and UI fragments?
3. Can an XML schema describe interactions between widgets and text files well enough for simple user plugins?
4. Should XML be the source of truth, an interchange format, or a generated/validated descriptor paired with a higher-level authoring format?
5. What small DSL should Magenta support for declarative widgets similar to the built-in dashboard widgets?
6. How can Kawa Scheme be used for live scripted widgets that query APIs or transform local data?
7. What Java facade classes should Magenta expose to scripts so users do not need raw Java/Spring internals?
8. How should agents safely inspect, create, update, test, and install user plugins?
9. How should plugin widgets store user data when DB-backed storage is unavailable or intentionally avoided?
10. How should plugin validation, sandboxing, versioning, rollback, and migration work?

## Must Cover

- Plugin package layout under a user/project/agent-owned directory.
- Manifest schema with explicit version, widget ids, display metadata, permissions, tool definitions, settings schema, data bindings, and migration/version fields.
- XML schema option for file-backed widgets, including examples for notes, todo lists, calendar/task lists, project materials, contacts, weather snapshots, and status summaries.
- A tiny declarative DSL option for widgets, including grammar-level examples and how it maps to the manifest/XML model.
- Kawa Scheme scripting architecture, including classloader/module loading, allowed imports, Java facade API, script lifecycle, error reporting, and reload behavior.
- A weather widget example that queries an API through an approved facade, persists a compact cache/snapshot, and renders a dashboard card/fragment.
- Agent-facing tool model: how plugins expose safe tool definitions; how agents discover tools; how tool calls validate arguments; how outputs map to files or widget state.
- File-backed persistence model: expected schemas, directory structure, lock/write strategy, conflict handling, atomic writes, and compatibility with future transparent versioning.
- Security model: permissions, path confinement, network allowlists, secrets handling, execution timeouts, rate limits, output size limits, audit logs, and user confirmation gates.
- UI model: SimplyPages/HTMX integration, settings modal behavior, preview/test harness, error states, visual consistency, and mobile constraints.
- Validation model: static validation, schema validation, script dry-run, tool contract tests, browser proof checklist, and regression evidence.
- Migration path from built-in widgets to plugin-capable descriptors without rewriting the existing dashboard suite.
- Clear recommendation with staged implementation phases.

## External Research Targets

Use current official/primary sources where possible. At minimum research:

- Kawa Scheme current documentation and Java interop embedding model.
- Java XML schema validation APIs and practical schema-versioning patterns.
- Safe plugin/scripting patterns for Java applications.
- Sandboxing reality after Java SecurityManager deprecation; assess process isolation, classloader isolation, permission facades, Graal/JShell alternatives only as comparisons.
- HTMX/fragment-driven plugin UI constraints if local context is insufficient.

## Constraints

- Magenta is an operational assistant, not a generic plugin marketplace.
- Default widget UX should remain dense, calm, and consistent with the Avatar dashboard, file browser, file viewer, and chat surfaces.
- Do not require every plugin to have database tables.
- Do not let scripts bypass Magenta path confinement, service validation, or future versioning.
- Do not expose raw Spring application context, arbitrary filesystem access, unrestricted network calls, or direct secrets access to scripts.
- Do not propose external notification delivery as an implicit plugin baseline.
- Prefer explicit, boring contracts over magical discovery.

## Expected Deliverable

Write one report artifact:

- `.internal-dev/plans/widget-plugin-scripting-research/01-investigation-report.md`

The report should include:

- Executive recommendation.
- Current-state summary grounded in repository files.
- Compared architecture options with tradeoffs.
- Recommended plugin package layout.
- Manifest/schema examples.
- XML schema examples.
- DSL examples.
- Kawa Scheme facade and lifecycle design.
- Weather widget worked example.
- Agent/tool access model.
- Security and validation model.
- Implementation phases with concrete acceptance criteria.
- Open questions and decisions needed from the user.

Also produce an HTML email-ready rendering of the full report, but do not send it unless explicitly instructed.

## Quality Bar

This report must be detailed enough for a later implementation agent to build from without redoing the strategy work. Avoid MVP language. If an option is unsafe or likely to cause validation churn, say so directly and recommend against it.

The final recommendation should be opinionated and should identify the first two or three implementation slices that can land safely while preserving the larger plugin architecture.
