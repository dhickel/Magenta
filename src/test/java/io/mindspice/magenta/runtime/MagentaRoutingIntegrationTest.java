package io.mindspice.magenta.runtime;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.InputRoutingEvent;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.tools.ToolResult;
import io.mindspice.magenta.support.TestRuntimeConfigs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MagentaRoutingIntegrationTest {

    @Test
    void lifecycleApisReturnHandleWithSettingsSnapshotAndActivePredicate() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());

        SessionHandle started = magenta.startBaseSession(
                "started",
                new SessionConfig(
                        new SessionParams(false, true, false),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );
        SessionHandle resumed = magenta.resumeSession(started.sessionId());
        SessionHandle forked = magenta.forkSession(started.sessionId(), "forked");
        SessionHandle defaultBase = magenta.startBaseSession("default-base");
        SessionHandle defaultAgent = magenta.startSession("agent-default", "default-agent");

        assertThat(started.isActive()).isTrue();
        assertThat(resumed.sessionId()).isEqualTo(started.sessionId());
        assertThat(started.settingsView().streamingEnabled()).isFalse();
        assertThat(forked.isActive()).isTrue();
        assertThat(defaultBase.isActive()).isTrue();
        assertThat(defaultAgent.isActive()).isTrue();
        assertThat(defaultBase.settingsView().toolsEnabled()).isTrue();
        assertThat(defaultBase.settingsView().streamingEnabled()).isTrue();
        assertThat(defaultAgent.settingsView().toolsEnabled()).isTrue();
        assertThat(defaultAgent.settingsView().streamingEnabled()).isTrue();

        magenta.closeSession(started);
        magenta.closeSession(defaultBase);
        magenta.closeSession(defaultAgent);
        assertThat(started.isActive()).isFalse();
    }

    @Test
    void inputRouteUpdateReplacesPolicyAndDenialsAreReported() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        SessionHandle handle = magenta.startBaseSession(
                "route-update",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );

        List<InputRoutingEvent> reports = new ArrayList<>();
        magenta.registerInputRoute(handle, InputRoutePolicy.defaults(), InputRoutingEvent.Level.ALL, reports::add);
        magenta.updateInputRoute(
                handle,
                new InputRoutePolicy(Set.of(SessionInput.AgentMsg.FILTER_FOR), Set.of("bus-A")),
                InputRoutingEvent.Level.ALL,
                reports::add
        );

        magenta.getMessageInputConsumer(handle).accept(new SessionInput.UserMsg("deny", "user", true));

        assertThat(reports)
                .extracting(InputRoutingEvent::outcome)
                .contains(InputRoutingEvent.OutCome.DENIED_POLICY);
    }

    @Test
    void closeSessionPrunesRoutesAndBlocksFurtherRouteRegistrationOnInactiveHandle() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        SessionHandle handle = magenta.startBaseSession(
                "close-prune",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );

        magenta.registerInputRoute(handle, InputRoutePolicy.defaults(), InputRoutingEvent.Level.ERROR, event -> {});
        UUID routeId = magenta.registerOutputRoute(handle, OutputRoutePolicy.defaults(), event -> {});
        assertThat(routeId).isNotNull();

        magenta.closeSession(handle);

        assertThatThrownBy(() -> magenta.registerInputRoute(
                handle, InputRoutePolicy.defaults(), InputRoutingEvent.Level.ERROR, event -> {}
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unknown session handle");

        assertThatThrownBy(() -> magenta.registerOutputRoute(handle, OutputRoutePolicy.defaults(), event -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown session handle");
    }

    @Test
    void streamingDisabledSessionRejectsPartialListeners() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        SessionHandle handle = magenta.startBaseSession(
                "no-stream",
                new SessionConfig(
                        new SessionParams(false, true, false),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );

        assertThatThrownBy(() -> magenta.registerOutputRoute(
                handle,
                OutputRoutePolicy.builder()
                        .allowedOutputTags(Set.of(SessionOutput.StreamedOutput.FILTER_TAG))
                        .build(),
                event -> {}
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("streamingEnabled=true");
    }
}
