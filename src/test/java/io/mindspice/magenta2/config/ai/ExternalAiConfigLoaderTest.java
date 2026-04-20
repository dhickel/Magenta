package io.mindspice.magenta2.config.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalAiConfigLoaderTest {

    @Test
    void loadsExternalYamlIntoConfigRecords(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("ai-config.yaml");
        Files.writeString(yaml, """
            defaultAgent: support
            models:
              local-fast:
                remoteModelName: qwen3:8b
                remoteEndpoint: http://localhost:11434
                endpointType: OLLAMA
                contextLength: 8192
              remote-large:
                remoteModelName: gpt-4o-mini
                remoteEndpoint: https://api.example.com/v1
                endpointType: OPENAI_COMPATIBLE
                contextLength: 128000
            agents:
              support:
                model: local-fast
                systemPrompt: You are support.
                approvedTools: []
              planner:
                model: remote-large
                systemPrompt: You are a planner.
                approvedTools:
                  - web_search
                  - sql_query
            """);

        AiConfig config = ExternalAiConfigLoader.load(yaml);

        assertNotNull(config);
        assertNotNull(config.models());
        assertNotNull(config.agents());
        assertEquals("support", config.defaultAgent());
        assertEquals(2, config.models().size());
        assertEquals(2, config.agents().size());

        ModelConfig localFast = config.models().get("local-fast");
        assertNotNull(localFast);
        assertEquals("qwen3:8b", localFast.remoteModelName());
        assertEquals("http://localhost:11434", localFast.remoteEndpoint());
        assertEquals(EndpointType.OLLAMA, localFast.endpointType());
        assertEquals(8192, localFast.contextLength());

        AgentConfig support = config.agents().get("support");
        assertNotNull(support);
        assertEquals("local-fast", support.model());
        assertEquals("You are support.", support.systemPrompt());
        assertNotNull(support.approvedTools());
        assertTrue(support.approvedTools().isEmpty());
    }

    @Test
    void loadsExternalJsonIntoConfigRecords(@TempDir Path tempDir) throws IOException {
        Path json = tempDir.resolve("ai-config.json");
        Files.writeString(json, """
            {
              "defaultAgent": "magenta",
              "models": {
                "local-qwen": {
                  "remoteModelName": "qwen3.6-35b-a3b-text-ctx32k",
                  "remoteEndpoint": "http://192.168.1.112:11434",
                  "endpointType": "OLLAMA",
                  "contextLength": 32768
                }
              },
              "agents": {
                "magenta": {
                  "model": "local-qwen",
                  "systemPrompt": "You are Magenta, a practical system administration assistant.",
                  "approvedTools": []
                }
              }
            }
            """);

        AiConfig config = ExternalAiConfigLoader.load(json);

        assertNotNull(config);
        assertNotNull(config.models());
        assertNotNull(config.agents());
        assertEquals("magenta", config.defaultAgent());
        assertEquals(1, config.models().size());
        assertEquals(1, config.agents().size());
        assertEquals("local-qwen", config.agents().get("magenta").model());
        assertEquals(EndpointType.OLLAMA, config.models().get("local-qwen").endpointType());
    }
}
