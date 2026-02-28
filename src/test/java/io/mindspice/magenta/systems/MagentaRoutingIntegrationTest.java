package io.mindspice.magenta.systems;

import io.mindspice.magenta.support.TestRuntimeConfigs;
import io.mindspice.magenta.systems.session.Session;
import io.mindspice.magenta.systems.session.SessionConfig;
import io.mindspice.magenta.systems.session.SessionInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MagentaRoutingIntegrationTest {

    @Test
    void publishToSessionsFailsWhenRouteReferencesClosedSession() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        Session session = magenta.startBaseSession("route-target", SessionConfig.defaults());
        magenta.registerSessionRoute(session.sessionId(), null);
        magenta.sessionManager().close(session.sessionId());

        assertThatThrownBy(() -> magenta.publishToSessions(SessionInput.userMessage("hello")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Session not found");
    }
}
