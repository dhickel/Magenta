package io.mindspice.magenta2.ai.config.user;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.util.StringUtils;

public final class ExternalAiConfigLoader {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private ExternalAiConfigLoader() {
    }

    public static AiConfig load(Path path) throws IOException {
        String fileName = path.getFileName().toString().toLowerCase();
        AiConfig config;
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            config = YAML_MAPPER.readValue(path.toFile(), AiConfig.class);
        } else if (fileName.endsWith(".json")) {
            config = JSON_MAPPER.readValue(path.toFile(), AiConfig.class);
        } else {
            throw new IllegalArgumentException("Unsupported config format: " + fileName);
        }
        return validate(resolveSystemPrompts(path, config));
    }

    private static AiConfig resolveSystemPrompts(Path configPath, AiConfig config) throws IOException {
        if (config == null || config.agents() == null) {
            return config;
        }
        Path configDirectory = configPath.toAbsolutePath().normalize().getParent();
        Map<String, AgentConfig> agents = config.agents().entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> resolveSystemPrompt(configDirectory, entry.getKey(), entry.getValue())
            ));
        return new AiConfig(
            config.defaultAgent(),
            config.summarizationAgent(),
            config.contextBufferPercent(),
            config.dataRoot(),
            config.webSearch(),
            config.models(),
            agents
        );
    }

    private static AiConfig validate(AiConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("AI config is required");
        }
        if (config.agents() == null || config.agents().isEmpty()) {
            throw new IllegalArgumentException("AI config must define agents");
        }
        if (config.models() == null || config.models().isEmpty()) {
            throw new IllegalArgumentException("AI config must define models");
        }
        if (!StringUtils.hasText(config.summarizationAgent())) {
            throw new IllegalArgumentException("AI config must define summarizationAgent");
        }
        AgentConfig summarizationAgent = config.agents().get(config.summarizationAgent());
        if (summarizationAgent == null) {
            throw new IllegalArgumentException(
                "summarizationAgent does not match a configured agent: " + config.summarizationAgent()
            );
        }
        if (!StringUtils.hasText(summarizationAgent.model())) {
            throw new IllegalArgumentException(
                "summarizationAgent '" + config.summarizationAgent() + "' must configure a model"
            );
        }
        ModelConfig summarizationModel = config.models().get(summarizationAgent.model());
        if (summarizationModel == null) {
            throw new IllegalArgumentException(
                "summarizationAgent '" + config.summarizationAgent() + "' references missing model: "
                    + summarizationAgent.model()
            );
        }
        if (summarizationModel.contextLength() == null || summarizationModel.contextLength() <= 0) {
            throw new IllegalArgumentException(
                "summarizationAgent '" + config.summarizationAgent() + "' model must define a positive contextLength"
            );
        }
        int bufferPercent = config.resolvedContextBufferPercent();
        if (bufferPercent < 1 || bufferPercent > 50) {
            throw new IllegalArgumentException("contextBufferPercent must be between 1 and 50");
        }
        return config;
    }

    private static AgentConfig resolveSystemPrompt(Path configDirectory, String agentName, AgentConfig agentConfig) {
        if (agentConfig == null || !StringUtils.hasText(agentConfig.systemPrompt())) {
            throw new IllegalArgumentException("Agent '" + agentName + "' must configure a systemPrompt file path");
        }

        Path promptPath = Path.of(agentConfig.systemPrompt());
        Path resolvedPromptPath = promptPath.isAbsolute()
            ? promptPath.normalize()
            : configDirectory.resolve(promptPath).normalize();
        if (!Files.isRegularFile(resolvedPromptPath)) {
            throw new IllegalArgumentException(
                "Agent '" + agentName + "' systemPrompt file does not exist: " + resolvedPromptPath
            );
        }

        try {
            return new AgentConfig(
                agentConfig.model(),
                Files.readString(resolvedPromptPath),
                agentConfig.approvedTools(),
                agentConfig.allowedShellCommands()
            );
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "Agent '" + agentName + "' systemPrompt file cannot be read: " + resolvedPromptPath,
                e
            );
        }
    }
}
