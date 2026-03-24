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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class OpenAiClient implements ModelClient {

    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 600_000;
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final Duration requestTimeout;

    public OpenAiClient() {
        this(DEFAULT_REQUEST_TIMEOUT_MS);
    }

    public OpenAiClient(int requestTimeoutMs) {
        if (requestTimeoutMs <= 0) {
            throw new IllegalArgumentException("requestTimeoutMs must be > 0");
        }
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
    }

    @Override
    public ChatResponse chatBlocking(RuntimeConfig.ModelConfig modelConfig, ChatRequest request) {
        String baseUrl = resolveBaseUrl(modelConfig.endpoint());
        JsonNode payload = toOpenAiPayload(modelConfig, request, false);

        HttpRequest.Builder httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl(baseUrl)))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json");
        applyAuthorizationHeader(httpRequest);
        httpRequest.POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8));

        try {
            HttpResponse<String> response = httpClient.send(httpRequest.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                throw classifyHttpFailure("chat", response.statusCode(), response.body());
            }
            JsonNode body;
            try {
                body = json.readTree(response.body());
            } catch (IOException parseError) {
                throw new ModelClientException(
                        ModelClientException.Reason.MALFORMED_RESPONSE,
                        "OpenAI-compatible chat returned malformed JSON",
                        response.statusCode(),
                        "",
                        preview(response.body()),
                        parseError
                );
            }
            JsonNode choice = firstChoice(body, response.statusCode(), response.body());
            ModelClientException finishReasonFailure = classifyFinishReasonFailure(
                    choice.path("finish_reason").asText(""),
                    response.statusCode(),
                    body.toString()
            );
            if (finishReasonFailure != null) {
                throw finishReasonFailure;
            }
            return toChatResponse(modelConfig, body, null, null);
        } catch (ModelClientException e) {
            throw e;
        } catch (IOException e) {
            throw new ModelClientException(
                    ModelClientException.Reason.HTTP_ERROR,
                    "OpenAI-compatible chat request failed",
                    0,
                    "",
                    "",
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelClientException(
                    ModelClientException.Reason.HTTP_ERROR,
                    "OpenAI-compatible chat request interrupted",
                    0,
                    "",
                    "",
                    e
            );
        }
    }

    @Override
    public ChatResponse chatStreaming(RuntimeConfig.ModelConfig modelConfig, ChatRequest request, Consumer<String> tokenCallback) {
        String baseUrl = resolveBaseUrl(modelConfig.endpoint());
        JsonNode payload = toOpenAiPayload(modelConfig, request, true);

        HttpRequest.Builder httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl(baseUrl)))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json");
        applyAuthorizationHeader(httpRequest);
        httpRequest.POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8));

        try {
            HttpResponse<InputStream> response = httpClient.send(httpRequest.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() > 299) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw classifyHttpFailure("stream", response.statusCode(), body);
            }

            StringBuilder completeText = new StringBuilder();
            Map<Integer, StreamToolCallAccumulator> streamedToolCalls = new HashMap<>();
            String finishReason = "";

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isBlank() || trimmed.startsWith(":")) {
                        continue;
                    }
                    if (!trimmed.startsWith("data:")) {
                        continue;
                    }
                    String payloadLine = trimmed.substring("data:".length()).trim();
                    if ("[DONE]".equals(payloadLine)) {
                        break;
                    }

                    JsonNode node;
                    try {
                        node = json.readTree(payloadLine);
                    } catch (IOException parseError) {
                        throw new ModelClientException(
                                ModelClientException.Reason.MALFORMED_RESPONSE,
                                "OpenAI-compatible stream returned malformed JSON chunk",
                                response.statusCode(),
                                "",
                                preview(payloadLine),
                                parseError
                        );
                    }

                    JsonNode choice = node.path("choices").isArray() && !node.path("choices").isEmpty()
                            ? node.path("choices").get(0)
                            : null;
                    if (choice == null || choice.isMissingNode()) {
                        continue;
                    }
                    String partialFinishReason = choice.path("finish_reason").asText("");
                    if (!partialFinishReason.isBlank()) {
                        finishReason = partialFinishReason;
                    }

                    JsonNode delta = choice.path("delta");
                    String partial = delta.path("content").asText("");
                    if (!partial.isBlank()) {
                        completeText.append(partial);
                        tokenCallback.accept(partial);
                    }
                    JsonNode toolCalls = delta.path("tool_calls");
                    if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                        mergeStreamedToolCalls(streamedToolCalls, toolCalls);
                    }
                }
            }

            ModelClientException finishReasonFailure = classifyFinishReasonFailure(
                    finishReason,
                    response.statusCode(),
                    completeText.toString()
            );
            if (finishReasonFailure != null) {
                throw finishReasonFailure;
            }

            return toChatResponse(
                    modelConfig,
                    null,
                    completeText.toString(),
                    streamedToolCalls.isEmpty() ? List.of() : toToolExecutionRequests(streamedToolCalls)
            );
        } catch (ModelClientException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelClientException(
                    ModelClientException.Reason.HTTP_ERROR,
                    "OpenAI-compatible streaming request interrupted",
                    0,
                    "",
                    "",
                    e
            );
        } catch (IOException e) {
            throw new ModelClientException(
                    ModelClientException.Reason.HTTP_ERROR,
                    "OpenAI-compatible streaming request failed",
                    0,
                    "",
                    "",
                    e
            );
        }
    }

    private void applyAuthorizationHeader(HttpRequest.Builder httpRequest) {
        String apiKey = System.getenv("MAGENTA_OPENAI_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            httpRequest.header("Authorization", "Bearer " + apiKey.trim());
        }
    }

    private String resolveBaseUrl(String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            return stripTrailingSlash(endpoint.trim());
        }
        String env = System.getenv("MAGENTA_OPENAI_URL");
        if (env != null && !env.isBlank()) {
            return stripTrailingSlash(env.trim());
        }
        return DEFAULT_BASE_URL;
    }

    private String stripTrailingSlash(String value) {
        String stripped = value;
        while (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
    }

    private String chatCompletionsUrl(String baseUrl) {
        if (baseUrl.endsWith("/v1")) {
            return baseUrl + "/chat/completions";
        }
        return baseUrl + "/v1/chat/completions";
    }

    private JsonNode toOpenAiPayload(RuntimeConfig.ModelConfig modelCfg, ChatRequest request, boolean stream) {
        ObjectNode root = json.createObjectNode();
        root.put("model", modelCfg.model());
        root.put("stream", stream);
        root.put("temperature", modelCfg.temperature());
        root.put("max_tokens", modelCfg.maxTokens());

        ArrayNode messages = json.createArrayNode();
        for (ChatMessage message : request.messages()) {
            ObjectNode msg = json.createObjectNode();
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
            root.put("tool_choice", "auto");
        }

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
        ArrayNode toolCalls = json.createArrayNode();
        if (requests == null || requests.isEmpty()) {
            return toolCalls;
        }

        for (ToolExecutionRequest request : requests) {
            if (request == null || request.name() == null || request.name().isBlank()) {
                continue;
            }

            ObjectNode toolCall = json.createObjectNode();
            String id = request.id();
            toolCall.put("id", id == null || id.isBlank() ? UUID.randomUUID().toString() : id);
            toolCall.put("type", "function");

            ObjectNode function = json.createObjectNode();
            function.put("name", request.name());
            String args = request.arguments();
            function.put("arguments", args == null || args.isBlank() ? "{}" : args);
            toolCall.set("function", function);
            toolCalls.add(toolCall);
        }
        return toolCalls;
    }

    private void mergeStreamedToolCalls(Map<Integer, StreamToolCallAccumulator> streamedToolCalls, JsonNode toolCalls) {
        for (JsonNode toolCallNode : toolCalls) {
            int index = toolCallNode.path("index").asInt(streamedToolCalls.size());
            StreamToolCallAccumulator accumulator = streamedToolCalls.computeIfAbsent(index, ignored -> new StreamToolCallAccumulator());
            String id = toolCallNode.path("id").asText("");
            if (!id.isBlank()) {
                accumulator.id = id;
            }
            String type = toolCallNode.path("type").asText("");
            if (!type.isBlank()) {
                accumulator.type = type;
            }
            JsonNode functionNode = toolCallNode.path("function");
            String name = functionNode.path("name").asText("");
            if (!name.isBlank()) {
                accumulator.name = name;
            }
            String arguments = functionNode.path("arguments").asText("");
            if (!arguments.isBlank()) {
                accumulator.arguments.append(arguments);
            }
        }
    }

    private List<ToolExecutionRequest> toToolExecutionRequests(Map<Integer, StreamToolCallAccumulator> streamedToolCalls) {
        return streamedToolCalls.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(Map.Entry::getValue)
                .filter(acc -> acc.name != null && !acc.name.isBlank())
                .map(acc -> ToolExecutionRequest.builder()
                        .id(acc.id == null || acc.id.isBlank() ? UUID.randomUUID().toString() : acc.id)
                        .name(acc.name)
                        .arguments(acc.arguments.isEmpty() ? "{}" : acc.arguments.toString())
                        .build())
                .toList();
    }

    private ChatResponse toChatResponse(
            RuntimeConfig.ModelConfig modelCfg,
            JsonNode body,
            String streamedContent,
            List<ToolExecutionRequest> streamedToolRequests
    ) {
        JsonNode choice = body == null ? null : body.path("choices").isArray() && !body.path("choices").isEmpty()
                ? body.path("choices").get(0)
                : null;
        JsonNode message = choice == null ? null : choice.path("message");
        String text = streamedContent == null
                ? (message == null ? "" : message.path("content").asText(""))
                : streamedContent;

        List<ToolExecutionRequest> toolRequests = streamedToolRequests != null
                ? streamedToolRequests
                : parseToolRequests(message == null ? null : message.path("tool_calls"));
        AiMessage aiMessage = toolRequests.isEmpty()
                ? AiMessage.from(text)
                : AiMessage.from(text, toolRequests);

        JsonNode usage = body == null ? null : body.path("usage");
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .modelName(modelCfg.model())
                .tokenUsage(new TokenUsage(
                        nullableInt(usage == null ? null : usage.path("prompt_tokens")),
                        nullableInt(usage == null ? null : usage.path("completion_tokens"))
                ))
                .build();
    }

    private JsonNode firstChoice(JsonNode body, int statusCode, String responseBody) {
        JsonNode choices = body.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new ModelClientException(
                    ModelClientException.Reason.MALFORMED_RESPONSE,
                    "OpenAI-compatible chat response did not include choices",
                    statusCode,
                    "",
                    preview(responseBody),
                    null
            );
        }
        return choices.get(0);
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
            String args = function.path("arguments").asText("{}");

            requests.add(ToolExecutionRequest.builder()
                    .id(id)
                    .name(name)
                    .arguments(args == null || args.isBlank() ? "{}" : args)
                    .build());
        }
        return requests;
    }

    private ModelClientException classifyHttpFailure(String phase, int statusCode, String body) {
        String safeBody = body == null ? "" : body;
        String normalized = safeBody.toLowerCase(Locale.ROOT);
        ModelClientException.Reason reason = ModelClientException.Reason.HTTP_ERROR;
        if (statusCode == 413 || looksLikeContextOverflow(normalized)) {
            reason = ModelClientException.Reason.CONTEXT_OVERFLOW;
        }
        return new ModelClientException(
                reason,
                "OpenAI-compatible " + phase + " failed with status " + statusCode,
                statusCode,
                "",
                preview(safeBody),
                null
        );
    }

    private ModelClientException classifyFinishReasonFailure(String finishReason, int statusCode, String previewText) {
        String normalized = finishReason == null ? "" : finishReason.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "stop".equals(normalized) || "tool_calls".equals(normalized)) {
            return null;
        }
        if ("length".equals(normalized) || normalized.contains("max_tokens")) {
            return new ModelClientException(
                    ModelClientException.Reason.OUTPUT_TRUNCATED,
                    "OpenAI-compatible generation stopped due to output length limit",
                    statusCode,
                    normalized,
                    preview(previewText),
                    null
            );
        }
        if (looksLikeContextOverflow(normalized)) {
            return new ModelClientException(
                    ModelClientException.Reason.CONTEXT_OVERFLOW,
                    "OpenAI-compatible generation stopped due to context pressure",
                    statusCode,
                    normalized,
                    preview(previewText),
                    null
            );
        }
        return null;
    }

    private boolean looksLikeContextOverflow(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        return normalizedText.contains("context length")
                || normalizedText.contains("context window")
                || normalizedText.contains("max context")
                || normalizedText.contains("prompt is too long")
                || normalizedText.contains("token limit")
                || normalizedText.contains("context overflow");
    }

    private String preview(String text) {
        String safe = text == null ? "" : text;
        int max = 512;
        if (safe.length() <= max) {
            return safe;
        }
        return safe.substring(0, max);
    }

    private static final class StreamToolCallAccumulator {
        private String id;
        private String type = "function";
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }
}
