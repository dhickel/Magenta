package io.mindspice.magenta.runtime.model;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiClientPayloadSerializationTest {

    @Test
    void serializesAssistantToolCallsIntoPayloadHistory() throws Exception {
        OpenAiClient client = new OpenAiClient();
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("read_file")
                .arguments("{\"path\":\"/tmp/test.txt\"}")
                .build();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(AiMessage.from("running tool", List.of(request))))
                .build();

        JsonNode payload = invokeToOpenAiPayload(client, modelConfig(), chatRequest, false);
        JsonNode message = payload.path("messages").get(0);

        assertThat(message.path("role").asText()).isEqualTo("assistant");
        assertThat(message.path("tool_calls").isArray()).isTrue();
        assertThat(message.path("tool_calls")).hasSize(1);
        JsonNode toolCall = message.path("tool_calls").get(0);
        assertThat(toolCall.path("id").asText()).isEqualTo("call-1");
        assertThat(toolCall.path("type").asText()).isEqualTo("function");
        assertThat(toolCall.path("function").path("name").asText()).isEqualTo("read_file");
        assertThat(toolCall.path("function").path("arguments").asText()).isEqualTo("{\"path\":\"/tmp/test.txt\"}");
    }

    @Test
    void serializesToolCallIdForToolMessages() throws Exception {
        OpenAiClient client = new OpenAiClient();
        ChatMessage toolMessage = ToolExecutionResultMessage.from("call-77", "search_replace", "{\"status\":\"ok\"}");
        ChatRequest chatRequest = ChatRequest.builder().messages(List.of(toolMessage)).build();

        JsonNode payload = invokeToOpenAiPayload(client, modelConfig(), chatRequest, false);
        JsonNode message = payload.path("messages").get(0);

        assertThat(message.path("role").asText()).isEqualTo("tool");
        assertThat(message.path("tool_call_id").asText()).isEqualTo("call-77");
    }

    @Test
    void serializesToolSpecificationsIntoOpenAiToolsPayload() throws Exception {
        OpenAiClient client = new OpenAiClient();
        ToolSpecification readFileSpec = ToolSpecification.builder()
                .name("read_file")
                .description("Read a file")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("path", "Target path")
                        .addProperty("startLine", JsonIntegerSchema.builder().description("Optional start line").build())
                        .required(List.of("path"))
                        .build())
                .build();
        ToolSpecification sqliteExecSpec = ToolSpecification.builder()
                .name("sqlite_exec")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("dbPath", "Database path")
                        .addProperty("sql", JsonStringSchema.builder().description("SQL text").build())
                        .required(List.of("dbPath", "sql"))
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(AiMessage.from("ready")))
                .toolSpecifications(readFileSpec, sqliteExecSpec)
                .build();

        JsonNode payload = invokeToOpenAiPayload(client, modelConfig(), chatRequest, false);
        JsonNode tools = payload.path("tools");

        assertThat(tools.isArray()).isTrue();
        assertThat(tools).hasSize(2);
        assertThat(payload.path("tool_choice").asText()).isEqualTo("auto");

        JsonNode first = tools.get(0);
        assertThat(first.path("type").asText()).isEqualTo("function");
        assertThat(first.path("function").path("name").asText()).isEqualTo("read_file");
        assertThat(first.path("function").path("description").asText()).isEqualTo("Read a file");
        assertThat(first.path("function").path("parameters").path("type").asText()).isEqualTo("object");
        assertThat(first.path("function").path("parameters").path("properties").path("path").path("type").asText())
                .isEqualTo("string");
    }

    private static JsonNode invokeToOpenAiPayload(
            OpenAiClient client,
            RuntimeConfig.ModelConfig modelConfig,
            ChatRequest request,
            boolean stream
    ) throws Exception {
        Method method = OpenAiClient.class.getDeclaredMethod(
                "toOpenAiPayload",
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
                "openai",
                "dummy",
                "http://localhost:8080",
                2048,
                2048,
                1800,
                0.0,
                "rolling_window",
                "cl100k_base",
                true,
                true,
                true
        );
    }
}
