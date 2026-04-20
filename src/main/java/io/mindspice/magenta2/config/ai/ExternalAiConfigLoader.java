package io.mindspice.magenta2.config.ai;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class ExternalAiConfigLoader {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private ExternalAiConfigLoader() {
    }

    public static AiConfig load(Path path) throws IOException {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            return YAML_MAPPER.readValue(path.toFile(), AiConfig.class);
        }
        if (fileName.endsWith(".json")) {
            return JSON_MAPPER.readValue(path.toFile(), AiConfig.class);
        }
        throw new IllegalArgumentException("Unsupported config format: " + fileName);
    }
}
