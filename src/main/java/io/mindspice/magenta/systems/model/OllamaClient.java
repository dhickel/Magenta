package io.mindspice.magenta.systems.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.mindspice.magenta.systems.config.RuntimeConfig.ModelConfig;
import io.mindspice.magenta.systems.config.RuntimeConfig;

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
                }
                case ToolExecutionResultMessage tool -> {
                    msg.put("role", "tool");
                    msg.put("content", tool.text());
                    msg.put("name", tool.toolName());
                }
                default -> {
                    msg.put("role", "user");
                    msg.put("content", message.toString());
                }
            }
            messages.add(msg);
        }
        root.set("messages", messages);

        var options = json.createObjectNode();
        options.put("temperature", modelCfg.temperature());
        options.put("num_ctx", modelCfg.maxContext());
        root.set("options", options);

        return root;
    }

    private ChatResponse toChatResponse(RuntimeConfig.ModelConfig modelCfg, JsonNode body, String streamedContent) {
        JsonNode message = body.path("message");
        String text = streamedContent == null
                ? message.path("content").asText(".")
                : (streamedContent.isBlank() ? "." : streamedContent);

        List<ToolExecutionRequest> toolRequests = parseToolRequests(message.path("tool_calls"));
        AiMessage aiMessage = toolRequests.isEmpty()
                ? AiMessage.from(text.isBlank() ? "." : text)
                : AiMessage.from(text.isBlank() ? "." : text, toolRequests);

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
