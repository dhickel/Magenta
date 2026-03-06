package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionRouterTest {

    @Test
    void multipleInputRoutesUseInsertionOrderAndShortCircuitOnApproval() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true);
        List<SessionInput> submitted = new ArrayList<>();
        List<RoutingEvent> reports = new ArrayList<>();

        SessionRouter router = new SessionRouter((h, input) -> submitted.add(input), reports::add, ignored -> {});
        router.addInputRoute(handle, new InputRoutePolicy(Set.of(SessionInput.AgentMsg.FILTER_FOR), Set.of("bus-A")));
        router.addInputRoute(handle, InputRoutePolicy.defaults());

        router.messageInputConsumer(handle).accept(new SessionInput.UserMsg("u-1", "user", true));

        assertThat(submitted).singleElement().extracting(SessionInput::text).isEqualTo("u-1");
        assertThat(reports)
                .filteredOn(event -> event instanceof RoutingEvent.InputResult input
                        && input.phase() == InputRoutingEvent.Phase.FINAL)
                .extracting(event -> ((RoutingEvent.InputResult) event).outcome())
                .containsExactly(InputRoutingEvent.OutCome.APPROVED);
    }

    @Test
    void outputPolicyFiltersByTag() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true);
        List<OutputRoutingEvent> received = new ArrayList<>();

        SessionRouter router = new SessionRouter((h, input) -> {}, event -> {}, ignored -> {});
        router.addOutputRoute(
                handle,
                OutputRoutePolicy.builder()
                        .allowedOutputTags(Set.of(SessionOutput.FinalOutput.FILTER_TAG))
                        .build(),
                received::add
        );

        router.emit(handle, new OutputRoutingEvent(handle, new SessionOutput.StreamedOutput("chunk")));
        router.emit(handle, new OutputRoutingEvent(handle, new SessionOutput.FinalOutput("final")));

        assertThat(received).singleElement()
                .extracting(event -> event.output().text())
                .isEqualTo("final");
    }

    @Test
    void listenerFailureDoesNotPreventOtherListeners() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true);
        AtomicInteger successCalls = new AtomicInteger();
        List<String> diagnostics = new ArrayList<>();

        SessionRouter router = new SessionRouter((h, input) -> {}, event -> {}, diagnostics::add);
        router.addOutputRoute(handle, OutputRoutePolicy.defaults(), event -> { throw new RuntimeException("boom"); });
        router.addOutputRoute(handle, OutputRoutePolicy.defaults(), event -> successCalls.incrementAndGet());

        assertThatCode(() -> router.emit(
                handle,
                new OutputRoutingEvent(handle, new SessionOutput.ToolMessageOutput(new ContextElement.ToolMsg(
                        "call-1",
                        "tool-a",
                        "x"
                )))
        )).doesNotThrowAnyException();
        assertThat(successCalls).hasValue(1);
        assertThat(diagnostics).anyMatch(msg -> msg.contains("output_route_listener_failure"));
    }

    @Test
    void pruneRemovesRoutesAndPreventsFurtherDelivery() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true);
        AtomicInteger outputs = new AtomicInteger();

        SessionRouter router = new SessionRouter((h, input) -> {}, event -> {}, ignored -> {});
        router.addInputRoute(handle, InputRoutePolicy.defaults());
        router.addOutputRoute(handle, OutputRoutePolicy.defaults(), event -> outputs.incrementAndGet());

        router.pruneSession(handle);

        assertThatThrownBy(() -> router.messageInputConsumer(handle).accept(SessionInput.userMessage("after-prune")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Input route not registered");
        router.emit(handle, new OutputRoutingEvent(handle, new SessionOutput.FinalOutput("after-prune")));
        assertThat(outputs).hasValue(0);
    }

    @Test
    void routeLookupAndHandleActiveStateTrackRemoval() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true);

        SessionRouter router = new SessionRouter((h, input) -> {}, event -> {}, ignored -> {});
        RouteHandle routeHandle = router.addOutputRoute(handle, OutputRoutePolicy.defaults(), event -> {});

        assertThat(routeHandle.isActive()).isTrue();
        assertThat(router.route(routeHandle)).isInstanceOf(Route.OutputRoute.class);

        router.removeRoute(routeHandle);
        assertThat(routeHandle.isActive()).isFalse();
        assertThatThrownBy(() -> router.route(routeHandle))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Route not registered");
    }
}
