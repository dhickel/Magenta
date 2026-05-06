# Knowledge: Spring AI Options Routing Pattern

## Topic
Endpoint-polymorphic model options routing in Spring AI

## Source References
- `ChatModelRouter.java` — the single routing point for model construction and options
- Spring AI type hierarchy: `OllamaChatOptions` and `OpenAiChatOptions` both implement `ToolCallingChatOptions`
- Bug fixed: three call sites hardcoded `ollamaOptions()` which throws for non-Ollama models

## Key Takeaways
1. **`ToolCallingChatOptions` is the polymorphic base** — both `OllamaChatOptions` and `OpenAiChatOptions` implement it. Despite the "ToolCalling" name, it works for plain (non-tool) chat too. Spring AI's `ChatClient.prompt().options()` and `Prompt` constructors accept `ToolCallingChatOptions` (or just `ChatOptions`).

2. **Never expose concrete option types to callers** — the pattern is: the router has one public endpoint-polymorphic method (`chatOptions()`/`toolCallingOptions()`) that returns the interface type, and endpoint-specific methods (`ollamaOptions()`) are kept but documented as unsafe for general use. Callers outside the router should never need concrete option types.

3. **The router must be the sole code path** — model construction, option building, and endpoint resolution should all flow through `ChatModelRouter`. Any call site that imports or constructs Spring AI Ollama/OpenAI types directly is a code smell. In this codebase, the only file importing those types (in production code) is `ChatModelRouter.java` — that's the correct pattern.

4. **Config-driven endpoint dispatch**: the `EndpointType` enum (`OLLAMA`, `OPENAI_COMPATIBLE`) is stored per-model in `ModelConfig`. The router switches on it at every decision point (model building, options building). This is safer than duck-typing or relying on conventions.

5. **External model keys are local aliases**: entries under `models` use the map key as Magenta's selectable model alias, while `remoteModelName` is the provider/Ollama model name sent to the endpoint. Adding another Ollama model with an existing endpoint only requires a new model entry when no runtime behavior changes.

## Engine Relevance
When adding a new endpoint type or modifying how models are called:
- Add the new `EndpointType` variant
- Add the switch case in `ChatModelRouter.buildModel()`, `toolCallingOptions()`, and `chatOptions()`
- Do not add new public methods that return the concrete type unless callers genuinely need it
- Audit all call sites of `ollamaOptions()` / `ollamaOptionsBuilder()` to ensure they still respect endpoint polymorphism

## Open Questions
- Should we add a third `EndpointType` (e.g., `ANTHROPIC`) when needed?
- Should `ollamaOptions()` and `ollamaOptionsBuilder()` be made package-private to enforce router-only access?
