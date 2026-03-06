package io.mindspice.magenta.runtime.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.mindspice.magenta.runtime.config.RuntimeConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class OllamaClient {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    public ChatResponse chatBlocking(RuntimeConfig.ModelConfig modelConfig, ChatRequest request) {
        String baseUrl = resolveBaseUrl(modelConfig.endpoint());
        JsonNode payload = toOllamaPayload(modelConfig, request, false);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                throw new IllegalStateException("Ollama chat failed with status " + response.statusCode() + ": " + response.body());
            }
            JsonNode body = json.readTree(response.body());
            return toChatResponse(modelConfig, body, null);
        } catch (IOException e) {
            throw new IllegalStateException("Ollama chat request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama chat request interrupted", e);
        }
    }

    public ChatResponse chatStreaming(RuntimeConfig.ModelConfig modelConfig, ChatRequest request, Consumer<String> tokenCallback) {
        String baseUrl = resolveBaseUrl(modelConfig.endpoint());
        JsonNode payload = toOllamaPayload(modelConfig, request, true);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("Ollama stream failed with status " + response.statusCode() + ": " + body);
            }

            StringBuilder completeText = new StringBuilder();
            JsonNode finalNode = null;
            JsonNode streamedToolCalls = null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode node = json.readTree(line);
                    JsonNode message = node.path("message");
                    String partial = message.path("content").asText("");
                    if (!partial.isBlank()) {
                        completeText.append(partial);
                        tokenCallback.accept(partial);
                    }
                    JsonNode toolCalls = message.path("tool_calls");
                    if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                        streamedToolCalls = toolCalls.deepCopy();
                    }
                    if (node.path("done").asBoolean(false)) {
                        finalNode = node;
                        break;
                    }
                }
            }

            if (finalNode == null) {
                finalNode = json.createObjectNode()
                        .put("model", modelConfig.model())
                        .set("message", json.createObjectNode().put("content", completeText.toString()));
            }
            if ((finalNode.path("message").path("tool_calls").isMissingNode()
                 || finalNode.path("message").path("tool_calls").isEmpty())
                && streamedToolCalls != null) {
                ObjectNode finalMessage = finalNode.path("message").isObject()
                        ? (ObjectNode) finalNode.path("message")
                        : json.createObjectNode();
                finalMessage.set("tool_calls", streamedToolCalls);
                if (!finalNode.path("message").isObject()) {
                    ((ObjectNode) finalNode).set("message", finalMessage);
                }
            }

            return toChatResponse(modelConfig, finalNode, completeText.toString());
        } catch (Exception e) {
            throw new IllegalStateException("Ollama streaming request failed", e);
        }
    }

    private String resolveBaseUrl(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "http://localhost:11434";
        }
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }

        String env = System.getenv("MAGENTA_OLLAMA_URL");
        if (env != null && !env.isBlank()) {
            return env;
        }

        return "http://localhost:11434";
    }

    private JsonNode toOllamaPayload(RuntimeConfig.ModelConfig modelCfg, ChatRequest request, boolean stream) {
        var root = json.createObjectNode();
        root.put("model", modelCfg.model());
        root.put("stream", stream);

        var messages = json.createArrayNode();
        for (ChatMessage message : request.messages()) {
            var msg = json.createObjectNode();
            switch (message) {
                case SystemMessage sys -> {
                    msg.put("role", "system");
                    msg.put("content", sys.text());
                }
                case UserMessage user -> {
                    msg.put("role", "user");
                    msg.put("content", user.singleText());
                }
                case AiMessage ai -> {
                    msg.put("role", "assistant");
                    msg.put("content", ai.text() == null ? "" : ai.text());
                    if (ai.hasToolExecutionRequests()) {
                        msg.set("tool_calls", toToolCallsJson(ai.toolExecutionRequests()));
                    }
                }
                case ToolExecutionResultMessage tool -> {
                    msg.put("role", "tool");
                    msg.put("content", tool.text());
                    msg.put("name", tool.toolName());
                    msg.put("tool_call_id", tool.id());
                }
                default -> {
                    msg.put("role", "user");
                    msg.put("content", message.toString());
                }
            }
            messages.add(msg);
        }
        root.set("messages", messages);
        if (request.toolSpecifications() != null && !request.toolSpecifications().isEmpty()) {
            root.set("tools", toToolSpecificationsJson(request.toolSpecifications()));
        }

        var options = json.createObjectNode();
        options.put("temperature", modelCfg.temperature());
        options.put("num_ctx", modelCfg.maxContext());
        root.set("options", options);

        return root;
    }

    private ArrayNode toToolSpecificationsJson(List<ToolSpecification> specifications) {
        ArrayNode tools = json.createArrayNode();
        if (specifications == null || specifications.isEmpty()) {
            return tools;
        }

        for (ToolSpecification specification : specifications) {
            if (specification == null || specification.name() == null || specification.name().isBlank()) {
                continue;
            }

            ObjectNode toolNode = json.createObjectNode();
            toolNode.put("type", "function");

            ObjectNode functionNode = json.createObjectNode();
            functionNode.put("name", specification.name());
            if (specification.description() != null && !specification.description().isBlank()) {
                functionNode.put("description", specification.description());
            }
            if (specification.parameters() != null) {
                functionNode.set("parameters", toJsonSchemaElement(specification.parameters()));
            }
            toolNode.set("function", functionNode);
            tools.add(toolNode);
        }
        return tools;
    }

    private JsonNode toJsonSchemaElement(JsonSchemaElement element) {
        if (element == null) {
            return json.createObjectNode();
        }
        return switch (element) {
            case JsonObjectSchema objectSchema -> toJsonObjectSchema(objectSchema);
            case JsonArraySchema arraySchema -> {
                ObjectNode node = baseTypedNode("array", arraySchema.description());
                if (arraySchema.items() != null) {
                    node.set("items", toJsonSchemaElement(arraySchema.items()));
                }
                yield node;
            }
            case JsonStringSchema stringSchema -> baseTypedNode("string", stringSchema.description());
            case JsonIntegerSchema integerSchema -> baseTypedNode("integer", integerSchema.description());
            case JsonNumberSchema numberSchema -> baseTypedNode("number", numberSchema.description());
            case JsonBooleanSchema booleanSchema -> baseTypedNode("boolean", booleanSchema.description());
            case JsonEnumSchema enumSchema -> {
                ObjectNode node = baseTypedNode("string", enumSchema.description());
                ArrayNode enumValues = json.createArrayNode();
                if (enumSchema.enumValues() != null) {
                    enumSchema.enumValues().forEach(enumValues::add);
                }
                node.set("enum", enumValues);
                yield node;
            }
            case JsonAnyOfSchema anyOfSchema -> {
                ObjectNode node = json.createObjectNode();
                if (anyOfSchema.description() != null && !anyOfSchema.description().isBlank()) {
                    node.put("description", anyOfSchema.description());
                }
                ArrayNode anyOf = json.createArrayNode();
                if (anyOfSchema.anyOf() != null) {
                    anyOfSchema.anyOf().forEach(entry -> anyOf.add(toJsonSchemaElement(entry)));
                }
                node.set("anyOf", anyOf);
                yield node;
            }
            case JsonReferenceSchema referenceSchema -> {
                ObjectNode node = json.createObjectNode();
                node.put("$ref", referenceSchema.reference());
                if (referenceSchema.description() != null && !referenceSchema.description().isBlank()) {
                    node.put("description", referenceSchema.description());
                }
                yield node;
            }
            case JsonNullSchema nullSchema -> baseTypedNode("null", nullSchema.description());
            default -> json.createObjectNode();
        };
    }

    private ObjectNode toJsonObjectSchema(JsonObjectSchema objectSchema) {
        ObjectNode node = baseTypedNode("object", objectSchema.description());

        ObjectNode properties = json.createObjectNode();
        if (objectSchema.properties() != null) {
            objectSchema.properties().forEach((name, child) -> properties.set(name, toJsonSchemaElement(child)));
        }
        node.set("properties", properties);

        if (objectSchema.required() != null && !objectSchema.required().isEmpty()) {
            ArrayNode required = json.createArrayNode();
            objectSchema.required().forEach(required::add);
            node.set("required", required);
        }
        if (objectSchema.additionalProperties() != null) {
            node.put("additionalProperties", objectSchema.additionalProperties());
        }
        if (objectSchema.definitions() != null && !objectSchema.definitions().isEmpty()) {
            ObjectNode definitions = json.createObjectNode();
            objectSchema.definitions().forEach((name, child) -> definitions.set(name, toJsonSchemaElement(child)));
            node.set("definitions", definitions);
        }
        return node;
    }

    private ObjectNode baseTypedNode(String type, String description) {
        ObjectNode node = json.createObjectNode();
        node.put("type", type);
        if (description != null && !description.isBlank()) {
            node.put("description", description);
        }
        return node;
    }

    private JsonNode toToolCallsJson(List<ToolExecutionRequest> requests) {
        var toolCalls = json.createArrayNode();
        if (requests == null || requests.isEmpty()) {
            return toolCalls;
        }

        for (ToolExecutionRequest request : requests) {
            if (request == null || request.name() == null || request.name().isBlank()) {
                continue;
            }

            var toolCall = json.createObjectNode();
            String id = request.id();
            toolCall.put("id", id == null || id.isBlank() ? UUID.randomUUID().toString() : id);
            toolCall.put("type", "function");

            var function = json.createObjectNode();
            function.put("name", request.name());
            String args = request.arguments();
            if (args == null || args.isBlank()) {
                function.set("arguments", json.createObjectNode());
            } else {
                try {
                    function.set("arguments", json.readTree(args));
                } catch (IOException e) {
                    function.put("arguments", args);
                }
            }
            toolCall.set("function", function);
            toolCalls.add(toolCall);
        }
        return toolCalls;
    }

    private ChatResponse toChatResponse(RuntimeConfig.ModelConfig modelCfg, JsonNode body, String streamedContent) {
        JsonNode message = body.path("message");
        String text = streamedContent == null
                ? message.path("content").asText("")
                : streamedContent;

        List<ToolExecutionRequest> toolRequests = parseToolRequests(message.path("tool_calls"));
        AiMessage aiMessage = toolRequests.isEmpty()
                ? AiMessage.from(text)
                : AiMessage.from(text, toolRequests);

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .modelName(modelCfg.model())
                .tokenUsage(new TokenUsage(
                        nullableInt(body.path("prompt_eval_count")),
                        nullableInt(body.path("eval_count"))
                ))
                .build();
    }

    private Integer nullableInt(JsonNode node) {
        return node != null && node.isInt() ? node.asInt() : null;
    }

    private List<ToolExecutionRequest> parseToolRequests(JsonNode toolCallsNode) {
        if (toolCallsNode == null || !toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
            return List.of();
        }

        List<ToolExecutionRequest> requests = new ArrayList<>();
        for (JsonNode callNode : toolCallsNode) {
            JsonNode function = callNode.path("function");
            String id = callNode.path("id").asText("");
            if (id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
            String name = function.path("name").asText("");
            if (name.isBlank()) {
                continue;
            }
            JsonNode argsNode = function.path("arguments");
            String args = argsNode.isMissingNode() ? "{}" : argsNode.toString();

            requests.add(ToolExecutionRequest.builder()
                    .id(id)
                    .name(name)
                    .arguments(args)
                    .build());
        }
        return requests;
    }
}
