package io.mindspice.magenta.runtime.model;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.mindspice.magenta.runtime.config.RuntimeConfig;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class RoutingModelClient implements ModelClient {

    private static final Set<String> OPENAI_PROVIDER_ALIASES = Set.of(
            "openai",
            "openai-compatible",
            "openai_compatible",
            "llama.cpp",
            "llamacpp",
            "llama-cpp"
    );

    private final OllamaClient ollamaClient;
    private final OpenAiClient openAiClient;

    public RoutingModelClient() {
        this(new OllamaClient(), new OpenAiClient());
    }

    public RoutingModelClient(int requestTimeoutMs) {
        this(new OllamaClient(requestTimeoutMs), new OpenAiClient(requestTimeoutMs));
    }

    RoutingModelClient(OllamaClient ollamaClient, OpenAiClient openAiClient) {
        this.ollamaClient = ollamaClient;
        this.openAiClient = openAiClient;
    }

    @Override
    public ChatResponse chatBlocking(RuntimeConfig.ModelConfig modelConfig, ChatRequest request) {
        return selectClient(modelConfig).chatBlocking(modelConfig, request);
    }

    @Override
    public ChatResponse chatStreaming(RuntimeConfig.ModelConfig modelConfig, ChatRequest request, Consumer<String> tokenCallback) {
        return selectClient(modelConfig).chatStreaming(modelConfig, request, tokenCallback);
    }

    private ModelClient selectClient(RuntimeConfig.ModelConfig modelConfig) {
        String provider = modelConfig.provider() == null ? "" : modelConfig.provider().trim().toLowerCase(Locale.ROOT);
        if (OPENAI_PROVIDER_ALIASES.contains(provider)) {
            return openAiClient;
        }
        return ollamaClient;
    }
}
