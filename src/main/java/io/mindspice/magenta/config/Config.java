package io.mindspice.magenta.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = false)
public final class Config {

    private static final Path DEFAULT_CONFIG_PATH = Path.of("config.json");
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    @JsonProperty("global")
    private GlobalConfig global;

    @JsonProperty("endpoints")
    private Map<String, EndpointConfig> endpoints;

    @JsonProperty("securities")
    private Map<String, SecurityConfig> securities;

    @JsonProperty("colors")
    private Map<String, ColorsConfig> colorConfigs;

    @JsonProperty("models")
    private Map<String, ModelConfig> models;

    @JsonProperty("agents")
    private Map<String, AgentConfig> agents;

    @JsonProperty("tool_sets")
    private Map<String, List<String>> toolSets;

    @JsonProperty("prompts")
    private Map<String, String> prompts;

    @JsonProperty("task_templates")
    private Map<String, Object> taskTemplates;

    @JsonProperty("delegation_templates")
    private Map<String, DelegationTemplate> delegationTemplates;

    public static Config loadDefault() {
        return load(DEFAULT_CONFIG_PATH);
    }

    public static Config load(Path configPath) {
        if (!Files.exists(configPath) || !Files.isRegularFile(configPath)) {
            throw new IllegalStateException("Config file not found: " + configPath.toAbsolutePath());
        }

        try {
            Config config = MAPPER.readValue(configPath.toFile(), Config.class);
            config.initializeReferences();
            return config;
        } catch (JsonProcessingException e) {
            throw parseException(configPath, e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config at " + configPath.toAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    private static IllegalStateException parseException(Path path, JsonProcessingException e) {
        JsonLocation location = e.getLocation();
        String lineCol = location == null
                ? "line=?, column=?"
                : "line=" + location.getLineNr() + ", column=" + location.getColumnNr();
        return new IllegalStateException(
                "Config parse failure at " + path.toAbsolutePath() + " (" + lineCol + "): " + e.getOriginalMessage(),
                e
        );
    }

    private void initializeReferences() {
        if (models != null) {
            models.values().forEach(model -> model.config = this);
        }
        if (agents != null) {
            agents.forEach((name, agent) -> {
                agent.config = this;
                agent.name = name;
            });
        }
    }

    public GlobalConfig global() { return global; }
    public Map<String, EndpointConfig> endpoints() { return endpoints == null ? Map.of() : endpoints; }
    public Map<String, SecurityConfig> securities() { return securities == null ? Map.of() : securities; }
    public Map<String, ColorsConfig> colorConfigs() { return colorConfigs == null ? Map.of() : colorConfigs; }
    public Map<String, ModelConfig> models() { return models == null ? Map.of() : models; }
    public Map<String, AgentConfig> agents() { return agents == null ? Map.of() : agents; }
    public Map<String, List<String>> toolSets() { return toolSets == null ? Map.of() : toolSets; }
    public Map<String, String> prompts() { return prompts == null ? Map.of() : prompts; }
    public Map<String, Object> taskTemplates() { return taskTemplates == null ? Map.of() : taskTemplates; }
    public Map<String, DelegationTemplate> delegationTemplates() {
        return delegationTemplates == null ? Map.of() : delegationTemplates;
    }

    public record GlobalConfig(
            @JsonProperty("base_agent") String baseAgent,
            @JsonProperty("storage_path") String storagePath,
            @JsonProperty("stream_delay_ms") Integer streamDelayMs,
            @JsonProperty("tool_display_mode") String toolDisplayMode,
            @JsonProperty("max_tool_iterations") Integer maxToolIterations,
            @JsonProperty("debug") Boolean debug,
            @JsonProperty("compaction_agent") String compactionAgent
    ) {
        public int streamDelayMsOrDefault() { return streamDelayMs != null ? streamDelayMs : 0; }
        public String toolDisplayModeOrDefault() { return toolDisplayMode != null ? toolDisplayMode : "simple"; }
        public int maxToolIterationsOrDefault() { return maxToolIterations != null ? maxToolIterations : 10; }
        public boolean debugOrDefault() { return Boolean.TRUE.equals(debug); }
        public String compactionAgentOrDefault() { return compactionAgent != null ? compactionAgent : "compactor"; }
    }

    public record SecurityConfig(
            @JsonProperty("approval_required_for") List<String> approvalRequiredFor,
            @JsonProperty("always_allow_commands") List<String> alwaysAllowCommands,
            @JsonProperty("blocked_commands") List<String> blockedCommands,
            @JsonProperty("blocked_tools") List<String> blockedTools,
            @JsonProperty("allowed_file_paths") List<String> allowedFilePaths
    ) { }

    public record ColorsConfig(
            @JsonProperty("error") Integer error,
            @JsonProperty("warning") Integer warning,
            @JsonProperty("success") Integer success,
            @JsonProperty("info") Integer info,
            @JsonProperty("agent") Integer agent,
            @JsonProperty("prompt") Integer prompt,
            @JsonProperty("security") Integer security,
            @JsonProperty("command") Integer command,
            @JsonProperty("tool_call") Integer toolCall,
            @JsonProperty("tool_result") Integer toolResult
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static final class ModelConfig {
        @JsonProperty("model_name")
        private String modelName;
        @JsonProperty("endpoint")
        private String endpointKey;
        @JsonProperty("max_tokens")
        private int maxTokens;
        @JsonProperty("max_context")
        private int maxContext;
        @JsonProperty("compact_threshold")
        private int compactThreshold;
        @JsonProperty("temperature")
        private double temperature;
        @JsonProperty("compaction_strategy")
        private String compactionStrategy;

        private Config config;

        public String modelName() { return modelName; }
        public String endpointKey() { return endpointKey; }
        public int maxTokens() { return maxTokens; }
        public int maxContext() { return maxContext; }
        public int compactThreshold() { return compactThreshold; }
        public double temperature() { return temperature; }
        public String compactionStrategy() { return compactionStrategy != null ? compactionStrategy : "truncate"; }

        public EndpointConfig endpoint() {
            EndpointConfig endpoint = config.endpoints().get(endpointKey);
            if (endpoint == null) {
                throw new IllegalStateException("Endpoint not found: " + endpointKey);
            }
            return endpoint;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static final class AgentConfig {
        @JsonProperty("system_prompt")
        private String systemPromptKey;
        @JsonProperty("model")
        private String modelKey;
        @JsonProperty("security")
        private String securityKey;
        @JsonProperty("colors")
        private String colorsKey;
        @JsonProperty("tools")
        private List<String> tools;
        @JsonProperty("color")
        private Integer color;
        @JsonProperty("cursor")
        private String cursor;
        @JsonProperty("cursor_color")
        private Integer cursorColor;

        private Config config;
        private String name;

        public String name() { return name; }
        public String systemPromptKey() { return systemPromptKey; }
        public String modelKey() { return modelKey; }
        public String securityKey() { return securityKey; }
        public String colorsKey() { return colorsKey; }
        public List<String> tools() { return tools == null ? List.of() : tools; }
        public Integer color() { return color; }
        public String cursor() { return cursor != null ? cursor : "Magenta|> "; }
        public Integer cursorColor() { return cursorColor; }

        public String systemPrompt() {
            String prompt = config.prompts().get(systemPromptKey);
            return config.resolvePrompt(prompt == null ? systemPromptKey : prompt);
        }

        public ModelConfig model() {
            ModelConfig model = config.models().get(modelKey);
            if (model == null) {
                throw new IllegalStateException("Model not found: " + modelKey);
            }
            return model;
        }

        public SecurityConfig security() {
            SecurityConfig security = config.securities().get(securityKey);
            if (security == null) {
                throw new IllegalStateException("Security config not found: " + securityKey);
            }
            return security;
        }

        public ColorsConfig colors() {
            if (colorsKey == null) {
                return null;
            }
            ColorsConfig colors = config.colorConfigs().get(colorsKey);
            if (colors == null) {
                throw new IllegalStateException("Colors config not found: " + colorsKey);
            }
            return colors;
        }
    }

    public String resolvePrompt(String promptOrPath) {
        if (promptOrPath == null) {
            return null;
        }
        if (promptOrPath.contains(" ")) {
            return promptOrPath;
        }

        boolean looksLikePath = promptOrPath.endsWith(".txt")
                || promptOrPath.endsWith(".md")
                || promptOrPath.startsWith("/")
                || promptOrPath.startsWith("./")
                || promptOrPath.contains(java.io.File.separator);

        if (!looksLikePath) {
            return promptOrPath;
        }

        Path promptPath = Path.of(promptOrPath);
        if (!Files.exists(promptPath) || !Files.isRegularFile(promptPath)) {
            Path fallbackPath = Path.of(System.getProperty("user.home"), ".magenta", promptOrPath);
            if (Files.exists(fallbackPath) && Files.isRegularFile(fallbackPath)) {
                promptPath = fallbackPath;
            }
        }

        if (!Files.exists(promptPath) || !Files.isRegularFile(promptPath)) {
            return promptOrPath;
        }

        try {
            return Files.readString(promptPath);
        } catch (IOException e) {
            return promptOrPath;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = EndpointConfig.Ollama.class, name = "ollama"),
            @JsonSubTypes.Type(value = EndpointConfig.OpenAI.class, name = "openai"),
            @JsonSubTypes.Type(value = EndpointConfig.Anthropic.class, name = "anthropic"),
            @JsonSubTypes.Type(value = EndpointConfig.RemoteAgent.class, name = "remote_agent")
    })
    public sealed interface EndpointConfig permits EndpointConfig.Ollama, EndpointConfig.OpenAI,
            EndpointConfig.Anthropic, EndpointConfig.RemoteAgent {

        int timeoutSeconds();

        record Ollama(String url, @JsonProperty("timeout_seconds") int timeoutSeconds) implements EndpointConfig { }

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

    public record DelegationTemplate(
            @JsonProperty("target_agent") String targetAgent,
            String title,
            @JsonProperty("prompt_template") String promptTemplate,
            @JsonProperty("expected_output") String expectedOutput
    ) { }
}
