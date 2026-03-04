package io.mindspice.magenta.runtime;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.InputRoutingEvent;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.routing.RouteHandle;
import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolResult;
import io.mindspice.magenta.support.TestRuntimeConfigs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MagentaRoutingIntegrationTest {

    @Test
    void lifecycleApisReturnActiveHandleAndSettingsLookup() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());

        SessionHandle started = magenta.startBaseSession(
                "started",
                new SessionConfig(
                        new SessionParams(false, true, false),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );
        SessionHandle resumed = magenta.resumeSession(started);
        SessionHandle forked = magenta.forkSession(started, "forked");
        SessionHandle defaultBase = magenta.startBaseSession("default-base");
        SessionHandle defaultAgent = magenta.startSession("agent-default", "default-agent");

        assertThat(started.isActive()).isTrue();
        assertThat(resumed.sessionId()).isEqualTo(started.sessionId());
        assertThat(magenta.settingsFor(started).streamingEnabled()).isFalse();
        assertThat(forked.isActive()).isTrue();
        assertThat(defaultBase.isActive()).isTrue();
        assertThat(defaultAgent.isActive()).isTrue();
        assertThat(magenta.settingsFor(defaultBase).toolsEnabled()).isTrue();
        assertThat(magenta.settingsFor(defaultBase).streamingEnabled()).isTrue();
        assertThat(magenta.settingsFor(defaultAgent).toolsEnabled()).isTrue();
        assertThat(magenta.settingsFor(defaultAgent).streamingEnabled()).isTrue();

        magenta.closeSession(started);
        magenta.closeSession(defaultBase);
        magenta.closeSession(defaultAgent);
        assertThat(started.isActive()).isFalse();
    }

    @Test
    void multipleInputRoutesShortCircuitAndEmitFinalDenial() {
        List<RoutingEvent> reports = new ArrayList<>();
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        SessionHandle handle = magenta.startBaseSession(
                "route-update",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        RoutingEventLevel.FINAL,
                        reports::add,
                        ignored -> {}
                )
        );

        magenta.addInputRoute(handle, new InputRoutePolicy(Set.of(SessionInput.AgentMsg.FILTER_FOR), Set.of("bus-A")));
        magenta.messageInputConsumer(handle).accept(new SessionInput.UserMsg("deny", "user", true));

        assertThat(reports)
                .filteredOn(event -> event instanceof RoutingEvent.InputResult)
                .extracting(event -> ((RoutingEvent.InputResult) event).outcome())
                .contains(InputRoutingEvent.OutCome.DENIED_POLICY);
    }

    @Test
    void closeSessionPrunesRoutesAndMakesRouteHandlesInactive() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        SessionHandle handle = magenta.startBaseSession(
                "close-prune",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );

        magenta.addInputRoute(handle, InputRoutePolicy.defaults());
        RouteHandle routeHandle = magenta.addOutputRoute(handle, OutputRoutePolicy.defaults(), event -> {});
        assertThat(routeHandle.isActive()).isTrue();

        magenta.closeSession(handle);
        assertThat(routeHandle.isActive()).isFalse();
    }

    @Test
    void streamingDisabledSessionRejectsStreamedListeners() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        SessionHandle handle = magenta.startBaseSession(
                "no-stream",
                new SessionConfig(
                        new SessionParams(false, true, false),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );

        assertThatThrownBy(() -> magenta.addOutputRoute(
                handle,
                OutputRoutePolicy.builder()
                        .allowedOutputTags(Set.of(SessionOutput.StreamedOutput.FILTER_TAG))
                        .build(),
                event -> {}
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("streamingEnabled=true");
    }
}
