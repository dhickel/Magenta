# Date
2026-05-04

# Change Summary
Added backend enforcement for saved-plan execution completion. EXECUTE_PLAN turns that return ordinary assistant text while the plan is still executing now receive a bounded repair prompt requiring `plan_complete` before final completion. Added a pre-validator criterion coverage check so `plan_complete` fails fast when any approved validation criterion is missing from `criterionResults`.

# Files
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionServiceTest.java`

# Behavioral Impact
- Saved-plan execution retries up to two times if the model attempts to finish without validator-gated completion.
- If the model still skips `plan_complete`, the existing fallback path records a missing structured completion ledger and marks the plan `NEEDS_REVIEW`.
- `plan_complete` now requires every approved validation criterion to appear in `criterionResults` as an exact `Criterion: <text> | Result: <evidence>` entry before the validator model is called.

# Risks
- Exact criterion matching may reject semantically correct but reformatted criterion labels.
- Models that do not support tools still cannot call `plan_complete`; those executions remain review-gated through the fallback path.

# Follow-up Items
- End-to-end exercise with a real execution model to tune the repair prompt wording if needed.
