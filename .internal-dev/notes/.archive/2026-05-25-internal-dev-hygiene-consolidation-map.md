# 2026-05-25 Internal-Dev Hygiene Consolidation Map

## Scope

Conservative archive/consolidation pass across `.internal-dev/plans`, `.internal-dev/notes`, and `.internal-dev/focus`.
No implementation code paths were changed.

## Moves Applied

### Plans archived (completed/stale top-level active plan artifacts)

- `.internal-dev/plans/chat-completion-compaction-reload-repair/`
  -> `.internal-dev/plans/.archive/chat-completion-compaction-reload-repair/`
- `.internal-dev/plans/in-chat-planning-validation-remediation/`
  -> `.internal-dev/plans/.archive/in-chat-planning-validation-remediation/`
- `.internal-dev/plans/internal-dev-focus-workflow/`
  -> `.internal-dev/plans/.archive/internal-dev-focus-workflow/`

### Notes archived and consolidated

- `.internal-dev/notes/idea_drop.md`
  -> `.internal-dev/notes/.archive/idea_drop.md`
- `.internal-dev/notes/scratch.md`
  -> `.internal-dev/notes/.archive/scratch.md`
- `.internal-dev/notes/2026-05-24-internal-dev-cleanup-deferred-candidates.md`
  -> `.internal-dev/notes/.archive/2026-05-24-internal-dev-cleanup-deferred-candidates.md`

## Consolidations Applied

Raw ideas were normalized into focus intake rows so active idea tracking lives in one canonical place:

- `.internal-dev/notes/.archive/idea_drop.md`
  -> `.internal-dev/focus/ideas-inbox.md` (new rows: `IDEA-20260525-02`, `IDEA-20260525-03`)
- `.internal-dev/notes/.archive/scratch.md`
  -> `.internal-dev/focus/ideas-inbox.md` (new row: `IDEA-20260525-01`)

## Breadcrumb Policy

Source files were moved to sibling `.archive/` locations without content rewriting so history remains inspectable and reversible.
