package io.mindspice.magenta.ui.tui;

import casciian.TWindow;
import io.mindspice.magenta.ui.tui.windows.WorkspaceTWindow;
import io.mindspice.magenta.ui.tui.workspace.WindowKindFactory;
import io.mindspice.magenta.ui.tui.workspace.WindowKindFactoryRegistry;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceConfigLoader;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceDefinition;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceOverlayStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

class TuiApplicationWindowMenuLabelTest {

    @TempDir
    Path tempDir;

    @Test
    void rendersVisibleWindowLabelWithoutSuffix() {
        assertThat(TuiApplication.windowMenuLabel(
                new WorkspaceHost.WindowMenuEntry("chat-main", "Chat", true, false, true)
        )).isEqualTo("Focus/Restore: Chat");
    }

    @Test
    void rendersHiddenWindowLabelWithHiddenSuffix() {
        assertThat(TuiApplication.windowMenuLabel(
                new WorkspaceHost.WindowMenuEntry("events-main", "Events", false, false, true)
        )).isEqualTo("Focus/Restore: Events [hidden]");
    }

    @Test
    void rendersMaximizedWindowLabelWithMaxSuffix() {
        assertThat(TuiApplication.windowMenuLabel(
                new WorkspaceHost.WindowMenuEntry("doc-main", "Document", true, true, true)
        )).isEqualTo("Focus/Restore: Document [max]");
    }

    @Test
    void rebuildMenuShellIsSafeWhenNoSubmenuIsOpen() throws Exception {
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
        WorkspaceHost host = new WorkspaceHost(
                Map.of(workspace.id(), workspace),
                WindowKindFactoryRegistry.fromFactories(List.of(testFactory())),
                new WorkspaceOverlayStore(tempDir),
                event -> { },
                error -> { throw error; }
        );

        TuiApplication app = new TuiApplication(new TuiThemeRegistry(tempDir), host);
        try {
            assertThatCode(app::rebuildMenuShell).doesNotThrowAnyException();
            assertThatCode(app::rebuildMenuShell).doesNotThrowAnyException();
        } finally {
            app.restoreConsole();
        }
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

    private static final class TestWorkspaceWindow extends WorkspaceTWindow {
        private TestWorkspaceWindow(TuiApplication application, String title, int width, int height) {
            super(application, title, width, height);
        }
    }
}
