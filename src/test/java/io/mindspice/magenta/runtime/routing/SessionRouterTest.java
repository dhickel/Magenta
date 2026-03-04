package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.SessionSettingsView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
    void inputRouteReplacesPriorPolicyAndReportsDenials() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true, settingsView(sessionId, true));
        List<SessionInput> submitted = new ArrayList<>();
        List<InputRoutingEvent> reports = new ArrayList<>();

        SessionRouter router = new SessionRouter(id -> id.equals(sessionId) ? handle : null, (id, input) -> submitted.add(input));
        router.registerInputRoute(handle, InputRoutePolicy.defaults(), InputRoutingEvent.Level.ALL, reports::add);

        router.getMessageInputConsumer(handle).accept(new SessionInput.UserMsg("u-1", "user", true));
        router.updateInputRoute(
                handle,
                new InputRoutePolicy(Set.of(SessionInput.AgentMsg.FILTER_FOR), Set.of("bus-A")),
                InputRoutingEvent.Level.ALL,
                reports::add
        );
        router.getMessageInputConsumer(handle).accept(new SessionInput.UserMsg("u-2", "user", true));
        router.getMessageInputConsumer(handle).accept(new SessionInput.AgentMsg("b-1", "bus-A", true));

        assertThat(submitted).hasSize(2);
        assertThat(submitted.get(0).text()).isEqualTo("u-1");
        assertThat(submitted.get(1).text()).isEqualTo("b-1");
        assertThat(reports)
                .extracting(InputRoutingEvent::outcome)
                .contains(InputRoutingEvent.OutCome.DENIED_POLICY, InputRoutingEvent.OutCome.APPROVED);
    }

    @Test
    void streamingDisabledSessionRejectsPartialRoute() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true, settingsView(sessionId, false));
        SessionRouter router = new SessionRouter(id -> id.equals(sessionId) ? handle : null, (id, input) -> {});

        assertThatThrownBy(() -> router.registerOutputRoute(
                handle,
                OutputRoutePolicy.builder()
                        .allowedOutputTags(Set.of(SessionOutput.StreamedOutput.FILTER_TAG))
                        .build(),
                event -> {}
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("streamingEnabled=true");
    }

    @Test
    void outputPolicyFiltersByTag() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true, settingsView(sessionId, true));
        List<OutputRoutingEvent> received = new ArrayList<>();

        SessionRouter router = new SessionRouter(id -> id.equals(sessionId) ? handle : null, (id, input) -> {});
        router.registerOutputRoute(
                handle,
                OutputRoutePolicy.builder()
                        .allowedOutputTags(Set.of(SessionOutput.FinalOutput.FILTER_TAG))
                        .build(),
                received::add
        );

        router.emit(handle, new OutputRoutingEvent(sessionId, new SessionOutput.StreamedOutput("chunk")));
        router.emit(handle, new OutputRoutingEvent(sessionId, new SessionOutput.FinalOutput("final")));

        assertThat(received).singleElement()
                .extracting(event -> event.output().text())
                .isEqualTo("final");
    }

    @Test
    void listenerFailureDoesNotPreventOtherListeners() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true, settingsView(sessionId, true));
        AtomicInteger successCalls = new AtomicInteger();
        List<String> diagnostics = new ArrayList<>();

        SessionRouter router = new SessionRouter(id -> id.equals(sessionId) ? handle : null, (id, input) -> {}, diagnostics::add);
        router.registerOutputRoute(handle, OutputRoutePolicy.defaults(), event -> { throw new RuntimeException("boom"); });
        router.registerOutputRoute(handle, OutputRoutePolicy.defaults(), event -> successCalls.incrementAndGet());

        assertThatCode(() -> router.emit(
                handle,
                new OutputRoutingEvent(sessionId, new SessionOutput.ContextMessageOutput(new ContextElement.UserMsg("x")))
        )).doesNotThrowAnyException();
        assertThat(successCalls).hasValue(1);
        assertThat(diagnostics).anyMatch(msg -> msg.contains("output_route_listener_failure"));
    }

    @Test
    void closePruneRemovesRoutesAndPreventsFurtherDelivery() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true, settingsView(sessionId, true));
        AtomicInteger outputs = new AtomicInteger();

        SessionRouter router = new SessionRouter(id -> id.equals(sessionId) ? handle : null, (id, input) -> {});
        router.registerInputRoute(handle, InputRoutePolicy.defaults(), InputRoutingEvent.Level.ERROR, event -> {});
        router.registerOutputRoute(handle, OutputRoutePolicy.defaults(), event -> outputs.incrementAndGet());

        router.pruneSession(sessionId);

        assertThatThrownBy(() -> router.getMessageInputConsumer(handle).accept(SessionInput.userMessage("after-prune")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Input route not registered");
        router.emit(handle, new OutputRoutingEvent(sessionId, new SessionOutput.FinalOutput("after-prune")));
        assertThat(outputs).hasValue(0);
    }

    private SessionSettingsView settingsView(UUID sessionId, boolean streamingEnabled) {
        return new SessionSettingsView(
                sessionId,
                "alias",
                "agent",
                Instant.now(),
                false,
                true,
                streamingEnabled,
                "model-default",
                List.of("base.system"),
                List.of(),
                List.of(),
                List.of("read_file"),
                true,
                "System prompt",
                "model-default",
                "test-provider",
                "test-model",
                "http://localhost:11434",
                4096,
                4096,
                500,
                0.0,
                "rolling_window",
                "cl100k_base",
                false,
                streamingEnabled,
                true
        );
    }
}
