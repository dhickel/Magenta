package io.mindspice.magenta.runtime;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.InputRouteReport;
import io.mindspice.magenta.runtime.routing.InputRouteReportLevel;
import io.mindspice.magenta.runtime.routing.InputRouteOutcome;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.routing.SessionOutputEvent;
import io.mindspice.magenta.runtime.session.SessionConfig;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
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
    void lifecycleApisReturnHandleWithConfigSnapshotAndActivePredicate() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());

        SessionHandle started = magenta.startBaseSession(
                "started",
                SessionConfig.builder().streamingEnabled(false).build()
        );
        SessionHandle resumed = magenta.resumeSession(started.sessionId());
        SessionHandle forked = magenta.forkSession(started.sessionId(), "forked");
        SessionHandle defaultBase = magenta.startBaseSession("default-base");
        SessionHandle defaultAgent = magenta.startSession("agent-default", "default-agent");

        assertThat(started.isActive()).isTrue();
        assertThat(resumed.sessionId()).isEqualTo(started.sessionId());
        assertThat(started.configView().streamingEnabled()).isFalse();
        assertThat(forked.isActive()).isTrue();
        assertThat(defaultBase.isActive()).isTrue();
        assertThat(defaultAgent.isActive()).isTrue();
        assertThat(defaultBase.configView().toolsEnabled()).isTrue();
        assertThat(defaultBase.configView().streamingEnabled()).isTrue();
        assertThat(defaultAgent.configView().toolsEnabled()).isTrue();
        assertThat(defaultAgent.configView().streamingEnabled()).isTrue();

        magenta.closeSession(started);
        magenta.closeSession(defaultBase);
        magenta.closeSession(defaultAgent);
        assertThat(started.isActive()).isFalse();
    }

    @Test
    void inputRouteUpdateReplacesPolicyAndDenialsAreReported() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        SessionHandle handle = magenta.startBaseSession("route-update", SessionConfig.defaults());

        List<InputRouteReport> reports = new ArrayList<>();
        magenta.registerInputRoute(handle, InputRoutePolicy.defaults(), InputRouteReportLevel.ALL, reports::add);
        magenta.updateInputRoute(
                handle,
                new InputRoutePolicy(Set.of(SessionInput.MessageInputKind.BUS_MESSAGE), Set.of(), Set.of("bus-A")),
                InputRouteReportLevel.ALL,
                reports::add
        );

        magenta.getMessageInputConsumer(handle).accept(new SessionInput.UserMessageInput("deny", "user", "", java.util.Map.of(), true));

        assertThat(reports)
                .extracting(InputRouteReport::outcome)
                .contains(InputRouteOutcome.DENIED_POLICY);
    }

    @Test
    void closeSessionPrunesRoutesAndBlocksFurtherRouteRegistrationOnInactiveHandle() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        SessionHandle handle = magenta.startBaseSession("close-prune", SessionConfig.defaults());

        magenta.registerInputRoute(handle, InputRoutePolicy.defaults(), InputRouteReportLevel.ERROR, report -> {});
        UUID routeId = magenta.registerOutputRoute(handle, OutputRoutePolicy.defaults(), event -> {});
        assertThat(routeId).isNotNull();

        magenta.closeSession(handle);

        assertThatThrownBy(() -> magenta.registerInputRoute(
                handle, InputRoutePolicy.defaults(), InputRouteReportLevel.ERROR, report -> {}
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
                SessionConfig.builder().streamingEnabled(false).build()
        );

        assertThatThrownBy(() -> magenta.registerOutputRoute(
                handle,
                OutputRoutePolicy.builder().eventKinds(Set.of(SessionOutputEvent.Kind.PARTIAL)).build(),
                event -> {}
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("streamingEnabled=true");
    }
}
