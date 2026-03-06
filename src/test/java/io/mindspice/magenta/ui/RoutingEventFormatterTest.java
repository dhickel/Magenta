package io.mindspice.magenta.ui;

import io.mindspice.magenta.runtime.routing.InputRoutingEvent;
import io.mindspice.magenta.runtime.routing.RouteHandle;
import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.session.SessionHandle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingEventFormatterTest {

    @Test
    void formatsInputEventWithActiveStatusAndReason() {
        UUID sessionId = UUID.randomUUID();
        RouteHandle routeHandle = new RouteHandle(UUID.randomUUID(), () -> true);
        RoutingEventFormatter formatter = new RoutingEventFormatter();

        List<String> lines = formatter.format(new RoutingEvent.InputResult(
                new SessionHandle(sessionId, () -> true),
                Optional.of(routeHandle),
                InputRoutingEvent.OutCome.APPROVED,
                InputRoutingEvent.Phase.FINAL,
                "approved",
                "UserMsg",
                "user"
        ));

        assertThat(lines).contains("sessionId=" + sessionId + " active=true");
        assertThat(lines).contains("type=input");
        assertThat(lines).contains("reason=approved");
    }

    @Test
    void formatsOutputEventWithSeparatedRouteSets() {
        UUID sessionId = UUID.randomUUID();
        RouteHandle matched = new RouteHandle(UUID.randomUUID(), () -> true);
        RouteHandle failed = new RouteHandle(UUID.randomUUID(), () -> true);
        RoutingEventFormatter formatter = new RoutingEventFormatter();

        List<String> lines = formatter.format(new RoutingEvent.OutputResult(
                new SessionHandle(sessionId, () -> false),
                "FinalOutput",
                Set.of(matched, failed),
                Set.of(matched),
                Set.of(failed)
        ));

        assertThat(lines).contains("sessionId=" + sessionId + " active=false");
        assertThat(lines).contains("type=output");
        assertThat(lines).contains("outputType=FinalOutput");
        assertThat(lines).anyMatch(line -> line.startsWith("matchedRoutes=["));
        assertThat(lines).anyMatch(line -> line.startsWith("deliveredRoutes=["));
        assertThat(lines).anyMatch(line -> line.startsWith("failedRoutes=["));
    }
}
