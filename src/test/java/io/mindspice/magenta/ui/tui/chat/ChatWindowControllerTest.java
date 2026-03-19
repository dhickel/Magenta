package io.mindspice.magenta.ui.tui.chat;

import casciian.TWindow;
import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.support.TestRuntimeConfigs;
import io.mindspice.magenta.ui.TerminalUiCallbacks;
import io.mindspice.magenta.ui.TerminalUiConfig;
import io.mindspice.magenta.ui.slash.SlashCommandRegistry;
import io.mindspice.magenta.ui.tui.TuiApplication;
import io.mindspice.magenta.ui.tui.TuiThemeRegistry;
import io.mindspice.magenta.ui.tui.WorkspaceHost;
import io.mindspice.magenta.ui.tui.windows.WorkspaceTWindow;
import io.mindspice.magenta.ui.tui.workspace.WindowKindFactory;
import io.mindspice.magenta.ui.tui.workspace.WindowKindFactoryRegistry;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceConfigLoader;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceDefinition;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceOverlayStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ChatWindowControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void slashRegistryIncludesCloseCommandAndHelpEntry() throws Exception {
        RuntimeConfig runtimeConfig = runtimeConfig(tempDir);
        Magenta magenta = new Magenta(runtimeConfig);
        WorkspaceHost host = newHost(tempDir);
        TuiApplication app = new TuiApplication(new TuiThemeRegistry(tempDir), host);

        try {
            SessionHandle handle = magenta.startBaseSession("chat-help");
            ChatWindow window = new ChatWindow(app, "Chat", 60, 18, new NoOpChatController(), uiConfig());
            ChatWindowController controller = new ChatWindowController(
                    magenta,
                    uiConfig(),
                    new ChatBinding(handle, input -> { }, () -> magenta.contextUsage(handle)),
                    window,
                    () -> { }
            );
            window.setController(controller);

            SlashCommandRegistry registry = slashRegistry(controller);
            assertThat(registry.find("help")).isPresent();
            assertThat(registry.find("close"))
                    .hasValueSatisfying(spec -> {
                        assertThat(spec.help()).isEqualTo("Close this chat window and its backing session");
                        assertThat(spec.usage()).isEqualTo("/close");
                    });

            magenta.closeSession(handle);
            controller.shutdown();
            window.close();
        } finally {
            app.restoreConsole();
        }
    }

    @Test
    void requestCloseWindowClosesBackingSessionAndController() throws Exception {
        RuntimeConfig runtimeConfig = runtimeConfig(tempDir);
        Magenta magenta = new Magenta(runtimeConfig);
        WorkspaceHost host = newHost(tempDir);
        TuiApplication app = new TuiApplication(new TuiThemeRegistry(tempDir), host);

        try {
            SessionHandle handle = magenta.startBaseSession("chat-close");
            AtomicInteger closeCalls = new AtomicInteger();
            ChatWindow window = new ChatWindow(app, "Chat", 60, 18, new NoOpChatController(), uiConfig());
            final ChatWindowController[] holder = new ChatWindowController[1];
            ChatWindowController controller = new ChatWindowController(
                    magenta,
                    uiConfig(),
                    new ChatBinding(handle, input -> { }, () -> magenta.contextUsage(handle)),
                    window,
                    () -> {
                        closeCalls.incrementAndGet();
                        holder[0].shutdown();
                        magenta.closeSession(handle);
                        window.close();
                    }
            );
            holder[0] = controller;
            window.setController(controller);

            assertThat(controller.requestCloseWindow()).isEqualTo("Closed chat window and backing session");
            assertThat(closeCalls.get()).isEqualTo(1);
            assertThat(handle.isActive()).isFalse();
            assertThat(controller.submitComposerText("after-close")).isFalse();
        } finally {
            app.restoreConsole();
        }
    }

    @Test
    void requestCloseWindowRefusesWhenTurnIsInProgress() throws Exception {
        RuntimeConfig runtimeConfig = runtimeConfig(tempDir);
        Magenta magenta = new Magenta(runtimeConfig);
        WorkspaceHost host = newHost(tempDir);
        TuiApplication app = new TuiApplication(new TuiThemeRegistry(tempDir), host);

        try {
            SessionHandle handle = magenta.startBaseSession("chat-busy");
            AtomicInteger closeCalls = new AtomicInteger();
            ChatWindow window = new ChatWindow(app, "Chat", 60, 18, new NoOpChatController(), uiConfig());
            ChatWindowController controller = new ChatWindowController(
                    magenta,
                    uiConfig(),
                    new ChatBinding(handle, input -> { }, () -> magenta.contextUsage(handle)),
                    window,
                    closeCalls::incrementAndGet
            );
            window.setController(controller);
            setTurnBusy(controller, true);

            assertThat(controller.requestCloseWindow()).isEqualTo("Cannot close chat window while a turn is in progress");
            assertThat(closeCalls.get()).isZero();
            assertThat(handle.isActive()).isTrue();

            magenta.closeSession(handle);
            controller.shutdown();
            window.close();
        } finally {
            app.restoreConsole();
        }
    }

    private RuntimeConfig runtimeConfig(Path tempDir) {
        RuntimeConfig base = TestRuntimeConfigs.basicRuntimeConfig();
        return new RuntimeConfig(
                tempDir,
                tempDir,
                base.baseAgentId(),
                base.compactionAgentId(),
                base.maxTurns(),
                base.sessionQueueCapacity(),
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

    private TerminalUiConfig uiConfig() {
        return new TerminalUiConfig(
                new TerminalUiConfig.Session("terminal", SessionParams.ofStreaming(false), RoutingEventLevel.NONE),
                TerminalUiConfig.Rendering.defaults(),
                new TerminalUiConfig.Behavior(null, "you> ", "cli-system"),
                new TerminalUiConfig.Observability(false),
                new TerminalUiConfig.Security(TerminalUiConfig.SecurityEventVisibility.DENIALS_ONLY),
                new TerminalUiConfig.ToolOutput(TerminalUiConfig.ToolOutputFormat.COMPACT_SUMMARY),
                new TerminalUiConfig.Prompts(true, 240),
                TerminalUiCallbacks.defaults()
        );
    }

    private WorkspaceHost newHost(Path tempDir) {
        WorkspaceDefinition workspace = new WorkspaceDefinition(
                WorkspaceConfigLoader.SUPPORTED_SCHEMA_VERSION,
                "default",
                "Default",
                WorkspaceDefinition.LayoutMode.TILED,
                List.of(new WorkspaceDefinition.WindowDescriptor(
                        "chat-main",
                        "chat",
                        "Chat",
                        false,
                        new WorkspaceDefinition.Geometry(4, 2, 30, 10),
                        Map.of()
                ))
        );
        return new WorkspaceHost(
                Map.of(workspace.id(), workspace),
                WindowKindFactoryRegistry.fromFactories(List.of(testFactory())),
                new WorkspaceOverlayStore(tempDir),
                event -> { },
                error -> { throw error; }
        );
    }

    private WindowKindFactory testFactory() {
        return new WindowKindFactory() {
            @Override
            public String kind() {
                return "chat";
            }

            @Override
            public TWindow create(WorkspaceDefinition.WindowDescriptor descriptor, TuiApplication app) {
                WorkspaceDefinition.Geometry geometry = descriptor.geometry();
                return new TestWorkspaceWindow(
                        app,
                        descriptor.title(),
                        geometry == null ? 30 : geometry.width(),
                        geometry == null ? 10 : geometry.height()
                );
            }
        };
    }

    private SlashCommandRegistry slashRegistry(ChatWindowController controller) throws Exception {
        Field slashRegistryField = ChatWindowController.class.getDeclaredField("slashRegistry");
        slashRegistryField.setAccessible(true);
        return (SlashCommandRegistry) slashRegistryField.get(controller);
    }

    private void setTurnBusy(ChatWindowController controller, boolean busy) throws Exception {
        Field turnBusyField = ChatWindowController.class.getDeclaredField("turnBusy");
        turnBusyField.setAccessible(true);
        AtomicBoolean turnBusy = (AtomicBoolean) turnBusyField.get(controller);
        turnBusy.set(busy);
    }

    private static final class NoOpChatController implements ChatController {
        @Override
        public boolean submitComposerText(String text) {
            return false;
        }

        @Override
        public void requestAbort() {
        }

        @Override
        public String requestCloseWindow() {
            return "not used";
        }
    }

    private static final class TestWorkspaceWindow extends WorkspaceTWindow {
        private TestWorkspaceWindow(TuiApplication application, String title, int width, int height) {
            super(application, title, width, height);
        }
    }
}
