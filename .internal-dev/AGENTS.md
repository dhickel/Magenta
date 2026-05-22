# Internal Development Document Store Guide (`.internal-dev`)

This guide defines how agents use persistent engineering records in `.internal-dev/`.

## Purpose

`.internal-dev/` is the development document store for planning, bug capture, reviews, changelogs, notes, reusable knowledge, and living focus records.
- Some repositories track selected `.internal-dev` files and some keep them local-only. Use `git status` to determine what must be included in commits for the current repository.

## Source-of-Truth Policy

- Code is the logical source of truth.
- Documentation is intended truth.
- If they diverge, record the mismatch in task output and create/update a tracking artifact in `.internal-dev/`.

## Access Discipline

- Do not read `.internal-dev` directories/files randomly.
- Use controlled access: read only what the active task needs.
- Prefer targeted lookups over broad scans.

## Directory Contract

- `bugs/`: bug reports discovered during implementation or review.
- `plans/`: active implementation plans in nested plan directories.
- `reviews/`: completed review write-ups.
- `notes/`: future considerations and deferred ideas.
- `knowledge/`: reusable domain research and learner-facing summaries.
- `changelogs/`: dated change records that summarize completed work.
- `debug_reports/`: local or committed debug captures when a task requires durable diagnostic evidence.
- `focus/`: living current-focus, unfinished-work, idea, horizon, architecture-focus, and decision records.

## Focus Workflow

`focus/` is the running operating picture for agents. It does not replace plans, bugs, changelogs, reviews, notes, or knowledge.

### Beginning pass

Before non-trivial work, use targeted focus reads:

- Read `focus/AGENTS.md`.
- Read `focus/current-focus.md` when the task may affect long-term direction, architecture direction, or multi-phase work.
- Read `focus/unfinished-work.md` when the task may resume, close, replace, or add unfinished work.
- Read `focus/architecture-focus.md` before architecture-affecting planning or edits.
- Read `focus/decisions.md` when making or revising durable process, architecture, or product decisions.

Do not read all of `focus/` by default. If focus state is stale but the next action is obvious, proceed and record the staleness during closeout. Ask the user only when the next focus, ownership, or strategic direction cannot be inferred safely.

### Closeout pass

During the required `.internal-dev` closeout workflow:

- Update `focus/unfinished-work.md` for work intentionally left incomplete, blocked, paused, handed off, resumed, or closed.
- Check `focus/current-focus.md`; if completed work appears to finish, obsolete, or materially change the long-term focus, report that staleness to the user instead of silently rewriting strategy.
- Update `focus/architecture-focus.md` when architecture direction, constraints, source references, or open questions materially changed.
- Update `focus/decisions.md` for durable decisions and back reusable lessons with `.internal-dev/knowledge/` entries when appropriate.
- Put raw ideas in `focus/ideas-inbox.md`; promote only curated future targets to `focus/horizon-ideas.md`.
- Archive completed, superseded, rejected, or stale focus entries according to `focus/AGENTS.md`.

## Workflow Rules

- Out-of-scope bugs discovered in passing must be logged immediately.
- If the project has a GitHub repository, every bug report created under `.internal-dev/bugs/` must be mirrored directly to that repository as a GitHub Issue when it is created or compiled.
- When adding or updating a local bug report in a project with a GitHub repository, check for related closed GitHub Issues before finishing; if the corresponding issue is already closed, move the local bug report to `.internal-dev/bugs/.archive/` instead of leaving it active.
- If a future consideration is identified and not implemented now, ask whether it should be recorded in `notes/`.
- Any completed review is written to `reviews/`.
- Plans in progress should live in their own plan directories and include phase implementation files.
- When a bug or plan is finalized, move it to a sibling `.archive/` directory in the same parent path.
- Existing `plans/.completed/` content is legacy/read-only; use `.archive/` going forward.
- Finalized code/documentation changes should have a changelog entry in `changelogs/`.

## Minimum Templates

### Bug (`bugs/<bug-id>/report.md`)

Required headings:

- `Summary`
- `Scope`
- `Reproduction`
- `Expected`
- `Actual`
- `Evidence`
- `Impact`
- `Status`
- `Next Action`

### Plan phase (`plans/<plan-id>/phase-XX-<name>.md`)

Required headings:

- `Context`
- `Goal`
- `In Scope`
- `Out of Scope`
- `Implementation Steps`
- `Validation`
- `Exit Criteria`

### Review (`reviews/<date>-<topic>-review.md`)

Required headings:

- `Scope`
- `Findings`
- `Risk Assessment`
- `Recommendations`
- `Follow-ups`

### Changelog (`changelogs/<date>-<topic>.md`)

Required headings:

- `Date`
- `Change Summary`
- `Files`
- `Behavioral Impact`
- `Risks`
- `Follow-up Items`

### Knowledge (`knowledge/<topic>.md`)

Required headings:

- `Topic`
- `Source References`
- `Key Takeaways`
- `Engine Relevance`
- `Open Questions`

## Related Guides

- Top-level orientation: `AGENTS.md`
- API docs index: `docs/api/00-index.md`
- Internal docs index: `docs/internal/00-index.md`
