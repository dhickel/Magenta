---
status: active
created: 2026-05-25
owner: advanced-planning
classification: small
source_intent: .internal-dev/plans/specifications-store-workflow/replacement-handoff.md
---

# Specification Store Workflow Lock

## Locked Objective

Replace the active `.internal-dev/focus/` and `.internal-dev/notes/` workflow with a flat `.internal-dev/specifications/` store that owns intended project contracts, future direction, deferred product capability, and durable decisions.

The current user request reclassifies this as small. Treat `.internal-dev/plans/specifications-store-workflow/replacement-handoff.md` as binding source intent, but do not preserve its older `large` classification or suggested multi-unit breakdown.

## Required Target Shape

Create exactly this flat structure unless the user approves a different class of specification file:

```text
.internal-dev/specifications/
  AGENTS.md
  index.md
  workflow.md
  schema.md
  architecture.md
  service-graph.md
  services.md
  api.md
  web.md
  simplypages.md
  decisions.md
  deferred-features.md
  horizon-ideas.md
```

No nested category directories are allowed under `.internal-dev/specifications/`.

`index.md` must list every specification file, its status/owner/domain boundary, and a clear rule: update existing living files by default. A new specification file is allowed only for a genuinely new specification class, and the index must define its ownership boundary before or in the same change.

## Source Files To Inspect

Read these before implementation:

- `AGENTS.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/plans/specifications-store-workflow/replacement-handoff.md`
- `.internal-dev/focus/AGENTS.md`
- `.internal-dev/focus/current-focus.md`
- `.internal-dev/focus/unfinished-work.md`
- `.internal-dev/focus/architecture-focus.md`
- `.internal-dev/focus/decisions.md`
- `.internal-dev/focus/ideas-inbox.md`
- `.internal-dev/focus/horizon-ideas.md`
- active files in `.internal-dev/notes/*.md`
- filenames in `.internal-dev/knowledge/`
- recent changelogs in `.internal-dev/changelogs/`

Do not use the deleted prior generated plan as source material if it reappears.

## Migration Triage

Copy or intentionally drop data before deleting `.internal-dev/focus/` and `.internal-dev/notes/`. Record the migration/drop audit in `.internal-dev/specifications/workflow.md`; do not add a separate migration file.

Focus migration:

- `current-focus.md`: migrate the Avatar dashboard durable direction into `web.md`, `simplypages.md`, and/or `horizon-ideas.md` depending on whether each row is contract, style direction, or future direction. Do not recreate a current-focus register.
- `unfinished-work.md`: migrate accepted future product capability to `deferred-features.md`; migrate unresolved design choices that are not accepted capability to `horizon-ideas.md`; convert true defects to `.internal-dev/bugs/` and mirror them to GitHub issues.
- `architecture-focus.md`: migrate active architecture rows to `architecture.md`, `service-graph.md`, and `decisions.md` as contract/decision entries.
- `decisions.md`: copy active and superseded decision rows into `specifications/decisions.md`, preserving id, decision text, source, decided date, knowledge reference, status, review timing, caveats, and supersession state.
- `ideas-inbox.md`: migrate product-directional rows to `horizon-ideas.md`; migrate process decisions only if they are accepted workflow decisions; otherwise record intentional drop.
- `horizon-ideas.md`: currently empty, but preserve the register role in `specifications/horizon-ideas.md`.

Known note migration targets:

- `future_features.md`: migrate product capability sections into `deferred-features.md`.
- `2026-05-22-avatar-dashboard-ui-style-guidelines.md`: migrate durable Avatar visual/interaction contract to `web.md` and `simplypages.md`; update top-level Avatar guidance to point at the new specs.
- `simplypages-upstream-module-candidates.md`: migrate accepted or plausible future library module candidates to `horizon-ideas.md` or `deferred-features.md`; keep Magenta-specific UI contract in `simplypages.md`.
- `2026-05-04-upgrade-to-spring-ai-2.0.md`, `2026-04-29-searxng-deployment.md`, `2026-05-11-container-runtime-selection.md`: migrate reusable tool/framework/deployment learning to domain-named knowledge files, and only mirror future product capability to specifications.
- Deferred orchestration/workflow/job/workspace/API/UI notes: migrate mature intended capability to `deferred-features.md`; migrate architecture constraints to `architecture.md`, `service-graph.md`, `services.md`, or `api.md`; convert current defects to `.internal-dev/bugs/` with GitHub issue mirroring.
- Obsolete blocked-validation notes, completed scratch notes, or raw session cleanup notes: intentionally drop after recording the decision in `workflow.md`.

