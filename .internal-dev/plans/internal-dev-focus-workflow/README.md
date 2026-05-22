# Internal Dev Focus Workflow Plan

## Context

`.internal-dev/notes/` had become the only durable place for raw ideas, deferred work, future targets, and architecture focus. The project needed a smaller living-document area that agents can read and maintain without scanning the whole document store.

## Goal

Create `.internal-dev/focus/` as a strict-schema operating picture for current focus, unfinished work, raw ideas, curated horizon ideas, architecture direction, and durable decisions. Update the `.internal-dev` workflow so agents perform a targeted beginning pass and a closeout pass. Update `init-internal-dev` so new projects receive the structure and top-level instructions automatically.

## In Scope

- Add `.internal-dev/focus/` with pregenerated living documents and local schema guide.
- Update root `AGENTS.md` and `.internal-dev/AGENTS.md` with beginning-pass and closeout-pass rules.
- Update the external `init-internal-dev` script and templates.
- Keep the workflow minimal, automation-first, and generic where the initializer is shared across projects.

## Out of Scope

- Migrating existing `.internal-dev/notes/` content.
- Choosing the first real long-term project focus without user confirmation.
- Changing application code or runtime behavior.

## Implementation Steps

1. Create strict-schema focus files:
   - `current-focus.md`
   - `unfinished-work.md`
   - `ideas-inbox.md`
   - `horizon-ideas.md`
   - `architecture-focus.md`
   - `decisions.md`
2. Add `.internal-dev/focus/AGENTS.md` with read discipline, update discipline, workflow passes, staleness checks, archive rules, and table schemas.
3. Update `.internal-dev/AGENTS.md` and root `AGENTS.md` so agents know when to read and update focus files.
4. Update `~/.scripts/init-internal-dev/init-internal-dev.sh` and templates to create focus files and prepend or replace a marked top-level internal-dev workflow block.
5. Validate idempotence and schema naming.

## Validation

- `bash -n ~/.scripts/init-internal-dev/init-internal-dev.sh`
- Initialize a temporary project with an old unmarked `.internal-dev` section and verify it is replaced with one marked block.
- Run the initializer twice and compare checksums for idempotence.
- Verify all expected focus files exist.
- Verify no stale `idea-scratch` or `future-horizon` names are generated.
- Run `git diff --check` for repo-managed documentation changes.

## Exit Criteria

- New focus files exist and use strict schemas.
- Repo guidance describes beginning and closeout focus passes.
- Initializer creates the new structure and handles top-level `AGENTS.md` automatically.
- Validation passes.
