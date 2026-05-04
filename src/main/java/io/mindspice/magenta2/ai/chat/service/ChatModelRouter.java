package io.mindspice.magenta2.ai.chat.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.observation.ObservationRegistry;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChatModelRouter {
    private final AiConfig aiConfig;
    private final ToolCallingManager toolCallingManager;
    private final ObservationRegistry observationRegistry;
    private final Map<String, ChatModel> modelsByKey = new ConcurrentHashMap<>();

    public ChatModelRouter(
        AiConfig aiConfig,
        ToolCallingManager toolCallingManager,
        ObservationRegistry observationRegistry
    ) {
        this.aiConfig = aiConfig;
        this.toolCallingManager = toolCallingManager;
        this.observationRegistry = observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
    }

    public ChatModel chatModel(String model) {
        ResolvedModel resolvedModel = resolve(model);
        return modelsByKey.computeIfAbsent(resolvedModel.key(), ignored -> buildModel(resolvedModel.config()));
    }

    public ChatClient chatClient(String model) {
        return ChatClient.builder(chatModel(model)).build();
    }

    public String remoteModelName(String model) {
        return resolve(model).config().remoteModelName();
    }

    public ModelConfig modelConfig(String model) {
        return resolve(model).config();
    }

    public OllamaChatOptions ollamaOptions(String model) {
        ModelConfig config = modelConfig(model);
        if (config.endpointType() != EndpointType.OLLAMA) {
            throw new IllegalStateException("ollamaOptions called for non-Ollama model: " + model);
        }
        return ollamaOptionsBuilder(model).build();
    }

    public OllamaChatOptions.Builder ollamaOptionsBuilder(String model) {
        ModelConfig config = modelConfig(model);
        OllamaChatOptions.Builder builder = OllamaChatOptions.builder()
            .model(config.remoteModelName());
        if (config.think()) {
            builder.enableThinking();
        }
        return builder;
    }

    public ToolCallingChatOptions toolCallingOptions(String model) {
        ModelConfig config = modelConfig(model);
        return switch (config.endpointType()) {
            case OLLAMA -> ollamaOptionsBuilder(model).build();
            case OPENAI_COMPATIBLE -> OpenAiChatOptions.builder()
                .model(config.remoteModelName())
                .build();
        };
    }

    private ChatModel buildModel(ModelConfig modelConfig) {
        if (!StringUtils.hasText(modelConfig.remoteEndpoint())) {
            throw new IllegalStateException("Model must define remoteEndpoint: " + modelConfig.remoteModelName());
        }
        if (!StringUtils.hasText(modelConfig.remoteModelName())) {
            throw new IllegalStateException("Model must define remoteModelName");
        }

        return switch (modelConfig.endpointType()) {
            case OLLAMA -> buildOllamaModel(modelConfig);
            case OPENAI_COMPATIBLE -> buildOpenAiModel(modelConfig);
        };
    }

    private OllamaChatModel buildOllamaModel(ModelConfig modelConfig) {
        OllamaChatOptions.Builder optionsBuilder = OllamaChatOptions.builder()
            .model(modelConfig.remoteModelName());
        if (modelConfig.think()) {
            optionsBuilder.enableThinking();
        }
        return OllamaChatModel.builder()
            .ollamaApi(OllamaApi.builder().baseUrl(modelConfig.remoteEndpoint()).build())
            .defaultOptions(optionsBuilder.build())
            .toolCallingManager(toolCallingManager)
            .observationRegistry(observationRegistry)
            .modelManagementOptions(ModelManagementOptions.defaults())
            .build();
    }

    private OpenAiChatModel buildOpenAiModel(ModelConfig modelConfig) {
        if (!StringUtils.hasText(modelConfig.apiKey())) {
            throw new IllegalStateException(
                "OpenAI-compatible model must define apiKey: " + modelConfig.remoteModelName());
        }
        OpenAiApi api = OpenAiApi.builder()
            .baseUrl(modelConfig.remoteEndpoint())
            .apiKey(modelConfig.apiKey())
            .completionsPath("/v1/chat/completions")
            .embeddingsPath("/v1/embeddings")
            .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .model(modelConfig.remoteModelName())
            .build();
        return OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(options)
            .toolCallingManager(toolCallingManager)
            .observationRegistry(observationRegistry)
            .build();
    }

    private ResolvedModel resolve(String model) {
        if (aiConfig == null || aiConfig.models() == null || aiConfig.models().isEmpty()) {
            throw new IllegalStateException("AI config must define models");
        }
        String selected = StringUtils.hasText(model) ? model : defaultRemoteModelName();
        ModelConfig byKey = aiConfig.models().get(selected);
        if (byKey != null) {
            return new ResolvedModel(selected, byKey);
        }
        return aiConfig.models().entrySet().stream()
            .filter(entry -> selected.equals(entry.getValue().remoteModelName()))
            .findFirst()
            .map(entry -> new ResolvedModel(entry.getKey(), entry.getValue()))
            .orElseThrow(() -> new IllegalArgumentException("Unknown configured model: " + selected));
    }

    private String defaultRemoteModelName() {
        if (!StringUtils.hasText(aiConfig.defaultAgent()) || aiConfig.agents() == null) {
            throw new IllegalStateException("AI config must define defaultAgent");
        }
        String defaultModelKey = aiConfig.agents().get(aiConfig.defaultAgent()).model();
        ModelConfig defaultModel = aiConfig.models().get(defaultModelKey);
        if (defaultModel == null) {
            throw new IllegalStateException("defaultAgent references missing model: " + defaultModelKey);
        }
        return defaultModel.remoteModelName();
    }

    private record ResolvedModel(String key, ModelConfig config) { }
}
