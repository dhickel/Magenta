package io.mindspice.magenta.runtime;

import com.sun.net.httpserver.HttpServer;
import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MagentaOpenAiCompatibleIntegrationTest {

    @Test
    void executesTurnAgainstOpenAiCompatibleEndpoint() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String response = """
                    {"choices":[{"message":{"role":"assistant","content":"assistant-reply"},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":2}}
                    """;
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();

        try {
            RuntimeConfig config = TestOpenAiRuntimeConfigs.runtimeConfig("http://127.0.0.1:" + server.getAddress().getPort());
            Magenta magenta = new Magenta(config);
            SessionHandle handle = magenta.startBaseSession("openai-turn");
            magenta.addInputRoute(handle, InputRoutePolicy.defaults());

            String response = magenta.submitAndAwait(handle, SessionInput.userMessage("hello"), TimeUnit.SECONDS.toMillis(2));

            assertThat(response).isEqualTo("assistant-reply");
            magenta.closeSession(handle);
        } finally {
            server.stop(0);
        }
    }
}
