package io.mindspice.magenta.runtime.model;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.model.OllamaClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaClientPayloadSerializationTest {

    @Test
    void serializesAssistantToolCallsIntoPayloadHistory() throws Exception {
        OllamaClient client = new OllamaClient();
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("read_file")
                .arguments("{\"path\":\"/tmp/test.txt\"}")
                .build();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(AiMessage.from("running tool", List.of(request))))
                .build();

        JsonNode payload = invokeToOllamaPayload(client, modelConfig(), chatRequest, false);
        JsonNode message = payload.path("messages").get(0);

        assertThat(message.path("role").asText()).isEqualTo("assistant");
        assertThat(message.path("tool_calls").isArray()).isTrue();
        assertThat(message.path("tool_calls")).hasSize(1);
        JsonNode toolCall = message.path("tool_calls").get(0);
        assertThat(toolCall.path("id").asText()).isEqualTo("call-1");
        assertThat(toolCall.path("type").asText()).isEqualTo("function");
        assertThat(toolCall.path("function").path("name").asText()).isEqualTo("read_file");
        assertThat(toolCall.path("function").path("arguments").path("path").asText()).isEqualTo("/tmp/test.txt");
    }

    @Test
    void serializesToolCallIdForToolMessages() throws Exception {
        OllamaClient client = new OllamaClient();
        ChatMessage toolMessage = ToolExecutionResultMessage.from("call-77", "search_replace", "{\"status\":\"ok\"}");
        ChatRequest chatRequest = ChatRequest.builder().messages(List.of(toolMessage)).build();

        JsonNode payload = invokeToOllamaPayload(client, modelConfig(), chatRequest, false);
        JsonNode message = payload.path("messages").get(0);

        assertThat(message.path("role").asText()).isEqualTo("tool");
        assertThat(message.path("name").asText()).isEqualTo("search_replace");
        assertThat(message.path("tool_call_id").asText()).isEqualTo("call-77");
    }

    private static JsonNode invokeToOllamaPayload(
            OllamaClient client,
            RuntimeConfig.ModelConfig modelConfig,
            ChatRequest request,
            boolean stream
    ) throws Exception {
        Method method = OllamaClient.class.getDeclaredMethod(
                "toOllamaPayload",
                RuntimeConfig.ModelConfig.class,
                ChatRequest.class,
                boolean.class
        );
        method.setAccessible(true);
        return (JsonNode) method.invoke(client, modelConfig, request, stream);
    }

    private static RuntimeConfig.ModelConfig modelConfig() {
        return new RuntimeConfig.ModelConfig(
                "model-default",
                "langchain4j",
                "dummy",
                "http://localhost:11434",
                2048,
                2048,
                1800,
                0.0,
                "rolling_window",
                "cl100k_base",
                true,
                false,
                true
        );
    }
}
