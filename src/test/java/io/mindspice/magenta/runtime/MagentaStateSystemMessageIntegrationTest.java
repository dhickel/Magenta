package io.mindspice.magenta.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class MagentaStateSystemMessageIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void stateSystemMessageIsBuiltBeforeEachModelRequestAcrossToolTurns() throws Exception {
        try (ScriptedOllamaServer stub = new ScriptedOllamaServer(
                toolCallResponse("call-tool-1", "pwd"),
                finalResponse("first turn complete"),
                toolCallResponse("call-tool-2", "echo two"),
                finalResponse("second turn complete")
        )) {
            Magenta magenta = new Magenta(runtimeConfigForEndpoint(stub.endpoint()));
            SessionHandle handle = magenta.startBaseSession("state-system-message");
            magenta.addInputRoute(handle, InputRoutePolicy.defaults());

            List<String> finalOutputs = java.util.Collections.synchronizedList(new ArrayList<>());
            magenta.addOutputRoute(handle, OutputRoutePolicy.defaults(), event -> {
                if (event.output() instanceof SessionOutput.FinalOutput finalOutput) {
                    finalOutputs.add(finalOutput.text());
                }
            });

            try {
                magenta.messageInputConsumer(handle).accept(SessionInput.userMessage("run first step"));
                waitUntil(() -> stub.requestBodies().size() >= 2 && finalOutputs.size() >= 1, 5);

                magenta.messageInputConsumer(handle).accept(SessionInput.userMessage("run second step"));
                waitUntil(() -> stub.requestBodies().size() >= 4 && finalOutputs.size() >= 2, 5);

                assertThat(stub.requestBodies()).hasSize(4);

                JsonNode request1State = parseStateFromRequest(stub.requestBodies().get(0));
                JsonNode request2State = parseStateFromRequest(stub.requestBodies().get(1));
                JsonNode request3State = parseStateFromRequest(stub.requestBodies().get(2));
                JsonNode request4State = parseStateFromRequest(stub.requestBodies().get(3));

                Set<String> callIds1 = toolCallIds(request1State);
                Set<String> callIds2 = toolCallIds(request2State);
                Set<String> callIds3 = toolCallIds(request3State);
                Set<String> callIds4 = toolCallIds(request4State);

                assertThat(callIds1).doesNotContain("call-tool-1", "call-tool-2");
                assertThat(callIds2).contains("call-tool-1").doesNotContain("call-tool-2");
                assertThat(callIds3).contains("call-tool-1").doesNotContain("call-tool-2");
                assertThat(callIds4).contains("call-tool-2");

                assertThat(request2State.path("toolUsage").toString()).contains("\"commandPreview\":\"pwd\"");
                assertThat(request4State.path("toolUsage").toString()).contains("\"commandPreview\":\"echo two\"");
                assertToolUsageIncludesTimeOnly(request2State);
                assertToolUsageIncludesTimeOnly(request4State);
            } finally {
                magenta.closeSession(handle);
            }
        }
    }

    private void assertToolUsageIncludesTimeOnly(JsonNode stateNode) {
        JsonNode usage = stateNode.path("toolUsage");
        if (!usage.isObject()) {
            return;
        }
        for (JsonNode bucket : usage) {
            if (!bucket.isArray()) {
                continue;
            }
            for (JsonNode item : bucket) {
                String time = item.path("time").asText("");
                assertThat(time).matches("^\\d{2}:\\d{2}:\\d{2}$");
            }
        }
    }

    private Set<String> toolCallIds(JsonNode stateNode) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        JsonNode usage = stateNode.path("toolUsage");
        if (!usage.isObject()) {
            return ids;
        }
        for (JsonNode bucket : usage) {
            if (!bucket.isArray()) {
                continue;
            }
            for (JsonNode item : bucket) {
                String id = item.path("toolCallId").asText("");
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private JsonNode parseStateFromRequest(String requestBody) throws Exception {
        JsonNode request = MAPPER.readTree(requestBody);
        JsonNode messages = request.path("messages");
        assertThat(messages.isArray()).isTrue();

        int stateCount = 0;
        int stateIndex = -1;
        int lastSystemIndex = -1;
        String stateBody = "";

        for (int i = 0; i < messages.size(); i++) {
            JsonNode message = messages.get(i);
            String role = message.path("role").asText("");
            if (!"system".equals(role)) {
                continue;
            }
            lastSystemIndex = i;
            String content = message.path("content").asText("");
            JsonNode parsed = tryParseJson(content);
            if (parsed != null && "state_snapshot".equals(parsed.path("kind").asText())) {
                stateCount++;
                stateIndex = i;
                stateBody = content;
            }
        }

        assertThat(stateCount).isEqualTo(1);
        assertThat(stateIndex).isEqualTo(lastSystemIndex);
        assertThat(stateBody).isNotBlank();
        return MAPPER.readTree(stateBody);
    }

    private JsonNode tryParseJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(content);
        } catch (Exception ignored) {
            return null;
        }
    }

    private RuntimeConfig runtimeConfigForEndpoint(String endpoint) {
        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "model-default",
                "test-provider",
                "test-model",
                endpoint,
                4096,
                4096,
                500,
                0.0,
                "rolling_window",
                "cl100k_base",
                true,
                false,
                true
        );

        RuntimeConfig.AgentConfig baseAgent = new RuntimeConfig.AgentConfig(
                "agent-default",
                "model-default",
                List.of("base.system", "agents.default"),
                "",
                List.of(),
                List.of(),
                List.of("shell_command"),
                true
        );

        RuntimeConfig.AgentConfig compactionAgent = new RuntimeConfig.AgentConfig(
                "agent-compaction",
                "model-default",
                List.of("base.compaction"),
                "",
                List.of(),
                List.of(),
                List.of(),
                true
        );

        return new RuntimeConfig(
                Path.of("configs"),
                Path.of(".").toAbsolutePath().normalize(),
                "agent-default",
                "agent-compaction",
                8,
                64,
                32_768,
                200,
                500,
                Map.of(modelConfig.id(), modelConfig),
                Map.of(baseAgent.id(), baseAgent, compactionAgent.id(), compactionAgent),
                Map.of(
                        "base.system", "Base prompt",
                        "agents.default", "Agent prompt",
                        "base.compaction", "Compaction prompt"
                ),
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                RuntimeConfig.TerminalConfig.defaults()
        );
    }

    private void waitUntil(BooleanSupplier condition, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static String toolCallResponse(String callId, String cmd) {
        return """
                {
                  "message": {
                    "role": "assistant",
                    "content": "calling a tool",
                    "tool_calls": [
                      {
                        "id": "%s",
                        "type": "function",
                        "function": {
                          "name": "shell_command",
                          "arguments": {
                            "cmd": "%s"
                          }
                        }
                      }
                    ]
                  },
                  "done": true,
                  "done_reason": "stop"
                }
                """.formatted(callId, cmd);
    }

    private static String finalResponse(String text) {
        return """
                {
                  "message": {
                    "role": "assistant",
                    "content": "%s"
                  },
                  "done": true,
                  "done_reason": "stop"
                }
                """.formatted(text);
    }

    private static final class ScriptedOllamaServer implements AutoCloseable {
        private final HttpServer server;
        private final List<String> requestBodies = java.util.Collections.synchronizedList(new ArrayList<>());
        private final Deque<String> responses = new ArrayDeque<>();

        private ScriptedOllamaServer(String... responseBodies) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            responses.addAll(List.of(responseBodies));
            server.createContext("/api/chat", exchange -> {
                byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                requestBodies.add(new String(bodyBytes, StandardCharsets.UTF_8));
                String response = responses.isEmpty()
                        ? finalResponse("fallback")
                        : responses.removeFirst();
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
                exchange.close();
            });
            server.start();
        }

        private String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private List<String> requestBodies() {
            return requestBodies;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
