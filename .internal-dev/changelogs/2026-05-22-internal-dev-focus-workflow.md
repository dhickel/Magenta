# Internal Dev Focus Workflow

## Date

2026-05-22

## Change Summary

Added a strict-schema `.internal-dev/focus/` living-document workflow and updated the internal-dev initializer so new projects receive the focus structure and top-level workflow block automatically.

## Files

- `AGENTS.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/focus/`
- `.internal-dev/plans/internal-dev-focus-workflow/README.md`
- `~/.scripts/init-internal-dev/init-internal-dev.sh`
- `~/.scripts/init-internal-dev/AGENTS.md`
- `~/.scripts/init-internal-dev/output.txt`
- `~/.scripts/init-internal-dev/templates/focus/`

## Behavioral Impact

Agents now have a required targeted beginning pass for relevant focus files and a closeout pass for unfinished work, stale current focus, architecture focus, durable decisions, raw ideas, horizon ideas, and archive maintenance.

The initializer now creates focus files and updates or creates top-level `AGENTS.md` with an idempotent marked internal-dev workflow block.

## Risks

- The first real long-term current focus still needs user confirmation.
- The initializer script is outside the Magenta repository, so its changes are not included in this repository's git history unless separately committed in its own location.

## Follow-up Items

- User should confirm the first durable `current-focus.md` entry.
