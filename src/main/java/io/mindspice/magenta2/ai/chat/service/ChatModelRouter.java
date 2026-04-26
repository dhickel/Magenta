package io.mindspice.magenta2.ai.chat.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.observation.ObservationRegistry;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
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

    private ChatModel buildModel(ModelConfig modelConfig) {
        if (modelConfig.endpointType() != EndpointType.OLLAMA) {
            throw new IllegalStateException("Unsupported chat endpoint type: " + modelConfig.endpointType());
        }
        if (!StringUtils.hasText(modelConfig.remoteEndpoint())) {
            throw new IllegalStateException("Ollama model must define remoteEndpoint: " + modelConfig.remoteModelName());
        }
        if (!StringUtils.hasText(modelConfig.remoteModelName())) {
            throw new IllegalStateException("Ollama model must define remoteModelName");
        }

        return OllamaChatModel.builder()
            .ollamaApi(OllamaApi.builder().baseUrl(modelConfig.remoteEndpoint()).build())
            .defaultOptions(OllamaChatOptions.builder().model(modelConfig.remoteModelName()).build())
            .toolCallingManager(toolCallingManager)
            .observationRegistry(observationRegistry)
            .modelManagementOptions(ModelManagementOptions.defaults())
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

    private record ResolvedModel(String key, ModelConfig config) {
    }
}
