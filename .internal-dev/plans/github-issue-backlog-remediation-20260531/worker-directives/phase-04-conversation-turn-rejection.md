# Phase 04 Worker Directive: Conversation Turn Executor Rejection (#12)

## Objective

Remediate GitHub issue #12 so executor submission rejection cannot permanently poison a conversation turn queue.

## User-Visible Outcome

Temporary chat executor saturation returns a failed future for the rejected turn and later turns for the same conversation can proceed when capacity is available.

## Issues

- #12 `Executor rejection can poison conversation turn queue`

## Direct Targets

- `src/main/java/io/mindspice/magenta2/ai/execution/ConversationTurnCoordinator.java`
- `src/main/java/io/mindspice/magenta2/ai/execution/MagentaWorkExecutor.java` only if needed
- `src/test/java/io/mindspice/magenta2/ai/execution/MagentaWorkExecutorTest.java` or new `ConversationTurnCoordinatorTest.java`
- `.internal-dev/specifications/architecture.md` if chat serialization drift is clarified
- `.internal-dev/changelogs/2026-05-31-conversation-turn-rejection.md`

## Forbidden Scope

- Do not redesign executor lanes.
- Do not add queues/schedulers beyond the minimal rejection fix.
- Do not change cross-conversation scheduling semantics except as required by tests.

## Supporting Docs To Read

- `.internal-dev/specifications/architecture.md` entry `ARCH-20260525-01`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` if browser chat validation becomes relevant

## Reproduction Probe Required Before Fix

Add a focused test that:

1. Creates a chat executor lane with one thread and zero queue, or an equivalent rejecting executor setup.
2. Occupies the lane with a blocking turn.
3. Submits a same-conversation turn through `ConversationTurnCoordinator` that is rejected.
4. Releases capacity.
5. Submits another same-conversation turn and proves it completes instead of staying behind the poisoned head.

## Implementation Steps

1. Add the reproduction test and confirm it fails or encodes the old hazard.
2. Fix `QueuedTurn.submit()`/`scheduleNext()` so `RejectedExecutionException`:
   - completes the queued turn exceptionally,
   - unblocks/removes the queue head through the existing completion path or explicit cleanup,
   - allows scheduling to continue.
3. Preserve cancellation behavior and same-conversation serialization.
4. Add a regression test for normal same-conversation serialization if the existing test needs adjustment.

## Senior-Engineer Guidance

- The bug occurs because `submitted = true` is set before a call that can throw.
- Avoid holding the global `queues` lock while executing user work.
- Ensure rejected turns complete exceptionally exactly once.

## Acceptance Criteria

- Rejected executor submission does not leave a stuck queue head.
- Later same-conversation turns proceed.
- Existing priority and serialization tests still pass.

## Negative Checks

- Do not swallow rejection silently.
- Do not run same-conversation turns concurrently.
- Do not leak queue entries after rejection.

## Validation Commands

- `mvn -q -Dtest=MagentaWorkExecutorTest test`
- Include new focused test class if created.

## Evidence Expectations

- Validator report: `.internal-dev/plans/github-issue-backlog-remediation-20260531/validation/phase-04-validation-report.md`

## Closeout Expectations

Main thread closes #12 after validation, commit, push, and email.

## Stop Conditions

- Stop if reproducing rejection requires changing executor public API.

## Do Not Close Unless

- The rejection regression test proves the later turn completes.
