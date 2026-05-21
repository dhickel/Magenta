# Root-Relative Workspace Migration Notes

## Global Assumptions

- User decisions on 2026-05-21:
  - The SQLite database should live inside the Magenta root and use root-relative path behavior where paths are Magenta-owned.
  - One-time migration/admin import/startup auto-repair should be documented as a future feature, not implemented in this phase.
  - Long-term absolute path columns should become root-relative where practical.
  - Old roots do not need automatic archive or symlink handling.
  - Existing chat files should be copied into the new root.
  - Existing workspace files can be dropped as part of this breaking cleanup.
  - Active runs/checkpoints can be ignored for this migration cleanup.
- Implementation and planning agents use `gpt-5.5` with high reasoning per user request.
- Testing/validation agents use `gpt-5.3-codex` with medium reasoning per repo validation instructions.
- Code-editing subplans run serially. Non-mutating review and planning may run in parallel.

## Active Agents

- None yet.

## Completed Work

- Created branch `root-relative-workspace-migration`.
- Created shared orchestration notes.

## Validation Results

- None yet.

## Remediation Notes

- None yet.

## Blockers

- None yet.

## Closeout Work

- Required before final sign-off:
  - `.internal-dev` changelog.
  - Relevant docs updates under `docs/`.
  - Reusable knowledge notes if new operational facts are learned.
  - Phase commits after validation gates.
  - Startup smoke after backend wiring.

## Final Validation Status

- Not started.

## Handoff Notes

- Preserve unrelated dirty work in the repo. Stage only files owned by this migration/refactor.
