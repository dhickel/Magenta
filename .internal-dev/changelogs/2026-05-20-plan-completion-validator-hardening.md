Date
2026-05-20

Change Summary
Hardened anonymous plan completion validation by extracting the validator model call behind a small `PlanCompletionValidator` boundary, framing validator inputs as untrusted data, carrying forward artifact paths recorded by earlier `plan_report` calls, preventing fallback to the execution model when no planning validator model resolves, and recording whether validation used a model or was skipped by fail-closed preflight.

Files
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionValidator.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/ChatModelPlanCompletionValidator.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `docs/technical/chat-planning-tasks.md`
- `docs/end-user/plans-and-tasks.md`

Behavioral Impact
`plan_complete` validation now includes artifacts previously reported through `plan_report` without requiring the executor to resubmit those paths. Validator prompts explicitly treat approved plan text, evidence, artifacts, prior feedback, and final messages as untrusted data. Validation feedback identifies the validator model used, states that deterministic preflight skipped the model validator, or fails closed when no planning validator model can resolve.

Risks
The validator model is still resolved through the existing planning-model path to avoid broad configuration churn. This does not add a separate config field that can explicitly assert validator and executor model separation when both point to the same remote model.

Follow-up Items
None.
