# Alpha Quality Review

## Scope

Review for alpha-readiness issues in robustness, contract adherence, fragile code, and abstraction quality. This was a non-mutating review pass requested through AgentMail during remote-work coordination.

## Findings

1. High: `CANCEL_REQUESTED` assignments can be overwritten by late lease-owner completion, failure, or waiting writes. `OrchestrationRuntimeRepository.saveAssignmentIfLeaseOwner` accepts rows in `RUNNING` or `CANCEL_REQUESTED`, while `OrchestrationRunnerService` routes multiple state writes through that path. Tracked locally in `.internal-dev/bugs/cancel-requested-late-assignment-overwrite/report.md` and on GitHub as https://github.com/dhickel/Magenta/issues/13.

2. High: executor rejection can poison a conversation queue. `ConversationTurnCoordinator` marks a queued turn as submitted before calling `MagentaWorkExecutor.submitChat`; if the executor rejects the submission, the head is not completed or removed and later turns for that conversation can remain stuck. Tracked locally in `.internal-dev/bugs/conversation-turn-rejection-poisons-queue/report.md` and on GitHub as https://github.com/dhickel/Magenta/issues/12.

3. Medium/high: web/API governance still referenced removed alpha auth/CSRF behavior and stale Avatar note paths. This review finding was remediated in the documentation guidance update recorded by `.internal-dev/changelogs/2026-05-25-doc-agent-guidance-audit.md`.

## Risk Assessment

The two implementation defects are alpha-relevant because they affect cancellation correctness and chat-turn recovery under executor pressure. The governance drift was documentation-only but could have misled future web work into reintroducing removed security helpers or reading retired `.internal-dev/notes` paths.

## Recommendations

- Fix the cancellation transition guard first and add tests for late completion, failure, waiting, and checkpoint writes after `CANCEL_REQUESTED`.
- Fix coordinator rejection handling next and add a saturation/rejection test that proves the queue head is cleared and later turns can proceed.
- Keep active web/package guidance aligned with `docs/technical/security.md` whenever the alpha security posture changes.

## Follow-ups

- Remediate GitHub issues #13 and #12 before alpha sign-off.
- Re-run the relevant focused tests plus full `mvn test` after remediation.

## Review Evidence

- `mvn -q -Dtest=MagentaWorkExecutorTest test` passed.
- `mvn -q -Dtest=OrchestrationRuntimeTest#runningAssignmentCanBeCanceled,OrchestrationRuntimeTest#forceInterruptedAssignmentRejectsLateLeasedCompletion test` passed.
- `mvn -q test` passed.
- The reviewer also ran targeted static searches over `src/main/java`, `src/test/java`, `docs`, and `.internal-dev`, plus two JShell reproductions against compiled classes and a temporary SQLite database for the high-severity findings.
