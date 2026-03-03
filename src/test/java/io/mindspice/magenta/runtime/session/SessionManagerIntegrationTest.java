package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.context.ContextManager;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.runtime.tools.ToolResult;
import io.mindspice.magenta.support.TestRuntimeConfigs;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionManagerIntegrationTest {

    @Test
    void startResolvesPromptsAndReturnsHandleWithConfigView() {
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
                .first()
                .isEqualTo(new ContextElement.SystemMsg("Base prompt\n\nAgent prompt"));

        SessionHandle handle = manager.handleFor(session.sessionId());
        assertThat(handle.sessionId()).isEqualTo(session.sessionId());
        assertThat(handle.isActive()).isTrue();
        assertThat(handle.configView().streamingEnabled()).isFalse();
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

        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> {
            throw new IllegalStateException("simulated-execution-failure");
        });

        SessionConfig cfg = new SessionConfig(
                SessionParams.ofStreaming(true),
                request -> ToolResult.notHandled(request.toolCall()),
                ignored -> onErrorCalls.incrementAndGet()
        );
        Session session = manager.start("agent-default", "router-error", cfg);

        assertThatCode(() -> manager.submitFromRoute(
                session.sessionId(),
                new SessionInput.UserMsg("hello", "user", true)
        )).doesNotThrowAnyException();
        assertThat(onErrorCalls).hasValue(1);
    }
}
