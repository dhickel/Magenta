---
schema_version: 1
document_type: focus-agent-guide
last_reviewed: 2026-05-22
owner: unassigned
status: active
---

# Focus Document Store Guide

`.internal-dev/focus/` contains living documents for durable project direction, unfinished work, raw idea intake, curated horizon ideas, architecture focus, and decision tracking. Keep these files generic, automation-first, and easy to parse.

## Required Read Discipline

- Read only the focus files needed for the active task.
- Before editing a focus file, read its local schema in this guide and the target file's `schema_version`.
- For implementation work, check `current-focus.md` and `unfinished-work.md` when the task may change active direction or leave follow-up work.
- For architecture-affecting work, check `architecture-focus.md` before planning or editing.
- For idea triage, update `ideas-inbox.md` first, then promote only curated items to `horizon-ideas.md`.
- For decisions with reusable technical lessons, update `decisions.md` and point to a dedicated `.internal-dev/knowledge/` entry when one exists or is created.

## Required Update Discipline

- Preserve file names and top-level section headings unless intentionally changing this guide in the same task.
- Keep entries newest-first unless a file says otherwise.
- Use ISO dates in `YYYY-MM-DD` format.
- Use stable IDs with these prefixes:
  - Current focus: `FOCUS-YYYYMMDD-NN`
  - Unfinished work: `UNFINISHED-YYYYMMDD-NN`
  - Ideas inbox: `IDEA-YYYYMMDD-NN`
  - Horizon ideas: `HORIZON-YYYYMMDD-NN`
  - Architecture focus: `ARCH-YYYYMMDD-NN`
  - Decisions: `DECISION-YYYYMMDD-NN`
- Do not delete stale entries silently. Mark status, add a note, or archive.
- Prefer table rows for machine-readable state and short bullets only inside `notes` or `context` fields.
- Keep examples clearly marked as placeholders when examples mention this repository.

## Workflow Passes

### Beginning Pass

Before non-trivial work, read this guide and then use targeted focus reads:

- Read `current-focus.md` when the task may affect project direction, architecture direction, long-running goals, or a multi-phase plan.
- Read `unfinished-work.md` when the task might resume, close, replace, or add unfinished work.
- Read `architecture-focus.md` before architecture-affecting planning or edits.
- Read `decisions.md` when making or revising a durable process, architecture, or product decision.
- Read `ideas-inbox.md` or `horizon-ideas.md` only when the task is idea triage, future planning, or deferral management.

If the task can proceed without user input, proceed and record the focus impact during closeout. Ask the user only when the next focus, strategic direction, or unresolved unfinished-work ownership cannot be inferred safely.

### Closeout Pass

During the `.internal-dev` closeout workflow:

- Update `unfinished-work.md` whenever work is intentionally left incomplete, blocked, paused, handed off, resumed, or closed.
- Check `current-focus.md`; if the completed work appears to finish, obsolete, or materially change the long-term focus, report that staleness to the user instead of silently rewriting strategy.
- Update `architecture-focus.md` only when architecture direction, constraints, open questions, or source references materially changed.
- Update `decisions.md` for durable decisions and link or create `.internal-dev/knowledge/` entries when the decision contains reusable knowledge.
- Put raw ideas in `ideas-inbox.md`; promote only reviewed future targets to `horizon-ideas.md`.
- Archive completed, superseded, or rejected entries after recording their final status.

## Staleness Checks

- Every focus file must include `last_reviewed` in front matter.
- If `last_reviewed` is more than 30 days old and the file informs the active task, refresh it before relying on it.
- If an entry has a `review_after` date in the past, either update its status, revise its next action, or move it to archive.
- If an owner is unknown, use `unassigned`; do not invent owners.
- If source material is missing, use `source: unknown` and add the smallest useful note.

## Archive Rules

- Archive completed, superseded, rejected, or stale entries when they no longer need to stay in the living file.
- Archive into `.internal-dev/focus/archive/` using `YYYY-MM-DD-<source-file-stem>.md`.
- Preserve original IDs, dates, owners, sources, and final status in archives.
- Do not archive active blockers or unresolved work.
- Keep `.internal-dev/focus/archive/.gitkeep` so the archive directory exists in fresh checkouts.

