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

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceHostTest {

    @TempDir
    Path tempDir;

    @Test
    void trueClosePreservesClosedGeometryAndReopensAtNormalBounds() throws Exception {
        WorkspaceDefinition.Geometry initialGeometry = new WorkspaceDefinition.Geometry(4, 2, 30, 10);
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
                        initialGeometry,
                        Map.of()
                ))
        );
        WorkspaceOverlayStore store = new WorkspaceOverlayStore(tempDir);
        WorkspaceHost host = new WorkspaceHost(
                Map.of(workspace.id(), workspace),
                WindowKindFactoryRegistry.fromFactories(List.of(testFactory())),
                store,
                event -> { },
                error -> { throw error; }
        );

        TuiApplication app = new TuiApplication(new TuiThemeRegistry(tempDir), host);
        try {
            host.switchWorkspace(workspace.id(), app);
            assertThat(host.openWindow("chat-main", app)).isEqualTo("Opened window 'Chat'");

            TWindow window = host.firstWindowByKind("chat");
            assertThat(window).isNotNull();
            WorkspaceDefinition.Geometry normalGeometry = relativeGeometry(window, app);
            assertThat(normalGeometry).isEqualTo(initialGeometry);

            host.toggleActiveWindowZoom(app);
            WorkspaceDefinition.Geometry maximizedGeometry = relativeGeometry(window, app);
            assertThat(maximizedGeometry).isNotEqualTo(normalGeometry);

            assertThat(host.closeActiveWindow(app)).isEqualTo("Closed window 'Chat'");
            assertThat(host.firstWindowByKind("chat")).isNull();

            host.saveActiveWorkspaceSnapshot(app);
            WorkspaceOverlayStore.Overlay closedOverlay = store.load("default");
            WorkspaceOverlayStore.OverlayWindowState closedState = closedOverlay.windows().get("chat-main");
            assertThat(closedState.visible()).isFalse();
            assertThat(closedState.maximized()).isFalse();
            assertThat(closedState.geometry()).isEqualTo(maximizedGeometry);
            assertThat(closedState.normalGeometry()).isEqualTo(normalGeometry);

            assertThat(host.openWindow("chat-main", app)).isEqualTo("Opened window 'Chat'");
            TWindow reopened = host.firstWindowByKind("chat");
            assertThat(reopened).isNotNull();
            assertThat(relativeGeometry(reopened, app)).isEqualTo(normalGeometry);

            host.saveActiveWorkspaceSnapshot(app);
            WorkspaceOverlayStore.Overlay reopenedOverlay = store.load("default");
            WorkspaceOverlayStore.OverlayWindowState reopenedState = reopenedOverlay.windows().get("chat-main");
            assertThat(reopenedState.visible()).isTrue();
            assertThat(reopenedState.maximized()).isFalse();
            assertThat(reopenedState.geometry()).isEqualTo(normalGeometry);
            assertThat(reopenedState.normalGeometry()).isEqualTo(normalGeometry);
        } finally {
            app.restoreConsole();
        }
    }

    @Test
    void windowMenuEntriesTrackVisibleHiddenAndClosedSelectorStates() throws Exception {
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
            host.switchWorkspace(workspace.id(), app);
            assertThat(host.windowMenuEntries()).containsExactly(
                    new WorkspaceHost.WindowMenuEntry("chat-main", "Chat", false, false, false)
            );

            host.focusOrRestoreWindow("chat-main", app);
            assertThat(host.windowMenuEntries()).containsExactly(
                    new WorkspaceHost.WindowMenuEntry("chat-main", "Chat", true, false, true)
            );

            host.hideActiveWindow(app);
            assertThat(host.windowMenuEntries()).containsExactly(
                    new WorkspaceHost.WindowMenuEntry("chat-main", "Chat", false, false, true)
            );

            host.focusOrRestoreWindow("chat-main", app);
            host.toggleActiveWindowZoom(app);
            assertThat(host.windowMenuEntries()).containsExactly(
                    new WorkspaceHost.WindowMenuEntry("chat-main", "Chat", true, true, true)
            );

            host.closeActiveWindow(app);
            assertThat(host.windowMenuEntries()).containsExactly(
                    new WorkspaceHost.WindowMenuEntry("chat-main", "Chat", false, false, false)
            );
        } finally {
            app.restoreConsole();
        }
    }

    @Test
    void maximizedWindowReloadRestoresToNormalGeometry() throws Exception {
        WorkspaceDefinition.Geometry initialGeometry = new WorkspaceDefinition.Geometry(4, 2, 30, 10);
        WorkspaceDefinition workspace = workspace(
                WorkspaceDefinition.LayoutMode.TILED,
                new WorkspaceDefinition.WindowDescriptor("chat-main", "chat", "Chat", false, initialGeometry, Map.of())
        );
        WorkspaceOverlayStore store = new WorkspaceOverlayStore(tempDir);

        WorkspaceHost firstHost = newHost(workspace, store);
        TuiApplication firstApp = new TuiApplication(new TuiThemeRegistry(tempDir), firstHost);
        try {
            firstHost.switchWorkspace(workspace.id(), firstApp);
            assertThat(firstHost.openWindow("chat-main", firstApp)).isEqualTo("Opened window 'Chat'");

            TWindow firstWindow = firstHost.firstWindowByKind("chat");
            assertThat(firstWindow).isNotNull();
            assertThat(relativeGeometry(firstWindow, firstApp)).isEqualTo(initialGeometry);

            assertThat(firstHost.toggleActiveWindowZoom(firstApp)).isEqualTo("Maximized active window");
            firstHost.saveActiveWorkspaceSnapshot(firstApp);
        } finally {
            firstApp.restoreConsole();
        }

        WorkspaceHost secondHost = newHost(workspace, store);
        TuiApplication secondApp = new TuiApplication(new TuiThemeRegistry(tempDir), secondHost);
        try {
            secondHost.switchWorkspace(workspace.id(), secondApp);
            TWindow reloaded = secondHost.firstWindowByKind("chat");
            assertThat(reloaded).isNotNull();
            assertThat(secondHost.windowMenuEntries()).containsExactly(
                    new WorkspaceHost.WindowMenuEntry("chat-main", "Chat", true, true, true)
            );

            assertThat(secondHost.toggleActiveWindowZoom(secondApp)).isEqualTo("Restored active window");
            assertThat(relativeGeometry(reloaded, secondApp)).isEqualTo(initialGeometry);
        } finally {
            secondApp.restoreConsole();
        }
    }

    @Test
    void hiddenMaximizedWindowRestoresFromMenuStillMaximized() throws Exception {
        WorkspaceDefinition.Geometry initialGeometry = new WorkspaceDefinition.Geometry(4, 2, 30, 10);
        WorkspaceDefinition workspace = workspace(
                WorkspaceDefinition.LayoutMode.TILED,
                new WorkspaceDefinition.WindowDescriptor("chat-main", "chat", "Chat", false, initialGeometry, Map.of())
        );
        WorkspaceHost host = newHost(workspace, new WorkspaceOverlayStore(tempDir));

        TuiApplication app = new TuiApplication(new TuiThemeRegistry(tempDir), host);
        try {
            host.switchWorkspace(workspace.id(), app);
            host.openWindow("chat-main", app);
            TWindow window = host.firstWindowByKind("chat");
            assertThat(window).isNotNull();

            host.toggleActiveWindowZoom(app);
            WorkspaceDefinition.Geometry maximizedGeometry = relativeGeometry(window, app);

            assertThat(host.hideActiveWindow(app)).isEqualTo("Hid active window");
            assertThat(host.windowMenuEntries()).containsExactly(
                    new WorkspaceHost.WindowMenuEntry("chat-main", "Chat", false, true, true)
            );

            assertThat(host.focusOrRestoreWindow("chat-main", app)).isEqualTo("Restored window 'Chat'");
            assertThat(host.windowMenuEntries()).containsExactly(
                    new WorkspaceHost.WindowMenuEntry("chat-main", "Chat", true, true, true)
            );
            assertThat(relativeGeometry(window, app)).isEqualTo(maximizedGeometry);

            assertThat(host.toggleActiveWindowZoom(app)).isEqualTo("Restored active window");
            assertThat(relativeGeometry(window, app)).isEqualTo(initialGeometry);
        } finally {
            app.restoreConsole();
        }
    }

    @Test
    void applyingLayoutNormalizesVisibleWindowsAndClearsMaximizedState() throws Exception {
        WorkspaceDefinition workspace = workspace(
                WorkspaceDefinition.LayoutMode.TILED,
                new WorkspaceDefinition.WindowDescriptor("chat-main", "chat", "Chat", true,
                        new WorkspaceDefinition.Geometry(4, 2, 30, 10), Map.of()),
                new WorkspaceDefinition.WindowDescriptor("events-main", "chat", "Events", true,
                        new WorkspaceDefinition.Geometry(10, 5, 28, 9), Map.of())
        );
        WorkspaceOverlayStore store = new WorkspaceOverlayStore(tempDir);
        WorkspaceHost host = newHost(workspace, store);

        TuiApplication app = new TuiApplication(new TuiThemeRegistry(tempDir), host);
        try {
            host.switchWorkspace(workspace.id(), app);
            TWindow firstWindow = host.firstWindowByKind("chat");
            assertThat(firstWindow).isNotNull();

            host.toggleActiveWindowZoom(app);
            assertThat(host.windowMenuEntries())
                    .extracting(WorkspaceHost.WindowMenuEntry::maximized)
                    .containsExactlyInAnyOrder(true, false);

            host.applyWorkspaceLayoutMode(app, WorkspaceDefinition.LayoutMode.CASCADE);
            host.saveActiveWorkspaceSnapshot(app);

            WorkspaceOverlayStore.Overlay overlay = store.load(workspace.id());
            assertThat(overlay).isNotNull();
            assertThat(overlay.windows()).hasSize(2);
            assertThat(overlay.windows().values())
                    .allSatisfy(windowState -> {
                        assertThat(windowState.visible()).isTrue();
                        assertThat(windowState.maximized()).isFalse();
                        assertThat(windowState.geometry()).isEqualTo(windowState.normalGeometry());
                    });
        } finally {
            app.restoreConsole();
        }
    }

    private WorkspaceHost newHost(WorkspaceDefinition workspace, WorkspaceOverlayStore store) {
        return new WorkspaceHost(
                Map.of(workspace.id(), workspace),
                WindowKindFactoryRegistry.fromFactories(List.of(testFactory())),
                store,
                event -> { },
                error -> { throw error; }
        );
    }

    private WorkspaceDefinition workspace(
            WorkspaceDefinition.LayoutMode layoutMode,
            WorkspaceDefinition.WindowDescriptor... descriptors
    ) {
        return new WorkspaceDefinition(
                WorkspaceConfigLoader.SUPPORTED_SCHEMA_VERSION,
                "default",
                "Default",
                layoutMode,
                List.of(descriptors)
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

    private WorkspaceDefinition.Geometry relativeGeometry(TWindow window, TuiApplication app) {
        return new WorkspaceDefinition.Geometry(
                window.getX(),
                Math.max(0, window.getY() - app.getDesktopTop()),
                window.getWidth(),
                window.getHeight()
        );
    }

    private static final class TestWorkspaceWindow extends WorkspaceTWindow {
        private TestWorkspaceWindow(TuiApplication application, String title, int width, int height) {
            super(application, title, width, height);
        }
    }
}
