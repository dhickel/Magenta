package com.magenta.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.magenta.model.ChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class Config {



    @JsonProperty("global")
    private GlobalConfig global;

    @JsonProperty("endpoints")
    public Map<String, EndpointConfig> endpoints;

    @JsonProperty("security")
    public SecurityConfig security;

    @JsonProperty("colors")
    public ColorsConfig colors;

    @JsonProperty("models")
    public Map<String, ModelConfig> models;

    @JsonProperty("agents")
    public Map<String, AgentConfig> agents;

    @JsonProperty("prompts")
    public Map<String, String> prompts;

    public void initializeReferences() {
        models.values().forEach(model -> model.config = this);
        agents.values().forEach(agent -> agent.config = this);
    }

    public AgentConfig baseAgent() {
        AgentConfig agent = agents.get(global.baseAgent());
        if (agent == null) { throw new IllegalStateException("Base agent not found: " + global.baseAgent()); }
        return agent;
    }

    public String baseStoragePath() {
        if (global.storagePath == null) { throw new IllegalStateException("No storage path defined"); }
        return global.storagePath;
    }

    public int streamDelayMs() {
        return global.getStreamDelayMs();
    }

    public record GlobalConfig(
            @JsonProperty("base_agent") String baseAgent,
            @JsonProperty("storage_path") String storagePath,
            @JsonProperty("stream_delay_ms") Integer streamDelayMs
    ) {
        public int getStreamDelayMs() {
            return streamDelayMs != null ? streamDelayMs : 0;
        }
    }

    public record SecurityConfig(
            @JsonProperty("approval_required_for") List<String> approvalRequiredFor,
            @JsonProperty("always_allow_commands") List<String> alwaysAllowCommands,
            @JsonProperty("blocked_commands") List<String> blockedCommands
    ) { }

    /**
     * Color codes (JLine AttributedStyle constants):
     * 0=BLACK, 1=RED, 2=GREEN, 3=YELLOW, 4=BLUE, 5=MAGENTA, 6=CYAN, 7=WHITE
     * Add 8 for bright variants (e.g., 9=BRIGHT_RED)
     */
    public record ColorsConfig(
            @JsonProperty("error") Integer error,
            @JsonProperty("warning") Integer warning,
            @JsonProperty("success") Integer success,
            @JsonProperty("info") Integer info,
            @JsonProperty("agent") Integer agent,
            @JsonProperty("prompt") Integer prompt,
            @JsonProperty("security") Integer security,
            @JsonProperty("command") Integer command
    ) {
        public Integer getColor(String styleName) {
            return switch (styleName.toLowerCase()) {
                case "error" -> error;
                case "warning" -> warning;
                case "success" -> success;
                case "info" -> info;
                case "agent" -> agent;
                case "prompt" -> prompt;
                case "security" -> security;
                case "command" -> command;
                default -> null;
            };
        }
    }

    public static class ModelConfig {
        @JsonProperty("model_name")
        private String modelName;
        @JsonProperty("endpoint")
        private String endpointKey;
        @JsonProperty("max_tokens")
        private int maxTokens;
        @JsonProperty("temperature")
        private double temperature;

        private Config config;

        public String modelName() { return modelName; }

        public int maxTokens() { return maxTokens; }

        public double temperature() { return temperature; }

        public EndpointConfig endpoint() {
            EndpointConfig endpoint = config.endpoints.get(endpointKey);
            if (endpoint == null) { throw new IllegalStateException("Endpoint not found: " + endpointKey); }
            return endpoint;
        }

        public ChatLanguageModel getAsChatModel() {
            return switch (endpoint()) {
                case EndpointConfig.Anthropic ep -> throw new UnsupportedOperationException();
                case EndpointConfig.Ollama ep -> OllamaChatModel.builder()
                        .baseUrl(ep.url)
                        .modelName(modelName)
                        .timeout(Duration.ofSeconds(ep.timeoutSeconds))
                        .temperature(temperature)
                        .numCtx(maxTokens)
                        .build();
                case EndpointConfig.OpenAI ep -> throw new UnsupportedOperationException();
                case EndpointConfig.RemoteAgent ep -> throw new UnsupportedOperationException();
            };
        }


        public StreamingChatLanguageModel getAsStreamingChatModel() {
            return switch (endpoint()) {
                case EndpointConfig.Anthropic ep -> throw new UnsupportedOperationException();
                case EndpointConfig.Ollama ep -> OllamaStreamingChatModel.builder()
                        .baseUrl(ep.url)
                        .modelName(modelName)
                        .timeout(Duration.ofSeconds(ep.timeoutSeconds))
                        .temperature(temperature)
                        .numCtx(maxTokens)
                        .build();
                case EndpointConfig.OpenAI ep -> throw new UnsupportedOperationException();
                case EndpointConfig.RemoteAgent ep -> throw new UnsupportedOperationException();
            };
        }

        public ChatModel asChatModel(boolean streaming) {
            return streaming
                    ? new ChatModel.Streaming(getAsStreamingChatModel())
                    : new ChatModel.Blocking(getAsChatModel());
        }

        public ChatModel asStreamingChatModel() {
            return new ChatModel.Streaming(getAsStreamingChatModel());
        }

        public ChatModel asBlockingChatModel() {
            return new ChatModel.Blocking(getAsChatModel());
        }
    }

    public static class AgentConfig {
        @JsonProperty("system_prompt")
        private String systemPromptKey;
        @JsonProperty("model")
        private String modelKey;
        @JsonProperty("tools")
        private List<String> tools;
        @JsonProperty("color")
        private Integer color;

        private Config config;

        public List<String> tools() { return tools; }

        public Integer color() { return color; }

        public String systemPrompt() {
            String prompt = config.prompts.get(systemPromptKey);
            if (prompt == null) { throw new IllegalStateException("System prompt not found: " + systemPromptKey); }
            return prompt;
        }

        public ModelConfig model() {
            ModelConfig model = config.models.get(modelKey);
            if (model == null) { throw new IllegalStateException("Model not found: " + modelKey); }
            return model;
        }
    }


    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type"
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = EndpointConfig.Ollama.class, name = "ollama"),
            @JsonSubTypes.Type(value = EndpointConfig.OpenAI.class, name = "openai"),
            @JsonSubTypes.Type(value = EndpointConfig.Anthropic.class, name = "anthropic"),
            @JsonSubTypes.Type(value = EndpointConfig.RemoteAgent.class, name = "remote_agent")
    })
    public sealed interface EndpointConfig {
        int timeoutSeconds();

        record Ollama(
                String url,
                @JsonProperty("timeout_seconds") int timeoutSeconds
        ) implements EndpointConfig { }

        record OpenAI(
                @JsonProperty("api_key") String apiKey,
                @JsonProperty("org_id") String orgId,
                @JsonProperty("base_url") String baseUrl,
                @JsonProperty("timeout_seconds") int timeoutSeconds
        ) implements EndpointConfig { }

        record Anthropic(
                @JsonProperty("api_key") String apiKey,
                String version,
                @JsonProperty("timeout_seconds") int timeoutSeconds
        ) implements EndpointConfig { }

        record RemoteAgent(
                String url,
                @JsonProperty("auth_token") String authToken,
                @JsonProperty("timeout_seconds") int timeoutSeconds,
                Map<String, String> capabilities
        ) implements EndpointConfig { }
    }

}
