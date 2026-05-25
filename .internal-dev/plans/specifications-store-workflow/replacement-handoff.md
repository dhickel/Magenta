---
status: review-draft
created: 2026-05-25
owner: planning
classification: large
---

# Specifications Store Workflow Replacement Handoff

## Objective

Replace the current `.internal-dev` governance model that depends on `focus/` and `notes/` with a stronger `.internal-dev/specifications/` workflow.

The new specifications store should become the canonical internal intended-truth system for Magenta project contracts, future direction, deferred product capability, and durable project decisions. The handoff is planning-only and is intended for review before a new advanced planning pass expands it into a full implementation suite.

## Source Intent

The prior generated plan is discarded. Do not reuse it as source material.

Use this handoff as the binding intent:

- Create `.internal-dev/specifications/` as the canonical intended-truth store.
- Move horizon ideas and durable decisions into the specifications workflow.
- Add a deferred-feature register in specifications for intended future product capability.
- Copy relevant data out of `.internal-dev/focus/` into the new specification files before deleting focus.
- Delete `.internal-dev/focus/` after relevant current-focus, horizon, decision, architecture, and deferred-work data is migrated or intentionally dropped.
- Delete `.internal-dev/notes/` after any clearly valuable content is migrated into the right durable structure.
- Remove all future workflow reliance on `.internal-dev/focus/`.
- Remove all future workflow reliance on `.internal-dev/notes/` and all guidance that treats notes as a fallback/catch-all lane.
- Strengthen `.internal-dev/knowledge/` discovery and update gates.
- Keep changelogs as the path for understanding prior edits and prior context.
- Keep bugs in `.internal-dev/bugs/` and mirror every created/compiled bug report to GitHub.
- Reinforce the `.internal-dev` closeout workflow so agents cannot escape contract updates by dumping material into an easier bucket.
- Percolate the beginning-workflow and mid-workflow guidance up to top-level `AGENTS.md`, not only `.internal-dev/AGENTS.md`, so agents encounter knowledge/specification expectations before they start work.

## Target Directory Shape

The implementation should use a flat specifications directory with specific living files and a single index. Do not create nested category directories for architecture, services, API, web, or SimplyPages. A flat structure reduces the chance that future agents redefine architecture across several overlapping files.

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

`decisions.md`, `deferred-features.md`, and `horizon-ideas.md` are first-class specification registers, not secondary notes.

`index.md` is the table of contents and routing map. It should track every specification file, owner/status/review metadata, what domain it owns, and when a new file is allowed.

New specification files should be rare. Agents should update existing living files by default. A new specification file is allowed only when a genuinely new specification class is being added and the index is updated with its ownership boundary. Agents must not split architecture, API, services, or web concerns into additional files just because a task touches one sub-area.

## Specification Roles

### Contracts

Service, API, web, architecture, and SimplyPages specs are living files that describe intended contracts:

- what Magenta promises or intends,
- current observed code anchors,
- ownership and boundaries,
- drift or gaps,
- validation expectations,
- related knowledge links.

Code remains observed/logical truth. Specs are intended truth. If they disagree, record drift and route it to implementation, bug tracking, deferred capability, or a design decision.

Existing specification files must be updated in place. Future agents should be explicitly warned not to create parallel architecture or service specs that restate or compete with the existing files. If a spec feels too large, improve its index, headings, and anchors before proposing a new file.

### Deferred Features

`.internal-dev/specifications/deferred-features.md` is for intended future product capability that is mature enough to shape later implementation but is not in current scope.

Use it for:

- accepted future route, service, UI, workflow, or runtime capability,
- planned contract extensions,
- design-intended behavior deferred out of the current implementation.

Do not use it for:

- raw ideas,
- vague product direction,
- operational handoffs,
- blocked validation state,
- bugs,
- session cleanup.

### Horizon Ideas

`.internal-dev/specifications/horizon-ideas.md` replaces the future-idea role previously handled by focus-style idea files.

Agents must add to this register when the user says something in passing that alludes to future capability or future product direction, including phrasing like:

- "in the future we will have X",
- "eventually this will do Y",
- "for context this will become Z",
- "later we need support for A",
- "this is not implemented yet, but it should become B".

This register is for future direction that is not yet committed enough to be a deferred feature. The entry should preserve source context, the implied capability, why it may matter, and when to review it.

### Decisions

`.internal-dev/specifications/decisions.md` replaces the durable decision role previously handled by focus decisions.

Record large design, architecture, workflow, or product decisions that influence future work. Each decision must include:

- the decision,
- why it was made,
- justification,
- alternatives or tradeoffs considered when known,
- caveats and risks,
- affected specs or domains,
- source context,
- review timing.

