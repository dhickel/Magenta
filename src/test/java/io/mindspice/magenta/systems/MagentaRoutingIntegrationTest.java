package io.mindspice.magenta.systems;

import io.mindspice.magenta.support.TestRuntimeConfigs;
import io.mindspice.magenta.systems.session.InputRouteReportLevel;
import io.mindspice.magenta.systems.session.Session;
import io.mindspice.magenta.systems.session.SessionConfig;
import io.mindspice.magenta.systems.session.SessionInput;
import io.mindspice.magenta.systems.session.SessionRoutePolicy;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MagentaRoutingIntegrationTest {

    @Test
    void publishToSessionsPrunesClosedRoutesAndContinuesDelivery() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());

        Session stale = magenta.startBaseSession("stale", SessionConfig.defaults());
        Session active = magenta.startBaseSession("active", SessionConfig.defaults());

        magenta.registerSessionRoute(
                stale.sessionId(),
                SessionRoutePolicy.defaults(),
                InputRouteReportLevel.ERROR,
                report -> {}
        );
        magenta.registerSessionRoute(
                active.sessionId(),
                new SessionRoutePolicy(
                        Set.of(SessionInput.MessageInputKind.BUS_MESSAGE),
                        Set.of(),
                        Set.of("bus-A")
                ),
                InputRouteReportLevel.ERROR,
                report -> {}
        );

        magenta.closeSession(stale.sessionId());

        int firstDelivered = magenta.publishToSessions(SessionInput.userMessage("hello"));
        int secondDelivered = magenta.publishToSessions(SessionInput.userMessage("hello again"));

        assertThat(firstDelivered).isEqualTo(0);
        assertThat(secondDelivered).isEqualTo(0);
    }
}
