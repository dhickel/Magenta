---
schema_version: 1
document_type: changelog
status: finalized
owner: ai-execution
created: 2026-05-31
---

# Conversation Turn Rejection Cleanup

## Date

2026-05-31

## Change Summary

- Completed coordinator-submitted chat turns exceptionally when the chat executor rejects submission.
- Preserved the existing coordinator completion cleanup path so a rejected same-conversation queue head is removed and later turns can proceed.
- Added a regression test for a saturated single-thread chat lane with zero queue capacity.

## Files

- `src/main/java/io/mindspice/magenta2/ai/execution/ConversationTurnCoordinator.java`
- `src/test/java/io/mindspice/magenta2/ai/execution/MagentaWorkExecutorTest.java`

## Behavioral Impact

Temporary chat executor saturation now returns a failed future for the rejected conversation turn instead of throwing out of coordinator submission and leaving the conversation queue stuck.

## Specification Impact

Specification Impact: none. This repairs the existing `ConversationTurnCoordinator` queue behavior without changing the chat serialization architecture contract recorded in `ARCH-20260525-01`.

## Risks

Low. The fix is scoped to executor submission rejection and reuses the existing future completion cleanup path.

## Follow-up Items

None.

## Validation

- Passed: `mvn -q -Dtest=MagentaWorkExecutorTest test`