## File Schemas

All focus markdown files use this front matter shape:

```yaml
---
schema_version: 1
document_type: <document-type>
last_reviewed: YYYY-MM-DD
owner: <owner-or-unassigned>
status: active
---
```

### `README.md`

Purpose: orient humans and automation to the focus document set.

Required sections:

- `# Focus Documents`
- `## Files`
- `## Common Workflow`
- `## Automation Contract`

### `current-focus.md`

Purpose: track the long-term focus of current work, not a sprint goal or short task list.

Required sections:

- `# Current Focus`
- `## Active Focus`
- `## Supporting Context`
- `## Review Log`

`Active Focus` table schema:

| id | focus | status | owner | source | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- |

Allowed `status`: `active`, `watching`, `blocked`, `superseded`, `archived`.

### `unfinished-work.md`

Purpose: track work left unfinished by agents or humans.

Required sections:

- `# Unfinished Work`
- `## Open Items`
- `## Recently Closed`
- `## Review Log`

`Open Items` table schema:

| id | title | status | next_action | owner | source | created | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

Allowed `status`: `open`, `blocked`, `needs-triage`, `in-progress`, `deferred`, `closed`, `archived`.

`Recently Closed` table schema:

| id | title | status | owner | source | closed_on | notes |
| --- | --- | --- | --- | --- | --- | --- |

Allowed `status`: `closed`, `superseded`, `archived`.

### `ideas-inbox.md`

Purpose: raw or semi-raw intake for ideas, observations, and possible future work.

Required sections:

- `# Ideas Inbox`
- `## Intake`
- `## Promoted`
- `## Review Log`

`Intake` table schema:

| id | idea | status | owner | source | captured | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- | --- |

Allowed `status`: `raw`, `needs-triage`, `promoted`, `rejected`, `archived`.

`Promoted` table schema:

| id | idea | promoted_to | promoted_on | notes |
| --- | --- | --- | --- | --- |

### `horizon-ideas.md`

Purpose: curated horizon ideas that are not active current focus.

Required sections:

- `# Horizon Ideas`
- `## Curated Targets`
- `## Parking Lot`
- `## Review Log`

`Curated Targets` table schema:

| id | target | status | owner | source | expected_value | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- | --- |

Allowed `status`: `candidate`, `watching`, `deferred`, `promoted`, `rejected`, `archived`.

`Parking Lot` table schema:

| id | target | status | owner | source | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- |

Allowed `status`: `candidate`, `watching`, `deferred`, `rejected`, `archived`.

### `architecture-focus.md`

Purpose: current architectural direction and constraints. This is part of current focus and does not replace or migrate any existing architecture note.

Required sections:

- `# Architecture Focus`
- `## Active Architecture Focus`
- `## Constraints`
- `## Open Questions`
- `## Review Log`

`Active Architecture Focus` table schema:

| id | area | direction | status | owner | source | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- | --- |

Allowed `status`: `active`, `watching`, `blocked`, `superseded`, `archived`.

`Constraints` table schema:

| id | constraint | status | owner | source | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- |

Allowed `status`: `active`, `watching`, `superseded`, `archived`.

`Open Questions` table schema:

| id | question | status | owner | source | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- |

Allowed `status`: `open`, `watching`, `answered`, `superseded`, `archived`.

### `decisions.md`

Purpose: capture decisions that shape focus and may inform reusable knowledge.

Required sections:

- `# Decisions`
- `## Active Decisions`
- `## Superseded Decisions`
- `## Review Log`

`Active Decisions` table schema:

| id | decision | status | owner | source | decided_on | knowledge_ref | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

Allowed `status`: `active`, `watching`, `superseded`, `reversed`, `archived`.
Use `knowledge_ref: none` when no `.internal-dev/knowledge/` document applies.

`Superseded Decisions` table schema:

| id | decision | status | owner | source | decided_on | superseded_on | superseded_by | knowledge_ref | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

Allowed `status`: `superseded`, `reversed`, `archived`.
