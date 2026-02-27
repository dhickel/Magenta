## Context
Top-level `AGENTS.md` needed updated execution guidance for the current Magenta2 rewrite workflow.

## Goal
Define enforceable pair-programming rewrite guardrails: simplified architecture focus, constrained legacy reference usage, anti-scaffolding scope controls, and explicit stop/escalation behavior.

## In Scope
- Update `AGENTS.md` with rewrite collaboration policy.
- Explicitly define `/home/hickelpickle/Code/Java/Magenta` as a reference-only source.
- Add maintainability guidance against class/interface over-fragmentation.
- Add out-of-scope dependency stop rule with recommendation to enter plan mode.
- Update scope boundaries to reject legacy scaffolding parity/backporting.

## Out of Scope
- Runtime code changes in `src/main/java`.
- Configuration schema changes.
- Tooling/security pipeline behavior changes.

## Implementation Steps
1. Insert a `Rewrite Collaboration Mode` section after project context.
2. Add rules for simplified-architecture rewrite intent and constrained legacy reference usage.
3. Add maintainability rules favoring cohesive co-located logic.
4. Add explicit stop/escalation rule for out-of-scope support requirements.
5. Reinforce anti-fragmentation in lean build constraints.
6. Extend out-of-scope list with legacy scaffolding parity prohibitions.
7. Record finalized change in `.internal-dev/changelogs/`.
8. Archive this plan directory to sibling `.archive/`.

## Validation
- Confirm `AGENTS.md` includes the exact legacy path `/home/hickelpickle/Code/Java/Magenta`.
- Confirm language explicitly states simplified architecture rewrite.
- Confirm anti-copy, anti-scaffolding, and stop/escalation clauses are present.
- Confirm anti-fragmentation guidance appears in both rewrite mode and lean build constraints.
- Confirm `.internal-dev/changelogs/` has a dated entry for this update.

## Exit Criteria
- Policy text is present and non-contradictory in `AGENTS.md`.
- Internal artifacts are created and the finalized plan is moved to `.internal-dev/plans/.archive/`.
