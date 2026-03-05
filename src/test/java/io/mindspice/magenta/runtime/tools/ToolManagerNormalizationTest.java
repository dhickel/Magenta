package io.mindspice.magenta.runtime.tools;

import com.fasterxml.jackson.databind.JsonNode;
import io.mindspice.magenta.runtime.context.ContextElement;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ToolManagerNormalizationTest {

    @Test
    void unknownToolReturnsNotHandledPayload() throws Exception {
        ToolManager manager = ToolManager.empty();
        ToolResult result = manager.execute(ToolTestSupport.request("missing_tool", "{}"));
        JsonNode payload = ToolTestSupport.payload(result);

        assertThat(result.handled()).isFalse();
        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("not_handled");
    }

    @Test
    void blankToolNameReturnsValidationError() throws Exception {
        ToolRequest request = new ToolRequest(
                UUID.randomUUID().toString(),
                "agent-default",
                new ContextElement.ToolCall("call-1", "   ", "{}")
        );
        ToolResult result = ToolManager.empty().execute(request);
        JsonNode payload = ToolTestSupport.payload(result);

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
    }

    @Test
    void handlerExceptionsAreNormalizedToFailurePayload() throws Exception {
        ToolManager manager = new ToolManager(Map.of(
                "boom", request -> {
                    throw new IllegalStateException("boom");
                }
        ));

        ToolResult result = manager.execute(ToolTestSupport.request("boom", "{}"));
        JsonNode payload = ToolTestSupport.payload(result);

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("handler_exception");
        assertThat(payload.path("data").path("errorType").asText()).isEqualTo("IllegalStateException");
    }

    @Test
    void unstructuredHandlerPayloadIsWrappedAsRawPassthrough() throws Exception {
        ToolManager manager = new ToolManager(Map.of(
                "raw", request -> ToolResult.handled(request.toolCall().id(), request.toolCall().name(), "plain text")
        ));

        ToolResult result = manager.execute(ToolTestSupport.request("raw", "{}"));
        JsonNode payload = ToolTestSupport.payload(result);

        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("code").asText()).isEqualTo("raw_passthrough");
        assertThat(payload.path("data").path("content").asText()).isEqualTo("plain text");
    }
}
