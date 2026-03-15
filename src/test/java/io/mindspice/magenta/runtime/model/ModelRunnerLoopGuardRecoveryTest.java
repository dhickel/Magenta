package io.mindspice.magenta.runtime.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.Context;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.session.Session;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRunnerLoopGuardRecoveryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String WARNING_PREFIX = "[tool-loop-warning] repeated_calls=2/2; window_failures=1; recovery_attempt=1/1; required_action=change_approach_or_return_defeat";

    @Test
    void loopWarningTriggersRecoveryModelRetryWithoutTranscriptLeak() throws Exception {
        try (StubOllamaServer stub = new StubOllamaServer(
                toolCallResponse("tool pass 1", "call-1"),
                toolCallResponse("tool pass 2", "call-2"),
                finalResponse("changed approach")
        )) {
            ModelRunner runner = new ModelRunner(new OllamaClient(10_000));
            Session session = testSession(stub.endpoint());
            SessionHandle handle = new SessionHandle(session.sessionId(), () -> true);
            List<SessionOutput> outputs = new ArrayList<>();

            String result = runner.runTurn(
                    session,
                    handle,
                    6,
                    false,
                    event -> outputs.add(event.output()),
                    () -> {},
                    List.of(),
                    new RuntimeConfig.ToolLoopGuardConfig(true, 2, 2, 1)
            );

            assertThat(result).isEqualTo("changed approach");
            assertThat(outputs)
                    .filteredOn(output -> output instanceof SessionOutput.FinalOutput)
                    .extracting(output -> ((SessionOutput.FinalOutput) output).text())
                    .noneMatch(text -> text.startsWith(WARNING_PREFIX));
            assertThat(stub.requestBodies()).hasSize(3);

            JsonNode thirdRequest = MAPPER.readTree(stub.requestBodies().get(2));
            JsonNode firstMessage = thirdRequest.path("messages").get(0);
            assertThat(firstMessage.path("role").asText()).isEqualTo("system");
            assertThat(firstMessage.path("content").asText()).isEqualTo(WARNING_PREFIX);
        }
    }

    @Test
    void zeroRecoveryAttemptsStopsImmediatelyOnLoopDetection() throws Exception {
        try (StubOllamaServer stub = new StubOllamaServer(
                toolCallResponse("tool pass 1", "call-1"),
                toolCallResponse("tool pass 2", "call-2")
        )) {
            ModelRunner runner = new ModelRunner(new OllamaClient(10_000));
            Session session = testSession(stub.endpoint());
            SessionHandle handle = new SessionHandle(session.sessionId(), () -> true);
            List<SessionOutput> outputs = new ArrayList<>();

            String result = runner.runTurn(
                    session,
                    handle,
                    6,
                    false,
                    event -> outputs.add(event.output()),
                    () -> {},
                    List.of(),
                    new RuntimeConfig.ToolLoopGuardConfig(true, 2, 2, 0)
            );

            assertThat(result).contains("[tool-loop-stop] repeated tool-call pattern detected");
            assertThat(result).contains("recovery_attempts=0/0");
            assertThat(stub.requestBodies()).hasSize(2);
            assertThat(outputs)
                    .filteredOn(output -> output instanceof SessionOutput.FinalOutput)
                    .extracting(output -> ((SessionOutput.FinalOutput) output).text())
                    .noneMatch(text -> text.startsWith("[tool-loop-warning]"));
        }
    }

    private static Session testSession(String endpoint) {
        Context context = new Context();
        context.append(new ContextElement.SystemCoreMsg("base system prompt"));
        context.append(new ContextElement.UserMsg("use tools"));
        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "test-model",
                "langchain4j",
                "dummy",
                endpoint,
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
        SessionConfig sessionConfig = new SessionConfig(
                SessionParams.ofBlocking(true),
                request -> ToolResult.handled(
                        request.toolCall().id(),
                        request.toolCall().name(),
                        "{\"status\":\"failed\",\"code\":\"overwrite_guard\",\"message\":\"exists\"}"
                ),
                ignored -> {}
        );
        return new Session(
                UUID.randomUUID(),
                "agent-test",
                "alias",
                modelConfig,
                List.of("write_file"),
                context,
                sessionConfig,
                Instant.now()
        );
    }

    private static String toolCallResponse(String text, String callId) {
        return """
                {
                  "message": {
                    "role": "assistant",
                    "content": "%s",
                    "tool_calls": [
                      {
                        "id": "%s",
                        "type": "function",
                        "function": {
                          "name": "write_file",
                          "arguments": {
                            "path": "notes.txt",
                            "content": "data",
                            "overwrite": false
                          }
                        }
                      }
                    ]
                  },
                  "done": true,
                  "done_reason": "stop"
                }
                """.formatted(text, callId);
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

    private static final class StubOllamaServer implements AutoCloseable {
        private final HttpServer server;
        private final List<String> requestBodies = java.util.Collections.synchronizedList(new ArrayList<>());
        private final Deque<String> responses = new ArrayDeque<>();

        private StubOllamaServer(String... responseBodies) throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
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
