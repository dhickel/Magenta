package com.magenta.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Map;

public class Config {


    @JsonProperty("global")
    public GlobalConfig global;

    @JsonProperty("endpoints")
    public Map<String, EndpointConfig> endpoints;

    @JsonProperty("security")
    public SecurityConfig security;

    @JsonProperty("models")
    public Map<String, ModelConfig> models;

    @JsonProperty("agents")
    public Map<String, AgentConfig> agents;

    @JsonProperty("prompts")
    public Map<String, String> prompts;

    public record GlobalConfig(
            @JsonProperty("base_agent") String baseAgent,
            @JsonProperty("storage_path") String storagePath
    ) { }

    public record SecurityConfig(
            @JsonProperty("approval_required_for") List<String> approvalRequiredFor,
            @JsonProperty("always_allow_commands") List<String> alwaysAllowCommands,
            @JsonProperty("blocked_commands") List<String> blockedCommands
    ) { }

    public static class ModelConfig {
        @JsonProperty("model_name")
        private String modelName;
        @JsonProperty("endpoint")
        private String endpointKey;
        @JsonProperty("max_tokens")
        private int maxTokens;

        public String modelName() { return modelName; }

        public int maxTokens() { return maxTokens; }

        public EndpointConfig endpoint(Config config) {
            EndpointConfig endpoint = config.endpoints.get(endpointKey);
            if (endpoint == null) { throw new IllegalStateException("Endpoint not found: " + endpointKey); }
            return endpoint;
        }
    }

    public static class AgentConfig {
        @JsonProperty("system_prompt")
        private String systemPromptKey;
        @JsonProperty("model")
        private String modelKey;
        private List<String> tools;

        public List<String> tools() { return tools; }

        public String systemPrompt(Config config) {
            String prompt = config.prompts.get(systemPromptKey);
            if (prompt == null) { throw new IllegalStateException("System prompt not found: " + systemPromptKey); }
            return prompt;
        }

        public ModelConfig model(Config config) {
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
        Integer timeoutSeconds();

        record Ollama(
                String url,
                @JsonProperty("timeout_seconds") Integer timeoutSeconds
        ) implements EndpointConfig { }

        record OpenAI(
                @JsonProperty("api_key") String apiKey,
                @JsonProperty("org_id") String orgId,
                @JsonProperty("base_url") String baseUrl,
                @JsonProperty("timeout_seconds") Integer timeoutSeconds
        ) implements EndpointConfig { }

        record Anthropic(
                @JsonProperty("api_key") String apiKey,
                String version,
                @JsonProperty("timeout_seconds") Integer timeoutSeconds
        ) implements EndpointConfig { }

        record RemoteAgent(
                String url,
                @JsonProperty("auth_token") String authToken,
                @JsonProperty("timeout_seconds") Integer timeoutSeconds,
                Map<String, String> capabilities
        ) implements EndpointConfig { }
    }

}
