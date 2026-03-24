package io.mindspice.magenta.runtime.model;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingModelClientTest {

    @Test
    void routesLlamaCppAliasToOpenAiCompatibleEndpoint() throws IOException {
        AtomicInteger openAiCalls = new AtomicInteger();
        AtomicInteger ollamaCalls = new AtomicInteger();
        HttpServer server = server(openAiCalls, ollamaCalls);
        server.start();

        try {
            RoutingModelClient client = new RoutingModelClient(10_000);
            client.chatBlocking(model("llama.cpp", baseUrl(server)), ChatRequest.builder()
                    .messages(java.util.List.of(UserMessage.from("hello")))
                    .build());

            assertThat(openAiCalls.get()).isEqualTo(1);
            assertThat(ollamaCalls.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void routesLegacyProviderToOllamaEndpoint() throws IOException {
        AtomicInteger openAiCalls = new AtomicInteger();
        AtomicInteger ollamaCalls = new AtomicInteger();
        HttpServer server = server(openAiCalls, ollamaCalls);
        server.start();

        try {
            RoutingModelClient client = new RoutingModelClient(10_000);
            client.chatBlocking(model("langchain4j", baseUrl(server)), ChatRequest.builder()
                    .messages(java.util.List.of(UserMessage.from("hello")))
                    .build());

            assertThat(ollamaCalls.get()).isEqualTo(1);
            assertThat(openAiCalls.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer server(AtomicInteger openAiCalls, AtomicInteger ollamaCalls) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            openAiCalls.incrementAndGet();
            String response = """
                    {"choices":[{"message":{"role":"assistant","content":"openai"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1}}
                    """;
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.createContext("/api/chat", exchange -> {
            ollamaCalls.incrementAndGet();
            String response = """
                    {"model":"test-model","message":{"role":"assistant","content":"ollama"},"done":true}
                    """;
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static RuntimeConfig.ModelConfig model(String provider, String endpoint) {
        return new RuntimeConfig.ModelConfig(
                "model-default",
                provider,
                "dummy",
                endpoint,
                1024,
                1024,
                768,
                0.0,
                "rolling_window",
                "cl100k_base",
                false,
                true,
                true
        );
    }
}
