# Phase 04 Validation Report: Conversation Turn Executor Rejection

## Scope

Validated Phase 04 only for GitHub issue #12, using directive `.internal-dev/plans/github-issue-backlog-remediation-20260531/worker-directives/phase-04-conversation-turn-rejection.md`.

Reviewed changed implementation, focused regression test, and changelog:

- `src/main/java/io/mindspice/magenta2/ai/execution/ConversationTurnCoordinator.java`
- `src/test/java/io/mindspice/magenta2/ai/execution/MagentaWorkExecutorTest.java`
- `.internal-dev/changelogs/2026-05-31-conversation-turn-rejection.md`

Repo governance reviewed:

- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `.internal-dev/specifications/architecture.md` entry `ARCH-20260525-01`

No package-level `AGENTS.md` exists under `src/main/java/io/mindspice/magenta2/ai/execution`, so the top-level and internal-dev governance applied.

## Criteria Checked

| Criterion | Result | Evidence |
| --- | --- | --- |
| Inspect actual diff for `ConversationTurnCoordinator`, `MagentaWorkExecutorTest`, and changelog | Pass | Diff is limited to `ConversationTurnCoordinator` rejection handling, one `MagentaWorkExecutorTest` regression, and changelog. No `MagentaWorkExecutor` redesign was made. |
| Rejection cannot leave `submitted=true` stuck at queue head | Pass | `QueuedTurn.submit()` catches `RejectedExecutionException`, completes `result` exceptionally, and returns `result`; `scheduleNext()` attaches `whenComplete` to that returned future, removes the queue head, and calls `scheduleNext()` again. A future already completed exceptionally still invokes the `whenComplete` cleanup. |
| Later same-conversation turns proceed and same-conversation serialization is preserved | Pass | New test `coordinatorRejectedTurnDoesNotPoisonConversationQueue` proves a later same-conversation turn completes after saturation clears. Existing `coordinatorSerializesSameConversationWithoutBlockingOtherConversations` still passes and verifies max same-conversation concurrency remains `1`. |
| Rejection is not swallowed silently and rejected turn completes exceptionally exactly once | Pass | Rejection is propagated through the returned `CompletableFuture`; the test asserts `ExecutionException` with `RejectedExecutionException` cause. There is no submitted executor future on the rejection path, so the only terminal completion path for that rejected turn is the catch block's `completeExceptionally`. |
| No unrelated executor lane redesign or cross-conversation scheduling change | Pass | No changes to `MagentaWorkExecutor`; no queue/scheduler redesign. Existing priority and cross-conversation behavior tests remain passing. |
| Changelog template and Specification Impact statement are correct | Pass after validator self-remediation | Changelog contains required headings. I changed one line from `None. ...` to `Specification Impact: none. ...` to match `.internal-dev/AGENTS.md` wording exactly. |

## Commands Run

- `git status --short`
- `rg --files .internal-dev`
- `sed -n '1,220p' .internal-dev/AGENTS.md`
- `sed -n '1,240p' .internal-dev/plans/github-issue-backlog-remediation-20260531/worker-directives/phase-04-conversation-turn-rejection.md`
- `rg -n "ConversationTurnCoordinator|MagentaWorkExecutor|conversation turn|executor|rejection|RejectedExecutionException" .internal-dev/specifications .internal-dev/knowledge .internal-dev/plans/github-issue-backlog-remediation-20260531 -g '*.md'`
- `sed -n '1,220p' .internal-dev/specifications/AGENTS.md`
- `sed -n '1,80p' .internal-dev/specifications/architecture.md`
- `find src/main/java/io/mindspice/magenta2/ai -name AGENTS.md -o -path 'src/test*' -name AGENTS.md`
- `git diff -- src/main/java/io/mindspice/magenta2/ai/execution/ConversationTurnCoordinator.java src/test/java/io/mindspice/magenta2/ai/execution/MagentaWorkExecutorTest.java .internal-dev/changelogs/2026-05-31-conversation-turn-rejection.md`
- `nl -ba src/main/java/io/mindspice/magenta2/ai/execution/ConversationTurnCoordinator.java | sed -n '1,180p'`
- `nl -ba src/test/java/io/mindspice/magenta2/ai/execution/MagentaWorkExecutorTest.java | sed -n '1,170p'`
- `sed -n '1,220p' .internal-dev/changelogs/2026-05-31-conversation-turn-rejection.md`
- `nl -ba src/main/java/io/mindspice/magenta2/ai/execution/MagentaWorkExecutor.java | sed -n '1,220p'`
- `mvn -q -Dtest=MagentaWorkExecutorTest test`
- `git diff --check -- src/main/java/io/mindspice/magenta2/ai/execution/ConversationTurnCoordinator.java src/test/java/io/mindspice/magenta2/ai/execution/MagentaWorkExecutorTest.java .internal-dev/changelogs/2026-05-31-conversation-turn-rejection.md`

## Evidence Reviewed

- `ConversationTurnCoordinator` lines 85-114: `submitted` guard remains, `submitChat` rejection is caught, `result.completeExceptionally(e)` runs, and the returned `result` is used by `scheduleNext()` cleanup.
- `ConversationTurnCoordinator` lines 56-67: completion cleanup polls the queue head and schedules the next turn.
- `MagentaWorkExecutorTest` lines 87-115: saturated one-thread/zero-queue lane rejects the coordinator-submitted turn, exposes the rejection through the future, releases capacity, and proves a later same-conversation turn completes.
- `MagentaWorkExecutorTest` lines 45-84: same-conversation serialization regression remains in place.
- `MagentaWorkExecutor` reviewed for lane behavior and confirmed unchanged.
- Changelog reviewed for required headings and specification-impact statement.

## Browser Proof Status

Not applicable. Phase 04 changes backend executor coordination and focused unit-level concurrency behavior only; no web or browser surface changed.

## Findings

None.

Validator self-remediation: one document-only changelog wording edit in `.internal-dev/changelogs/2026-05-31-conversation-turn-rejection.md` changed the Specification Impact body to explicitly say `Specification Impact: none`, matching `.internal-dev/AGENTS.md`. `git diff --check` passed after the edit.

## Required Remediation

None.

## Residual Risk

The regression covers executor saturation and same-conversation recovery for a single JVM test lane. Broader live chat/SSE concurrency remains governed by existing architecture drift `ARCH-20260525-01` and was intentionally outside this phase.

## Pass/Fail

Pass.
