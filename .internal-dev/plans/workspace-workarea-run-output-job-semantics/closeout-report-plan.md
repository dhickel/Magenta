# Closeout Report Plan

## Required Closeout Work

- Update affected `.internal-dev/specifications/*`.
- Update relevant `.internal-dev/knowledge/*` with reusable lessons and corrected assumptions.
- Create `.internal-dev/changelogs/<date>-workspace-workarea-run-output-job-semantics.md`.
- Create `.internal-dev/bugs/*` for out-of-scope defects found, mirror each to GitHub if the repository remote is available, and archive any bug already represented by a closed GitHub issue.
- Move finalized plan artifacts to `.internal-dev/plans/.archive/` only after implementation and validation are complete.
- Update `docs/` end-user, technical, and API docs for behavior, architecture, route, payload, schema, and service changes.
- Commit phase work at each completed/validated phase on the dedicated branch.

## Final User Report Shape

- Branch and commit summary.
- Phases completed and validation status.
- Changed behavior summary.
- Any blockers, deferred items, or residual risks.
- Confirmation that unrelated untracked files were left alone unless explicitly used as evidence.

## Email

No email was requested in this handoff. If the user later asks for an email report, the main thread should use the global `email-followup-wait` skill; this plan does not own send/wait mechanics.

