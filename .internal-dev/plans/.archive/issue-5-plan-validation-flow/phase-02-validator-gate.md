# Phase 02 - Validator Gate

## Context
`plan_complete` records execution evidence and calls `PlanCompletionService`, but validation failure returns remediation text into the tool loop. If execution later exhausts completion repair, `ChatService` can still mark a plan complete from ordinary assistant text.

## Goal
Make validation an internal gate. User-visible completion should be emitted only after validator-passed `plan_complete`, or else a distinct needs-review message should be persisted after automatic remediation is exhausted.

## In Scope
- Strengthen validator output schema and feedback rendering.
- Make validation feedback explicit and durable in `PlanDefinition.validationFeedback`.
- Prevent `executeSavedPlan(...)` and stream finalization from marking a still-executing plan completed from ordinary model text.
- Add tests for validation failure, successful completion, and exhausted review state.

## Out of Scope
- New database schema.
- Multiple persisted retry counters across HTTP requests.
- Model tool-call JSON preflight repair from issue #3.

## Implementation Steps
1. In `PlanCompletionService`, update the validator prompt to require per-criterion JSON objects with `criterion`, `status`, `evidence`, `risk`, and `requiredRemediation`.
2. Parse the new schema while tolerating existing string-list fields so current tests and local model variance remain manageable.
3. Prefix durable validation feedback with clear pass/fail status, and preserve per-criterion remediation text.
4. Keep failed validation as tool feedback only; do not mark the plan complete.
5. In `ChatService.handleFinalize(...)`, if the original turn mode is `EXECUTE_PLAN`, the plan is still executing, and execution-completion repair has exhausted, mark the plan `NEEDS_REVIEW` and persist a controlled review-state assistant message instead of the model's ordinary text.
6. In `executeSavedPlan(...)` and `handlePlanExecutionStreamFinished(...)`, if the plan is still `EXECUTE_PLAN` after chat returns, mark `NEEDS_REVIEW`; never mark `COMPLETED` from the returned assistant text unless `plan_complete` already marked completion.

## Validation
- Run focused service/tool tests: `./mvnw -q -Dtest=PlanServiceTest,ChatServiceTest,PlanSaveToolsTest test`.
- Verify failed validation leaves the plan executing during retries, then needs-review only after exhaustion.
- Verify successful validation persists only the validator-approved final message.

## Exit Criteria
- Failed validator feedback is not used as the final assistant completion.
- Exhausted execution ends in `NEEDS_REVIEW` with a clear review message.
- Successful validation still returns and persists the approved final message.
