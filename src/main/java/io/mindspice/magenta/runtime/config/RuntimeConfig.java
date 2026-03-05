package io.mindspice.magenta.runtime.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.knuddels.jtokkit.api.EncodingType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record RuntimeConfig(
        Path rootDir,
        Path workspaceRoot,
        String baseAgentId,
        String compactionAgentId,
        int maxTurns,
        int maxToolOutputBytes,
        int maxFileReadLines,
        int maxSqlRows,
        Map<String, ModelConfig> modelsById,
        Map<String, AgentConfig> agentsById,
        Map<String, String> promptsById,
        SecurityPolicyConfig security
) {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    private static final Path DEFAULT_PATH = Path.of("configs", "magenta.yaml");
    private static final String DEFAULT_TOKENIZER_ENCODING = "cl100k_base";
    private static final int DEFAULT_MAX_TOOL_OUTPUT_BYTES = 32_768;
    private static final int DEFAULT_MAX_FILE_READ_LINES = 200;
    private static final int DEFAULT_MAX_SQL_ROWS = 500;

    public RuntimeConfig {
        workspaceRoot = workspaceRoot == null ? Path.of("").toAbsolutePath().normalize() : workspaceRoot.toAbsolutePath().normalize();
        if (maxToolOutputBytes <= 0) {
            throw new IllegalStateException("instance.maxToolOutputBytes must be > 0");
        }
        if (maxFileReadLines <= 0) {
            throw new IllegalStateException("instance.maxFileReadLines must be > 0");
        }
        if (maxSqlRows <= 0) {
            throw new IllegalStateException("instance.maxSqlRows must be > 0");
        }
        modelsById = Map.copyOf(modelsById);
        agentsById = Map.copyOf(agentsById);
        promptsById = Map.copyOf(promptsById);
        security = Objects.requireNonNull(security, "security");
    }

    public static RuntimeConfig loadDefault() {
        return load(DEFAULT_PATH);
    }

    public static RuntimeConfig load(Path magentaYamlPath) {
        if (!Files.isRegularFile(magentaYamlPath)) {
            throw new IllegalStateException("Missing config file: " + magentaYamlPath.toAbsolutePath());
        }

        final RootDocument root;
        try {
            root = MAPPER.readValue(magentaYamlPath.toFile(), RootDocument.class);
        } catch (JsonProcessingException e) {
            throw parseException(magentaYamlPath, e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse config: " + magentaYamlPath.toAbsolutePath(), e);
        }

        Path configRoot = magentaYamlPath.toAbsolutePath().getParent();
        if (configRoot == null) {
            throw new IllegalStateException("Invalid config root path for: " + magentaYamlPath.toAbsolutePath());
        }

        Map<String, String> prompts = loadPrompts(configRoot, includePatterns(root.prompts));
        Map<String, ModelConfig> models = loadModels(configRoot, includePatterns(root.models));
        Map<String, AgentConfig> agents = loadAgents(configRoot, includePatterns(root.agents));

        String baseAgentId = Optional.ofNullable(root.instance)
                .map(InstanceConfig::baseAgentId)
                .filter(id -> !id.isBlank())
                .orElseGet(() -> firstEnabledAgent(agents));
        String compactionAgentId = Optional.ofNullable(root.instance)
                .map(InstanceConfig::compactionAgentId)
                .filter(id -> !id.isBlank())
                .orElse(baseAgentId);
        int maxTurns = Optional.ofNullable(root.instance)
                .map(InstanceConfig::maxTurns)
                .orElse(8);
        Path workspaceRoot = resolveWorkspaceRoot(configRoot, Optional.ofNullable(root.instance)
                .map(InstanceConfig::workspaceRoot)
                .orElse("."));
        int maxToolOutputBytes = Optional.ofNullable(root.instance)
                .map(InstanceConfig::maxToolOutputBytes)
                .orElse(DEFAULT_MAX_TOOL_OUTPUT_BYTES);
        int maxFileReadLines = Optional.ofNullable(root.instance)
                .map(InstanceConfig::maxFileReadLines)
                .orElse(DEFAULT_MAX_FILE_READ_LINES);
        int maxSqlRows = Optional.ofNullable(root.instance)
                .map(InstanceConfig::maxSqlRows)
                .orElse(DEFAULT_MAX_SQL_ROWS);

        validate(models, agents, prompts, baseAgentId, compactionAgentId);
        validateInstanceLimits(maxToolOutputBytes, maxFileReadLines, maxSqlRows);

        SecurityPolicyConfig securityConfig = toSecurityPolicyConfig(root.security);

        return new RuntimeConfig(
                configRoot,
                workspaceRoot,
                baseAgentId,
                compactionAgentId,
                maxTurns,
                maxToolOutputBytes,
                maxFileReadLines,
                maxSqlRows,
                models,
                agents,
                prompts,
                securityConfig
        );
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

    private static Path resolveWorkspaceRoot(Path configRoot, String workspaceRootText) {
        String effective = workspaceRootText == null || workspaceRootText.isBlank()
                ? "."
                : workspaceRootText.trim();
        Path workspacePath = Path.of(effective);
        if (!workspacePath.isAbsolute()) {
            workspacePath = configRoot.resolve(workspacePath);
        }
        return workspacePath.toAbsolutePath().normalize();
    }

    private static void validateInstanceLimits(int maxToolOutputBytes, int maxFileReadLines, int maxSqlRows) {
        if (maxToolOutputBytes <= 0) {
            throw new IllegalStateException("instance.maxToolOutputBytes must be > 0");
        }
        if (maxFileReadLines <= 0) {
            throw new IllegalStateException("instance.maxFileReadLines must be > 0");
        }
        if (maxSqlRows <= 0) {
            throw new IllegalStateException("instance.maxSqlRows must be > 0");
        }
    }

    private static List<String> includePatterns(IncludeSet includeSet) {
        return includeSet == null || includeSet.include == null ? List.of() : includeSet.include;
    }

    private static Map<String, ModelConfig> loadModels(Path root, List<String> patterns) {
        List<Path> files = resolveIncludes(root, patterns);
        Map<String, ModelConfig> output = new LinkedHashMap<>();
        Map<String, Path> sourcesById = new HashMap<>();
        for (Path file : files) {
            try {
                ModelConfig cfg = MAPPER.readValue(file.toFile(), ModelConfig.class);
                validateTokenizerEncoding(cfg.id(), cfg.tokenizerEncodingOrDefault());
                putUniqueOrThrow("model", cfg.id(), cfg, file, output, sourcesById);
            } catch (JsonProcessingException e) {
                throw parseException(file, e);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read model config: " + file, e);
            }
        }
        return output;
    }

    private static Map<String, AgentConfig> loadAgents(Path root, List<String> patterns) {
        List<Path> files = resolveIncludes(root, patterns);
        Map<String, AgentConfig> output = new LinkedHashMap<>();
        Map<String, Path> sourcesById = new HashMap<>();
        for (Path file : files) {
            try {
                AgentConfig cfg = MAPPER.readValue(file.toFile(), AgentConfig.class);
                putUniqueOrThrow("agent", cfg.id(), cfg, file, output, sourcesById);
            } catch (JsonProcessingException e) {
                throw parseException(file, e);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read agent config: " + file, e);
            }
        }
        return output;
    }

    private static Map<String, String> loadPrompts(Path root, List<String> patterns) {
        List<Path> files = resolveIncludes(root, patterns);
        Map<String, String> output = new LinkedHashMap<>();
        Map<String, Path> sourcesById = new HashMap<>();
        Path promptsRoot = root.resolve("prompts").normalize();

        for (Path file : files) {
            Path normalized = file.toAbsolutePath().normalize();
            String id = normalized.startsWith(promptsRoot.toAbsolutePath().normalize())
                    ? toPromptId(promptsRoot.relativize(normalized))
                    : toPromptId(root.relativize(normalized));
            try {
                putUniqueOrThrow("prompt", id, Files.readString(file), file, output, sourcesById);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read prompt: " + file, e);
            }
        }
        return output;
    }

    private static <T> void putUniqueOrThrow(
            String type,
            String id,
            T value,
            Path sourceFile,
            Map<String, T> output,
            Map<String, Path> sourcesById
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Invalid " + type + " id in " + sourceFile.toAbsolutePath() + ": blank or missing");
        }

        Path prior = sourcesById.putIfAbsent(id, sourceFile.toAbsolutePath());
        if (prior != null) {
            throw new IllegalStateException(
                    "Duplicate " + type + " id '" + id + "' in "
                            + prior + " and " + sourceFile.toAbsolutePath()
            );
        }
        output.put(id, value);
    }

    private static String toPromptId(Path relativePath) {
        String raw = relativePath.toString().replace('\\', '/');
        int dot = raw.lastIndexOf('.');
        if (dot > 0) {
            raw = raw.substring(0, dot);
        }
        return raw.replace('/', '.');
    }

    private static List<Path> resolveIncludes(Path root, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return List.of();
        }

        List<Path> matched = new ArrayList<>();
        for (String pattern : patterns) {
            try (Stream<Path> stream = Files.walk(root)) {
                var matcher = root.getFileSystem().getPathMatcher("glob:" + pattern);
                stream.filter(Files::isRegularFile)
                        .filter(path -> matcher.matches(root.relativize(path)))
                        .forEach(matched::add);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to resolve include pattern: " + pattern, e);
            }
        }
        return matched.stream()
                .distinct()
                .sorted(Comparator.comparing(Path::toString))
                .collect(Collectors.toList());
    }

    private static String firstEnabledAgent(Map<String, AgentConfig> agents) {
        return agents.values().stream()
                .filter(AgentConfig::enabled)
                .map(AgentConfig::id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No enabled agents found in configs/agents"));
    }

    private static void validateTokenizerEncoding(String modelId, String tokenizerEncoding) {
        String normalized = tokenizerEncoding.trim().replace('-', '_').toUpperCase();
        try {
            EncodingType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Unsupported tokenizerEncoding for model '" + modelId + "': " + tokenizerEncoding,
                    e
            );
        }
    }

    private static void validate(
            Map<String, ModelConfig> models,
            Map<String, AgentConfig> agents,
            Map<String, String> prompts,
            String baseAgentId,
            String compactionAgentId
    ) {
        if (models.isEmpty()) {
            throw new IllegalStateException("No models loaded from config includes");
        }
        if (agents.isEmpty()) {
            throw new IllegalStateException("No agents loaded from config includes");
        }

        AgentConfig base = agents.get(baseAgentId);
        if (base == null || !base.enabled()) {
            throw new IllegalStateException("Base agent is missing or disabled: " + baseAgentId);
        }

        AgentConfig compactor = agents.get(compactionAgentId);
        if (compactor == null || !compactor.enabled()) {
            throw new IllegalStateException("Compaction agent is missing or disabled: " + compactionAgentId);
        }

        for (AgentConfig agent : agents.values()) {
            if (!agent.enabled()) {
                continue;
            }
            ModelConfig model = models.get(agent.modelId());
            if (model == null || !model.enabled()) {
                throw new IllegalStateException("Enabled agent references missing/disabled model: " + agent.id() + " -> " + agent.modelId());
            }
            for (String promptId : agent.promptIds()) {
                if (!prompts.containsKey(promptId)) {
                    throw new IllegalStateException("Agent prompt id not found: " + agent.id() + " -> " + promptId);
                }
            }
        }
    }

    private static SecurityPolicyConfig toSecurityPolicyConfig(RawSecurityConfig rawConfig) {
        if (rawConfig == null) {
            return SecurityPolicyConfig.defaults();
        }

        return new SecurityPolicyConfig(
                parseSecurityMode(rawConfig.mode),
                Boolean.TRUE.equals(rawConfig.devYoloOverride),
                normalizeList(rawConfig.allowedPaths),
                normalizeList(rawConfig.allowedCommands),
                normalizeList(rawConfig.allowedTools),
                normalizeList(rawConfig.deniedTools),
                toWebAccessConfig(rawConfig.webAccess),
                toRuleConfigs(rawConfig.rules)
        );
    }

    private static WebAccessConfig toWebAccessConfig(RawWebAccessConfig rawWebAccessConfig) {
        if (rawWebAccessConfig == null) {
            return new WebAccessConfig(false, false);
        }
        return new WebAccessConfig(
                Boolean.TRUE.equals(rawWebAccessConfig.localEnabled),
                Boolean.TRUE.equals(rawWebAccessConfig.externalEnabled)
        );
    }

    private static List<SecurityRuleConfig> toRuleConfigs(List<RawSecurityRuleConfig> rawRules) {
        if (rawRules == null || rawRules.isEmpty()) {
            return List.of();
        }
        return rawRules.stream()
                .map(rawRule -> new SecurityRuleConfig(
                        normalizeOrDefault(rawRule.id, "rule"),
                        parseRuleAction(rawRule.action),
                        normalizeList(rawRule.commandPrefix),
                        normalizeOrDefault(rawRule.reason, "")
                ))
                .toList();
    }

    private static SecurityMode parseSecurityMode(String value) {
        String normalized = normalizeToken(value);
        return switch (normalized) {
            case "approveall", "allowall" -> SecurityMode.APPROVE_ALL;
            case "denyall" -> SecurityMode.DENY_ALL;
            case "blacklist" -> SecurityMode.BLACKLIST;
            case "whitelist", "denybydefault" -> SecurityMode.WHITELIST;
            case "prompt", "requireapproval" -> SecurityMode.PROMPT;
            case "" -> SecurityMode.BLACKLIST;
            default -> throw new IllegalStateException("Unsupported security mode: " + value);
        };
    }

    private static SecurityRuleAction parseRuleAction(String value) {
        String normalized = normalizeToken(value);
        return switch (normalized) {
            case "allow" -> SecurityRuleAction.ALLOW;
            case "deny" -> SecurityRuleAction.DENY;
            case "prompt" -> SecurityRuleAction.PROMPT;
            case "" -> SecurityRuleAction.DENY;
            default -> throw new IllegalStateException("Unsupported security rule action: " + value);
        };
    }

    private static String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
    }

    private static String normalizeOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static List<String> normalizeList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .distinct()
                .toList();
    }

    public enum SecurityMode {
        APPROVE_ALL,
        DENY_ALL,
        BLACKLIST,
        WHITELIST,
        PROMPT
    }

    public enum SecurityRuleAction {
        ALLOW,
        DENY,
        PROMPT
    }

    public record SecurityPolicyConfig(
            SecurityMode mode,
            boolean devYoloOverride,
            List<String> allowedPaths,
            List<String> allowedCommands,
            List<String> allowedTools,
            List<String> deniedTools,
            WebAccessConfig webAccess,
            List<SecurityRuleConfig> rules
    ) {
        public SecurityPolicyConfig {
            mode = mode == null ? SecurityMode.BLACKLIST : mode;
            allowedPaths = allowedPaths == null ? List.of() : List.copyOf(allowedPaths);
            allowedCommands = allowedCommands == null ? List.of() : List.copyOf(allowedCommands);
            allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
            deniedTools = deniedTools == null ? List.of() : List.copyOf(deniedTools);
            webAccess = webAccess == null ? new WebAccessConfig(false, false) : webAccess;
            rules = rules == null ? List.of() : List.copyOf(rules);
        }

        public static SecurityPolicyConfig defaults() {
            return new SecurityPolicyConfig(
                    SecurityMode.BLACKLIST,
                    false,
                    List.of("."),
                    List.of(),
                    List.of(),
                    List.of(),
                    new WebAccessConfig(false, false),
                    List.of()
            );
        }
    }

    public record WebAccessConfig(
            boolean localEnabled,
            boolean externalEnabled
    ) {
    }

    public record SecurityRuleConfig(
            String id,
            SecurityRuleAction action,
            List<String> commandPrefix,
            String reason
    ) {
        public SecurityRuleConfig {
            id = id == null || id.isBlank() ? "rule" : id.trim();
            action = action == null ? SecurityRuleAction.DENY : action;
            commandPrefix = commandPrefix == null ? List.of() : List.copyOf(commandPrefix);
            reason = reason == null ? "" : reason;
        }
    }

    public record ModelConfig(
            @JsonProperty("id") String id,
            @JsonProperty("provider") String provider,
            @JsonProperty("model") String model,
            @JsonProperty("endpoint") String endpoint,
            @JsonProperty("maxTokens") int maxTokens,
            @JsonProperty("maxContext") int maxContext,
            @JsonProperty("compactThreshold") int compactThreshold,
            @JsonProperty("temperature") double temperature,
            @JsonProperty("compactionStrategy") String compactionStrategy,
            @JsonProperty("tokenizerEncoding") String tokenizerEncoding,
            @JsonProperty("supportsToolCalling") boolean supportsToolCalling,
            @JsonProperty("supportsStreaming") boolean supportsStreaming,
            @JsonProperty("enabled") boolean enabled
    ) {
        public ModelConfig {
            Objects.requireNonNull(id, "model.id");
            Objects.requireNonNull(provider, "model.provider");
            Objects.requireNonNull(model, "model.model");
            Objects.requireNonNull(endpoint, "model.endpoint");
        }

        public String compactionStrategyOrDefault() {
            return compactionStrategy == null || compactionStrategy.isBlank() ? "rolling_window" : compactionStrategy;
        }

        public String tokenizerEncodingOrDefault() {
            return tokenizerEncoding == null || tokenizerEncoding.isBlank() ? DEFAULT_TOKENIZER_ENCODING : tokenizerEncoding;
        }
    }

    public record AgentConfig(
            @JsonProperty("id") String id,
            @JsonProperty("modelId") String modelId,
            @JsonProperty("promptIds") List<String> promptIds,
            @JsonProperty("taskIds") List<String> taskIds,
            @JsonProperty("workflowIds") List<String> workflowIds,
            @JsonProperty("toolIds") List<String> toolIds,
            @JsonProperty("enabled") boolean enabled
    ) {
        public AgentConfig {
            Objects.requireNonNull(id, "agent.id");
            Objects.requireNonNull(modelId, "agent.modelId");
            promptIds = promptIds == null ? List.of() : List.copyOf(promptIds);
            taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
            workflowIds = workflowIds == null ? List.of() : List.copyOf(workflowIds);
            toolIds = toolIds == null ? List.of() : List.copyOf(toolIds);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class RootDocument {
        @JsonProperty("instance")
        private InstanceConfig instance;
        @JsonProperty("security")
        private RawSecurityConfig security;
        @JsonProperty("models")
        private IncludeSet models;
        @JsonProperty("agents")
        private IncludeSet agents;
        @JsonProperty("prompts")
        private IncludeSet prompts;
        @JsonProperty("tasks")
        private IncludeSet tasks;
        @JsonProperty("workflows")
        private IncludeSet workflows;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class InstanceConfig {
        @JsonProperty("workspaceRoot")
        private String workspaceRoot;
        @JsonProperty("dataRoot")
        private String dataRoot;
        @JsonProperty("debugJsonl")
        private String debugJsonl;
        @JsonProperty("localDevMode")
        private Boolean localDevMode;
        @JsonProperty("baseAgentId")
        private String baseAgentId;
        @JsonProperty("compactionAgentId")
        private String compactionAgentId;
        @JsonProperty("maxTurns")
        private Integer maxTurns;
        @JsonProperty("maxToolOutputBytes")
        private Integer maxToolOutputBytes;
        @JsonProperty("maxFileReadLines")
        private Integer maxFileReadLines;
        @JsonProperty("maxSqlRows")
        private Integer maxSqlRows;
        @JsonProperty("maxEssenceBodyBytes")
        private Integer maxEssenceBodyBytes;

        private String baseAgentId() { return baseAgentId; }

        private String compactionAgentId() { return compactionAgentId; }

        private Integer maxTurns() { return maxTurns; }

        private String workspaceRoot() { return workspaceRoot; }

        private Integer maxToolOutputBytes() { return maxToolOutputBytes; }

        private Integer maxFileReadLines() { return maxFileReadLines; }

        private Integer maxSqlRows() { return maxSqlRows; }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class RawSecurityConfig {
        @JsonProperty("mode")
        private String mode;
        @JsonProperty("devYoloOverride")
        private Boolean devYoloOverride;
        @JsonProperty("allowedPaths")
        private List<String> allowedPaths;
        @JsonProperty("allowedCommands")
        private List<String> allowedCommands;
        @JsonProperty("allowedTools")
        private List<String> allowedTools;
        @JsonProperty("deniedTools")
        private List<String> deniedTools;
        @JsonProperty("webAccess")
        private RawWebAccessConfig webAccess;
        @JsonProperty("rules")
        private List<RawSecurityRuleConfig> rules;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class RawWebAccessConfig {
        @JsonProperty("localEnabled")
        private Boolean localEnabled;
        @JsonProperty("externalEnabled")
        private Boolean externalEnabled;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class RawSecurityRuleConfig {
        @JsonProperty("id")
        private String id;
        @JsonProperty("action")
        private String action;
        @JsonProperty("commandPrefix")
        private List<String> commandPrefix;
        @JsonProperty("reason")
        private String reason;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private static final class IncludeSet {
        @JsonProperty("include")
        private List<String> include;
    }
}
