package io.mindspice.magenta.runtime;

import com.sun.net.httpserver.HttpServer;
import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.events.SessionEvent;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.support.TestRuntimeConfigs;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class MagentaContextUsageTest {

    @Test
    void contextUsageSupplierReturnsModelAndTokenSnapshot() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        SessionHandle handle = magenta.startBaseSession("usage-test");

        Supplier<Magenta.SessionContextUsage> supplier = magenta.contextUsageSupplier(handle);
        Magenta.SessionContextUsage usage = supplier.get();

        assertThat(usage.sessionId()).isEqualTo(handle.sessionId());
        assertThat(usage.modelId()).isEqualTo("model-default");
        assertThat(usage.maxContextTokens()).isEqualTo(4096);
        assertThat(usage.estimatedContextTokens()).isGreaterThan(0);
        assertThat(usage.percentOfMaxContext()).isGreaterThanOrEqualTo(0.0);

        magenta.closeSession(handle);
    }

    @Test
    void contextUsageIncludesUserAndAssistantAfterSingleTurn() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            String response = """
                    {"model":"test-model","message":{"role":"assistant","content":"assistant-reply"},"done":true}
                    """;
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();

        try {
            RuntimeConfig config = runtimeConfigForEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
            Magenta magenta = new Magenta(config);
            SessionHandle handle = magenta.startBaseSession("usage-turn");
            magenta.addInputRoute(handle, InputRoutePolicy.defaults());

            Magenta.SessionContextUsage before = magenta.contextUsage(handle);
            magenta.messageInputConsumer(handle).accept(SessionInput.userMessage("hello"));
            Magenta.SessionContextUsage after = magenta.contextUsage(handle);

            assertThat(after.messageCount()).isGreaterThanOrEqualTo(before.messageCount() + 2);
            assertThat(after.estimatedContextTokens()).isGreaterThan(before.estimatedContextTokens());

            magenta.closeSession(handle);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void emitsContextCompactedEventWithBoundaryDiagnostics() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            String response = """
                    {"model":"test-model","message":{"role":"assistant","content":"assistant-reply"},"done":true}
                    """;
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();

        try {
            RuntimeConfig config = runtimeConfigForEndpoint("http://127.0.0.1:" + server.getAddress().getPort(), 32);
            Magenta magenta = new Magenta(config);
            SessionHandle handle = magenta.startBaseSession("usage-compaction");
            magenta.addInputRoute(handle, InputRoutePolicy.defaults());

            AtomicReference<SessionEvent.Action.ContextCompacted> eventRef = new AtomicReference<>();
            magenta.addEventListener(handle, SessionEvent.Action.ContextCompacted.class, eventRef::set);

            magenta.messageInputConsumer(handle).accept(SessionInput.userMessage("long-context ".repeat(200)));

            SessionEvent.Action.ContextCompacted event = eventRef.get();
            assertThat(event).isNotNull();
            assertThat(event.tokensBefore()).isGreaterThan(event.tokensAfter());
            assertThat(event.messagesBefore()).isGreaterThan(event.messagesAfter());
            assertThat(event.protectedSystemCount()).isGreaterThanOrEqualTo(1);
            assertThat(event.summarizedCount()).isGreaterThanOrEqualTo(0);
            assertThat(event.preservedRecentCount()).isGreaterThanOrEqualTo(0);

            magenta.closeSession(handle);
        } finally {
            server.stop(0);
        }
    }

    private RuntimeConfig runtimeConfigForEndpoint(String endpoint) {
        return runtimeConfigForEndpoint(endpoint, 500);
    }

    private RuntimeConfig runtimeConfigForEndpoint(String endpoint, int compactThreshold) {
        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
                "model-default",
                "test-provider",
                "test-model",
                endpoint,
                4096,
                4096,
                compactThreshold,
                0.0,
                "summarize",
                "cl100k_base",
                false,
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
                List.of("read_file"),
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
                32_768,
                200,
                500,
                Map.of(modelConfig.id(), modelConfig),
                Map.of(baseAgent.id(), baseAgent, compactionAgent.id(), compactionAgent),
                Map.of(
                        "base.system", "Base prompt",
                        "base.compaction", "Compaction prompt",
                        "agents.default", "Agent prompt"
                ),
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                RuntimeConfig.TerminalConfig.defaults()
        );
    }
}
