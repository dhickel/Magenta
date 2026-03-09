package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.context.ContextManager;
import io.mindspice.magenta.runtime.routing.InputRoutingEvent;
import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolResult;
import io.mindspice.magenta.support.TestRuntimeConfigs;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionManagerIntegrationTest {

    @Test
    void startResolvesPromptsAndReturnsHandleWithSettingsView() {
        RuntimeConfig config = TestRuntimeConfigs.basicRuntimeConfig();
        ContextManager contextManager = new ContextManager();
        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> "ok");

        Session session = manager.start(
                "agent-default",
                "alpha",
                new SessionConfig(
                        new SessionParams(false, true, false),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );

        assertThat(session.context().snapshot())
                .startsWith(
                        new ContextElement.SystemMsg("Base prompt"),
                        new ContextElement.SystemMsg("Agent prompt")
                );

        SessionHandle handle = manager.handleFor(session.sessionId());
        SessionSettingsView settings = manager.settingsFor(handle);
        assertThat(handle.sessionId()).isEqualTo(session.sessionId());
        assertThat(handle.isActive()).isTrue();
        assertThat(settings.streamingEnabled()).isFalse();
        assertThat(settings.agentId()).isEqualTo("agent-default");
        assertThat(settings.agentModelId()).isEqualTo("model-default");
        assertThat(settings.agentPromptIds()).containsExactly("base.system", "agents.default");
        assertThat(settings.agentToolIds()).containsExactly("read_file");
        assertThat(settings.resolvedSystemPrompt()).isEqualTo("Base prompt\n\nAgent prompt");
        assertThat(settings.modelName()).isEqualTo("test-model");
    }

    @Test
    void settingsViewIsStableSnapshotAndReadOnly() {
        RuntimeConfig config = TestRuntimeConfigs.basicRuntimeConfig();
        ContextManager contextManager = new ContextManager();
        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> "ok");

        Session session = manager.start(
                "agent-default",
                "stable",
                new SessionConfig(
                        new SessionParams(false, true, true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );

        SessionHandle handle = manager.handleFor(session.sessionId());
        SessionSettingsView settings = manager.settingsFor(handle);
        Instant createdAt = settings.createdAt();
        manager.close(session.sessionId());

        assertThat(handle.isActive()).isFalse();
        assertThat(settings.createdAt()).isEqualTo(createdAt);
        assertThatThrownBy(() -> settings.agentPromptIds().add("new.prompt"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void forkCopiesContextAndAllowsSessionConfigOverride() {
        RuntimeConfig config = TestRuntimeConfigs.basicRuntimeConfig();
        ContextManager contextManager = new ContextManager();
        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> "ok");

        Session source = manager.start(
                "agent-default",
                "source",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );
        source.context().append(new ContextElement.UserMsg("from-source"));

        SessionConfig overrideConfig = new SessionConfig(
                new SessionParams(true, true, false),
                request -> ToolResult.notHandled(request.toolCall()),
                ignored -> {}
        );
        Session fork = manager.fork(source.sessionId(), "fork", overrideConfig);

        source.context().append(new ContextElement.UserMsg("source-after-fork"));

        assertThat(fork.sessionConfig()).isSameAs(overrideConfig);
        assertThat(fork.context().snapshot())
                .contains(new ContextElement.UserMsg("from-source"))
                .doesNotContain(new ContextElement.UserMsg("source-after-fork"));
    }

    @Test
    void closeRemovesSessionFromManager() {
        RuntimeConfig config = TestRuntimeConfigs.basicRuntimeConfig();
        ContextManager contextManager = new ContextManager();
        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> "ok");

        Session session = manager.start(
                "agent-default",
                "closable",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );
        assertThat(manager.list()).extracting(Session::sessionId).contains(session.sessionId());

        manager.close(session.sessionId());

        assertThat(manager.list()).extracting(Session::sessionId).doesNotContain(session.sessionId());
        assertThatThrownBy(() -> manager.resume(session.sessionId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void submitFromRouteSwallowsExecutionFailureAndEmitsSessionOnError() {
        RuntimeConfig config = TestRuntimeConfigs.basicRuntimeConfig();
        ContextManager contextManager = new ContextManager();
        AtomicInteger onErrorCalls = new AtomicInteger();
        AtomicReference<SessionHandle> errorHandle = new AtomicReference<>();

        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> {
            throw new IllegalStateException("simulated-execution-failure");
        });

        SessionConfig cfg = new SessionConfig(
                SessionParams.ofStreaming(true),
                request -> ToolResult.notHandled(request.toolCall()),
                error -> {
                    onErrorCalls.incrementAndGet();
                    errorHandle.set(error.sessionHandle());
                }
        );
        Session session = manager.start("agent-default", "router-error", cfg);
        SessionHandle handle = manager.handleFor(session.sessionId());

        assertThatCode(() -> manager.submitFromRoute(
                handle,
                new SessionInput.UserMsg("hello", "user", true)
        )).doesNotThrowAnyException();
        assertThat(onErrorCalls).hasValue(1);
        assertThat(errorHandle.get().sessionId()).isEqualTo(session.sessionId());
    }

    @Test
    void inputRoutingObserverRespectsConfiguredTraceLevel() {
        RuntimeConfig config = TestRuntimeConfigs.basicRuntimeConfig();
        ContextManager contextManager = new ContextManager();
        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> "ok");
        AtomicInteger callbackCalls = new AtomicInteger();

        Session session = manager.start(
                "agent-default",
                "trace",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        RoutingEventLevel.FINAL,
                        event -> callbackCalls.incrementAndGet(),
                        ignored -> {}
                )
        );

        SessionHandle handle = manager.handleFor(session.sessionId());
        manager.onRoutingEvent(new RoutingEvent.InputResult(
                handle,
                java.util.Optional.empty(),
                InputRoutingEvent.OutCome.DENIED_POLICY,
                InputRoutingEvent.Phase.ATTEMPT,
                "attempt",
                "UserMsg",
                "user"
        ));
        manager.onRoutingEvent(new RoutingEvent.InputResult(
                handle,
                java.util.Optional.empty(),
                InputRoutingEvent.OutCome.DENIED_POLICY,
                InputRoutingEvent.Phase.FINAL,
                "final",
                "UserMsg",
                "user"
        ));

        assertThat(callbackCalls).hasValue(1);
    }
}