Do not leave heavy architecture/design decisions only in chat, plans, changelogs, or implementation summaries.

Relevant existing decision rows from `.internal-dev/focus/decisions.md` must be copied into this file before focus is deleted. The migration should preserve the original decision, source, date, knowledge references where useful, and current status.

## Knowledge Workflow

`.internal-dev/knowledge/` remains separate from specifications.

Knowledge is for reusable learning:

- framework techniques,
- implementation gotchas,
- validation patterns,
- research corrections,
- recurring failure modes,
- user-provided important facts,
- impactful user clarifications that correct an assumption.

Knowledge is not the canonical contract store for services, APIs, web pages, SimplyPages modules, future features, or durable decisions.

The knowledge workflow must be reflected in both top-level `AGENTS.md` and `.internal-dev/AGENTS.md`. The top-level guide is the earlier interception point; relying only on `.internal-dev/AGENTS.md` is not enough.

### Required Knowledge Gates

At the beginning of non-trivial work:

1. List or search knowledge filenames.
2. Read only knowledge files whose filenames/domains appear relevant to the task.
3. If no filename looks relevant, proceed without broad reads.

When an agent is lost, confused, blocked by intuition failure, or unsure whether prior project learning exists:

1. Search knowledge filenames again.
2. Run a deeper grep across `.internal-dev/knowledge/`.
3. If no useful knowledge exists and the issue is external/framework/tool behavior, search the web or official docs as appropriate.
4. Add or update a knowledge entry after the learning is resolved.

When the user supplies an important fact, correction, or clarification:

- If it affects future reasoning beyond the current task, add a domain-named knowledge entry or update an existing relevant one.
- Name knowledge files after the domain they cover, not after a random incident title.

When the agent makes a false assumption, repeats a mistake, or performs a large correction because it lacked project context:

- Identify the missing reusable fact, pattern, gotcha, or decision context.
- Add it to a domain-named knowledge file or update the existing relevant file.
- Link to a specification or changelog when that makes the learning easier to verify later.
- Do not treat the correction as only a chat lesson if another agent is likely to need the same context.

When the agent repeatedly has to reverify the same project fact, or discovers a piece of context that should have been innate from the plan but was missing:

- Record that reusable context in knowledge.
- If it is an intended project contract, update the relevant specification instead or in addition.
- If it is a durable architecture/product decision, record it in `.internal-dev/specifications/decisions.md`.

When an agent is confused about prior edits or needs prior implementation context:

- Use changelogs first.
- Use plans/reviews when the changelog points there.
- Do not use notes as a fallback.

## Notes Removal

`.internal-dev/notes/` should be deleted and references to it removed from AGENTS guidance.

Reasoning:

- Notes act as an escape hatch when an agent cannot decide where information belongs.
- The new workflow should force classification into specifications, horizon ideas, deferred features, durable decisions, knowledge, bugs, changelogs, plans, or reviews.
- If information does not fit any of those categories, it is probably not durable enough to keep.

Implementation should remove `.internal-dev/notes/` references from workflow docs. Existing notes content should only be migrated if it clearly belongs in a new or existing durable structure. Otherwise it can be dropped rather than preserved by default. After selective migration/drop review, delete the notes directory.

## Focus Removal

The new workflow should migrate relevant `.internal-dev/focus/` data, then delete `.internal-dev/focus/` and remove references to it as an active governance system.

Replacement mapping:

| Old focus role | New home |
| --- | --- |
| current durable direction | relevant domain spec when it is intended contract, or `.internal-dev/specifications/horizon-ideas.md` when it is future direction |
| unfinished operational work | `.internal-dev/specifications/deferred-features.md` only if it represents future product capability; otherwise convert to bug, active plan handoff, changelog note, or drop |
| raw/future ideas | `.internal-dev/specifications/horizon-ideas.md` when product-directional; otherwise drop |
| architecture focus | `.internal-dev/specifications/architecture.md`, `service-graph.md`, and/or `decisions.md` |
| decisions | `.internal-dev/specifications/decisions.md` |

The advanced planning agent must identify existing focus content worth migrating. It should not preserve everything by default, but relevant data must be copied before deletion. The final implementation should delete focus after migration/drop review.

## Internal Dev Workflow Reinforcement

Update both top-level `AGENTS.md` and `.internal-dev/AGENTS.md` so future agents must:

