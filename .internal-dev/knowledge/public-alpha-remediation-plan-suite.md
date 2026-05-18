# Public Alpha Remediation Plan Suite

## Topic

How the public-alpha remediation sprint plans are organized from the `public-alpha-quality-review` findings.

## Source References

- `.internal-dev/reviews/public-alpha-quality-review/`
- `.internal-dev/bugs/public-alpha-quality-review/`
- `.internal-dev/plans/public-alpha-remediation/`

## Key Takeaways

- The remediation suite is intentionally broader than the bug ledger: it includes bugs, review-only smells, stale-code concerns, refactor targets, and validation gaps.
- Every addressable item has one primary domain in `finding-inventory.md` and a corresponding row in `progress.md`.
- Validation agents must read the original review and bug files before gating a domain.
- Implementation agents should start from the domain plan and use `review-context-index.md` only when they need more original evidence.
- Domain implementation is serial by default because the shared checkout does not use worktrees.

## Engine Relevance

This suite is the control surface for future remediation sprints before public alpha. It prevents review findings from being lost and gives agents a shared place to coordinate branch, validation, and closeout state.

## Open Questions

- None at creation time. Skips or deferrals require explicit user approval and updates to `no-action-registry.md`, `finding-inventory.md`, and `progress.md`.
