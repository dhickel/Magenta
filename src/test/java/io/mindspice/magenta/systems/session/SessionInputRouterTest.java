package io.mindspice.magenta.systems.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionInputRouterTest {

    @Test
    void routeSubmitsAndReportsApprovedAtAllLevel() {
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicInteger submitted = new AtomicInteger();
        List<InputRouteReport> reports = new ArrayList<>();

        SessionInputRouter router = new SessionInputRouter(
                new SessionHandle(java.util.UUID.randomUUID(), active::get),
                SessionRoutePolicy.defaults(),
                input -> submitted.incrementAndGet(),
                reports::add,
                InputRouteReportLevel.ALL
        );

        boolean routed = router.route(new SessionInput.UserMessageInput("hello", "user", "", java.util.Map.of(), true));

        assertThat(routed).isTrue();
        assertThat(submitted).hasValue(1);
        assertThat(reports)
                .singleElement()
                .extracting(InputRouteReport::outcome)
                .isEqualTo(InputRouteOutcome.APPROVED);
    }

    @Test
    void routeDenialDoesNotSubmitAndReportsForFailureLevel() {
        AtomicInteger submitted = new AtomicInteger();
        List<InputRouteReport> reports = new ArrayList<>();

        SessionInputRouter router = new SessionInputRouter(
                new SessionHandle(java.util.UUID.randomUUID(), () -> true),
                new SessionRoutePolicy(
                        Set.of(SessionInput.MessageInputKind.BUS_MESSAGE),
                        Set.of(),
                        Set.of("bus-A")
                ),
                input -> submitted.incrementAndGet(),
                reports::add,
                InputRouteReportLevel.FAILURE
        );

        boolean routed = router.route(new SessionInput.UserMessageInput("skip", "user", "", java.util.Map.of(), true));

        assertThat(routed).isFalse();
        assertThat(submitted).hasValue(0);
        assertThat(reports)
                .singleElement()
                .extracting(InputRouteReport::outcome)
                .isEqualTo(InputRouteOutcome.DENIED_POLICY);
    }

    @Test
    void inactiveSessionReportsAcrossAllLevelsAndNeverSubmits() {
        AtomicInteger submitted = new AtomicInteger();

        List<InputRouteReport> allReports = new ArrayList<>();
        List<InputRouteReport> failureReports = new ArrayList<>();
        List<InputRouteReport> errorReports = new ArrayList<>();

        SessionInput input = new SessionInput.BusMessageInput("hello", "bus-A", "", java.util.Map.of(), true);

        SessionInputRouter allRouter = new SessionInputRouter(
                new SessionHandle(java.util.UUID.randomUUID(), () -> false),
                SessionRoutePolicy.defaults(),
                ignored -> submitted.incrementAndGet(),
                allReports::add,
                InputRouteReportLevel.ALL
        );
        SessionInputRouter failureRouter = new SessionInputRouter(
                new SessionHandle(java.util.UUID.randomUUID(), () -> false),
                SessionRoutePolicy.defaults(),
                ignored -> submitted.incrementAndGet(),
                failureReports::add,
                InputRouteReportLevel.FAILURE
        );
        SessionInputRouter errorRouter = new SessionInputRouter(
                new SessionHandle(java.util.UUID.randomUUID(), () -> false),
                SessionRoutePolicy.defaults(),
                ignored -> submitted.incrementAndGet(),
                errorReports::add,
                InputRouteReportLevel.ERROR
        );

        assertThat(allRouter.route(input)).isFalse();
        assertThat(failureRouter.route(input)).isFalse();
        assertThat(errorRouter.route(input)).isFalse();

        assertThat(submitted).hasValue(0);
        assertThat(allReports).singleElement().extracting(InputRouteReport::outcome).isEqualTo(InputRouteOutcome.SESSION_INACTIVE);
        assertThat(failureReports).singleElement().extracting(InputRouteReport::outcome).isEqualTo(InputRouteOutcome.SESSION_INACTIVE);
        assertThat(errorReports).singleElement().extracting(InputRouteReport::outcome).isEqualTo(InputRouteOutcome.SESSION_INACTIVE);
    }

    @Test
    void constructorRequiresReportCallbackAndReportLevel() {
        SessionHandle handle = new SessionHandle(java.util.UUID.randomUUID(), () -> true);

        assertThatThrownBy(() -> new SessionInputRouter(
                handle,
                SessionRoutePolicy.defaults(),
                input -> {},
                null,
                InputRouteReportLevel.ERROR
        )).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SessionInputRouter(
                handle,
                SessionRoutePolicy.defaults(),
                input -> {},
                report -> {},
                null
        )).isInstanceOf(NullPointerException.class);
    }
}