- read relevant specifications before changing services, APIs, web pages/fragments, reusable modules, architecture, persistence, workflows, or product contracts,
- read relevant knowledge filenames before non-trivial work,
- deepen the knowledge search when lost or confused,
- use the internal-dev structure mid-workflow as a utility: specifications for intended contracts, decisions for durable tradeoffs, knowledge for reusable context, changelogs for prior edits, bugs for defects, and plans/reviews for scoped handoffs,
- update affected specifications during closeout or explicitly state no specification impact in the changelog,
- record user-mentioned future direction in horizon ideas,
- record accepted deferred product capability in deferred features,
- record major design and architecture decisions with justification and caveats,
- record reusable lessons from false assumptions, repeated mistakes, large corrections, and missing context in domain-named knowledge files,
- use changelogs to understand prior edits,
- log bugs in `.internal-dev/bugs/`,
- mirror bug reports to GitHub issues when created or compiled,
- avoid `.internal-dev/notes/` and `.internal-dev/focus/` as workflow targets,
- commit implementation plus `.internal-dev` updates after closeout.

## Advanced Planning Requirements

After this handoff is approved, launch `advanced_planning_agent` with `gpt-5.5 high`.

Classification: `large`.

The advanced planner must research current code and internal docs, then produce a full plan suite under:

```text
.internal-dev/plans/specifications-store-workflow/
```

Required planning concerns:

- design the specifications schema and registers,
- include schema guidance or concrete subschema examples for the entry shapes agents should use when updating the flat living specification files and registers,
- map existing focus/notes content to migrate or drop, then delete those directories after review,
- inventory current services, APIs, web surfaces, and SimplyPages modules,
- design top-level `AGENTS.md` and `.internal-dev/AGENTS.md` updates,
- design knowledge-discovery and knowledge-update gates,
- design changelog, bug, GitHub issue, and closeout workflow reinforcement,
- define validation proving focus/notes references are removed from active workflow guidance,
- define validation proving all relevant categories have a spec home.

## Suggested Work Units

1. Specification schema and register design.
2. Focus/notes retirement and migration/drop audit.
3. Knowledge workflow gate design.
4. Architecture and service specification inventory.
5. API and endpoint specification inventory.
6. Web, fragment, and SimplyPages inventory.
7. Top-level `AGENTS.md` and `.internal-dev/AGENTS.md` integration and validation.
8. Final review, changelog, and commit workflow.

## Validation Expectations

The final implementation should include static validation that proves:

- `.internal-dev/specifications/` exists with AGENTS, index, workflow, schema, decisions, horizon ideas, deferred features, and flat living specification files.
- `schema.md` includes concrete examples or subschema sections for service/API/web/architecture/specification entries, decision rows, deferred-feature rows, horizon-idea rows, drift records, and no-impact notes.
- `index.md` tracks each specification file and makes clear that existing files should be updated rather than duplicated.
- Relevant `.internal-dev/focus/` data has been copied to specifications before focus deletion.
- Relevant `.internal-dev/notes/` data has been migrated or intentionally dropped before notes deletion.
- `.internal-dev/focus/` is deleted after migration/drop review.
- `.internal-dev/notes/` is deleted after migration/drop review.
- Top-level `AGENTS.md` includes beginning-workflow knowledge/specification gates.
- Top-level `AGENTS.md` describes mid-workflow use of specifications, decisions, knowledge, changelogs, bugs, plans, and reviews when the agent is unsure.
- Top-level `AGENTS.md` tells agents to learn from false assumptions, repeated mistakes, and major missing-context corrections by updating knowledge or specifications.
- `.internal-dev/AGENTS.md` no longer routes active work to focus or notes.
- `.internal-dev/AGENTS.md` contains knowledge filename/search/deep-grep gates.
- `.internal-dev/AGENTS.md` routes future product hints to horizon ideas.
- `.internal-dev/AGENTS.md` routes accepted future capability to deferred features.
- `.internal-dev/AGENTS.md` routes durable architecture/design choices to decisions with justification and caveats.
- `.internal-dev/AGENTS.md` tells agents to use changelogs for prior-edit context.
- `.internal-dev/AGENTS.md` preserves GitHub bug mirroring.
- Existing `.internal-dev/notes/` references are removed from active guidance, except historical archive references if unavoidable and explicitly marked inactive.
- Existing `.internal-dev/focus/` references are removed from active guidance, except historical archive references if unavoidable and explicitly marked inactive.
- Every controller/service/web/SimplyPages category has a spec entry or an explicit tracked gap.

Documentation-only work does not require Maven, Spring startup, or Playwright validation unless product code or UI files are touched.

## Open Review Questions

- Should horizon ideas allow rough raw entries, or should only product-directional user statements be kept?
- Should durable decisions be one register file or split by domain once the register grows?
- Should the closeout changelog require a dedicated "Specification Impact" field in every future changelog template?
