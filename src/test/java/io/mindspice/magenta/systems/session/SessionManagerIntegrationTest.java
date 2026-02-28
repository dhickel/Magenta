package io.mindspice.magenta.systems.session;

import io.mindspice.magenta.support.TestRuntimeConfigs;
import io.mindspice.magenta.systems.config.RuntimeConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionManagerIntegrationTest {

    @Test
    void startResolvesPromptsAndRoutesOnlyAllowedInputs() {
        RuntimeConfig config = TestRuntimeConfigs.basicRuntimeConfig();
        ContextManager contextManager = new ContextManager();
        AtomicInteger submitted = new AtomicInteger();
        List<SessionInput> received = new ArrayList<>();

        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> {
            submitted.incrementAndGet();
            received.add(input);
            return "ok";
        });

        Session session = manager.start("agent-default", "alpha", SessionConfig.defaults());
        assertThat(session.context().snapshot())
                .first()
                .isEqualTo(new SessionMessage.SystemMsg("Base prompt\n\nAgent prompt"));

        SessionRoutePolicy policy = new SessionRoutePolicy(
                java.util.Set.of(SessionInput.MessageInputKind.BUS_MESSAGE),
                java.util.Set.of(),
                java.util.Set.of("bus-A")
        );

        Consumer<SessionInput.MessageInput> consumer = manager.messageConsumerFor(session.sessionId(), policy);
        consumer.accept(new SessionInput.UserMessageInput("skip", "user", "", java.util.Map.of(), true));
        consumer.accept(new SessionInput.BusMessageInput("take", "bus-A", "", java.util.Map.of(), true));

        assertThat(submitted).hasValue(1);
        assertThat(received).hasSize(1);
        assertThat(received.getFirst())
                .isInstanceOf(SessionInput.BusMessageInput.class)
                .extracting(SessionInput::text)
                .isEqualTo("take");
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
}
