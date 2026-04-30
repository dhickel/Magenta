package io.mindspice.magenta2.ai.chat.tool.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.WebSearchConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentWebToolServiceTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void searchesSearxngJsonResults() throws Exception {
        startServer((exchange) -> {
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/search");
            respond(exchange, "application/json", """
                {
                  "results": [
                    {"title":"One","url":"https://example.com/one","content":"First result","engine":"duckduckgo","publishedDate":"2026-01-01"},
                    {"title":"Two","url":"https://example.com/two","content":"Second result","engine":"brave"}
                  ]
                }
                """);
        });
        AgentWebToolService service = service(true);

        AgentWebToolService.WebSearchResult result = service.search("magenta test", 5);

        assertThat(result.query()).isEqualTo("magenta test");
        assertThat(result.results()).hasSize(2);
        assertThat(result.results().getFirst().url()).isEqualTo("https://example.com/one");
        assertThat(result.results().getFirst().engine()).isEqualTo("duckduckgo");
    }

    @Test
    void searchRequiresEnabledConfig() {
        AgentWebToolService service = service(false);

        assertThatThrownBy(() -> service.search("query", 5))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not enabled");
    }

    @Test
    void fetchExtractsHtmlTextAndTruncates() throws Exception {
        startServer((exchange) -> respond(exchange, "text/html", """
            <html><head><title>Readable Page</title><script>bad()</script></head>
            <body><nav>skip nav</nav><main><h1>Hello</h1><p>Alpha beta gamma delta.</p></main></body></html>
            """));
        AgentWebToolService service = service(true);

        AgentWebToolService.WebFetchResult result = service.fetch(baseUrl() + "/page", 1_000);

        assertThat(result.title()).isEqualTo("Readable Page");
        assertThat(result.text()).contains("Hello").contains("Alpha beta gamma delta");
        assertThat(result.text()).doesNotContain("skip nav").doesNotContain("bad()");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void fetchRejectsUnsupportedSchemes() {
        AgentWebToolService service = service(true);

        assertThatThrownBy(() -> service.fetch("file:///etc/passwd", 1_000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("http and https");
    }

    @Test
    void fetchRejectsPrivateHostsInProductionPath() {
        AgentWebToolService service = new AgentWebToolService(config(true), new ObjectMapper());

        assertThatThrownBy(() -> service.fetch("http://127.0.0.1:8080/private", 1_000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("public web hosts");
    }

    private AgentWebToolService service(boolean enabled) {
        return new AgentWebToolService(config(enabled), new ObjectMapper(), HttpClient.newHttpClient());
    }

    private AiConfig config(boolean enabled) {
        AiConfig config = new AiConfig(
            "magenta",
            "magenta",
            10,
            null,
            new WebSearchConfig(enabled, "searxng", enabled && server != null ? baseUrl() : "http://localhost:1"),
            Map.of(),
            Map.of("magenta", new AgentConfig("model", "prompt", List.of()))
        );
        return config;
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (AssertionError error) {
                byte[] body = error.getMessage().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
