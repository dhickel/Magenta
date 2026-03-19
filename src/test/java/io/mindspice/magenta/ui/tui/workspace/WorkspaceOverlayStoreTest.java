package io.mindspice.magenta.ui.tui.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceOverlayStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsNullWhenOverlayIsMissing() {
        WorkspaceOverlayStore store = new WorkspaceOverlayStore(tempDir);

        WorkspaceOverlayStore.Overlay loaded = store.load("default");

        assertThat(loaded).isNull();
    }

    @Test
    void savesAndLoadsOverlayRoundTrip() {
        WorkspaceOverlayStore store = new WorkspaceOverlayStore(tempDir);
        WorkspaceOverlayStore.Overlay overlay = new WorkspaceOverlayStore.Overlay(
                "chat-main",
                Map.of(
                        "chat-main", new WorkspaceOverlayStore.OverlayWindowState(
                                Boolean.TRUE,
                                Boolean.FALSE,
                                new WorkspaceDefinition.Geometry(1, 2, 80, 24),
                                new WorkspaceDefinition.Geometry(1, 2, 80, 24)
                        ),
                        "events-main", new WorkspaceOverlayStore.OverlayWindowState(
                                Boolean.FALSE,
                                Boolean.TRUE,
                                new WorkspaceDefinition.Geometry(10, 4, 60, 12),
                                new WorkspaceDefinition.Geometry(4, 3, 50, 11)
                        )
                )
        );

        store.save("default", overlay);
        WorkspaceOverlayStore.Overlay loaded = store.load("default");

        assertThat(loaded).isNotNull();
        assertThat(loaded.activeWindowId()).isEqualTo("chat-main");
        assertThat(loaded.windows().get("chat-main").geometry())
                .isEqualTo(new WorkspaceDefinition.Geometry(1, 2, 80, 24));
        assertThat(loaded.windows().get("chat-main").normalGeometry())
                .isEqualTo(new WorkspaceDefinition.Geometry(1, 2, 80, 24));
        assertThat(loaded.windows().get("events-main").maximized()).isTrue();
        assertThat(loaded.windows().get("events-main").normalGeometry())
                .isEqualTo(new WorkspaceDefinition.Geometry(4, 3, 50, 11));
    }

    @Test
    void loadsLegacyOverlayWithoutNormalGeometryUsingGeometryAsRestoreTarget() throws Exception {
        WorkspaceOverlayStore store = new WorkspaceOverlayStore(tempDir);
        Path overlayFile = tempDir.resolve(".magenta").resolve("ui").resolve("workspaces").resolve("default.yaml");
        Files.createDirectories(overlayFile.getParent());
        Files.writeString(overlayFile, """
                activeWindowId: chat-main
                windows:
                  - id: chat-main
                    visible: true
                    maximized: true
                    geometry:
                      x: 0
                      y: 0
                      width: 96
                      height: 26
                """, StandardCharsets.UTF_8);

        WorkspaceOverlayStore.Overlay loaded = store.load("default");

        assertThat(loaded).isNotNull();
        assertThat(loaded.windows().get("chat-main").geometry())
                .isEqualTo(new WorkspaceDefinition.Geometry(0, 0, 96, 26));
        assertThat(loaded.windows().get("chat-main").normalGeometry())
                .isEqualTo(new WorkspaceDefinition.Geometry(0, 0, 96, 26));
    }

    @Test
    void roundTripsDistinctFullscreenAndRestoreBoundsForMaximizedWindow() {
        WorkspaceOverlayStore store = new WorkspaceOverlayStore(tempDir);
        WorkspaceOverlayStore.Overlay overlay = new WorkspaceOverlayStore.Overlay(
                "chat-main",
                Map.of(
                        "chat-main", new WorkspaceOverlayStore.OverlayWindowState(
                                Boolean.TRUE,
                                Boolean.TRUE,
                                new WorkspaceDefinition.Geometry(0, 0, 160, 40),
                                new WorkspaceDefinition.Geometry(4, 2, 96, 26)
                        )
                )
        );

        store.save("default", overlay);
        WorkspaceOverlayStore.Overlay loaded = store.load("default");

        assertThat(loaded).isNotNull();
        assertThat(loaded.windows().get("chat-main").maximized()).isTrue();
        assertThat(loaded.windows().get("chat-main").geometry())
                .isEqualTo(new WorkspaceDefinition.Geometry(0, 0, 160, 40));
        assertThat(loaded.windows().get("chat-main").normalGeometry())
                .isEqualTo(new WorkspaceDefinition.Geometry(4, 2, 96, 26));
    }
}
