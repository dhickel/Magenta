# Internal Dev Focus Workflow Orchestration Notes

## Global Assumptions

- The work is implementation, not only planning.
- The new `.internal-dev/focus/` area should contain strict-schema living documents.
- Existing `.internal-dev` workflows should remain functional and only be refined where needed.
- No broad migration of existing notes is in scope.

## Active Agents

- Planning agent: pending.

## Completed Work

- Created shared orchestration notes file.

## Validation Results

- Pending.

## Remediation Notes

- Pending.

## Blockers

- None currently known.

## Closeout Work

- Pending `.internal-dev` changelog/knowledge/focus updates and commit decision after implementation.

## Final Validation Status

- Pending.

## Handoff Notes

- Agents must not revert unrelated existing work.

## Planning Agent Notes - 2026-05-22

- Planning-only pass completed; no repo workflow files were edited in this pass.
- Recommended unfinished-work file name: `.internal-dev/focus/unfinished-work.md`.
- Recommended ideas files: `.internal-dev/focus/ideas-inbox.md` and `.internal-dev/focus/horizon-ideas.md`.
- Keep architecture focus inside `.internal-dev/focus/current-focus.md`; do not migrate existing notes wholesale.
- Implementation should update repo guidance minimally, initialize strict-schema focus files, and make the init script idempotently prepend/update top-level `AGENTS.md` guidance.
- Validation should prove script syntax, temp-project initialization, marker-block idempotence, and strict focus-file schemas.

## Implementation Worker Notes - 2026-05-22

- Created `.internal-dev/focus/` with strict-schema living documents only; did not edit root guidance, `.internal-dev/AGENTS.md`, scripts, code, docs, or existing `.internal-dev/notes/`.
- Used requested filenames: `AGENTS.md`, `README.md`, `current-focus.md`, `unfinished-work.md`, `idea-scratch.md`, `future-horizon.md`, `architecture-focus.md`, `decisions.md`, and `archive/.gitkeep`.
- Kept initialization additive; no migration from `.internal-dev/notes/current-architecture-focus.md`.
