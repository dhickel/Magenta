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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    void startWithLaunchTaskOverrideAppendsOverrideAsFinalSystemMessage() {
        RuntimeConfig config = TestRuntimeConfigs.basicRuntimeConfig();
        ContextManager contextManager = new ContextManager();
        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> "ok");

        Session session = manager.start(
                "agent-default",
                "override",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                ),
                "default-task"
        );

        assertThat(session.context().snapshot())
                .startsWith(
                        new ContextElement.SystemMsg("Base prompt"),
                        new ContextElement.SystemMsg("Agent prompt"),
                        new ContextElement.SystemMsg("Configured task")
                )
                .doesNotContain(new ContextElement.SystemMsg("Override task"));

        SessionSettingsView settings = manager.settingsFor(session.sessionId());
        assertThat(settings.resolvedSystemPrompt()).isEqualTo("Base prompt\n\nAgent prompt\n\nConfigured task");
    }

    @Test
    void applyTaskReplacesExistingTaskSystemPromptAndPreservesConversation() {
        RuntimeConfig config = TestRuntimeConfigs.basicRuntimeConfig();
        ContextManager contextManager = new ContextManager();
        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> "ok");

        Session session = manager.start(
                "agent-default",
                "apply-task",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );
        session.context().append(new ContextElement.UserMsg("hello"));

        String applied = manager.applyTask(session.sessionId(), "default-task");
        assertThat(applied).isEqualTo("default-task");
        assertThat(manager.activeTaskId(session.sessionId())).isEqualTo("default-task");
        assertThat(session.context().snapshot())
                .startsWith(
                        new ContextElement.SystemMsg("Base prompt"),
                        new ContextElement.SystemMsg("Agent prompt"),
                        new ContextElement.SystemMsg("Configured task")
                )
                .contains(new ContextElement.UserMsg("hello"));
    }

    @Test
    void applyAnonTaskPromptReplacesTaskSystemPromptAndPreservesConversation() {
        RuntimeConfig config = TestRuntimeConfigs.basicRuntimeConfig();
        ContextManager contextManager = new ContextManager();
        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> "ok");

        Session session = manager.start(
                "agent-default",
                "apply-anon-task",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );
        session.context().append(new ContextElement.UserMsg("hello"));

        String applied = manager.applyAnonTaskPrompt(session.sessionId(), "Stay autonomous and continue until fully done.");
        assertThat(applied).isEqualTo("anon task");
        assertThat(manager.activeTaskId(session.sessionId())).isEqualTo("anon task");
        assertThat(session.context().snapshot())
                .startsWith(
                        new ContextElement.SystemMsg("Base prompt"),
                        new ContextElement.SystemMsg("Agent prompt"),
                        new ContextElement.SystemMsg("Stay autonomous and continue until fully done.")
                )
                .contains(new ContextElement.UserMsg("hello"));
    }

    @Test
    void clearConversationKeepSystemMessagesDropsChatHistoryAndRetainsTaskPrompts() {
        RuntimeConfig config = TestRuntimeConfigs.basicRuntimeConfig();
        ContextManager contextManager = new ContextManager();
        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> "ok");

        Session session = manager.start(
                "agent-default",
                "clear-history",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );
        manager.applyTask(session.sessionId(), "default-task");
        session.context().append(new ContextElement.UserMsg("hello"));
        session.context().append(new ContextElement.AssistantMsg("hi", java.util.List.of()));
        session.context().append(new ContextElement.SystemMsg("late-system"));

        var retained = manager.clearConversationKeepSystemMessages(session.sessionId());

        assertThat(retained)
                .containsExactly(
                        new ContextElement.SystemMsg("Base prompt"),
                        new ContextElement.SystemMsg("Agent prompt"),
                        new ContextElement.SystemMsg("Configured task")
                );
        assertThat(session.context().snapshot())
                .containsExactly(
                        new ContextElement.SystemMsg("Base prompt"),
                        new ContextElement.SystemMsg("Agent prompt"),
                        new ContextElement.SystemMsg("Configured task")
                );
        assertThat(manager.activeTaskId(session.sessionId())).isEqualTo("default-task");
    }

    @Test
    void switchModelUpdatesSessionModelConfigAndKeepsContext() {
        RuntimeConfig base = TestRuntimeConfigs.basicRuntimeConfig();
        RuntimeConfig.ModelConfig altModel = new RuntimeConfig.ModelConfig(
                "model-alt",
                "test-provider",
                "alt-model",
                "http://localhost:11435",
                8192,
                8192,
                800,
                0.2,
                "rolling_window",
                "cl100k_base",
                true,
                true,
                true
        );
        Map<String, RuntimeConfig.ModelConfig> models = new LinkedHashMap<>(base.modelsById());
        models.put(altModel.id(), altModel);
        RuntimeConfig config = new RuntimeConfig(
                base.rootDir(),
                base.workspaceRoot(),
                base.baseAgentId(),
                base.compactionAgentId(),
                base.maxTurns(),
                base.sessionQueueCapacity(),
                base.maxToolOutputBytes(),
                base.maxFileReadLines(),
                base.maxSqlRows(),
                base.modelRequestTimeoutMs(),
                base.toolLoopGuard(),
                models,
                base.agentsById(),
                base.promptsById(),
                base.tasksById(),
                base.workflowsById(),
                base.security(),
                base.terminal(),
                base.observability()
        );
        ContextManager contextManager = new ContextManager();
        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> "ok");

        Session session = manager.start(
                "agent-default",
                "switch-model",
                new SessionConfig(
                        SessionParams.ofStreaming(true),
                        request -> ToolResult.notHandled(request.toolCall()),
                        ignored -> {}
                )
        );
        session.context().append(new ContextElement.UserMsg("retain-me"));

        RuntimeConfig.ModelConfig switched = manager.switchModel(session.sessionId(), "model-alt");

        assertThat(switched.id()).isEqualTo("model-alt");
        assertThat(manager.resume(session.sessionId()).modelConfig().id()).isEqualTo("model-alt");
        assertThat(manager.settingsFor(session.sessionId()).modelName()).isEqualTo("alt-model");
        assertThat(manager.resume(session.sessionId()).context().snapshot())
                .contains(new ContextElement.UserMsg("retain-me"));
    }

    @Test
    void availableModelsReturnsEnabledModelsSortedById() {
        RuntimeConfig base = TestRuntimeConfigs.basicRuntimeConfig();
        RuntimeConfig.ModelConfig disabledModel = new RuntimeConfig.ModelConfig(
                "model-disabled",
                "test-provider",
                "disabled-model",
                "http://localhost:11436",
                1024,
                1024,
                100,
                0.0,
                "rolling_window",
                "cl100k_base",
                false,
                false,
                false
        );
        RuntimeConfig.ModelConfig secondModel = new RuntimeConfig.ModelConfig(
                "model-aaa",
                "test-provider",
                "second-model",
                "http://localhost:11437",
                2048,
                2048,
                200,
                0.1,
                "rolling_window",
                "cl100k_base",
                true,
                true,
                true
        );
        Map<String, RuntimeConfig.ModelConfig> models = new LinkedHashMap<>(base.modelsById());
        models.put(disabledModel.id(), disabledModel);
        models.put(secondModel.id(), secondModel);
        RuntimeConfig config = new RuntimeConfig(
                base.rootDir(),
                base.workspaceRoot(),
                base.baseAgentId(),
                base.compactionAgentId(),
                base.maxTurns(),
                base.sessionQueueCapacity(),
                base.maxToolOutputBytes(),
                base.maxFileReadLines(),
                base.maxSqlRows(),
                base.modelRequestTimeoutMs(),
                base.toolLoopGuard(),
                models,
                base.agentsById(),
                base.promptsById(),
                base.tasksById(),
                base.workflowsById(),
                base.security(),
                base.terminal(),
                base.observability()
        );
        SessionManager manager = new SessionManager(config, new ContextManager(), (sessionId, input) -> "ok");

        assertThat(manager.availableModels())
                .extracting(RuntimeConfig.ModelConfig::id)
                .containsExactly("model-aaa", "model-default");
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
        waitForValue(onErrorCalls, 1);
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

    @Test
    void queueFullRejectsNewestInputAndEmitsOnError() throws Exception {
        RuntimeConfig config = withQueueCapacity(TestRuntimeConfigs.basicRuntimeConfig(), 1);
        ContextManager contextManager = new ContextManager();
        AtomicInteger onErrorCalls = new AtomicInteger();
        CountDownLatch firstTurnStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTurn = new CountDownLatch(1);

        SessionManager manager = new SessionManager(config, contextManager, (sessionId, input) -> {
            if ("first".equals(input.text())) {
                firstTurnStarted.countDown();
                try {
                    releaseFirstTurn.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting in test turn handler", e);
                }
            }
            return "ok";
        });

        SessionConfig cfg = new SessionConfig(
                SessionParams.ofStreaming(true),
                request -> ToolResult.notHandled(request.toolCall()),
                error -> onErrorCalls.incrementAndGet()
        );
        Session session = manager.start("agent-default", "queue-capacity", cfg);
        SessionHandle handle = manager.handleFor(session.sessionId());

        manager.submitFromRoute(handle, new SessionInput.UserMsg("first", "user", true));
        assertThat(firstTurnStarted.await(2, TimeUnit.SECONDS)).isTrue();
        manager.submitFromRoute(handle, new SessionInput.UserMsg("second", "user", true));

        assertThatThrownBy(() -> manager.submitFromRoute(handle, new SessionInput.UserMsg("third", "user", true)))
                .isInstanceOf(SessionQueueFullException.class)
                .hasMessageContaining("queue_full");

        waitForValue(onErrorCalls, 1);
        releaseFirstTurn.countDown();
    }

    private void waitForValue(AtomicInteger value, int expected) {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(2);
        while (System.currentTimeMillis() < deadline) {
            if (value.get() == expected) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(value).hasValue(expected);
    }

    private RuntimeConfig withQueueCapacity(RuntimeConfig base, int capacity) {
        return new RuntimeConfig(
                base.rootDir(),
                base.workspaceRoot(),
                base.baseAgentId(),
                base.compactionAgentId(),
                base.maxTurns(),
                capacity,
                base.maxToolOutputBytes(),
                base.maxFileReadLines(),
                base.maxSqlRows(),
                base.modelRequestTimeoutMs(),
                base.toolLoopGuard(),
                base.modelsById(),
                base.agentsById(),
                base.promptsById(),
                base.tasksById(),
                base.workflowsById(),
                base.security(),
                base.terminal(),
                base.observability()
        );
    }
}
