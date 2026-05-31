# Work Units

## Recommended Order

1. `phase-01-sql-identifier-hardening.md` for #9.
2. `phase-02-workflow-migration-errors.md` for #10.
3. `phase-03-global-exception-handler.md` for #11.
4. `phase-04-conversation-turn-rejection.md` for #12.
5. `phase-05-cancel-requested-lease-guard.md` for #13.
6. `phase-06-pending-chat-fifo.md` for #19.
7. `phase-07-chat-sse-interrupt-lifecycle.md` for #14 and #15.
8. `phase-08-run-display-name-boundary.md` for #16.
9. `phase-09-workflow-pass-through.md` for #17.
10. `phase-10-workflow-delegation-evidence.md` for #18.
11. `phase-11-slotkey-template-refactor.md` for #33.

#8 is skipped by user direction and remains open. Do not dispatch dashboard empty-row/density remediation during this plan.

#34 is tracked in the issue inventory as future typed-ID refactor work and remains open. Do not dispatch it during this plan unless the user explicitly changes scope.

## Combined Units

Only #14 and #15 are combined. The implementation is likely safer as one coherent chat/SSE active-turn contract fix because both issues involve advertised interrupt metadata, active-turn cleanup, and stream terminal paths.

## Commit Boundaries

Commit after every phase that passes validation. The commit message should reference the GitHub issue number(s). Suggested prefix:

- `Fix #9: Harden SQL identifier usage`
- `Fix #14 #15: Align chat SSE interrupt lifecycle`

If a phase proves an issue is already fixed and only closeout/docs/evidence changes are needed, still keep that closeout in a dedicated commit referencing the issue.
