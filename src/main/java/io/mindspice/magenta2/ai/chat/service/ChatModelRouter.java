package io.mindspice.magenta2.ai.chat.service;

import java.net.http.HttpClient;
import java.time.Duration;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class ChatModelRouter {
    private final AiConfig aiConfig;
    private final ToolCallingManager toolCallingManager;
    private final ObservationRegistry observationRegistry;
    private final Map<String, ChatModel> modelsByKey = new ConcurrentHashMap<>();
    private final Duration openAiCompatibleReadTimeout;

    public ChatModelRouter(
        AiConfig aiConfig,
        ToolCallingManager toolCallingManager,
        ObservationRegistry observationRegistry
    ) {
        this(aiConfig, toolCallingManager, observationRegistry, 360);
    }

    @Autowired
    public ChatModelRouter(
        AiConfig aiConfig,
        ToolCallingManager toolCallingManager,
        ObservationRegistry observationRegistry,
        @Value("${magenta.ai.openai-compatible-read-timeout-seconds:360}") long openAiCompatibleReadTimeoutSeconds
    ) {
        this.aiConfig = aiConfig;
        this.toolCallingManager = toolCallingManager;
        this.observationRegistry = observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
        this.openAiCompatibleReadTimeout = Duration.ofSeconds(Math.max(1, openAiCompatibleReadTimeoutSeconds));
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

    /** Returns Ollama-specific options. Throws if the model is not an Ollama endpoint.
     * Prefer {@link #chatOptions(String)} for endpoint-agnostic callers. */
    public OllamaChatOptions ollamaOptions(String model) {
        ModelConfig config = modelConfig(model);
        if (config.endpointType() != EndpointType.OLLAMA) {
            throw new IllegalStateException("ollamaOptions called for non-Ollama model: " + model);
        }
        return ollamaOptionsBuilder(model).build();
    }

    public OllamaChatOptions.Builder ollamaOptionsBuilder(String model) {
        ModelConfig config = modelConfig(model);
        if (config.endpointType() != EndpointType.OLLAMA) {
            throw new IllegalStateException("ollamaOptionsBuilder called for non-Ollama model: " + model);
        }
        OllamaChatOptions.Builder builder = OllamaChatOptions.builder()
            .model(config.remoteModelName());
        applyOllamaThink(builder, effectiveThinkLevel(config));
        return builder;
    }

    public ToolCallingChatOptions toolCallingOptions(String model) {
        ModelConfig config = modelConfig(model);
        return switch (config.endpointType()) {
            case OLLAMA -> ollamaOptionsBuilder(model).build();
            case OPENAI_COMPATIBLE -> {
                OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                    .model(config.remoteModelName());
                applyOpenAiReasoningEffort(builder, effectiveThinkLevel(config), false);
                yield builder.build();
            }
            case DEEPSEEK -> {
                OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                    .model(config.remoteModelName());
                applyOpenAiReasoningEffort(builder, effectiveThinkLevel(config), true);
                yield builder.build();
            }
        };
    }

    /** Endpoint-polymorphic options for any chat call. Use this instead of
     * {@link #ollamaOptions(String)} unless you specifically need the
     * concrete {@code OllamaChatOptions} type. */
    public ToolCallingChatOptions chatOptions(String model) {
        return toolCallingOptions(model);
    }

    private static int effectiveThinkLevel(ModelConfig config) {
        Integer level = config.thinkLevel();
        if (level == null) return 0;
        if (level < 0) return 0;
        if (level > 4) return 4;
        return level;
    }

    private static void applyOllamaThink(OllamaChatOptions.Builder builder, int level) {
        switch (level) {
            case 0 -> builder.disableThinking();
            case 1 -> builder.thinkLow();
            case 2 -> builder.thinkMedium();
            case 3 -> builder.thinkHigh();
            default -> builder.thinkHigh(); // level >= 4 clamped
        }
    }

    private static void applyOpenAiReasoningEffort(OpenAiChatOptions.Builder builder, int level, boolean isDeepSeek) {
        if (level <= 0) return;
        String effort = switch (level) {
            case 1 -> isDeepSeek ? "high" : "low";
            case 2 -> isDeepSeek ? "high" : "medium";
            case 3 -> "high";
            default -> isDeepSeek ? "max" : "high";
        };
        builder.reasoningEffort(effort);
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
            case DEEPSEEK -> buildDeepSeekModel(modelConfig);
        };
    }

    private OllamaChatModel buildOllamaModel(ModelConfig modelConfig) {
        OllamaChatOptions.Builder optionsBuilder = OllamaChatOptions.builder()
            .model(modelConfig.remoteModelName());
        applyOllamaThink(optionsBuilder, effectiveThinkLevel(modelConfig));
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
        OpenAiChatOptions options = buildOpenAiOptions(modelConfig, false);
        return buildOpenAiChatModel(modelConfig, options);
    }

    private OpenAiChatModel buildDeepSeekModel(ModelConfig modelConfig) {
        if (!StringUtils.hasText(modelConfig.apiKey())) {
            throw new IllegalStateException(
                "DeepSeek model must define apiKey: " + modelConfig.remoteModelName());
        }
        OpenAiChatOptions options = buildOpenAiOptions(modelConfig, true);
        return buildOpenAiChatModel(modelConfig, options);
    }

    private OpenAiChatOptions buildOpenAiOptions(ModelConfig modelConfig, boolean isDeepSeek) {
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
            .model(modelConfig.remoteModelName());
        applyOpenAiReasoningEffort(optionsBuilder, effectiveThinkLevel(modelConfig), isDeepSeek);
        return optionsBuilder.build();
    }

    private OpenAiChatModel buildOpenAiChatModel(ModelConfig modelConfig, OpenAiChatOptions options) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(openAiCompatibleReadTimeout);
        RestClient.Builder restClientBuilder = RestClient.builder().requestFactory(requestFactory);
        OpenAiApi api = OpenAiApi.builder()
            .baseUrl(modelConfig.remoteEndpoint())
            .apiKey(modelConfig.apiKey())
            .completionsPath("/v1/chat/completions")
            .embeddingsPath("/v1/embeddings")
            .restClientBuilder(restClientBuilder)
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
