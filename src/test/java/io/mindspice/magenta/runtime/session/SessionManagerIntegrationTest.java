package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextManager;
import io.mindspice.magenta.runtime.session.config.SessionConfig;
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
                SessionConfig.builder().streamingEnabled(false).build()
        );

        assertThat(session.context().snapshot())
                .first()
                .isEqualTo(new SessionMessage.SystemMsg("Base prompt\n\nAgent prompt"));

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

        Session source = manager.start("agent-default", "source", SessionConfig.defaults());
        source.context().append(new SessionMessage.UserMsg("from-source"));

        SessionConfig overrideConfig = SessionConfig.builder().blockingOnly(true).build();
        Session fork = manager.fork(source.sessionId(), "fork", overrideConfig);

        source.context().append(new SessionMessage.UserMsg("source-after-fork"));

        assertThat(fork.sessionConfig()).isSameAs(overrideConfig);
        assertThat(fork.context().snapshot())
                .contains(new SessionMessage.UserMsg("from-source"))
                .doesNotContain(new SessionMessage.UserMsg("source-after-fork"));
    }

    @Test
    void closeRemovesSessionFromManager() {
        RuntimeConfig config = TestRuntimeConfigs.basicRuntimeConfig();
        ContextManager contextManager = new ContextManager();
        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> "ok");

        Session session = manager.start("agent-default", "closable", SessionConfig.defaults());
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

        SessionConfig cfg = SessionConfig.builder().onError(err -> onErrorCalls.incrementAndGet()).build();
        Session session = manager.start("agent-default", "router-error", cfg);

        assertThatCode(() -> manager.submitFromRoute(
                session.sessionId(),
                new SessionInput.UserMessageInput("hello", "user", "", java.util.Map.of(), true)
        )).doesNotThrowAnyException();
        assertThat(onErrorCalls).hasValue(1);
    }
}
