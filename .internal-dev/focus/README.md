---
schema_version: 1
document_type: focus-readme
last_reviewed: 2026-05-22
owner: unassigned
status: active
---

# Focus Documents

This directory holds living focus documents for durable project direction. The files are intentionally strict so agents and scripts can read, update, validate, and archive entries without guessing at structure.

## Files

| file | purpose | update trigger |
| --- | --- | --- |
| `current-focus.md` | Long-term focus of current work. | Current direction changes or a task depends on active direction. |
| `unfinished-work.md` | Work left incomplete by humans or agents. | Work is paused, blocked, handed off, resumed, or closed. |
| `ideas-inbox.md` | Raw or semi-raw idea intake. | An idea is captured before it is curated. |
| `horizon-ideas.md` | Curated horizon ideas and future targets. | An inbox idea is promoted or a future target changes state. |
| `architecture-focus.md` | Current architecture direction and constraints. | Architecture direction, constraints, or open questions change. |
| `decisions.md` | Decisions that shape focus or inform knowledge. | A decision is made, superseded, reversed, or linked to knowledge. |
| `archive/` | Historical focus entries. | Entries no longer belong in living documents. |

## Common Workflow

1. Read `.internal-dev/focus/AGENTS.md`.
2. Read only the focus file required for the task.
3. Apply staleness checks before relying on an entry.
4. Update entries with stable IDs, ISO dates, owner, status, next action when applicable, and source.
5. Archive completed or superseded material instead of deleting it silently.

## Automation Contract

- Front matter is required in every markdown document in this directory except `.gitkeep`.
- Top-level section names are stable schema markers.
- Table columns are schema fields and must not be reordered without updating `AGENTS.md`.
- Unknown values should be explicit, using `unassigned`, `unknown`, or `none`.
- Placeholder rows may be removed when real entries are added.
