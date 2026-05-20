# Plan Validation Review Flow

## Date
2026-05-20

## Change Summary
Fixed anonymous chat plan execution so validator-gated completion remains internal until it passes. `NEEDS_REVIEW` no longer falls back into PLAN mode, exhausted execution now persists a controlled review-state message, and validation feedback includes explicit pass/fail and per-criterion remediation details.

## Files
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `docs/end-user/plans-and-tasks.md`
- `docs/technical/chat-planning-tasks.md`
- `docs/technical/api-reference.md`
- `docs/api/00-index.md`

## Behavioral Impact
- Failed completion validation stays in the execution tool loop while retries are available.
- Exhausted validation/completion attempts move the plan to `NEEDS_REVIEW` with a clear review notice instead of a generic planning prompt.
- `NEEDS_REVIEW` displays evidence and validation feedback without showing planning controls.
- Final-message artifacts are written only for validator-completed anonymous plan executions.

## Risks
- The validator still depends on model compliance with the requested JSON schema, though parsing remains tolerant of older response shapes.
- The browser behavior depends on current `ChatPlanState.status` values; future new review states should update the same UI branch.

## Follow-up Items
- GitHub issue #3 remains the broader follow-up for malformed tool-call JSON repair and overlapping execution guards.
