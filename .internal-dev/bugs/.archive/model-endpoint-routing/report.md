# Summary

Magenta model configuration includes per-model `remoteEndpoint`, but chat execution currently uses a single Spring AI `ChatModel` bean configured by `spring.ai.ollama.base-url`.

# Scope

Chat model routing for configured AI models.

# Reproduction

Configure two models with different `remoteEndpoint` values, then select the non-default model in the UI or request.

# Expected

Magenta should send the request to the endpoint configured for the selected model.

# Actual

Magenta only changes the model name through `OllamaChatOptions`; the underlying `ChatModel` client endpoint remains the one configured in `application.yml`.

# Evidence

- `ModelConfig` has `remoteEndpoint`.
- `ChatBeanConfig` builds `ChatClient` from a single `ChatModel` bean.
- `ChatService` passes only `.model(request.model())` in `OllamaChatOptions`.

# Impact

Model selection works only when all configured models are hosted on the same Ollama endpoint. Mixed endpoints or endpoint types are represented in config but not actually honored by chat execution.

# Status

Open.

# Next Action

Add a bounded model client resolver that maps selected configured model keys/names to the correct endpoint-specific chat client/model, or validate startup config to reject unsupported mixed endpoints until routing is implemented.