## Specification Content Minimums

`schema.md` must include concrete examples or subschema sections for:

- specification file metadata and status
- architecture entries
- service entries
- service graph entries
- API entries
- web/page/fragment entries
- SimplyPages component/module entries
- decision rows
- deferred-feature rows
- horizon-idea rows
- drift records
- no-impact notes

Use compact table schemas. Prefer stable IDs:

- `SPEC-YYYYMMDD-NN`
- `ARCH-YYYYMMDD-NN`
- `SVC-YYYYMMDD-NN`
- `API-YYYYMMDD-NN`
- `WEB-YYYYMMDD-NN`
- `SP-YYYYMMDD-NN`
- `DECISION-YYYYMMDD-NN`
- `DEFERRED-YYYYMMDD-NN`
- `HORIZON-YYYYMMDD-NN`
- `DRIFT-YYYYMMDD-NN`

Every domain spec should include:

- intended contract
- observed anchors
- ownership boundary
- drift/gaps
- validation expectations
- related decisions
- related knowledge

If an implementation has no impact on a spec, the changelog must say `Specification Impact: none` with one sentence explaining why.

## AGENTS.md Workflow Language Requirements

Update both `AGENTS.md` and `.internal-dev/AGENTS.md` so agents encounter these gates early and again during closeout:

- Beginning gate: read relevant specifications before changing services, APIs, web pages/fragments, SimplyPages modules, architecture, persistence, workflow behavior, or product contracts.
- Beginning knowledge gate: list or search `.internal-dev/knowledge/` filenames before nontrivial work; read only relevant domain files.
- Lost/confused gate: search knowledge filenames again, run a deeper grep across `.internal-dev/knowledge/`, then use web or official docs for missing external/framework/tool behavior.
- Mid-workflow routing gate: use specifications for intended contracts, decisions for durable tradeoffs, knowledge for reusable learning, changelogs for prior edit context, bugs for defects, and plans/reviews for scoped handoffs.
- Correction gate: when a false assumption, repeated mistake, major correction, important user correction, or repeated reverification reveals reusable context, update a domain-named knowledge file and link the affected spec/changelog when useful.
- Future-direction gate: user hints like "future", "eventually", "later", or "this will become" go to `specifications/horizon-ideas.md` unless accepted as deferred product capability.
- Deferred-capability gate: accepted future product capability goes to `specifications/deferred-features.md`.
- Durable-decision gate: architecture/design/product/workflow decisions go to `specifications/decisions.md` with justification, alternatives/tradeoffs when known, caveats, affected specs, source, and review timing.
- Bug gate: `.internal-dev/bugs/` remains the defect store and every created/compiled local bug report must be mirrored directly to a GitHub issue.
- Closeout gate: update affected specifications, knowledge, bugs, changelogs, plans, and reviews; remove `focus/` and `notes/` as active workflow targets.

Remove active guidance that sends agents to `.internal-dev/focus/` or `.internal-dev/notes/`. Historical references in old changelogs/reviews can remain if they are not active workflow guidance.

## Acceptance Criteria

- The flat specifications directory exists with exactly the required living files and no nested category directories.
- `index.md` routes agents to existing specs and warns against duplicate living files.
- `schema.md` contains all required subschema examples.
- Relevant focus data is copied or intentionally dropped before `.internal-dev/focus/` is deleted.
- Relevant active notes data is migrated or intentionally dropped before `.internal-dev/notes/` is deleted.
- `AGENTS.md` and `.internal-dev/AGENTS.md` contain beginning and mid-workflow gates for specifications, knowledge, decisions, changelogs, bugs, plans, and reviews.
- Knowledge workflow wording includes filename/domain pass, deeper grep when lost/confused, web/official docs fallback for external behavior, and update triggers for corrections and repeated reverification.
- Bug guidance still requires GitHub issue mirroring.
- Closeout includes a changelog, any reusable knowledge learned while implementing the workflow, and a git commit after validation.

## Negative Criteria

- Do not create `.internal-dev/specifications/architecture/`, `api/`, `services/`, `web/`, or similar nested directories.
- Do not keep `.internal-dev/focus/` or `.internal-dev/notes/` as active workflow stores.
- Do not preserve notes as a catch-all fallback lane.
- Do not duplicate architecture/API/service/web intent across competing spec files.
- Do not delete focus or notes before migration/drop review is recorded.
- Do not broaden into product-code, route, service, schema, or UI behavior changes.
- Do not run Maven, Spring startup, or Playwright as required validation unless product code or UI files are touched.
