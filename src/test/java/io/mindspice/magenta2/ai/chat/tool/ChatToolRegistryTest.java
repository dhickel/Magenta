package io.mindspice.magenta2.ai.chat.tool;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.tool.file.AgentFileToolConfiguration;
import io.mindspice.magenta2.ai.chat.tool.file.AgentFileToolService;
import io.mindspice.magenta2.ai.chat.tool.file.AgentFileTools;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatToolRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesMethodToolProviderCallbacksByApprovedName() throws IOException {
        AgentFileToolService service = new AgentFileToolService(aiConfig());
        AgentFileTools tools = new AgentFileTools(service, new ObjectMapper());
        ToolCallbackProvider provider = new AgentFileToolConfiguration().agentFileToolCallbackProvider(tools);
        ChatToolRegistry registry = new ChatToolRegistry(List.of(), List.of(provider));

        assertThat(registry.resolveApprovedTools(List.of("file_read", "file_replace")))
            .extracting(callback -> callback.getToolDefinition().name())
            .containsExactly("file_read", "file_replace");
    }

    @Test
    void exposesFileToolsWithModelVisibleDescriptionsAndArgumentSchemas() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentFileToolService service = new AgentFileToolService(aiConfig());
        AgentFileTools tools = new AgentFileTools(service, objectMapper);
        ToolCallbackProvider provider = new AgentFileToolConfiguration().agentFileToolCallbackProvider(tools);
        Map<String, ToolCallback> callbacks = Arrays.stream(provider.getToolCallbacks())
            .collect(Collectors.toMap(callback -> callback.getToolDefinition().name(), Function.identity()));

        assertThat(callbacks.keySet())
            .containsExactlyInAnyOrder("file_list", "file_read", "file_search", "file_write", "file_replace");

        assertTool(callbacks.get("file_list"), objectMapper, List.of(), "path", "recursive", "maxEntries");
        assertTool(callbacks.get("file_read"), objectMapper, List.of("path"), "path", "startLine", "maxLines");
        assertTool(
            callbacks.get("file_search"),
            objectMapper,
            List.of("query"),
            "path",
            "query",
            "regex",
            "caseSensitive",
            "contextLines",
            "maxMatches"
        );
        assertTool(callbacks.get("file_write"), objectMapper, List.of("path", "content"), "path", "content", "overwrite");
        assertTool(
            callbacks.get("file_replace"),
            objectMapper,
            List.of("path", "startAnchor", "replacement"),
            "path",
            "startAnchor",
            "endAnchor",
            "replacement"
        );

        assertThat(callbacks.get("file_list").getToolDefinition().description())
            .contains("List files and directories")
            .contains("Use this before reading");
        assertThat(callbacks.get("file_replace").getToolDefinition().description())
            .contains("lineNumber:hash anchors")
            .contains("avoid stale edits");
    }

    @Test
    void rejectsUnknownApprovedToolNames() {
        ChatToolRegistry registry = new ChatToolRegistry(List.of(), List.of());

        assertThatThrownBy(() -> registry.resolveApprovedTools(List.of("missing_tool")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing_tool");
    }

    private AiConfig aiConfig() {
        return new AiConfig(
            "magenta",
            "magenta",
            10,
            tempDir,
            Map.of(),
            Map.of("magenta", new AgentConfig("model", "prompt", List.of()))
        );
    }

    private void assertTool(
        ToolCallback callback,
        ObjectMapper objectMapper,
        List<String> requiredProperties,
        String... expectedProperties
    ) throws Exception {
        assertThat(callback).isNotNull();
        assertThat(callback.getToolDefinition().description()).isNotBlank();

        JsonNode schema = objectMapper.readTree(callback.getToolDefinition().inputSchema());
        JsonNode properties = schema.path("properties");
        assertThat(properties.fieldNames()).toIterable().containsExactlyInAnyOrder(expectedProperties);
        for (String property : expectedProperties) {
            assertThat(properties.path(property).path("description").asText())
                .as(callback.getToolDefinition().name() + "." + property + " description")
                .isNotBlank();
        }
        assertThat(StreamSupport.stream(schema.path("required").spliterator(), false).map(JsonNode::asText).toList())
            .containsExactlyInAnyOrderElementsOf(requiredProperties);
    }
}
