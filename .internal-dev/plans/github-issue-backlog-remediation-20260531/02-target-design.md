# Target Design

## Execution Shape

Use sequential issue remediation with rollback-friendly commits. The recommended order is:

1. #9 SQL identifier hardening.
2. #10 workflow schema migration error handling.
3. #11 exception handler constructor cleanup.
4. #12 conversation turn executor rejection.
5. #13 cancel-requested lease transition hardening.
6. #19 pending chat FIFO ordering.
7. #14 + #15 chat/SSE active-turn lifecycle and interrupt contract.
8. #16 run display name enforcement.
9. #17 PASS_THROUGH workflow route semantics.
10. #18 DELEGATION node execution evidence.
11. #33 SlotKey/package-guide enforcement and stable-template refactor pass.

#8 is intentionally skipped and left open. Dashboard editing has moved, the issue wording is stale, and the user does not want dashboard work outside the SlotKey issue in this remediation pass.

## Combined-Fix Decisions

- Combine #14 and #15 only. They share `ChatController`, `ChatService`, `ActiveTurnRegistry`, and browser SSE validation. Fixing one without the other risks contradictory active-turn semantics.
- Keep #9 and #10 separate despite both being persistence/security because rollback and validation are cleaner by issue.
- Keep #17 and #18 separate despite both being workflow runner changes because they encode different workflow semantics and require different fixtures.
- Skip #8 entirely for this pass. #33 remains in scope because SlotKey/package-guide enforcement is explicitly requested and may audit dashboard/static surfaces only for stable-template reuse, not for dashboard editor density changes.

## Design Principles

- Prefer narrow invariants over broad rewrites.
- Add a failing reproduction or invariant test before changing behavior for #12 through #19.
- For security/persistence, reject unsafe identifiers or fail loudly on unexpected migration errors; do not merely log and continue for critical failures unless the failure is the known idempotent "already exists" case.
- For cancellation and executor rejection, terminal or blocked states must not be silently overwritten.
- For workflow semantics, runtime and validator must match the living route model.
- For UI/SimplyPages, use stable templates and per-request `RenderContext` for repeated structures with stable DOM and changing values. Use Home dashboard/dashboard widget wording in user-facing docs/specs; treat `Avatar*` class/database names as legacy implementation names unless a small safe rename is explicitly scoped.

## Evidence Design

The orchestrator maintains:

- Per-phase validation reports in `validation/phase-XX-validation-report.md`.
- Browser proof directories only for UI/browser phases.
- Canonical evidence index: `artifacts/github-issue-backlog-remediation-20260531/validation-summary.json`.

Evidence status must stay conservative. Do not use `fully_validated` until all relevant phase validators, browser agents, final stale-reference sweep, and final quality review pass.
