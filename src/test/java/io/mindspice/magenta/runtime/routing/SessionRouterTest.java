package io.mindspice.magenta.runtime.routing;

import io.mindspice.magenta.runtime.session.SessionConfigView;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionMessage;
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
    void inputRouteReplacesPriorPolicyAndReportsDenials() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true, new SessionConfigView(false, true, false, true));
        List<SessionInput> submitted = new ArrayList<>();
        List<InputRouteReport> reports = new ArrayList<>();

        SessionRouter router = new SessionRouter(id -> id.equals(sessionId) ? handle : null, (id, input) -> submitted.add(input));
        router.registerInputRoute(handle, InputRoutePolicy.defaults(), InputRouteReportLevel.ALL, reports::add);

        router.getMessageInputConsumer(handle).accept(new SessionInput.UserMessageInput("u-1", "user", "", java.util.Map.of(), true));
        router.updateInputRoute(
                handle,
                new InputRoutePolicy(Set.of(SessionInput.MessageInputKind.BUS_MESSAGE), Set.of(), Set.of("bus-A")),
                InputRouteReportLevel.ALL,
                reports::add
        );
        router.getMessageInputConsumer(handle).accept(new SessionInput.UserMessageInput("u-2", "user", "", java.util.Map.of(), true));
        router.getMessageInputConsumer(handle).accept(new SessionInput.BusMessageInput("b-1", "bus-A", "", java.util.Map.of(), true));

        assertThat(submitted).hasSize(2);
        assertThat(submitted.get(0).text()).isEqualTo("u-1");
        assertThat(submitted.get(1).text()).isEqualTo("b-1");
        assertThat(reports)
                .extracting(InputRouteReport::outcome)
                .contains(InputRouteOutcome.DENIED_POLICY, InputRouteOutcome.APPROVED);
    }

    @Test
    void streamingDisabledSessionRejectsPartialRoute() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true, new SessionConfigView(false, true, false, false));
        SessionRouter router = new SessionRouter(id -> id.equals(sessionId) ? handle : null, (id, input) -> {});

        assertThatThrownBy(() -> router.registerOutputRoute(
                handle,
                OutputRoutePolicy.builder().eventKinds(Set.of(SessionOutputEvent.Kind.PARTIAL)).build(),
                event -> {}
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("streamingEnabled=true");
    }

    @Test
    void outputPolicyFiltersByKindSourceAndTag() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true, new SessionConfigView(false, true, false, true));
        List<SessionOutputEvent> received = new ArrayList<>();

        SessionRouter router = new SessionRouter(id -> id.equals(sessionId) ? handle : null, (id, input) -> {});
        router.registerOutputRoute(
                handle,
                OutputRoutePolicy.builder()
                        .eventKinds(Set.of(SessionOutputEvent.Kind.FINAL))
                        .sourceAllowlist(Set.of("model"))
                        .tagAllowlist(Set.of("assistant"))
                        .build(),
                received::add
        );

        router.emit(handle, new SessionOutputEvent.PartialToken("tok", "model", Set.of("assistant")));
        router.emit(handle, new SessionOutputEvent.AssistantFinal("final", "model", Set.of("assistant")));
        router.emit(handle, new SessionOutputEvent.AssistantFinal("wrong-source", "router", Set.of("assistant")));
        router.emit(handle, new SessionOutputEvent.AssistantFinal("wrong-tag", "model", Set.of("other")));

        assertThat(received).singleElement()
                .isInstanceOf(SessionOutputEvent.AssistantFinal.class)
                .extracting("text")
                .isEqualTo("final");
    }

    @Test
    void listenerFailureDoesNotPreventOtherListeners() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true, new SessionConfigView(false, true, false, true));
        AtomicInteger successCalls = new AtomicInteger();
        List<String> diagnostics = new ArrayList<>();

        SessionRouter router = new SessionRouter(id -> id.equals(sessionId) ? handle : null, (id, input) -> {}, diagnostics::add);
        router.registerOutputRoute(handle, OutputRoutePolicy.defaults(), event -> { throw new RuntimeException("boom"); });
        router.registerOutputRoute(handle, OutputRoutePolicy.defaults(), event -> successCalls.incrementAndGet());

        assertThatCode(() -> router.emit(
                handle,
                new SessionOutputEvent.MessageAppended(new SessionMessage.UserMsg("x"), "session-context", Set.of("input"))
        )).doesNotThrowAnyException();
        assertThat(successCalls).hasValue(1);
        assertThat(diagnostics).anyMatch(msg -> msg.contains("output_route_listener_failure"));
    }

    @Test
    void closePruneRemovesRoutesAndPreventsFurtherDelivery() {
        UUID sessionId = UUID.randomUUID();
        SessionHandle handle = new SessionHandle(sessionId, () -> true, new SessionConfigView(false, true, false, true));
        AtomicInteger outputs = new AtomicInteger();

        SessionRouter router = new SessionRouter(id -> id.equals(sessionId) ? handle : null, (id, input) -> {});
        router.registerInputRoute(handle, InputRoutePolicy.defaults(), InputRouteReportLevel.ERROR, report -> {});
        router.registerOutputRoute(handle, OutputRoutePolicy.defaults(), event -> outputs.incrementAndGet());

        router.pruneSession(sessionId);

        assertThatThrownBy(() -> router.getMessageInputConsumer(handle).accept(SessionInput.userMessage("after-prune")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Input route not registered");
        router.emit(handle, new SessionOutputEvent.AssistantFinal("after-prune", "model", Set.of("assistant")));
        assertThat(outputs).hasValue(0);
    }
}
