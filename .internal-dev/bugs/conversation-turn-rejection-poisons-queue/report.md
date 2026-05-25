# Executor Rejection Can Poison Conversation Turn Queue

## Summary

`ConversationTurnCoordinator` marks a queued turn as submitted before calling the chat executor. If executor submission is rejected, the queue head is not completed or removed, leaving later turns for that conversation stuck.

## Scope

- `src/main/java/io/mindspice/magenta2/ai/execution/ConversationTurnCoordinator.java`
- `src/main/java/io/mindspice/magenta2/ai/execution/MagentaWorkExecutor.java`
- Chat turn queueing and executor saturation behavior.

## Reproduction

From the May 25 quality review:

1. Configure a one-thread, zero-queue chat lane.
2. Submit a conversation turn through `ConversationTurnCoordinator` while the lane is saturated.
3. Observe `MagentaWorkExecutor.submitChat` throw `RejectedExecutionException`.
4. Free executor capacity and submit another turn for the same conversation.

The reviewer reproduced this with compiled classes: the first submit threw `Magenta work queue is full`, then the second submit for the same conversation stayed incomplete.

## Expected

If executor submission is rejected, the coordinator should complete the returned future exceptionally, remove or unblock the queue head, and allow later turns for the same conversation to proceed when capacity returns.

## Actual

`ConversationTurnCoordinator` marks the head turn as submitted before calling `MagentaWorkExecutor.submitChat`. When the executor rejects the submission, the queue head remains stuck and later turns for that conversation remain blocked.

## Evidence

- `ConversationTurnCoordinator.java` marks queued turns as submitted before executor submission.
- `MagentaWorkExecutor.submitChat` can throw `RejectedExecutionException` when the chat lane is full.
- The reviewer reproduced the stuck queue with a one-thread, zero-queue chat lane.

## Impact

High. Temporary executor saturation can permanently block a conversation queue, making later user turns hang even after worker capacity recovers.

## Status

Open. Discovered during the May 25 alpha-readiness quality review and not yet remediated. Mirrored to GitHub: https://github.com/dhickel/Magenta/issues/12.

## Next Action

Add a saturation/rejection test for `ConversationTurnCoordinator`, then ensure rejected executor submissions complete the returned future exceptionally and remove or unblock the queue head.
