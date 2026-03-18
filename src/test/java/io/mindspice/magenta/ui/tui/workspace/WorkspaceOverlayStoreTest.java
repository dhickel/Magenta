package io.mindspice.magenta.ui.tui.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
                                new WorkspaceDefinition.Geometry(1, 2, 80, 24)
                        ),
                        "events-main", new WorkspaceOverlayStore.OverlayWindowState(
                                Boolean.FALSE,
                                Boolean.TRUE,
                                new WorkspaceDefinition.Geometry(10, 4, 60, 12)
                        )
                )
        );

        store.save("default", overlay);
        WorkspaceOverlayStore.Overlay loaded = store.load("default");

        assertThat(loaded).isNotNull();
        assertThat(loaded.activeWindowId()).isEqualTo("chat-main");
        assertThat(loaded.windows().get("chat-main").geometry())
                .isEqualTo(new WorkspaceDefinition.Geometry(1, 2, 80, 24));
        assertThat(loaded.windows().get("events-main").maximized()).isTrue();
    }
}
