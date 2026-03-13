package io.mindspice.magenta.runtime;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.events.SessionEvent;
import io.mindspice.magenta.runtime.events.SessionEventListenerHandle;
import io.mindspice.magenta.runtime.routing.InputRoutePolicy;
import io.mindspice.magenta.runtime.routing.InputRoutingEvent;
import io.mindspice.magenta.runtime.routing.OutputRoutePolicy;
import io.mindspice.magenta.runtime.routing.RouteHandle;
import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolResult;
import io.mindspice.magenta.support.TestRuntimeConfigs;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MagentaRoutingIntegrationTest {

    @Test
    void constructorFailsWhenEnabledAgentReferencesUnresolvedToolId() {
        RuntimeConfig.ModelConfig modelConfig = new RuntimeConfig.ModelConfig(
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
                true,
                false,
                true
        );
        RuntimeConfig.AgentConfig baseAgent = new RuntimeConfig.AgentConfig(
                "agent-default",
                "model-default",
                List.of("base.system"),
                "",
                List.of(),
                List.of(),
                List.of("missing_tool"),
                true
        );
        RuntimeConfig.AgentConfig compactionAgent = new RuntimeConfig.AgentConfig(
                "agent-compaction",
                "model-default",
                List.of("base.system"),
                "",
                List.of(),
                List.of(),
                List.of(),
                true
        );
        RuntimeConfig config = new RuntimeConfig(
                Path.of("configs"),
                Path.of(".").toAbsolutePath().normalize(),
                "agent-default",
                "agent-compaction",
                8,
                64,
                32_768,
                200,
                500,
                Map.of("model-default", modelConfig),
                Map.of(
                        "agent-default", baseAgent,
                        "agent-compaction", compactionAgent
                ),
                Map.of("base.system", "Base prompt"),
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                RuntimeConfig.TerminalConfig.defaults()
        );

        assertThatThrownBy(() -> new Magenta(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unresolved tool id")
                .hasMessageContaining("missing_tool");
    }

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

    @Test
    void setToolPolicyReplacesSingleSessionPolicy() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        SessionHandle handle = magenta.startBaseSession("policy-mutate");

        assertThat(magenta.toolPolicy(handle).mode()).isEqualTo(io.mindspice.magenta.runtime.config.RuntimeConfig.SecurityMode.BLACKLIST);

        SecurityManager.ToolPolicy denyAll = new SecurityManager.ToolPolicy(
                io.mindspice.magenta.runtime.config.RuntimeConfig.SecurityMode.DENY_ALL,
                false,
                Set.of(),
                Set.of(),
                List.of("."),
                Set.of(),
                new SecurityManager.WebAccessPolicy(false, false),
                List.of()
        );
        magenta.setToolPolicy(handle, denyAll);

        assertThat(magenta.toolPolicy(handle).mode()).isEqualTo(io.mindspice.magenta.runtime.config.RuntimeConfig.SecurityMode.DENY_ALL);
    }

    @Test
    void typedEventListenersReceiveMatchingActionEventsAndCanBeRemoved() {
        Magenta magenta = new Magenta(TestRuntimeConfigs.basicRuntimeConfig());
        SessionHandle handle = magenta.startBaseSession("listener-lifecycle");
        AtomicInteger actionEvents = new AtomicInteger();

        SessionEventListenerHandle listenerHandle = magenta.addEventListener(
                handle,
                SessionEvent.Action.class,
                event -> event instanceof SessionEvent.Action.InputRouteAdded
                         || event instanceof SessionEvent.Action.RouteRemoved,
                ignored -> actionEvents.incrementAndGet()
        );

        RouteHandle inputRoute = magenta.addInputRoute(handle, InputRoutePolicy.defaults());
        magenta.removeRoute(inputRoute);
        assertThat(actionEvents).hasValue(2);

        magenta.removeEventListener(listenerHandle);
        magenta.addInputRoute(handle, InputRoutePolicy.defaults());
        assertThat(actionEvents).hasValue(2);
    }
}
