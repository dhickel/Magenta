package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionConfigTest {

    @Test
    void defaultsEnableStreamingAndTools() {
        SessionConfig config = new SessionConfig(
                SessionParams.ofStreaming(true),
                request -> ToolResult.notHandled(request.toolCall()),
                ignored -> {}
        );

        assertThat(config.params().blockingOnly()).isFalse();
        assertThat(config.params().toolsEnabled()).isTrue();
        assertThat(config.params().streamingEnabled()).isTrue();
        assertThat(config.routingEventLevel()).isEqualTo(RoutingEventLevel.NONE);
    }

    @Test
    void exposesConfiguredParams() {
        SessionConfig config = new SessionConfig(
                new SessionParams(true, false, false),
                request -> ToolResult.notHandled(request.toolCall()),
                RoutingEventLevel.ALL,
                ignored -> {},
                ignored -> {}
        );

        SessionParams view = config.params();
        assertThat(view.blockingOnly()).isTrue();
        assertThat(view.toolsEnabled()).isFalse();
        assertThat(view.streamingEnabled()).isFalse();
        assertThat(config.routingEventLevel()).isEqualTo(RoutingEventLevel.ALL);
    }
}
