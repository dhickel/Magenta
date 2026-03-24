package io.mindspice.magenta.runtime.model;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiClientStreamingTest {

    @Test
    void streamingReassemblesToolCallArguments() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String response = """
                    data: {"choices":[{"delta":{"content":"Working ","tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"read_file","arguments":"{\\"path\\":\\""}}]}}]}

                    data: {"choices":[{"delta":{"content":"now","tool_calls":[{"index":0,"function":{"arguments":"/tmp/test.txt\\"}"}}]}}]}

                    data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

                    data: [DONE]

                    """;
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();

        try {
            OpenAiClient client = new OpenAiClient(10_000);
            List<String> streamed = new ArrayList<>();
            ChatResponse response = client.chatStreaming(
                    modelConfig("http://127.0.0.1:" + server.getAddress().getPort()),
                    ChatRequest.builder().messages(List.of()).build(),
                    streamed::add
            );

            assertThat(streamed).containsExactly("Working ", "now");
            assertThat(response.aiMessage().text()).isEqualTo("Working now");
            assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
            assertThat(response.aiMessage().toolExecutionRequests().getFirst().name()).isEqualTo("read_file");
            assertThat(response.aiMessage().toolExecutionRequests().getFirst().arguments()).isEqualTo("{\"path\":\"/tmp/test.txt\"}");
        } finally {
            server.stop(0);
        }
    }

    private static RuntimeConfig.ModelConfig modelConfig(String endpoint) {
        return new RuntimeConfig.ModelConfig(
                "model-default",
                "openai",
                "dummy",
                endpoint,
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
