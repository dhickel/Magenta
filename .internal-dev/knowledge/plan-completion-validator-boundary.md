Topic
Plan completion validator boundary

Source References
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionValidator.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/ChatModelPlanCompletionValidator.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`

Key Takeaways
- `PlanCompletionService` owns deterministic preflight, artifact collection, validator prompt construction, response parsing, and fail-closed completion contract enforcement.
- `PlanCompletionValidator` is intentionally small: it accepts the resolved model, system prompt, and user input, then returns raw validator content plus the model identifier used.
- Tests should assert validator requests through the boundary instead of mocking Spring AI chat internals. This makes clean-context guarantees explicit: approved plan, evidence, artifacts, prior feedback, and final message are present, while broad chat history is absent.
- Artifact validation should collect `Artifact: ...` entries already persisted in execution evidence and dedupe them with the current `plan_complete.artifactPaths` list.
- Completion validation resolves through the planning validator model path and fails closed when no planning validator model can resolve; it should not fall back to the execution model.

Engine Relevance
Keeping the validator model call injectable makes it easier to prove that plan completion validation uses a separate, clean request shape. It also preserves the service boundary: execution reports are untrusted input, while Magenta-owned validation feedback and plan status transitions define trusted completion.

Open Questions
- Whether Magenta should grow a dedicated validator model config field that can explicitly forbid using the same remote model as execution.
